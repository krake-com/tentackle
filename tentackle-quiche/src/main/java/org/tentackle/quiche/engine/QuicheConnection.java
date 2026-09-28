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
import org.tentackle.quiche.ffm.ConnectionError;
import org.tentackle.quiche.ffm.PathEvent;
import org.tentackle.quiche.ffm.Quiche;
import org.tentackle.quiche.ffm.QuicheConn;
import org.tentackle.quiche.ffm.QuicheException;
import org.tentackle.quiche.ffm.RecvInfo;
import org.tentackle.quiche.ffm.SendInfo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One QUIC connection, with the locking and the buffers that make it usable from several threads.
 * <p>
 * <strong>The locking rule:</strong> every call into the underlying {@link QuicheConn} happens while
 * holding {@link #getLock()}. Quiche does no locking of its own, and two threads inside one connection
 * corrupt its state, so this is not an optimization but a correctness requirement. The lock is held by
 * the endpoint's event loop while it feeds received datagrams in, and by application threads while they
 * read from or write to a stream.
 * <p>
 * <strong>Why the flow control still works:</strong> received data is deliberately <em>not</em> drained
 * into Java buffers when it arrives. It stays inside quiche until an application thread reads it, which
 * is what makes quiche stop granting credits when nobody reads &mdash; buffering it here would advertise
 * a window this side cannot honor and turn flow control into unbounded memory-growth.
 * <p>
 * Threads waiting for a stream to become readable or writable park on {@link #getCondition()}, which the
 * event loop signals after every packet and every timeout. There is one condition per connection rather
 * than one per stream: with the handful of streams a TRIP connection pool holds, waking all of them and
 * letting most go back to sleep costs less than the bookkeeping the finer granularity would need.
 * <p>
 * <strong>Connection migration:</strong> once established, each side keeps handing the peer spare
 * connection IDs, as many as the peer accepts, and registers them with the endpoint. A client connection
 * uses them to move to a new source port when its path stops delivering, which is what a router that
 * hashes flows onto paths by their addresses and ports needs to put the connection on another path. A
 * stall is detected by the probe timeout firing repeatedly with not a single byte acknowledged in between
 * (see {@link #setMigrationProbeTimeouts(int)}), which scales with the round trip time by itself. The
 * client then opens a new socket and probes the path from it; only once the server answered the probe
 * does the connection switch over, retire the connection ID of the old path and close the old socket. A
 * probe that fails leaves the connection where it was. The server follows a migration by itself.
 */
public class QuicheConnection implements AutoCloseable {

  /**
   * The size of the buffer a single outgoing datagram is built in.
   * <p>
   * Larger than the largest UDP payload quiche is configured to send, so that a packet is never
   * truncated by this buffer rather than by the path MTU.
   */
  public static final int SEND_BUFFER_SIZE = 1500;

  /**
   * The size of the buffer stream data is copied through.
   * <p>
   * Reads and writes larger than this are split into several calls into quiche, which costs nothing:
   * quiche copies into its own send buffer anyway, and a short read is normal on a stream.
   */
  public static final int STREAM_BUFFER_SIZE = 16 * 1024;

  /**
   * The application error code used when a stream is shut down as part of an orderly close.
   */
  public static final long STREAM_CLOSED_ERROR_CODE = 0;

  /**
   * The most connection IDs issued in one go.
   * <p>
   * Only a safeguard: quiche limits the number anyway, to what the peer is willing to hold.
   */
  private static final int MAX_ISSUED_IDS = 16;

  private static final Logger LOGGER = Logger.get(QuicheConnection.class);


  private final QuicheEndpoint endpoint;
  private final QuicheConn conn;
  private final ReentrantLock lock;
  private final Condition condition;
  private final Arena arena;
  private final MemorySegment sendBuffer;
  private final MemorySegment streamBuffer;
  private final SendInfo sendInfo;
  private final byte[] connectionId;
  private final Map<Long, QuicheStream> streams;
  private final boolean client;
  private volatile InetSocketAddress peer;
  private volatile boolean failed;                  // true once the connection is unusable
  private volatile String failure;                  // why it became unusable, null if it did not
  private volatile ConnectionError localError;      // the structured reason, set only on a handshake failure
  private volatile StreamListener streamListener;   // null on a client connection
  private volatile long keepAliveMillis;            // 0 to send no keep-alive packets
  private volatile long nextKeepAliveNanos;         // when the next one is due
  private long nextStreamId;                        // guarded by the lock
  private volatile int migrationProbeTimeouts;      // 0 to never migrate
  private volatile int migrationCount;              // how often the connection migrated
  private volatile InetSocketAddress activeLocal;   // the local address of the active path
  private long activeDcidSeq;                       // the peer's connection ID the active path uses, guarded by the lock
  private InetSocketAddress probeLocal;             // the local address of the path being probed, guarded by the lock
  private long lastAckedBytes;                      // the acknowledged bytes at the last stall check, guarded by the lock
  private long ptoBaseline;                         // the probe timeouts at the last progress, guarded by the lock
  private boolean migrationRefusedLogged;           // guarded by the lock

  /**
   * Creates a connection.
   *
   * @param endpoint the endpoint carrying the packets
   * @param conn the native connection
   * @param connectionId the connection ID this side issued, i.e., the key the endpoint routes by
   * @param peer the peer's address
   * @param client true if this is the client side, which decides how stream ids are allocated
   */
  public QuicheConnection(QuicheEndpoint endpoint, QuicheConn conn,
                          byte[] connectionId, InetSocketAddress peer, boolean client) {
    this.endpoint = endpoint;
    this.conn = conn;
    this.connectionId = connectionId;
    this.peer = peer;
    this.client = client;

    activeLocal = endpoint.getLocalAddress();
    lastAckedBytes = -1;    // take the first stall check as the baseline
    lock = new ReentrantLock();
    condition = lock.newCondition();
    arena = Arena.ofShared();
    sendBuffer = arena.allocate(SEND_BUFFER_SIZE);
    streamBuffer = arena.allocate(STREAM_BUFFER_SIZE);
    sendInfo = new SendInfo(arena);
    streams = new ConcurrentHashMap<>();
    // client-initiated bidirectional streams are 0, 4, 8, ..., server-initiated ones 1, 5, 9, ...
    nextStreamId = client ? 0 : 1;
  }


  /**
   * Gets the lock every access to the native connection is serialized by.
   *
   * @return the lock
   */
  public ReentrantLock getLock() {
    return lock;
  }

  /**
   * Gets the condition signaled whenever the connection made progress.
   * <p>
   * Only to be awaited with {@link #getLock()} held.
   *
   * @return the condition
   */
  public Condition getCondition() {
    return condition;
  }

  /**
   * Gets the native connection.
   * <p>
   * Only to be touched with {@link #getLock()} held.
   *
   * @return the native connection
   */
  public QuicheConn getConn() {
    return conn;
  }

  /**
   * Gets the buffer stream data is copied through.
   * <p>
   * Only to be used with {@link #getLock()} held, which is what makes one buffer per connection enough.
   *
   * @return the buffer
   */
  public MemorySegment getStreamBuffer() {
    return streamBuffer;
  }

  /**
   * Gets the connection ID this side issued.
   *
   * @return the connection ID
   */
  public byte[] getConnectionId() {
    return connectionId;
  }

  /**
   * Gets the peer's address.
   *
   * @return the address
   */
  public InetSocketAddress getPeer() {
    return peer;
  }

  /**
   * Sets the peer's address.<br>
   * Invoked after the peer migrated to another one.
   *
   * @param peer the address
   */
  public void setPeer(InetSocketAddress peer) {
    this.peer = peer;
  }

  /**
   * Gets the local address the connection currently sends from.
   * <p>
   * The endpoint's own address, unless the connection migrated.
   *
   * @return the local address
   */
  public InetSocketAddress getLocalAddress() {
    return activeLocal;
  }

  /**
   * Sets after how many consecutive probe timeouts without any acknowledgment a client connection
   * migrates to a new source port.
   * <p>
   * The probe timeout adapts to the measured round trip time, so the same count means a longer wait on a
   * slow path than on a fast one. Since it backs off exponentially, {@code n} timeouts take roughly
   * {@code 2^n - 1} times the first one. Ignored on a server connection, which cannot migrate.
   *
   * @param probeTimeouts the number of probe timeouts, 0 to never migrate
   */
  public void setMigrationProbeTimeouts(int probeTimeouts) {
    migrationProbeTimeouts = Math.max(probeTimeouts, 0);
  }

  /**
   * Gets after how many consecutive probe timeouts a client connection migrates.
   *
   * @return the number of probe timeouts, 0 if the connection never migrates
   */
  public int getMigrationProbeTimeouts() {
    return migrationProbeTimeouts;
  }

  /**
   * Gets how often the connection migrated to a new source port.
   *
   * @return the number of migrations
   */
  public int getMigrationCount() {
    return migrationCount;
  }

  /**
   * Tells whether the connection is usable, i.e., established and neither closed nor failed.
   *
   * @return true if usable
   */
  public boolean isConnected() {
    if (failed) {
      return false;
    }
    lock.lock();
    try {
      return !conn.isFreed() && conn.isEstablished() && !conn.isClosed() && !conn.isDraining();
    }
    catch (RuntimeException e) {
      return false;
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Tells whether the connection has been given up on.
   *
   * @return true if unusable
   */
  public boolean isFailed() {
    return failed;
  }

  /**
   * Gets the reason the connection became unusable.
   *
   * @return the reason, null if the connection is still fine
   */
  public String getFailure() {
    return failure;
  }

  /**
   * Gets the structured QUIC error a rejected packet during the handshake was reported with, in
   * particular a rejected server certificate (see {@link QuicheConn#getLocalError()}).
   *
   * @return the error, or null if the connection did not fail during the handshake this way
   */
  public ConnectionError getLocalError() {
    return localError;
  }

  /**
   * Waits until the handshake completed or the deadline passed.
   * <p>
   * Like a read or a write on a {@link QuicheStream}, this does not give up when the waiting thread is
   * interrupted: connecting is what an interrupt-agnostic blocking socket does too, and a thread that
   * carries an interrupt for entirely unrelated reasons must still be able to establish a connection.
   * The interrupt is re-asserted before returning, so nothing is swallowed. Only the deadline, a failure,
   * or the connection itself ends the wait.
   *
   * @param timeoutMillis the time to wait
   * @return true if the connection is established
   */
  public boolean awaitEstablished(long timeoutMillis) {
    long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
    boolean interrupted = false;
    lock.lock();
    try {
      while (!failed && !conn.isFreed() && !conn.isEstablished() && !conn.isClosed()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        try {
          condition.awaitNanos(remaining);
        }
        catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return !failed && !conn.isFreed() && conn.isEstablished();
    }
    finally {
      lock.unlock();
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Opens a bidirectional stream.
   * <p>
   * The stream is created inside quiche right away, with a write of zero bytes. That puts nothing on the
   * wire &mdash; a QUIC stream only announces itself when it carries data &mdash; but it is what makes
   * quiche apply the peer's stream limit here, where the caller can be told about it, instead of at the
   * first write. It also gets the stream into quiche's bookkeeping, without which
   * {@code quiche_conn_stream_finished} reports an unknown stream as finished and a reader would see an
   * immediate end of the stream.
   * <p>
   * Never blocks.
   *
   * @return the stream
   * @throws StreamLimitException if the peer grants no further streams until one is closed
   * @throws QuicheException if the stream cannot be created
   */
  public QuicheStream openStream() {
    lock.lock();
    try {
      checkUsable();
      long streamId = nextStreamId;
      try {
        conn.streamSend(streamId, streamBuffer, 0, false);
      }
      catch (QuicheException e) {
        if (e.getErrorCode() == Quiche.ERR_STREAM_LIMIT) {
          throw new StreamLimitException(
            "the peer grants no further streams on " + this + " until one is closed");
        }
        throw e;
      }
      nextStreamId += 4;
      QuicheStream stream = new QuicheStream(this, streamId);
      streams.put(streamId, stream);
      return stream;
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Gets a stream the peer opened, creating the bookkeeping for it if this is the first time it is seen.
   *
   * @param streamId the stream id
   * @return the stream
   */
  public QuicheStream peerStream(long streamId) {
    return streams.computeIfAbsent(streamId, id -> new QuicheStream(this, id));
  }

  /**
   * Forgets a stream that has been closed.
   *
   * @param streamId the stream id
   */
  public void removeStream(long streamId) {
    streams.remove(streamId);
  }

  /**
   * Gets the number of bidirectional streams this side may still open.
   *
   * @return the number of streams, 0 if the connection is not usable
   */
  public long getStreamsLeft() {
    lock.lock();
    try {
      return conn.isFreed() ? 0 : conn.peerStreamsLeftBidi();
    }
    catch (RuntimeException e) {
      return 0;
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Feeds a received datagram to the connection.
   * <p>
   * Invoked by the endpoint's event loop only.
   *
   * @param buffer the datagram, which quiche decrypts in place
   * @param length the number of bytes
   * @param info the addresses the datagram traveled between
   */
  public void receive(MemorySegment buffer, long length, RecvInfo info) {
    lock.lock();
    try {
      if (conn.isFreed()) {
        return;
      }
      String rejectionMessage = null;
      try {
        long offset = 0;
        // a single datagram may carry several coalesced QUIC packets
        while (offset < length) {
          long read = conn.recv(buffer.asSlice(offset), length - offset, info);
          if (read <= 0) {
            break;
          }
          offset += read;
        }
      }
      catch (QuicheException e) {
        // a rejected packet is not fatal in itself - a stray or replayed one is dropped - but quiche
        // reports a broken connection the same way, so the state decides
        LOGGER.fine("{0}: a received packet was rejected: {1}", this, e.getMessage());
        rejectionMessage = e.getMessage();
      }
      // quiche does not always throw for a packet that makes it close the connection: rejecting the
      // peer's certificate, in particular, is reported as a successful recv() that consumed the packet.
      // Checking here, regardless of whether recv() threw, is the only way to catch that case too.
      if (!conn.isFreed()) {
        if (!conn.isEstablished()) {
          // quiche_conn_local_error() is already populated as soon as a fatal handshake error - a
          // rejected certificate, in particular - makes quiche decide to close the connection, well
          // before is_closed() turns true: that one only flips once the closing/draining period has run
          // its course, which can noticeably outlast this call. Capturing eagerly here, instead of
          // waiting for is_closed() below, is what closes the race with a connect() thread parked in
          // awaitEstablished(): otherwise it can wake up - via the unconditional signal() below - and
          // see the connection neither established nor yet closed, without ever learning why.
          captureLocalError();
        }
        if (localError != null || conn.isClosed()) {
          fail(rejectionMessage != null ? rejectionMessage : "the connection was closed");
        }
        maintainPaths();
      }
    }
    finally {
      lock.unlock();
    }
    flush();
    signal();
    notifyNewStreams();
  }

  /**
   * Captures the structured local error, if any, while the connection can still be queried.
   * <p>
   * Must be called while holding {@link #getLock()}, with {@code conn} confirmed neither freed nor
   * un-closed. This is what tells a rejected certificate (see {@link QuicheConn#getLocalError()}) apart
   * from any other handshake failure - the plain string {@link #fail}'s caller passes never does.
   * <p>
   * In practice, the handshake does not always fail where {@link #receive} would notice it: quiche
   * sometimes closes the connection without {@code conn.recv()} ever throwing (rejecting the peer's
   * certificate is reported this way), and the closure only becomes externally visible - {@code is_closed()}
   * turning true - some time later, by which point nothing may ever call {@link #receive} again to see
   * it. {@code QuicheEndpoint#reap()} is the backstop for that case, which is why this is package-visible
   * rather than private to {@link #receive}.
   */
  void captureLocalError() {
    if (localError == null) {
      localError = conn.getLocalError();
    }
  }

  /**
   * Tells the connection that its timer expired.
   * <p>
   * Invoked by the endpoint's event loop only.
   */
  public void timeout() {
    lock.lock();
    try {
      if (conn.isFreed()) {
        return;
      }
      conn.onTimeout();
      if (conn.isTimedOut()) {
        fail("the connection timed out");
      }
      else {
        maintainPaths();
        detectStall();
      }
    }
    catch (QuicheException e) {
      fail(e.getMessage());
    }
    finally {
      lock.unlock();
    }
    flush();
    signal();
  }

  /**
   * Gets the time until this connection's next timeout expires.
   *
   * @return the time in milliseconds, {@link Long#MAX_VALUE} if no timer is armed
   */
  public long getTimeoutMillis() {
    lock.lock();
    try {
      return conn.isFreed() ? Long.MAX_VALUE : conn.timeoutAsMillis();
    }
    catch (RuntimeException e) {
      return Long.MAX_VALUE;
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Sets how often an ack-eliciting packet is sent to keep the connection from timing out.
   * <p>
   * A pooled TRIP connection sits idle between calls for far longer than the QUIC idle timeout, and both
   * peers discard such a connection silently. Without a keep-alive the pool would hand out connections
   * whose QUIC connection is long gone.
   *
   * @param millis the interval in milliseconds, 0 to send none
   */
  public void setKeepAliveMillis(long millis) {
    keepAliveMillis = millis;
    nextKeepAliveNanos = millis <= 0 ? Long.MAX_VALUE : System.nanoTime() + millis * 1_000_000L;
    endpoint.wakeup();
  }

  /**
   * Gets the time until the next thing this connection needs doing.
   *
   * @return the time in milliseconds, {@link Long#MAX_VALUE} if there is nothing pending
   */
  public long getNextDeadlineMillis() {
    long deadline = getTimeoutMillis();
    if (keepAliveMillis > 0) {
      long remaining = (nextKeepAliveNanos - System.nanoTime()) / 1_000_000L;
      deadline = Math.min(deadline, Math.max(remaining, 0));
    }
    return deadline;
  }

  /**
   * Runs whatever became due: the quiche timeout, the keep-alive, or both.
   * <p>
   * The timeout is processed first, deliberately: if the idle timeout has expired, the peer discarded the
   * connection at the same moment, and a keep-alive would only pretend it were still alive. Keeping the
   * connection is therefore the job of an interval that is safely shorter than the idle timeout, not of
   * the order in which the two are handled here.
   * <p>
   * Invoked by the endpoint's event loop only.
   */
  public void runDueWork() {
    if (getTimeoutMillis() <= 0) {
      timeout();
    }
    if (keepAliveMillis > 0 && System.nanoTime() >= nextKeepAliveNanos) {
      nextKeepAliveNanos = System.nanoTime() + keepAliveMillis * 1_000_000L;
      ping();
    }
  }

  /**
   * Sets the listener notified about the streams the peer opens.
   *
   * @param streamListener the listener, null for a connection that expects none
   */
  public void setStreamListener(StreamListener streamListener) {
    this.streamListener = streamListener;
    // data may have arrived before the listener was installed
    notifyNewStreams();
  }

  /**
   * Schedules an ack-eliciting packet, which resets the peer's idle timer.
   */
  public void ping() {
    lock.lock();
    try {
      if (!conn.isFreed() && conn.isEstablished() && !conn.isClosed()) {
        conn.sendAckEliciting();
      }
    }
    catch (QuicheException e) {
      LOGGER.fine("{0}: cannot send a keep-alive packet: {1}", this, e.getMessage());
    }
    finally {
      lock.unlock();
    }
    flush();
  }

  /**
   * Sends everything the connection has queued.
   * <p>
   * Must be called after every operation that may have produced packets &mdash; a received datagram, a
   * stream write, a stream read granting flow control credits, a timeout. Cheap when there is nothing
   * to send.
   */
  public void flush() {
    lock.lock();
    try {
      flushLocked();
    }
    finally {
      lock.unlock();
    }
    // the timers may have moved, so the event loop has to recompute how long it may sleep
    endpoint.wakeup();
  }

  /**
   * Signals everyone waiting for the connection to make progress.
   */
  public void signal() {
    lock.lock();
    try {
      condition.signalAll();
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Marks the connection unusable and wakes everybody waiting on it.
   *
   * @param reason why it is unusable
   */
  public void fail(String reason) {
    if (!failed) {
      failed = true;
      failure = reason;
      LOGGER.fine("{0} became unusable: {1}", this, reason);
    }
    signal();
  }

  /**
   * Throws if the connection cannot be used.
   */
  public void checkUsable() {
    if (failed) {
      throw new QuicheException("the QUIC connection to " + peer + " is gone: " + failure);
    }
    if (conn.isFreed()) {
      throw new QuicheException("the QUIC connection to " + peer + " is closed");
    }
  }

  /**
   * Initiates an orderly close, which makes the peer see a CONNECTION_CLOSE frame.
   * <p>
   * The native connection is not freed here: it must stay alive until the frame has actually been sent,
   * which the event loop takes care of before it drops the connection.
   *
   * @param errorCode the application error code
   * @param reason the reason phrase
   */
  public void closeGracefully(long errorCode, String reason) {
    lock.lock();
    try {
      if (!conn.isFreed() && !conn.isClosed()) {
        conn.closeConnection(true, errorCode, reason);
      }
    }
    catch (QuicheException e) {
      LOGGER.fine("{0}: cannot close gracefully: {1}", this, e.getMessage());
    }
    finally {
      lock.unlock();
    }
    flush();
    signal();
  }

  /**
   * Frees the native connection and releases everyone waiting on it.
   * <p>
   * Invoked by the endpoint once the connection is done, never by an application thread: a connection
   * freed while a reader is inside quiche would be a use-after-free.
   */
  @Override
  public void close() {
    lock.lock();
    try {
      if (!conn.isFreed()) {
        failed = true;
        if (failure == null) {
          failure = "the connection was closed";
        }
        conn.close();
        arena.close();
      }
      condition.signalAll();
    }
    finally {
      lock.unlock();
    }
    streams.clear();
  }

  @Override
  public String toString() {
    return "QUIC connection " + HexFormat.of().formatHex(connectionId) + " to " + peer;
  }


  /**
   * Hands the streams the peer newly opened to the listener.
   * <p>
   * A QUIC stream announces itself by its first data arriving, so the readable set is what tells this
   * side that a stream exists. Draining that set here is harmless: quiche puts a stream back into it
   * whenever further data arrives, and a reader takes its data straight out of the stream rather than
   * out of the set.
   * <p>
   * The listener is invoked <em>after</em> the lock is released, so that it can hand the stream to
   * another thread which starts reading it immediately.
   */
  private void notifyNewStreams() {
    StreamListener listener = streamListener;
    if (listener == null) {
      return;
    }
    List<QuicheStream> opened = null;
    lock.lock();
    try {
      if (conn.isFreed() || !conn.isEstablished()) {
        return;
      }
      long streamId;
      while ((streamId = conn.streamReadableNext()) >= 0) {
        if (!streams.containsKey(streamId)) {
          QuicheStream stream = new QuicheStream(this, streamId);
          streams.put(streamId, stream);
          if (opened == null) {
            opened = new ArrayList<>();
          }
          opened.add(stream);
        }
      }
    }
    catch (QuicheException e) {
      LOGGER.fine("{0}: cannot look for new streams: {1}", this, e.getMessage());
      return;
    }
    finally {
      lock.unlock();
    }
    if (opened != null) {
      for (QuicheStream stream : opened) {
        listener.streamOpened(stream);
      }
    }
  }

  /**
   * Keeps the peer supplied with spare connection IDs and handles what happened to the network paths.
   * <p>
   * Must be called while holding {@link #getLock()}, with {@code conn} not freed. Cheap when there is
   * nothing to do, which is almost always: two counters are read.
   */
  private void maintainPaths() {
    if (!conn.isEstablished() || conn.isClosed()) {
      return;
    }
    try {
      for (byte[] retired : conn.takeRetiredScids()) {
        endpoint.removeRoute(retired);
      }
      for (int i = 0; i < MAX_ISSUED_IDS && conn.scidsLeft() > 0; i++) {
        byte[] id = endpoint.newConnectionId();
        // routed before quiche announces it, so that no packet carrying it can arrive unroutable
        endpoint.addRoute(id, this);
        try {
          conn.newScid(id, endpoint.newResetToken());
        }
        catch (QuicheException e) {
          endpoint.removeRoute(id);
          throw e;
        }
      }
      PathEvent event;
      while ((event = conn.nextPathEvent()) != null) {
        switch (event.type()) {
          case VALIDATED -> pathValidated(event);
          case FAILED_VALIDATION -> pathFailed(event);
          case PEER_MIGRATED -> {
            LOGGER.info("{0}: the peer migrated to {1}", this, event.peer());
            peer = event.peer();
          }
          default -> LOGGER.fine("{0}: {1}", this, event);
        }
      }
    }
    catch (QuicheException e) {
      // nothing here is essential for the connection as it is, only for a future migration
      LOGGER.fine("{0}: cannot maintain the connection IDs and paths: {1}", this, e.getMessage());
    }
  }

  /**
   * Checks whether the active path stopped delivering and starts migrating away from it if so.
   * <p>
   * Invoked after every timeout, which is when a probe timeout fires. Must be called while holding
   * {@link #getLock()}, with {@code conn} not freed.
   */
  private void detectStall() {
    int threshold = migrationProbeTimeouts;
    if (!client || threshold <= 0 || probeLocal != null ||
        !conn.isEstablished() || conn.isClosed() || conn.isDraining()) {
      return;
    }
    long ackedBytes;
    long ptoCount;
    try {
      ackedBytes = conn.getAckedBytes();
      ptoCount = conn.getActivePathPtoCount();
    }
    catch (QuicheException e) {
      LOGGER.fine("{0}: cannot read the connection statistics: {1}", this, e.getMessage());
      return;
    }
    // the counter is per path, so it starts over after a migration
    if (ackedBytes != lastAckedBytes || ptoCount < ptoBaseline) {
      lastAckedBytes = ackedBytes;
      ptoBaseline = ptoCount;
      return;
    }
    if (ptoCount - ptoBaseline >= threshold) {
      ptoBaseline = ptoCount;   // another attempt only after as many further timeouts
      if (conn.availableDcids() == 0) {
        // quiche keeps every path a connection ever used, up to the connection ID limit, and the server
        // has none left to spare: the connection stays where it is and, if the path does not recover,
        // runs into its idle timeout
        if (!migrationRefusedLogged) {
          migrationRefusedLogged = true;
          LOGGER.warning(this + ": the path is stalled, but the peer has no spare connection ID left to " +
                         "migrate with after " + migrationCount + " migrations");
        }
        return;
      }
      LOGGER.info("{0}: nothing acknowledged for {1} probe timeouts, the path from local port {2} is stalled",
                  this, threshold, Integer.toString(activeLocal.getPort()));
      probeNewPath();
    }
  }

  /**
   * Opens a socket on a new source port and starts validating the path from it.
   * <p>
   * Must be called while holding {@link #getLock()}.
   */
  private void probeNewPath() {
    InetSocketAddress newLocal;
    try {
      newLocal = endpoint.openChannel(this);
    }
    catch (IOException e) {
      LOGGER.warning(this + ": cannot open a socket to migrate to", e);
      return;
    }
    try {
      conn.probePath(newLocal, peer);
    }
    catch (QuicheException e) {
      endpoint.closeChannel(newLocal);
      LOGGER.warning(this + ": cannot probe a path from local port " + newLocal.getPort(), e);
      return;
    }
    probeLocal = newLocal;
    LOGGER.info("{0}: probing a new path from local port {1}", this, Integer.toString(newLocal.getPort()));
  }

  /**
   * Switches to a path that has been validated, if it is the one being probed.
   * <p>
   * Must be called while holding {@link #getLock()}.
   *
   * @param event the event
   */
  private void pathValidated(PathEvent event) {
    InetSocketAddress newLocal = probeLocal;
    if (!client || newLocal == null || event.local().getPort() != newLocal.getPort()) {
      LOGGER.fine("{0}: {1}", this, event);
      return;
    }
    probeLocal = null;
    long newDcidSeq;
    try {
      newDcidSeq = conn.migrate(newLocal, event.peer());
    }
    catch (QuicheException e) {
      endpoint.closeChannel(newLocal);
      LOGGER.warning(this + ": cannot migrate to local port " + newLocal.getPort(), e);
      return;
    }
    InetSocketAddress oldLocal = activeLocal;
    long oldDcidSeq = activeDcidSeq;
    activeLocal = newLocal;
    activeDcidSeq = newDcidSeq;
    migrationCount++;
    lastAckedBytes = -1;
    try {
      // an old connection ID must not show up on the new path (RFC 9000, 9.5)
      conn.retireDcid(oldDcidSeq);
    }
    catch (QuicheException e) {
      LOGGER.fine("{0}: cannot retire the connection ID of the old path: {1}", this, e.getMessage());
    }
    endpoint.closeChannel(oldLocal);   // a no-op for the endpoint's own socket
    LOGGER.info("{0}: migrated from local port {1} to {2}",
                this, Integer.toString(oldLocal.getPort()), Integer.toString(newLocal.getPort()));
  }

  /**
   * Gives up on a path that could not be validated, if it is the one being probed.
   * <p>
   * Must be called while holding {@link #getLock()}.
   *
   * @param event the event
   */
  private void pathFailed(PathEvent event) {
    InetSocketAddress failedLocal = probeLocal;
    if (!client || failedLocal == null || event.local().getPort() != failedLocal.getPort()) {
      LOGGER.fine("{0}: {1}", this, event);
      return;
    }
    probeLocal = null;
    endpoint.closeChannel(failedLocal);
    LOGGER.info("{0}: the path from local port {1} could not be validated, staying on port {2}",
                this, Integer.toString(failedLocal.getPort()), Integer.toString(activeLocal.getPort()));
  }

  /**
   * Drains the connection's outgoing packets to the socket.
   * <p>
   * quiche's pacing time is deliberately ignored: sending earlier than it asks for is allowed and merely
   * lets a burst go out at once, whereas honoring it would need a timer per packet.
   */
  private void flushLocked() {
    if (conn.isFreed()) {
      return;
    }
    try {
      long length;
      while ((length = conn.send(sendBuffer, SEND_BUFFER_SIZE, sendInfo)) > 0) {
        endpoint.send(sendBuffer, length, sendInfo.getFromPort(), sendInfo.getTo());
      }
    }
    catch (QuicheException e) {
      fail("sending failed: " + e.getMessage());
    }
  }

}
