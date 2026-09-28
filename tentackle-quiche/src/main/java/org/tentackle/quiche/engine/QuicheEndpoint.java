/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.quiche.engine;

import org.tentackle.log.Logger;
import org.tentackle.quiche.ffm.Quiche;
import org.tentackle.quiche.ffm.QuicheConn;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.quiche.ffm.QuicheException;
import org.tentackle.quiche.ffm.RecvInfo;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A UDP endpoint carrying any number of QUIC connections, with the event loop driving them.
 * <p>
 * This is the part quiche does not provide: the library processes packets and keeps connection state, but
 * it owns no socket, runs no loop, and arms no timer. An endpoint supplies all three.
 * <p>
 * One thread &mdash; a virtual one, named after the socket &mdash; does everything the protocol needs:
 * <ol>
 *   <li>receive a datagram, find the connection it belongs to by its destination connection ID, and hand
 *       it over;</li>
 *   <li>let a {@link QuicheServer}, if there is one, decide what to do with a datagram that belongs to no
 *       connection yet;</li>
 *   <li>run the timeouts, computing how long it may sleep from the earliest deadline of all
 *       connections;</li>
 *   <li>reap connections that quiche reports as closed.</li>
 * </ol>
 * Application threads never enter the loop. They interact with a connection directly, under its lock, and
 * {@link #wakeup()} the loop whenever they may have moved a timer.
 * <p>
 * Connections are keyed by the connection IDs <em>this</em> side issued, which is what the peer puts into
 * the destination field of every packet it sends here. That is what lets one UDP port serve any number of
 * clients, and what makes a connection survive the client changing its address. A connection is reachable
 * under several IDs at once: besides the one of the handshake, each side hands the peer spare ones, which
 * the peer switches to when it moves to another network path (see {@link QuicheConnection}).
 * <p>
 * An endpoint owns one socket, bound at creation, and on the client side possibly further ones: a
 * connection whose path stopped delivering opens one to migrate to a new source port. Each packet is sent
 * through the socket bound to the local address quiche assigned it to.
 */
public class QuicheEndpoint implements Closeable {

  /**
   * The size of the buffer an incoming datagram is received into.
   * <p>
   * Large enough for the biggest UDP payload a peer could legitimately send, so a datagram is never
   * truncated silently.
   */
  public static final int RECEIVE_BUFFER_SIZE = 65535;

  /**
   * The length of the connection IDs this endpoint issues.
   */
  public static final int CONNECTION_ID_LENGTH = 16;

  /**
   * The longest the event loop sleeps when no connection has a timer armed.
   */
  public static final long MAX_SLEEP_MILLIS = 1000;

  private static final Logger LOGGER = Logger.get(QuicheEndpoint.class);


  private final DatagramChannel channel;                         // the socket bound at creation
  private final Selector selector;
  private final InetSocketAddress localAddress;                   // the address of that socket
  private final Map<Integer, DatagramChannel> channels;           // all sockets by local port, including channel
  private final Map<Integer, QuicheConnection> channelOwners;     // the connection each extra socket belongs to
  private final Map<ConnectionIdKey, QuicheConnection> routes;    // the connections by the IDs they are reached by
  private final Set<QuicheConnection> connections;
  private final SecureRandom random;
  private final Arena arena;
  private final MemorySegment receiveBuffer;
  private final ByteBuffer receiveByteBuffer;
  private final RecvInfo recvInfo;
  private final Thread eventLoop;
  private final AtomicBoolean closing;          // guards close() against being run twice
  private volatile QuicheServer server;         // null on a pure client endpoint
  private volatile boolean closed;              // true once the socket must not be used anymore

  /**
   * Creates an endpoint bound to the given address.
   *
   * @param bindAddress the address to bind to; a port of 0 letting the system choose one
   * @throws IOException if the socket cannot be bound
   */
  public QuicheEndpoint(InetSocketAddress bindAddress) throws IOException {
    channels = new ConcurrentHashMap<>();
    channelOwners = new ConcurrentHashMap<>();
    routes = new ConcurrentHashMap<>();
    connections = ConcurrentHashMap.newKeySet();
    closing = new AtomicBoolean();
    random = new SecureRandom();
    arena = Arena.ofShared();
    receiveBuffer = arena.allocate(RECEIVE_BUFFER_SIZE);
    receiveByteBuffer = receiveBuffer.asByteBuffer();
    recvInfo = new RecvInfo(arena);

    DatagramChannel newChannel = null;
    Selector newSelector = null;
    try {
      newChannel = DatagramChannel.open();
      /*
       * Deliberately no SO_REUSEADDR. UDP has no TIME_WAIT, so there is nothing it would buy, and on
       * the BSDs it lets a second socket bind a port this one already holds - the datagrams are then
       * split between the two and a server silently answers only half of its clients.
       */
      newChannel.bind(bindAddress);
      newChannel.configureBlocking(false);
      newSelector = Selector.open();
      newChannel.register(newSelector, SelectionKey.OP_READ);
      channel = newChannel;
      selector = newSelector;
      localAddress = (InetSocketAddress) channel.getLocalAddress();
      channels.put(localAddress.getPort(), channel);
    }
    catch (IOException | RuntimeException e) {
      closeQuietly(newSelector);
      closeQuietly(newChannel);
      arena.close();
      throw e;
    }

    /*
     * A platform thread, not a virtual one. The loop spends its life in Selector.select(), which is a
     * native blocking call: on a virtual thread it pins its carrier for the whole sleep instead of
     * releasing it. A handful of endpoints would then occupy every carrier the scheduler has, and the
     * virtual threads doing the actual work - the TRIP connection handlers, the callers waiting on a
     * stream - would be starved. There is exactly one of these per endpoint, so a platform thread costs
     * nothing worth saving. It is a daemon, so it never keeps the JVM alive.
     */
    eventLoop = Thread.ofPlatform()
                      .daemon()
                      .name("QUIC endpoint " + localAddress)
                      .start(this::run);
  }


  /**
   * Gets the address this endpoint is bound to.
   *
   * @return the address
   */
  public InetSocketAddress getLocalAddress() {
    return localAddress;
  }

  /**
   * Installs the server that turns unknown datagrams into new connections.
   * <p>
   * Without one, an endpoint is a pure client: a datagram belonging to no connection is dropped.
   *
   * @param server the server, null to stop accepting
   */
  public void setServer(QuicheServer server) {
    this.server = server;
  }

  /**
   * Gets the server accepting connections on this endpoint.
   *
   * @return the server, null if this endpoint does not accept
   */
  public QuicheServer getServer() {
    return server;
  }

  /**
   * Tells whether the endpoint has been closed.
   *
   * @return true if closed
   */
  public boolean isClosed() {
    return closing.get();
  }

  /**
   * Gets the number of connections currently alive on this endpoint.
   *
   * @return the number of connections
   */
  public int getConnectionCount() {
    return connections.size();
  }

  /**
   * Establishes an outgoing connection.
   * <p>
   * The handshake runs on the event loop; this method only creates the connection and produces its first
   * packet. Use {@link QuicheConnection#awaitEstablished(long)} to wait for it to complete.
   *
   * @param peer the server's address
   * @param serverName the name verified against the server's certificate, null to skip that check
   * @param config the configuration, which must outlive the connection
   * @return the connection
   */
  public QuicheConnection connect(InetSocketAddress peer, String serverName, QuicheConfig config) {
    byte[] scid = newConnectionId();
    QuicheConn conn = QuicheConn.connect(serverName, scid, localAddress, peer, config);
    QuicheConnection connection = new QuicheConnection(this, conn, scid, peer, true);
    connections.add(connection);
    addRoute(scid, connection);
    connection.flush();   // puts the initial packet on the wire
    return connection;
  }

  /**
   * Registers a connection the server just accepted.
   *
   * @param connection the connection
   */
  public void register(QuicheConnection connection) {
    connections.add(connection);
    addRoute(connection.getConnectionId(), connection);
  }

  /**
   * Routes the packets carrying a connection ID to a connection.
   *
   * @param connectionId a connection ID this side issued for the connection
   * @param connection the connection
   */
  public void addRoute(byte[] connectionId, QuicheConnection connection) {
    routes.put(new ConnectionIdKey(connectionId), connection);
  }

  /**
   * Stops routing the packets carrying a connection ID, after the peer retired it.
   *
   * @param connectionId the connection ID
   */
  public void removeRoute(byte[] connectionId) {
    routes.remove(new ConnectionIdKey(connectionId));
  }

  /**
   * Opens a further socket for a connection that migrates to a new local address.
   * <p>
   * It is bound to the same local IP address as the endpoint's own socket, on a port the system chooses:
   * a new source port is the whole point, since it is what routers along the way hash a flow onto a path
   * by. Invoked on the event loop only, which is the only thread that uses the selector.
   *
   * @param owner the connection the socket is for
   * @return the local address of the new socket
   * @throws IOException if the socket cannot be opened
   */
  public InetSocketAddress openChannel(QuicheConnection owner) throws IOException {
    DatagramChannel newChannel = DatagramChannel.open();
    try {
      newChannel.bind(new InetSocketAddress(localAddress.getAddress(), 0));
      newChannel.configureBlocking(false);
      newChannel.register(selector, SelectionKey.OP_READ);
      InetSocketAddress address = (InetSocketAddress) newChannel.getLocalAddress();
      channelOwners.put(address.getPort(), owner);
      channels.put(address.getPort(), newChannel);
      return address;
    }
    catch (IOException | RuntimeException e) {
      closeQuietly(newChannel);
      throw e;
    }
  }

  /**
   * Closes a socket opened by {@link #openChannel(QuicheConnection)}.
   * <p>
   * The endpoint's own socket is never closed this way: other connections may use it.
   *
   * @param address the local address of the socket
   */
  public void closeChannel(InetSocketAddress address) {
    int port = address.getPort();
    if (port == localAddress.getPort()) {
      return;
    }
    channelOwners.remove(port);
    DatagramChannel extraChannel = channels.remove(port);
    if (extraChannel != null) {
      closeQuietly(extraChannel);
    }
  }

  /**
   * Gets the number of sockets this endpoint currently has open.
   *
   * @return the number of sockets, at least 1 unless closed
   */
  public int getChannelCount() {
    return channels.size();
  }

  /**
   * Creates a connection ID for this endpoint.
   *
   * @return the connection ID
   */
  public byte[] newConnectionId() {
    byte[] id = new byte[CONNECTION_ID_LENGTH];
    random.nextBytes(id);
    return id;
  }

  /**
   * Creates a stateless reset token to go with a connection ID.
   * <p>
   * This endpoint does not send stateless resets, but the protocol requires a token with every
   * connection ID issued after the first, and a random one gives nothing away.
   *
   * @return the token, 16 bytes
   */
  public byte[] newResetToken() {
    byte[] token = new byte[16];
    random.nextBytes(token);
    return token;
  }

  /**
   * Sends a datagram through the endpoint's own socket.
   *
   * @param buffer the datagram
   * @param length the number of bytes
   * @param target the address to send it to
   */
  public void send(MemorySegment buffer, long length, InetSocketAddress target) {
    send(buffer, length, localAddress.getPort(), target);
  }

  /**
   * Sends a datagram through the socket bound to a given local port.
   * <p>
   * Called with a connection's lock held, from whichever thread is flushing it. A UDP send does not
   * block in any way that matters, so holding the lock across it is not a problem.
   *
   * @param buffer the datagram
   * @param length the number of bytes
   * @param fromPort the local port quiche assigned the packet to
   * @param target the address to send it to
   */
  public void send(MemorySegment buffer, long length, int fromPort, InetSocketAddress target) {
    if (closed) {
      return;
    }
    DatagramChannel sendChannel = channels.get(fromPort);
    if (sendChannel == null) {
      // a path whose socket was closed after the connection left it: quiche may still send a packet or
      // two on it, which is no different from losing them
      LOGGER.fine("dropping {0} bytes to {1}: no socket on port {2}", length, target, Integer.toString(fromPort));
      return;
    }
    try {
      ByteBuffer byteBuffer = buffer.asSlice(0, length).asByteBuffer();
      // The channel is non-blocking, so this writes nothing when the socket's send buffer is full.
      // That is not worth handling: to QUIC an undelivered packet is a lost packet, and losing one is
      // what its loss detection and congestion control exist for. Blocking here would instead stall
      // whichever thread happens to be flushing, with the connection's lock held.
      sendChannel.send(byteBuffer, target);
    }
    catch (IOException e) {
      // an unreachable peer or a full send buffer must not take the connection down: QUIC treats a
      // lost packet as exactly that and retransmits it
      LOGGER.fine("cannot send {0} bytes to {1}: {2}", length, target, e.getMessage());
    }
  }

  /**
   * Wakes the event loop so it recomputes how long it may sleep.
   */
  public void wakeup() {
    selector.wakeup();
  }

  /**
   * Closes the endpoint, terminating every connection on it.
   * <p>
   * The connections are closed gracefully first, so the peers learn about it instead of waiting for their
   * idle timeout, and the loop is given a moment to put those frames on the wire.
   */
  @Override
  public void close() {
    if (!closing.compareAndSet(false, true)) {
      return;
    }

    // the graceful close has to happen while the socket is still usable, or the CONNECTION_CLOSE
    // frames it produces would be dropped by send() and every peer would be left waiting for its
    // idle timeout instead of learning that this end is gone
    for (QuicheConnection connection : connections) {
      connection.closeGracefully(0, "shutting down");
    }

    closed = true;
    selector.wakeup();
    try {
      // long enough for the CONNECTION_CLOSE frames to go out, short enough not to hang a shutdown
      eventLoop.join(1000);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    for (QuicheConnection connection : connections) {
      connection.close();
    }
    connections.clear();
    routes.clear();

    closeQuietly(selector);
    for (DatagramChannel openChannel : channels.values()) {
      closeQuietly(openChannel);
    }
    channels.clear();
    channelOwners.clear();
    try {
      arena.close();
    }
    catch (IllegalStateException e) {
      // the event loop did not finish within the grace period and is still inside a call holding one
      // of these segments. Closing would be a use-after-free, so the FFM API refuses it and the memory
      // is leaked instead - the right trade, and not worth failing a shutdown over.
      LOGGER.warning("the QUIC endpoint on " + localAddress + " is still in use, its buffers are leaked", e);
    }
  }


  /**
   * The event loop.
   */
  private void run() {
    LOGGER.fine("QUIC endpoint on {0} started", localAddress);
    try {
      while (!closed) {
        long sleep = Math.min(nextTimeout(), MAX_SLEEP_MILLIS);
        if (sleep > 0) {
          selector.select(sleep);
        }
        else {
          selector.selectNow();
        }
        selector.selectedKeys().clear();

        if (closed) {
          break;
        }
        receiveAll();
        runTimeouts();
        reap();
      }
    }
    catch (IOException e) {
      if (!closed) {
        LOGGER.severe("the QUIC endpoint on " + localAddress + " failed", e);
        failAll("the endpoint failed: " + e.getMessage());
      }
    }
    catch (RuntimeException e) {
      LOGGER.severe("unexpected error in the QUIC endpoint on " + localAddress, e);
      failAll("the endpoint failed: " + e.getMessage());
    }
    finally {
      LOGGER.fine("QUIC endpoint on {0} stopped", localAddress);
    }
  }

  /**
   * Receives and dispatches everything the sockets hold.
   *
   * @throws IOException if receiving failed
   */
  private void receiveAll() throws IOException {
    for (Map.Entry<Integer, DatagramChannel> entry : channels.entrySet()) {
      DatagramChannel receiveChannel = entry.getValue();
      if (receiveChannel == channel) {
        receiveAll(receiveChannel, localAddress);
      }
      else if (receiveChannel.isOpen()) {
        try {
          receiveAll(receiveChannel, new InetSocketAddress(localAddress.getAddress(), entry.getKey()));
        }
        catch (ClosedChannelException e) {
          // closed by a connection that left the path while its datagrams were being dispatched
        }
      }
    }
  }

  /**
   * Receives and dispatches everything one socket holds.
   *
   * @param receiveChannel the socket
   * @param local the local address it is bound to
   * @throws IOException if receiving failed
   */
  private void receiveAll(DatagramChannel receiveChannel, InetSocketAddress local) throws IOException {
    while (!closed && receiveChannel.isOpen()) {
      receiveByteBuffer.clear();
      SocketAddress source;
      try {
        source = receiveChannel.receive(receiveByteBuffer);
      }
      catch (PortUnreachableException e) {
        // an earlier datagram to some peer bounced off an ICMP "port unreachable". On Windows this
        // surfaces here, on the next receive(), even though this channel was never connected and the
        // datagram that provoked it has nothing to do with whatever is received next - so it is exactly
        // as harmless as the same condition already is in send(). Not catching it here would kill this
        // whole endpoint's event loop over a single stray packet.
        LOGGER.fine("ignoring a port-unreachable notification on {0}: {1}", localAddress, e.getMessage());
        continue;
      }
      if (source == null) {
        return;
      }
      int length = receiveByteBuffer.position();
      if (length <= 0) {
        continue;
      }
      dispatch((InetSocketAddress) source, local, length);
    }
  }

  /**
   * Routes one received datagram to its connection, or to the server if there is none yet.
   *
   * @param peer the sender's address
   * @param local the local address it was received on
   * @param length the number of bytes received
   */
  private void dispatch(InetSocketAddress peer, InetSocketAddress local, int length) {
    byte[] dcid;
    try {
      dcid = Quiche.headerInfo(receiveBuffer, length, CONNECTION_ID_LENGTH).destinationConnectionId();
    }
    catch (QuicheException e) {
      LOGGER.fine("dropping an unparsable datagram of {0} bytes from {1}: {2}", length, peer, e.getMessage());
      return;
    }

    QuicheConnection connection = routes.get(new ConnectionIdKey(dcid));
    if (connection != null) {
      // a packet from an address the connection has not seen yet is a new path to quiche, which
      // validates it itself and reports a migration as a path event, and sends its replies to wherever
      // the path goes - so there is nothing to track here
      recvInfo.set(peer, local);
      connection.receive(receiveBuffer, length, recvInfo);
      return;
    }

    QuicheServer currentServer = server;
    if (currentServer == null || local.getPort() != localAddress.getPort()) {
      LOGGER.fine("dropping a datagram from {0} for the unknown connection {1}",
                  peer, HexFormat.of().formatHex(dcid));
      return;
    }
    currentServer.accept(peer, receiveBuffer, length, recvInfo);
  }

  /**
   * Gets the time until the earliest timeout of any connection.
   *
   * @return the time in milliseconds
   */
  private long nextTimeout() {
    long earliest = Long.MAX_VALUE;
    for (QuicheConnection connection : connections) {
      earliest = Math.min(earliest, connection.getNextDeadlineMillis());
      if (earliest <= 0) {
        return 0;
      }
    }
    return earliest;
  }

  /**
   * Runs the timeouts and keep-alives of every connection whose deadline has passed.
   */
  private void runTimeouts() {
    for (QuicheConnection connection : connections) {
      connection.runDueWork();
    }
  }

  /**
   * Frees the connections quiche reports as done.
   */
  private void reap() {
    List<QuicheConnection> done = null;
    for (QuicheConnection connection : connections) {
      boolean closedConnection;
      connection.getLock().lock();
      try {
        closedConnection = connection.getConn().isFreed() || connection.getConn().isClosed();
        if (!connection.getConn().isFreed()) {
          // quiche_conn_local_error() can already be populated well before is_closed() turns true (see
          // QuicheConnection.receive()), and a connection that never receives another datagram after the
          // one that doomed it would otherwise never get a chance to capture it; this is the backstop,
          // still under the connection's own lock while conn can still be queried safely
          connection.captureLocalError();
        }
      }
      catch (RuntimeException e) {
        closedConnection = true;
      }
      finally {
        connection.getLock().unlock();
      }
      if (closedConnection) {
        if (done == null) {
          done = new ArrayList<>();
        }
        done.add(connection);
      }
    }
    if (done != null) {
      for (QuicheConnection connection : done) {
        if (connections.remove(connection)) {
          LOGGER.fine("reaping {0}", connection);
          routes.values().removeIf(routed -> routed == connection);
          for (Map.Entry<Integer, QuicheConnection> owner : channelOwners.entrySet()) {
            if (owner.getValue() == connection) {
              closeChannel(new InetSocketAddress(localAddress.getAddress(), owner.getKey()));
            }
          }
          connection.fail("the connection was closed");
          connection.close();
        }
      }
    }
  }

  /**
   * Marks every connection unusable, after the endpoint itself failed.
   *
   * @param reason why they are unusable
   */
  private void failAll(String reason) {
    for (QuicheConnection connection : connections) {
      connection.fail(reason);
    }
  }

  /**
   * Closes something, ignoring a failure.
   *
   * @param closeable the thing to close, may be null
   */
  private static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      }
      catch (IOException e) {
        // nothing left to do about it
      }
    }
  }


  /**
   * A connection ID usable as a map key.
   * <p>
   * A record over a {@code byte[]} would compare by identity, which is exactly wrong here.
   *
   * @param id the connection ID
   */
  public record ConnectionIdKey(byte[] id) {

    @Override
    public boolean equals(Object other) {
      return other instanceof ConnectionIdKey key && Arrays.equals(id, key.id);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(id);
    }

    @Override
    public String toString() {
      return HexFormat.of().formatHex(id);
    }
  }

}
