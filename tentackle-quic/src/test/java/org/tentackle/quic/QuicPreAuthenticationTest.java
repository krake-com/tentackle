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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import org.tentackle.trip.RejectionDelay;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the pre-authentication policy on the QUIC connections of a {@code tripq} server.<br>
 * Not only the streams, but the QUIC connections themselves are subject to the policy.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicPreAuthenticationTest {

  private Path keyStore;
  private Transport server;
  private int port;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
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

    QuicClientConnection connection = connectWithoutStream();
    try {
      assertTrue(connection.isConnected());
      // the keep-alive would keep the connection open forever
      connection.keepAlive(1);
      assertTrue(await(() -> !connection.isConnected(), 10000), "a QUIC connection without a stream was not closed");
      assertTrue(await(() -> policy.getAdmittedConnections() == 0, 5000), "the admission was not released");
    }
    finally {
      connection.close();
    }
  }

  @Test(timeOut = 60000)
  public void quicConnectionAboveRateIsRefused() throws Exception {
    Reporter.log("quicConnectionAboveRateIsRefused", true);
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setConnectionRate(1);
    policy.setConnectionBurst(1);
    startServer(policy);

    QuicClientConnection first = connectWithoutStream();
    QuicClientConnection second = connectWithoutStream();
    try {
      assertTrue(await(() -> !second.isConnected(), 10000), "the QUIC connection above the rate was not closed");
      assertTrue(first.isConnected(), "the first QUIC connection was closed");
    }
    finally {
      first.close();
      second.close();
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
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?idle=1&certcheck=false&connecttimeout=30"));
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


  @Test(timeOut = 60000)
  public void compressedRequestNotDecidedEarly() throws Exception {
    Reporter.log("compressedRequestNotDecidedEarly", true);
    startServer(new DefaultPreAuthenticationPolicy(), CompressedQuicTripTransport.PROTOCOL);

    QuicClientConnection connection = connectWithoutStream(CompressedQuicTripTransport.PROTOCOL);
    try {
      QuicStream stream = connection.createStream(true);
      OutputStream out = stream.getOutputStream();
      // a compressed packet of garbage: the inflater would fail at the third byte
      out.write(new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff });
      out.flush();
      CompletableFuture<Integer> closed = CompletableFuture.supplyAsync(() -> {
        try {
          return stream.getInputStream().read();
        }
        catch (IOException e) {
          return -2;    // reset
        }
      });
      // the server waits for the packet header and the whole request: closing now would tell the wire format
      expectThrows(TimeoutException.class, () -> closed.get(1, TimeUnit.SECONDS));
      out.write(new byte[17]);
      out.flush();
      assertTrue(closed.get(10, TimeUnit.SECONDS) < 0, "the invalid request was answered");
    }
    finally {
      connection.close();
    }
  }


  private void startServer(DefaultPreAuthenticationPolicy policy) throws Exception {
    startServer(policy, QuicTripTransport.PROTOCOL);
  }

  private void startServer(DefaultPreAuthenticationPolicy policy, String protocol) throws Exception {
    port = QuicTestSupport.freeUdpPort();
    server = TripFactory.getInstance().createTransport(URI.create(
      protocol + "://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    server.setPreAuthenticationPolicy(policy);    // before accept()
    server.accept();
    Thread.sleep(1000);   // wait for the server to start accepting connections
  }

  private QuicClientConnection connectWithoutStream() throws Exception {
    return connectWithoutStream(QuicTripTransport.PROTOCOL);
  }

  private QuicClientConnection connectWithoutStream(String protocol) throws Exception {
    QuicClientConnection connection = QuicClientConnection.newBuilder()
                                                          .uri(URI.create("https://localhost:" + port))
                                                          .applicationProtocol(protocol)
                                                          .noServerCertificateCheck()
                                                          .maxIdleTimeout(Duration.ofSeconds(30))
                                                          .connectTimeout(Duration.ofSeconds(30))
                                                          .build();
    connection.connect();
    return connection;
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
