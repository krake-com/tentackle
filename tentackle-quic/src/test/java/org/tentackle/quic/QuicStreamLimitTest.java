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
import org.testng.annotations.Test;

import tech.kwik.core.QuicStream;

import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that running into the peer's stream limit fails the request instead of blocking the transport.
 * <p>
 * Every pooled connection is one QUIC stream and holds it for as long as it sits in the pool, so a
 * busy client walks into the limit the peer advertises. KWIK's {@code createStream} then waits for
 * credits that only become available when a pooled connection is closed &mdash; but the waiting thread
 * holds the pool's <em>and</em> the transport's monitor, so no other thread can return one. Without a
 * bound this deadlocks the whole client transport, permanently and for every thread using it.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicStreamLimitTest {

  /** the number of streams the server grants per QUIC connection. */
  private static final int MAX_STREAMS = 2;

  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  /**
   * With client and server configured alike, the pool must cap itself at the number of streams
   * the peer grants, rather than trying to open one more and blocking in {@code createStream}.
   */
  @Test(timeOut = 120000)
  public void capsThePoolAtTheNumberOfStreamsThePeerGrants() throws Exception {
    Reporter.log("capsThePoolAtTheNumberOfStreamsThePeerGrants", true);
    try (Endpoints endpoints = startEndpoints("&maxstreams=" + MAX_STREAMS)) {
      Transport client = endpoints.client();
      List<Connection> connections = takeConnections(client, MAX_STREAMS);

      TripRuntimeException exception = expectThrows(TripRuntimeException.class, client::getConnection);
      assertTrue(exception.getMessage().contains("max. pool size"), "unexpected message: " + exception.getMessage());
      assertTrue(exception.isTemporary(), "the pool may well hand out a connection again: " + exception);

      // the transport is still usable: returning a connection makes room for the next one
      client.putConnection(connections.removeLast());
      connections.add(client.getConnection());
      connections.forEach(client::putConnection);
    }
  }

  /**
   * If the peer grants fewer streams than this client is configured for &mdash; a configuration
   * mismatch the client cannot detect &mdash; {@code streamtimeout} has to bound the wait, and the
   * transport must be usable by other threads again once it struck.
   */
  @Test(timeOut = 120000)
  public void failsInsteadOfBlockingWhenThePeerGrantsFewerStreams() throws Exception {
    Reporter.log("failsInsteadOfBlockingWhenThePeerGrantsFewerStreams", true);
    final int streamTimeout = 2;
    // note: no maxstreams on the client, so its pool allows far more connections than the server does
    try (Endpoints endpoints = startEndpoints("&streamtimeout=" + streamTimeout)) {
      Transport client = endpoints.client();
      List<Connection> connections = takeConnections(client, MAX_STREAMS);

      long startTime = System.nanoTime();
      TripRuntimeException exception = expectThrows(TripRuntimeException.class, client::getConnection);
      long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
      Reporter.log("gave up after " + elapsedSeconds + "s: " + exception.getMessage(), true);

      assertTrue(exception.getMessage().contains("no QUIC stream available"),
                 "unexpected message: " + exception.getMessage());
      assertTrue(exception.isTemporary(), "another stream may well become available: " + exception);
      assertTrue(elapsedSeconds >= streamTimeout,
                 "gave up after " + elapsedSeconds + "s, before the configured timeout of " + streamTimeout + "s");

      // The point of the timeout: the pool's and the transport's monitors are free again.
      // Before, this thread would have blocked behind the one stuck in createStream - forever,
      // since returning a connection is the only thing that could ever have released it.
      CompletableFuture<Void> giveBack =
        CompletableFuture.runAsync(() -> client.putConnection(connections.removeLast()));
      giveBack.get(30, TimeUnit.SECONDS);

      // and with a stream released, the pool hands out a connection again
      connections.add(client.getConnection());
      connections.forEach(client::putConnection);
    }
  }


  /**
   * A connection that cannot be created must give its QUIC stream back.
   * <p>
   * {@link QuicTripConnection}'s constructor sends the TRIP preamble, so it can fail after the stream
   * has been opened. Dropping the stream would keep one of the peer's stream slots occupied until the
   * whole QUIC connection is gone, so a client failing repeatedly would run out of streams for good
   * &mdash; ending up exactly where the stream limit hurts most.
   */
  @Test(timeOut = 120000)
  public void releasesTheStreamWhenTheConnectionCannotBeCreated() throws Exception {
    Reporter.log("releasesTheStreamWhenTheConnectionCannotBeCreated", true);
    int port = QuicTestSupport.freeUdpPort();
    Transport server = TripFactory.getInstance().createTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?" +
      QuicTestSupport.keyStoreParameters(keyStore) + "&maxstreams=" + MAX_STREAMS));
    server.accept();
    // wait for the server to start accepting connections, otherwise the client will fail to connect
    Thread.sleep(1000);

    // fails to create as many connections as the server grants streams: without releasing them,
    // there would be no stream left for the connections that follow
    FailingTransport client = new FailingTransport(
      URI.create(QuicTripTransport.PROTOCOL + "://localhost:" + port +
                 "?initial=1&increment=1&streamtimeout=30"), MAX_STREAMS);
    try {
      for (int i = 0; i < MAX_STREAMS; i++) {
        TripRuntimeException exception = expectThrows(TripRuntimeException.class, client::getConnection);
        Reporter.log("attempt " + i + " failed as expected: " + exception.getMessage(), true);
      }

      List<Connection> connections = takeConnections(client, MAX_STREAMS);
      connections.forEach(client::putConnection);
    }
    finally {
      client.close();
      server.close();
    }
  }


  /**
   * Takes the given number of connections from the pool.
   *
   * @param client the client transport
   * @param count the number of connections
   * @return the connections, in a modifiable list
   */
  private List<Connection> takeConnections(Transport client, int count) {
    List<Connection> connections = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      connections.add(client.getConnection());
    }
    return connections;
  }

  /**
   * A transport failing to bridge the first QUIC streams to TRIP, as a failing preamble would.
   */
  private static class FailingTransport extends QuicTripTransport {

    private final AtomicInteger remainingFailures;

    FailingTransport(URI uri, int failures) {
      super(uri);
      remainingFailures = new AtomicInteger(failures);
    }

    @Override
    public TripStream createStream(Dictionary dictionary, QuicStream quicStream) {
      if (remainingFailures.getAndDecrement() > 0) {
        throw new TripRuntimeException("simulated failure while sending the TRIP preamble");
      }
      return super.createStream(dictionary, quicStream);
    }
  }

  /**
   * A running server transport plus a matching client transport, closed together.
   *
   * @param server the accepting server transport
   * @param client the client transport
   */
  private record Endpoints(Transport server, Transport client) implements AutoCloseable {
    @Override
    public void close() {
      client.close();
      server.close();
    }
  }

  /**
   * Starts a server granting {@value #MAX_STREAMS} streams and a client transport for it.
   *
   * @param clientParameters additional client URI query parameters, each prefixed with {@code &}
   * @return the endpoints
   * @throws Exception if the endpoints could not be set up
   */
  private Endpoints startEndpoints(String clientParameters) throws Exception {
    int port = QuicTestSupport.freeUdpPort();
    Transport server = TripFactory.getInstance().createTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?" +
      QuicTestSupport.keyStoreParameters(keyStore) + "&maxstreams=" + MAX_STREAMS));
    server.accept();
    // wait for the server to start accepting connections, otherwise the client will fail to connect
    Thread.sleep(1000);

    Transport client = TripFactory.getInstance().createTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?initial=1&increment=1" + clientParameters));
    return new Endpoints(server, client);
  }

}
