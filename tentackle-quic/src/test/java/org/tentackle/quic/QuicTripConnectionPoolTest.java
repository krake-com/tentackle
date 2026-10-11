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
import org.testng.annotations.Test;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the failure paths of {@link QuicTripConnectionPool}.<br>
 * The successful paths (opening the QUIC connection and one stream per pool slot) are covered
 * end-to-end by {@link QuicRemoteInvocationTest}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicTripConnectionPoolTest {

  @Test(timeOut = 30000)
  public void rejectsUriWithoutPort() {
    Reporter.log("rejectsUriWithoutPort", true);
    // unlike TCP, a QUIC client has no default port to fall back to
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost"));
    try {
      IllegalArgumentException exception =
        expectThrows(IllegalArgumentException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains("must be > 0"), "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  /**
   * A QUIC connection that cannot be established must surface as a {@link TripRuntimeException}
   * carrying the original {@link IOException}.
   */
  @Test(timeOut = 60000)
  public void wrapsConnectFailure() {
    Reporter.log("wrapsConnectFailure", true);
    // ".invalid" is reserved by RFC 6761 and never resolves, so this fails without waiting
    // for KWIK's connect timeout
    URI uri = URI.create("tripq://tentackle-quic-test.invalid:9999");
    QuicTripTransport transport = new QuicTripTransport(uri);
    try {
      TripRuntimeException exception =
        expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains(uri.toString()), "unexpected message: " + exception.getMessage());
      assertNotNull(exception.getCause());
      assertTrue(exception.getCause() instanceof IOException,
                 "expected the original IOException, got " + exception.getCause());
    }
    finally {
      transport.close();
    }
  }

  @Test(timeOut = 30000)
  public void rejectsNonPositiveConnectTimeout() {
    Reporter.log("rejectsNonPositiveConnectTimeout", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345?connecttimeout=0"));
    try {
      IllegalArgumentException exception =
        expectThrows(IllegalArgumentException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains("connect timeout must be > 0"),
                 "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  /**
   * {@code connecttimeout} must actually bound how long connecting to an unresponsive server takes.
   * <p>
   * Nothing listens on the port, and a UDP datagram into the void produces no answer, so KWIK
   * waits for the whole handshake timeout &mdash; which is exactly what the parameter controls.
   * Without it, the default of {@value QuicTripConnectionPool#DEFAULT_CONNECT_TIMEOUT} seconds
   * would apply.
   */
  @Test(timeOut = 60000)
  public void honorsTheConfiguredConnectTimeout() throws Exception {
    Reporter.log("honorsTheConfiguredConnectTimeout", true);
    final int connectTimeout = 2;
    URI uri = URI.create("tripq://localhost:" + QuicTestSupport.freeUdpPort() +
                         "?connecttimeout=" + connectTimeout);
    QuicTripTransport transport = new QuicTripTransport(uri);
    try {
      long startTime = System.nanoTime();
      expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);

      assertTrue(elapsedSeconds >= connectTimeout,
                 "gave up after " + elapsedSeconds + "s, before the configured timeout of " + connectTimeout + "s");
      assertTrue(elapsedSeconds < QuicTripConnectionPool.DEFAULT_CONNECT_TIMEOUT,
                 "waited " + elapsedSeconds + "s, so the configured timeout was ignored " +
                 "in favor of the " + QuicTripConnectionPool.DEFAULT_CONNECT_TIMEOUT + "s default");
    }
    finally {
      transport.close();
    }
  }

  /**
   * A client connecting with {@code certcheck=true} to a server presenting a certificate the client does
   * not trust must fail with a {@link TripRuntimeException} whose cause chain reveals exactly that,
   * matching what the TCP/SSL transport throws for the same situation: an {@link SSLHandshakeException}
   * wrapping the original {@link CertificateException}. This is what
   * {@code LoginFailedWithUpdateHandler} looks for to trigger the client auto-update.
   */
  @Test(timeOut = 60000)
  public void rejectsUntrustedCertificateWithCertcheck() throws Exception {
    Reporter.log("rejectsUntrustedCertificateWithCertcheck", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport server = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        // no tsfile configured -> the JVM's default cacerts apply, which do not trust this self-signed cert
        QuicTripTransport client = new QuicTripTransport(URI.create(
          "tripq://localhost:" + port + "?certcheck=true"));
        try {
          TripRuntimeException exception =
            expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(client, dictionary()));
          SSLHandshakeException sslException = assertHandshakeException(exception);
          assertTrue(sslException.getCause() instanceof CertificateException,
                     "expected an SSLHandshakeException wrapping a CertificateException, got " +
                     sslException.getCause());
        }
        finally {
          client.close();
        }
      }
      finally {
        server.close();
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
  }

  /**
   * The counterpart of {@link #rejectsUntrustedCertificateWithCertcheck}: a client trusting the server's
   * certificate via {@code tsfile} must connect successfully with {@code certcheck=true}, proving that
   * installing the certificate-capturing trust manager did not change the accepted-certificate behavior.
   */
  @Test(timeOut = 60000)
  public void acceptsTrustedCertificateWithCertcheck() throws Exception {
    Reporter.log("acceptsTrustedCertificateWithCertcheck", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      Path trustStore = QuicTestSupport.deriveTrustStore(serverKeyStore);
      try {
        int port = QuicTestSupport.freeUdpPort();
        QuicTripTransport server = new QuicTripTransport(URI.create(
          "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
        try {
          server.accept();
          Thread.sleep(1000);   // let the server start accepting connections

          QuicTripTransport client = new QuicTripTransport(URI.create(
            "tripq://localhost:" + port + "?certcheck=true&" + QuicTestSupport.trustStoreParameters(trustStore)));
          try {
            QuicTripConnectionPool pool = new QuicTripConnectionPool(client, dictionary());
            pool.shutdown();
          }
          finally {
            client.close();
          }
        }
        finally {
          server.close();
        }
      }
      finally {
        Files.deleteIfExists(trustStore);
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
  }

  /**
   * The counterpart of {@link #rejectsUntrustedCertificateWithCertcheck} proving that {@code certcheck}
   * defaults to {@code true}: a client that does not set the parameter at all must still reject a server
   * presenting a certificate it does not trust.
   */
  @Test(timeOut = 60000)
  public void rejectsUntrustedCertificateByDefault() throws Exception {
    Reporter.log("rejectsUntrustedCertificateByDefault", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport server = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        // no certcheck and no tsfile configured -> the default (true) applies against the JVM's cacerts,
        // which do not trust this self-signed cert
        QuicTripTransport client = new QuicTripTransport(URI.create("tripq://localhost:" + port));
        try {
          TripRuntimeException exception =
            expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(client, dictionary()));
          SSLHandshakeException sslException = assertHandshakeException(exception);
          assertTrue(sslException.getCause() instanceof CertificateException,
                     "expected an SSLHandshakeException wrapping a CertificateException, got " +
                     sslException.getCause());
        }
        finally {
          client.close();
        }
      }
      finally {
        server.close();
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
  }

  /**
   * The escape hatch for {@link #rejectsUntrustedCertificateByDefault}: {@code certcheck=false} must still
   * let a client connect to a server whose certificate it does not trust.
   */
  @Test(timeOut = 60000)
  public void acceptsUntrustedCertificateWithCertcheckDisabled() throws Exception {
    Reporter.log("acceptsUntrustedCertificateWithCertcheckDisabled", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport server = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        QuicTripTransport client = new QuicTripTransport(URI.create(
          "tripq://localhost:" + port + "?certcheck=false"));
        try {
          QuicTripConnectionPool pool = new QuicTripConnectionPool(client, dictionary());
          pool.shutdown();
        }
        finally {
          client.close();
        }
      }
      finally {
        server.close();
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
  }

  private static SSLHandshakeException assertHandshakeException(TripRuntimeException exception) {
    Throwable cause = exception.getCause();
    assertTrue(cause instanceof SSLHandshakeException,
               "expected the cause to be an SSLHandshakeException, got " + cause);
    return (SSLHandshakeException) cause;
  }

  /**
   * The thread providing a stream or a connection may complete the future right after the wait for it
   * timed out. It then assumes its result was taken and does not release it, so reporting the timeout
   * nevertheless would leak the result: it must be returned as if it had arrived in time.
   */
  @Test(timeOut = 30000)
  public void awaitReturnsResultThatArrivesWhileTimingOut() throws Exception {
    Reporter.log("awaitReturnsResultThatArrivesWhileTimingOut", true);
    CompletableFuture<String> future = new CompletableFuture<>() {
      @Override
      public String get(long timeout, TimeUnit unit) throws TimeoutException {
        if (complete("late")) {
          // the provider won the race between the wait timing out and the waiter abandoning the future
          throw new TimeoutException();
        }
        return join();
      }
    };

    assertEquals(QuicTripConnectionPool.await(future, System.nanoTime()), "late");
  }

  /**
   * Giving up must abandon the future, so that the providing thread releases a result showing up later
   * instead of leaking it.
   */
  @Test(timeOut = 30000)
  public void awaitAbandonsTheFutureOnTimeout() {
    Reporter.log("awaitAbandonsTheFutureOnTimeout", true);
    CompletableFuture<String> future = new CompletableFuture<>();

    expectThrows(TimeoutException.class, () -> QuicTripConnectionPool.await(future, System.nanoTime()));

    assertTrue(future.isCompletedExceptionally(), "future was not abandoned");
    assertTrue(!future.complete("too late"), "a late result must be rejected");
  }

  private static Dictionary dictionary() {
    return TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

}
