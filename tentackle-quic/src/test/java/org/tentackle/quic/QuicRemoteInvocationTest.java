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

package org.tentackle.quic;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.trip.Connection;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * End-to-end tests running real remote invocations across a real QUIC connection.<br>
 * A KWIK server connector is started on the loopback interface and a client transport talks to it,
 * so the complete path &mdash; TLS handshake, ALPN negotiation, stream creation, TRIP serialization,
 * call dispatch and result delivery &mdash; is exercised for both the plain ({@code tripq}) and the
 * compressed ({@code tripcq}) scheme.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicRemoteInvocationTest {

  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }

  @DataProvider(name = "schemes")
  public Object[][] schemes() {
    return new Object[][] { { QuicTripTransport.PROTOCOL }, { CompressedQuicTripTransport.PROTOCOL } };
  }


  @Test(dataProvider = "schemes")
  public void invokesRemoteMethodsOverQuic(String scheme) throws Exception {
    Reporter.log("invokesRemoteMethodsOverQuic", true);
    EchoServiceImpl service = new EchoServiceImpl("echo-" + scheme);
    try (Endpoints endpoints = startEndpoints(scheme, service, "&connecttimeout=60")) {
      EchoService remote = endpoints.client().lookup(EchoService.class, "echo");

      assertEquals(remote.getName(), "echo-" + scheme);
      assertEquals(remote.add(17, 25), 42);

      // a payload large enough that the compressed scheme actually deflates it
      Payload payload = Payload.of("hello quic", 64 * 1024);
      assertSame(remote.echo(payload), payload);    // sent back by reference (TRIP's deduplication)

      // the copy is built on the server, so it has to travel back through the QUIC stream in full
      Payload duplicate = remote.duplicate(payload);
      assertNotSame(duplicate, payload);
      assertEquals(duplicate.text(), "hello quic-copy");
      assertEquals(duplicate.number(), payload.number());
      assertEquals(duplicate.blob(), payload.blob());
      assertEquals(duplicate.tags(), payload.tags());

      assertEquals(service.getInvocationCount(), 4);
    }
  }

  @Test(dataProvider = "schemes")
  public void propagatesServerSideExceptions(String scheme) throws Exception {
    Reporter.log("propagatesServerSideExceptions", true);
    try (Endpoints endpoints = startEndpoints(scheme, new EchoServiceImpl("echo"), "")) {
      EchoService remote = endpoints.client().lookup(EchoService.class, "echo");
      IllegalStateException exception = expectThrows(IllegalStateException.class, () -> remote.fail("boom"));
      assertEquals(exception.getMessage(), "boom");

      // the transport must still be usable after a failed call
      assertEquals(remote.add(1, 2), 3);
    }
  }

  /**
   * Each pooled TRIP connection is a separate QUIC stream on one and the same QUIC connection.
   */
  @Test
  public void multiplexesPooledConnectionsAsQuicStreams() throws Exception {
    Reporter.log("multiplexesPooledConnectionsAsQuicStreams", true);
    try (Endpoints endpoints = startEndpoints(QuicTripTransport.PROTOCOL, new EchoServiceImpl("echo"),
                                              "&initial=1&increment=1")) {
      Transport client = endpoints.client();
      List<Connection> connections = new ArrayList<>();
      try {
        for (int i = 0; i < 4; i++) {
          Connection connection = client.getConnection();
          assertTrue(connection instanceof QuicTripConnection, "expected a QuicTripConnection, got " + connection);
          assertTrue(connection.isOpen());
          for (Connection previous : connections) {
            assertNotSame(connection, previous);    // the pool had to open another QUIC stream
          }
          connections.add(connection);
        }
      }
      finally {
        connections.forEach(client::putConnection);
      }
    }
  }

  /**
   * Concurrent calls must be dispatched independently &mdash; each on its own QUIC stream.
   */
  @Test
  public void handlesConcurrentInvocations() throws Exception {
    Reporter.log("handlesConcurrentInvocations", true);
    final int callCount = 8;
    EchoServiceImpl service = new EchoServiceImpl("echo");
    try (Endpoints endpoints = startEndpoints(QuicTripTransport.PROTOCOL, service, "")) {
      EchoService remote = endpoints.client().lookup(EchoService.class, "echo");
      try (ExecutorService executor = Executors.newFixedThreadPool(callCount)) {
        List<Callable<Payload>> calls = new ArrayList<>();
        for (int i = 0; i < callCount; i++) {
          final int index = i;
          calls.add(() -> remote.duplicate(Payload.of("call-" + index, 1024)));
        }
        List<Future<Payload>> results = executor.invokeAll(calls, 60, TimeUnit.SECONDS);
        for (int i = 0; i < callCount; i++) {
          Payload result = results.get(i).get();
          assertEquals(result.text(), "call-" + i + "-copy");   // no call picked up another call's answer
          assertEquals(result.blob(), Payload.of("call-" + i, 1024).blob());
        }
      }
      assertEquals(service.getInvocationCount(), callCount);
    }
  }

  /**
   * A QUIC connection is discarded silently by both peers once it stays idle for longer than the
   * QUIC max idle timeout (RFC-9000, 10.1), which is far shorter than the pool's idle timeout.
   * The client must keep it alive, otherwise the next invocation fails with
   * "connection closed by server".
   */
  @Test
  public void survivesAnIdlePeriodLongerThanTheQuicIdleTimeout() throws Exception {
    Reporter.log("survivesAnIdlePeriodLongerThanTheQuicIdleTimeout", true);
    try (Endpoints endpoints = startEndpoints(QuicTripTransport.PROTOCOL, new EchoServiceImpl("echo"),
                                              "&quicidle=1")) {
      EchoService remote = endpoints.client().lookup(EchoService.class, "echo");
      assertEquals(remote.add(1, 2), 3);
      Thread.sleep(3000);   // safely longer than quicidle
      assertEquals(remote.add(20, 22), 42);
    }
  }

  /**
   * A remote invocation must survive an interrupt of the calling thread.
   * <p>
   * Tentackle's task dispatchers interrupt their own thread whenever work is queued, in order to cut a
   * pending sleep short, and such an interrupt regularly lands while that thread is inside a remote
   * invocation &mdash; {@code DbModificationTracker} polls from its dispatcher thread. A blocking socket
   * ignores the interrupt status entirely, and this transport has to do the same instead of failing the
   * call: opening a pooled stream waits for the peer's credits, which used to be given up on an
   * interrupt that had nothing to do with the connection.
   */
  @Test
  public void survivesAnInterruptOfTheCallingThread() throws Exception {
    Reporter.log("survivesAnInterruptOfTheCallingThread", true);
    try (Endpoints endpoints = startEndpoints(QuicTripTransport.PROTOCOL, new EchoServiceImpl("echo"), "")) {
      try {
        // pending before anything is connected, so it hits the connect and the stream creation as well
        Thread.currentThread().interrupt();
        EchoService remote = endpoints.client().lookup(EchoService.class, "echo");
        assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");

        assertEquals(remote.add(17, 25), 42);
        assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");

        // larger than the flow control window, so the argument cannot be written in one go: KWIK has to
        // wait for credits, which is exactly where it used to fail on a pending interrupt
        assertEquals(remote.duplicate(Payload.of("interrupted", 700_000)).text(), "interrupted-copy");
        assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");
      }
      finally {
        Thread.interrupted();   // never leave the flag behind for whatever runs next
      }
    }
  }

  /**
   * Client and server must agree on the ALPN id, otherwise the QUIC handshake is refused.
   */
  @Test
  public void rejectsClientWithMismatchingAlpnId() throws Exception {
    Reporter.log("rejectsClientWithMismatchingAlpnId", true);
    try (Endpoints endpoints = startEndpoints(QuicTripTransport.PROTOCOL, new EchoServiceImpl("echo"), "")) {
      URI uri = URI.create(QuicTripTransport.PROTOCOL + "://localhost:" + endpoints.port() + "?alpnid=nosuchprotocol");
      Transport client = TripFactory.getInstance().createTransport(uri);
      try {
        expectThrows(RuntimeException.class, client::getConnection);
      }
      finally {
        client.close();
      }
    }
  }


  /**
   * A running server transport plus a matching client transport, closed together.
   *
   * @param server the accepting server transport
   * @param client the client transport
   * @param port the UDP port both are bound to
   */
  private record Endpoints(Transport server, Transport client, int port) implements AutoCloseable {
    @Override
    public void close() {
      client.close();
      server.close();
    }
  }

  /**
   * Starts a QUIC server transport with the given service registered as {@code "echo"}
   * and creates a client transport for it.
   *
   * @param scheme the URI scheme, {@code tripq} or {@code tripcq}
   * @param service the service to register
   * @param clientParameters additional client URI query parameters, each prefixed with {@code &}
   * @return the endpoints
   * @throws Exception if the endpoints could not be set up
   */
  private Endpoints startEndpoints(String scheme, EchoServiceImpl service, String clientParameters) throws Exception {
    int port = QuicTestSupport.freeUdpPort();
    Transport server = TripFactory.getInstance().createTransport(URI.create(
      scheme + "://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    server.accept();
    assertTrue(server.isAccepting());
    server.getRegistry().register(service, "echo");

    Transport client = TripFactory.getInstance().createTransport(URI.create(
      scheme + "://localhost:" + port + "?idle=1" + clientParameters));
    return new Endpoints(server, client, port);
  }

}
