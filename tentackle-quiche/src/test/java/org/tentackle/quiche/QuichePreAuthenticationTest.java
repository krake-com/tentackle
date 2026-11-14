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

package org.tentackle.quiche;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.quiche.engine.QuicheConnection;
import org.tentackle.quiche.engine.QuicheEndpoint;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.trip.RejectionDelay;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the pre-authentication policy on the QUIC connections of a {@code tripj} server.<br>
 * Not only the streams, but the QUIC connections themselves are subject to the policy.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuichePreAuthenticationTest {

  private Path keyStore;
  private Transport server;
  private int port;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicheTestSupport.createKeyStore();
    RejectionDelay.setInterval(0, 0);
  }

  @AfterClass
  public void tearDown() throws Exception {
    RejectionDelay.setInterval(RejectionDelay.DEFAULT_MIN_DELAY, RejectionDelay.DEFAULT_MAX_DELAY);
    Files.deleteIfExists(keyStore);
  }

  @AfterMethod
  public void stopServer() {
    if (server != null) {
      server.close();
      server = null;
    }
  }


  @Test(timeOut = 60000)
  public void quicConnectionWithoutStreamIsClosed() throws Exception {
    Reporter.log("quicConnectionWithoutStreamIsClosed", true);
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(500);
    startServer(policy);

    try (RawClient client = new RawClient()) {
      assertTrue(client.connection.isConnected());
      // the keep-alive would keep the connection open forever
      client.connection.setKeepAliveMillis(200);
      assertTrue(await(() -> !client.connection.isConnected(), 10000), "a QUIC connection without a stream was not closed");
      assertTrue(await(() -> policy.getAdmittedConnections() == 0, 5000), "the admission was not released");
    }
  }

  /**
   * A connection above the rate is dropped without an answer. The client repeats its Initial packet,
   * and gets in once the host's bucket has refilled.
   */
  @Test(timeOut = 60000)
  public void quicConnectionAboveRateIsDelayed() throws Exception {
    Reporter.log("quicConnectionAboveRateIsDelayed", true);
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setConnectionRate(1);
    policy.setConnectionBurst(1);
    startServer(policy);

    long started = System.nanoTime();
    try (RawClient first = new RawClient()) {
      assertTrue(first.connection.isConnected());
      try (RawClient second = new RawClient()) {
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(second.connection.isConnected(), "the second QUIC connection was never admitted");
        assertTrue(elapsedMillis >= 900, "the second QUIC connection was admitted after " + elapsedMillis + " ms");
      }
      assertTrue(first.connection.isConnected(), "the first QUIC connection was closed");
    }
  }

  @Test(timeOut = 60000)
  public void publicServiceIsReachable() throws Exception {
    Reporter.log("publicServiceIsReachable", true);
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(5000);
    startServer(policy);
    server.getRegistry().register(new EchoServiceImpl("echo"), "echo");

    Transport client = TripFactory.getInstance().createTransport(URI.create(
      QuicheTripTransport.PROTOCOL + "://localhost:" + port + "?idle=1&certcheck=false&connecttimeout=30"));
    try {
      EchoService remote = client.lookup(EchoService.class, "echo");
      assertEquals(remote.add(17, 25), 42);
      // the QUIC connection and its stream, both of 127.0.0.1
      assertEquals(policy.getAdmittedConnections(), 2);
    }
    finally {
      client.close();
    }
    assertTrue(await(() -> policy.getAdmittedConnections() == 0, 10000), "the admissions were not released");
  }


  /**
   * A QUIC connection that never opens a stream.
   */
  private class RawClient implements AutoCloseable {

    private final QuicheTripTransport transport;
    private final QuicheConfig config;
    private final QuicheEndpoint endpoint;
    private final QuicheConnection connection;

    RawClient() throws Exception {
      transport = new QuicheTripTransport(URI.create(QuicheTripTransport.PROTOCOL + "://localhost:" + port + "?certcheck=false"));
      config = transport.createConfig(QuicheTripTransport.PROTOCOL);
      endpoint = new QuicheEndpoint(new InetSocketAddress(0));
      connection = endpoint.connect(new InetSocketAddress("localhost", port), "localhost", config);
      connection.awaitEstablished(30000);
    }

    @Override
    public void close() {
      connection.closeGracefully(0, "");
      endpoint.close();
      config.close();
      transport.close();
    }
  }

  private void startServer(DefaultPreAuthenticationPolicy policy) throws Exception {
    port = QuicheTestSupport.freeUdpPort();
    server = TripFactory.getInstance().createTransport(URI.create(
      QuicheTripTransport.PROTOCOL + "://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(keyStore)));
    server.setPreAuthenticationPolicy(policy);    // before accept()
    server.accept();
  }

  private static boolean await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      Thread.sleep(50);
    }
    return condition.getAsBoolean();
  }

}
