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

import org.tentackle.quiche.QuicheTestSupport;
import org.tentackle.quiche.QuicheTripTransport;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.quiche.ffm.RecvInfo;

import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Drives the engine over a real UDP loopback socket.
 * <p>
 * Where {@code HandshakeTest} proves the binding is right by moving datagrams in memory, this proves the
 * machinery around it is: the socket, the event loop, the timers, the connection routing, the address
 * validation and the blocking stream adapters.
 */
public class QuicheEndpointTest {

  /**
   * Generous enough for a handshake on a loaded build machine, short enough to fail rather than hang.
   */
  private static final long TIMEOUT_MILLIS = 15_000;


  private QuicheConfig serverConfig;
  private QuicheConfig clientConfig;
  private QuicheEndpoint serverEndpoint;
  private QuicheEndpoint clientEndpoint;
  private QuicheServer server;
  private final List<Thread> handlers = new ArrayList<>();

  @BeforeMethod
  public void setUp() throws Exception {
    serverConfig = QuicheTestSupport.serverConfig();
    clientConfig = QuicheTestSupport.clientConfig();
  }

  @AfterMethod
  public void tearDown() {
    for (Thread handler : handlers) {
      handler.interrupt();
    }
    handlers.clear();
    if (clientEndpoint != null) {
      clientEndpoint.close();
      clientEndpoint = null;
    }
    if (server != null) {
      server.close();
      server = null;
    }
    if (serverEndpoint != null) {
      serverEndpoint.close();
      serverEndpoint = null;
    }
    if (clientConfig != null) {
      clientConfig.close();
      clientConfig = null;
    }
    if (serverConfig != null) {
      serverConfig.close();
      serverConfig = null;
    }
  }


  @Test(timeOut = 60000)
  public void testHandshakeOverUdp() throws Exception {
    Reporter.log("a client connects to a server over a real UDP socket", true);
    startServer(stream -> { });
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS), "the handshake did not complete");
    assertTrue(connection.isConnected());
    assertEquals(serverEndpoint.getConnectionCount(), 1, "the server did not keep the connection");
  }

  @Test(timeOut = 60000)
  public void testStreamEcho() throws Exception {
    Reporter.log("data written on a stream comes back through the server's echo handler", true);
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    QuicheStream stream = connection.openStream();
    try (OutputStream out = stream.getOutputStream()) {
      out.write("hello over udp".getBytes(StandardCharsets.UTF_8));
    }
    assertEquals(readFully(stream.getInputStream()), "hello over udp");
  }

  @Test(timeOut = 60000)
  public void testLargePayloadCrossesTheFlowControlWindow() throws Exception {
    Reporter.log("a payload larger than the flow control window is transferred in full", true);
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    // the window is 250 kB, so this forces the sender to block and wait for credits
    byte[] payload = new byte[700_000];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }

    QuicheStream stream = connection.openStream();
    Thread writer = Thread.ofVirtual().start(() -> {
      try (OutputStream out = stream.getOutputStream()) {
        out.write(payload);
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    });
    handlers.add(writer);

    byte[] received = readFully(stream.getInputStream(), payload.length);
    writer.join(TIMEOUT_MILLIS);
    assertEquals(received.length, payload.length, "the payload was truncated");
    assertEquals(received, payload);
  }

  @Test(timeOut = 60000)
  public void testSeveralConcurrentStreams() throws Exception {
    Reporter.log("several streams of one connection are served independently", true);
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    int count = 5;
    CountDownLatch done = new CountDownLatch(count);
    AtomicInteger failures = new AtomicInteger();
    for (int i = 0; i < count; i++) {
      String message = "stream " + i;
      QuicheStream stream = connection.openStream();
      Thread thread = Thread.ofVirtual().start(() -> {
        try {
          try (OutputStream out = stream.getOutputStream()) {
            out.write(message.getBytes(StandardCharsets.UTF_8));
          }
          if (!message.equals(readFully(stream.getInputStream()))) {
            failures.incrementAndGet();
          }
        }
        catch (IOException e) {
          failures.incrementAndGet();
        }
        finally {
          done.countDown();
        }
      });
      handlers.add(thread);
    }
    assertTrue(done.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "not all streams finished");
    assertEquals(failures.get(), 0, "some streams did not echo correctly");
  }

  @Test(timeOut = 60000)
  public void testAvailableIsPerStream() throws Exception {
    Reporter.log("available() reports the data of its own stream, not of any stream of the connection", true);
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    QuicheStream withData = connection.openStream();
    QuicheStream withoutData = connection.openStream();
    withoutData.getOutputStream().write(new byte[0]);
    withoutData.getOutputStream().flush();
    OutputStream out = withData.getOutputStream();
    out.write("x".getBytes(StandardCharsets.UTF_8));
    out.flush();

    long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (withData.getInputStream().available() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertEquals(withData.getInputStream().available(), 1, "the echo was not reported as available");
    assertEquals(withoutData.getInputStream().available(), 0, "data of another stream was reported");
    assertEquals(withData.getInputStream().read(), 'x');
  }

  /**
   * A blocked read must not give up when its thread is interrupted, and must not swallow the interrupt
   * either &mdash; the blocking socket streams the TCP transports use behave exactly that way, and the
   * layers above rely on it.
   */
  @Test(timeOut = 60000)
  public void testAnInterruptDoesNotAbortABlockedRead() throws Exception {
    Reporter.log("an interrupt while parked in a read is remembered, not turned into a failure", true);
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    QuicheStream stream = connection.openStream();
    InputStream in = stream.getInputStream();
    OutputStream out = stream.getOutputStream();

    AtomicReference<String> received = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean interruptSeen = new AtomicBoolean();
    Thread reader = Thread.ofVirtual().start(() -> {
      try {
        byte[] data = in.readNBytes(5);
        interruptSeen.set(Thread.currentThread().isInterrupted());
        received.set(new String(data, StandardCharsets.UTF_8));
      }
      catch (Throwable t) {
        failure.set(t);
      }
    });
    handlers.add(reader);

    // nothing has been sent, so the reader is parked and cannot have returned for any other reason
    Thread.sleep(500);
    reader.interrupt();
    Thread.sleep(500);
    assertTrue(reader.isAlive(), "the read gave up on the interrupt: " + failure.get());

    out.write("hello".getBytes(StandardCharsets.UTF_8));
    out.flush();
    reader.join(TIMEOUT_MILLIS);

    assertNull(failure.get(), "the read failed: " + failure.get());
    assertEquals(received.get(), "hello", "the echoed data did not arrive");
    assertTrue(interruptSeen.get(), "the interrupt was swallowed instead of re-asserted");
  }

  @Test(timeOut = 60000)
  public void testStreamLimitIsEnforced() throws Exception {
    Reporter.log("opening more streams than the peer grants is refused, not blocked forever", true);
    startServer(stream -> { });
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));

    // the configuration grants 8 bidirectional streams
    for (int i = 0; i < 8; i++) {
      connection.openStream();
    }
    StreamLimitException e = expectThrows(StreamLimitException.class, connection::openStream);
    assertTrue(e.getMessage().contains("no further streams"), e.getMessage());
  }

  @Test(timeOut = 60000)
  public void testServerRefusesAnAlpnMismatch() throws Exception {
    Reporter.log("a client speaking a different application protocol never gets established", true);
    startServer(stream -> { });
    try (QuicheConfig otherConfig = QuicheTestSupport.clientConfig()) {
      otherConfig.setApplicationProtocols("something-else");
      clientEndpoint = new QuicheEndpoint(loopback(0));
      QuicheConnection connection = clientEndpoint.connect(serverAddress(), "localhost", otherConfig);
      assertFalse(connection.awaitEstablished(3000), "the handshake must not succeed");
    }
  }

  @Test(timeOut = 60000)
  public void testClosingTheClientTerminatesTheServerSide() throws Exception {
    Reporter.log("closing the client endpoint makes the server drop its connection too", true);
    startServer(stream -> { });
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
    assertEquals(serverEndpoint.getConnectionCount(), 1);

    clientEndpoint.close();
    clientEndpoint = null;

    long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (serverEndpoint.getConnectionCount() > 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertEquals(serverEndpoint.getConnectionCount(), 0, "the server kept a connection that is gone");
  }

  @Test(timeOut = 60000)
  public void testUnknownConnectionIdIsDroppedByAClientEndpoint() throws Exception {
    Reporter.log("a client endpoint drops datagrams for connections it does not know", true);
    clientEndpoint = new QuicheEndpoint(loopback(0));
    try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
      byte[] garbage = new byte[1200];
      new java.util.Random(42).nextBytes(garbage);
      socket.send(new java.net.DatagramPacket(garbage, garbage.length,
                                              InetAddress.getLoopbackAddress(),
                                              clientEndpoint.getLocalAddress().getPort()));
    }
    Thread.sleep(200);
    assertFalse(clientEndpoint.isClosed(), "the endpoint must survive a stray datagram");
    assertEquals(clientEndpoint.getConnectionCount(), 0);
  }

  @Test(timeOut = 60000)
  public void testFailingEventLoopClosesTheEndpoint() throws Exception {
    Reporter.log("an endpoint whose event loop dies closes itself instead of holding on to its port", true);
    serverEndpoint = new QuicheEndpoint(loopback(0));
    int port = serverEndpoint.getLocalAddress().getPort();
    server = new QuicheServer(serverEndpoint, serverConfig, stream -> { }) {
      @Override
      public void accept(InetSocketAddress peer, MemorySegment buffer, int length, RecvInfo recvInfo) {
        throw new IllegalStateException("injected failure");
      }
    };
    serverEndpoint.setServer(server);

    byte[] datagram = new byte[1200];
    datagram[0] = 0x40;   // a short header packet for an unknown connection is handed to the server
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.send(new DatagramPacket(datagram, datagram.length, InetAddress.getLoopbackAddress(), port));
    }

    long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (!serverEndpoint.isClosed() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertTrue(serverEndpoint.isClosed(), "the endpoint survived the death of its event loop");
    assertEquals(serverEndpoint.getChannelCount(), 0, "the socket was not closed");
    try (DatagramChannel rebound = DatagramChannel.open()) {
      rebound.bind(loopback(port));    // throws if the port is still taken
    }
  }


  /**
   * A path that stops delivering in the middle of a connection is left for a new source port, and the
   * connection carries on as if nothing happened &mdash; several times in a row, which needs the connection
   * IDs of the abandoned paths to be retired and new ones to be issued.
   */
  @Test(timeOut = 60000)
  public void testMigratesAwayFromAStalledPath() throws Exception {
    Reporter.log("a client whose path is black-holed migrates to a new source port and keeps going", true);
    startServer(this::echo);
    try (UdpRelay relay = new UdpRelay(serverAddress())) {
      clientEndpoint = new QuicheEndpoint(loopback(0));
      QuicheConnection connection = clientEndpoint.connect(relay.getAddress(), "localhost", clientConfig);
      assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
      connection.setMigrationProbeTimeouts(2);
      assertEquals(roundTrip(connection, "before"), "before");

      int migrations = 3;
      for (int i = 1; i <= migrations; i++) {
        int stalledPort = connection.getLocalAddress().getPort();
        relay.blackhole(stalledPort);
        // blocks until the connection found a way around the black hole
        assertEquals(roundTrip(connection, "after " + i), "after " + i);
        assertEquals(connection.getMigrationCount(), i, "the connection did not migrate");
        assertTrue(connection.getLocalAddress().getPort() != stalledPort, "still on the stalled port");
      }

      assertEquals(relay.getFlowCount(), migrations + 1, "every migration must use a new source port");
      // the endpoint's own socket and the one of the active path: the abandoned ones are closed
      assertEquals(clientEndpoint.getChannelCount(), 2, "sockets of abandoned paths were left open");
      assertEquals(serverEndpoint.getConnectionCount(), 1, "the server must follow, not start over");
      assertTrue(connection.isConnected());
    }
  }

  /**
   * Once the server has no spare connection ID left, a stalled connection nobody holds a lease on is
   * given up on at once, long before its idle timeout, so that it can be replaced.
   */
  @Test(timeOut = 60000)
  public void testIdleConnectionIsGivenUpWhenNoConnectionIdIsLeft() throws Exception {
    Reporter.log("a stalled connection that cannot migrate any further is closed if it is not leased", true);
    startServer(this::echo);
    try (UdpRelay relay = new UdpRelay(serverAddress())) {
      clientEndpoint = new QuicheEndpoint(loopback(0));
      QuicheConnection connection = clientEndpoint.connect(relay.getAddress(), "localhost", clientConfig);
      assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
      connection.setMigrationProbeTimeouts(2);
      assertEquals(roundTrip(connection, "before"), "before");

      long stalledNanos = 0;
      for (int i = 1; i <= 2 * QuicheTripTransport.ACTIVE_CONNECTION_ID_LIMIT && !connection.isFailed(); i++) {
        relay.blackhole(connection.getLocalAddress().getPort());
        stalledNanos = System.nanoTime();
        try {
          // blocks until the connection found a way around the black hole or was given up on
          assertEquals(roundTrip(connection, "after " + i), "after " + i);
        }
        catch (IOException e) {
          break;
        }
      }

      long millis = (System.nanoTime() - stalledNanos) / 1_000_000L;
      assertTrue(connection.isFailed(), "the connection was not given up on");
      assertTrue(connection.getFailure().contains("spare connection ID"), connection.getFailure());
      assertTrue(connection.getMigrationCount() > 0, "the connection never migrated");
      assertTrue(millis < 10_000, "given up on after " + millis + " ms only");
      assertFalse(connection.isConnected());
      assertFalse(connection.acquire(), "a connection given up on must not be leased");
    }
  }

  /**
   * A stalled connection that cannot migrate any further stays while it is leased, i.e., while a call is
   * in flight on it, and is given up on only once the lease has been released.
   */
  @Test(timeOut = 60000)
  public void testLeasedConnectionStaysWhenNoConnectionIdIsLeft() throws Exception {
    Reporter.log("a stalled connection that cannot migrate any further stays while it is leased", true);
    startServer(this::echo);
    try (UdpRelay relay = new UdpRelay(serverAddress())) {
      clientEndpoint = new QuicheEndpoint(loopback(0));
      QuicheConnection connection = clientEndpoint.connect(relay.getAddress(), "localhost", clientConfig);
      assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
      connection.setMigrationProbeTimeouts(2);
      assertEquals(roundTrip(connection, "before"), "before");
      assertTrue(connection.acquire());

      // one stream for the whole test, echoed back in the background, since the server grants only a few
      QuicheStream stream = connection.openStream();
      OutputStream out = stream.getOutputStream();
      InputStream in = stream.getInputStream();
      AtomicInteger echoed = new AtomicInteger();
      Thread reader = Thread.ofVirtual().start(() -> {
        try {
          while (in.read() >= 0) {
            echoed.incrementAndGet();
          }
        }
        catch (IOException e) {
          // the connection is given up on at the end of the test
        }
      });
      handlers.add(reader);

      // Migrate until the connection runs out of connection IDs and stays on the stalled path. Waiting for
      // the echo gives each new path a measured round trip time before it is black-holed: a path without
      // one starts from quiche's initial estimate of 333 ms, and the probe timeouts would take seconds.
      int written = 0;
      boolean exhausted = false;
      for (int i = 1; i <= 2 * QuicheTripTransport.ACTIVE_CONNECTION_ID_LIMIT && !exhausted; i++) {
        relay.blackhole(connection.getLocalAddress().getPort());
        byte[] message = ("after " + i).getBytes(StandardCharsets.UTF_8);
        out.write(message);
        out.flush();
        written += message.length;
        long deadline = System.nanoTime() + 1_500_000_000L;
        while (echoed.get() < written && System.nanoTime() < deadline) {
          Thread.sleep(10);
        }
        exhausted = echoed.get() < written;
      }
      assertTrue(exhausted, "the connection never ran out of connection IDs");
      assertFalse(connection.isFailed(), "a leased connection must not be given up on: " + connection.getFailure());
      assertTrue(connection.isConnected());

      connection.release();
      long deadline = System.nanoTime() + 25_000_000_000L;   // below the idle timeout of 30 seconds
      while (!connection.isFailed() && System.nanoTime() < deadline) {
        Thread.sleep(20);
      }
      assertTrue(connection.isFailed(), "the connection was not given up on once released");
      assertTrue(connection.getFailure().contains("spare connection ID"), connection.getFailure());
    }
  }

  /**
   * Leases are counted, and releasing more of them than were taken is ignored.
   */
  @Test(timeOut = 60000)
  public void testLeases() throws Exception {
    startServer(this::echo);
    QuicheConnection connection = connect();
    assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
    assertTrue(connection.acquire());
    assertTrue(connection.acquire());
    connection.release();
    connection.release();
    connection.release();   // one too many: ignored
    assertTrue(connection.acquire());
    connection.release();
    assertTrue(connection.isConnected());
  }

  @Test(timeOut = 60000)
  public void testNoMigrationIfDisabled() throws Exception {
    Reporter.log("a client that is not allowed to migrate stays on its stalled path", true);
    startServer(this::echo);
    try (UdpRelay relay = new UdpRelay(serverAddress())) {
      clientEndpoint = new QuicheEndpoint(loopback(0));
      QuicheConnection connection = clientEndpoint.connect(relay.getAddress(), "localhost", clientConfig);
      assertTrue(connection.awaitEstablished(TIMEOUT_MILLIS));
      assertEquals(connection.getMigrationProbeTimeouts(), 0, "migration must be off unless enabled");
      assertEquals(roundTrip(connection, "before"), "before");

      relay.blackhole(connection.getLocalAddress().getPort());
      QuicheStream stream = connection.openStream();
      try (OutputStream out = stream.getOutputStream()) {
        out.write("lost".getBytes(StandardCharsets.UTF_8));
      }
      // far more probe timeouts than it takes to migrate on loopback
      Thread.sleep(2000);
      assertEquals(connection.getMigrationCount(), 0);
      assertEquals(relay.getFlowCount(), 1);
      assertEquals(clientEndpoint.getChannelCount(), 1);
    }
  }


  /**
   * Starts the server endpoint.
   *
   * @param listener what to do with the streams the clients open
   * @throws IOException if the socket cannot be bound
   */
  private void startServer(StreamListener listener) throws IOException {
    serverEndpoint = new QuicheEndpoint(loopback(0));
    server = new QuicheServer(serverEndpoint, serverConfig, listener);
    serverEndpoint.setServer(server);
  }

  /**
   * Connects a client endpoint to the running server.
   *
   * @return the connection
   * @throws IOException if the socket cannot be bound
   */
  private QuicheConnection connect() throws IOException {
    clientEndpoint = new QuicheEndpoint(loopback(0));
    return clientEndpoint.connect(serverAddress(), "localhost", clientConfig);
  }

  /**
   * Gets the address the server is listening on.
   *
   * @return the address
   */
  private InetSocketAddress serverAddress() {
    return loopback(serverEndpoint.getLocalAddress().getPort());
  }

  /**
   * Gets a loopback address.
   *
   * @param port the port
   * @return the address
   */
  private static InetSocketAddress loopback(int port) {
    return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
  }

  /**
   * Echoes everything a stream carries back to its sender, on a thread of its own.
   * <p>
   * This is what {@code QuicheProtocolConnection} does for real: the listener runs on the event loop and
   * must hand the work over instead of doing it there.
   *
   * @param stream the stream the peer opened
   */
  private void echo(QuicheStream stream) {
    Thread thread = Thread.ofVirtual().name("echo " + stream).start(() -> {
      try (InputStream in = stream.getInputStream();
           OutputStream out = stream.getOutputStream()) {
        in.transferTo(out);
      }
      catch (IOException e) {
        // the client went away, which the test's assertions will show
      }
    });
    handlers.add(thread);
  }

  /**
   * Sends a message on a stream of its own and reads the echo.
   *
   * @param connection the connection
   * @param message the message
   * @return the echo
   * @throws IOException if the stream failed
   */
  private static String roundTrip(QuicheConnection connection, String message) throws IOException {
    QuicheStream stream = connection.openStream();
    try (OutputStream out = stream.getOutputStream()) {
      out.write(message.getBytes(StandardCharsets.UTF_8));
    }
    return readFully(stream.getInputStream());
  }

  /**
   * Reads a stream to its end.
   *
   * @param in the stream
   * @return the content as a string
   * @throws IOException if reading failed
   */
  private static String readFully(InputStream in) throws IOException {
    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
  }

  /**
   * Reads an exact number of bytes.
   *
   * @param in the stream
   * @param length the number of bytes
   * @return the bytes
   * @throws IOException if reading failed
   */
  private static byte[] readFully(InputStream in, int length) throws IOException {
    return in.readNBytes(length);
  }

}
