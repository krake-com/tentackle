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

import tech.kwik.core.QuicClientConnection;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
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
  @Test(timeOut = 30000)
  public void rejectsUnknownNamedGroup() {
    Reporter.log("rejectsUnknownNamedGroup", true);
    // the obsolete draft group is not known to KWIK
    QuicTripTransport transport = new QuicTripTransport(URI.create(
      "tripq://localhost:12345?namedgroups=X25519MLKEM768,X25519Kyber768Draft00"));
    try {
      IllegalArgumentException exception =
        expectThrows(IllegalArgumentException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains("unknown named group 'X25519Kyber768Draft00'"),
                 "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  /**
   * A client offering nothing but the post-quantum hybrid group {@code X25519MLKEM768} connects to a
   * server with the default configuration. Since there is no other group to fall back to, the successful
   * handshake proves that agent15-pqc is present on both ends and the hybrid key exchange was negotiated.
   */
  @Test(timeOut = 60000)
  public void negotiatesPostQuantumKeyExchange() throws Exception {
    Reporter.log("negotiatesPostQuantumKeyExchange", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport server = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        QuicTripTransport client = new QuicTripTransport(URI.create(
          "tripq://localhost:" + port + "?certcheck=false&namedgroups=X25519MLKEM768"));
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
   * A connect attempt that was given up on may still be at work when the next one is started. If the
   * server certificate is rejected in that old attempt only then, this must not be taken for the reason
   * the next attempt failed for: a failure that has nothing to do with certificates would be reported as
   * an {@link SSLHandshakeException}, which {@code LoginFailedWithUpdateHandler} reacts to.
   */
  @Test(timeOut = 120000)
  public void doesNotBlameAnAttemptForTheCertificateFailureOfAnEarlierOne() throws Exception {
    Reporter.log("doesNotBlameAnAttemptForTheCertificateFailureOfAnEarlierOne", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    Path otherKeyStore = QuicTestSupport.createKeyStore();
    try {
      Path trustStore = QuicTestSupport.deriveTrustStore(serverKeyStore);
      try {
        X509Certificate untrusted = certificate(otherKeyStore);
        int port = QuicTestSupport.freeUdpPort();
        QuicTripTransport server = new QuicTripTransport(URI.create(
          "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
        try {
          server.accept();
          Thread.sleep(1000);   // let the server start accepting connections

          // the outer bound of a connect attempt is connecttimeout + CONNECT_TIMEOUT_GRACE
          QuicTripTransport client = new QuicTripTransport(URI.create(
            "tripq://localhost:" + port + "?certcheck=true&connecttimeout=1&" +
            QuicTestSupport.trustStoreParameters(trustStore)));
          try {
            AtomicInteger attempts = new AtomicInteger();
            CountDownLatch secondAttemptGivenUp = new CountDownLatch(1);
            CountDownLatch secondAttemptDone = new CountDownLatch(1);
            AtomicReference<Throwable> secondAttemptFailure = new AtomicReference<>();
            ConnectException refused = new ConnectException("connection refused");

            QuicTripConnectionPool pool = new QuicTripConnectionPool(client, dictionary()) {
              @Override
              protected QuicClientConnection establishConnection(X509TrustManager attemptTrustManager)
                  throws IOException {
                switch (attempts.incrementAndGet()) {
                  case 1:
                    // the pool's constructor: a regular connection
                    return super.establishConnection(attemptTrustManager);

                  case 2:
                    // hangs until the pool gave up on it, and only then gets to see the certificate
                    try {
                      awaitIgnoringInterrupts(secondAttemptGivenUp);
                      attemptTrustManager.checkServerTrusted(new X509Certificate[] { untrusted }, "RSA");
                    }
                    catch (CertificateException e) {
                      secondAttemptFailure.set(e);
                    }
                    finally {
                      secondAttemptDone.countDown();
                    }
                    throw new ConnectException("handshake failed");

                  default:
                    // fails for a reason of its own, after the second attempt has rejected the certificate
                    secondAttemptGivenUp.countDown();
                    awaitIgnoringInterrupts(secondAttemptDone);
                    throw refused;
                }
              }
            };
            try {
              TripRuntimeException timedOut = expectThrows(TripRuntimeException.class, pool::connect);
              assertTrue(timedOut.getCause() instanceof TimeoutException, "unexpected cause: " + timedOut.getCause());

              TripRuntimeException failed = expectThrows(TripRuntimeException.class, pool::connect);

              assertTrue(secondAttemptFailure.get() instanceof CertificateException,
                         "the certificate was not rejected in the second attempt: " + secondAttemptFailure.get());
              assertSame(failed.getCause(), refused, "the third attempt was blamed for something else");
            }
            finally {
              secondAttemptGivenUp.countDown();
              pool.shutdown();
            }
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
      Files.deleteIfExists(otherKeyStore);
    }
  }

  /**
   * Monitoring the pool must not re-establish a lost QUIC connection.
   * <p>
   * {@code monitor()} runs on the thread that monitors all pools, with the pool's monitor held. If the
   * server is not reachable, re-establishing the connection waits for the handshake for as long as
   * {@code connecttimeout} allows, round after round. The dead connections are only removed, and the
   * QUIC connection is re-established when the next connection is requested.
   */
  @Test(timeOut = 120000)
  public void monitoringDoesNotReconnect() throws Exception {
    Reporter.log("monitoringDoesNotReconnect", true);
    Path serverKeyStore = QuicTestSupport.createKeyStore();
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport server = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(serverKeyStore)));
      QuicTripTransport client = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?certcheck=false&connecttimeout=2"));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        AtomicInteger connects = new AtomicInteger();
        QuicTripConnectionPool pool = new QuicTripConnectionPool(client, dictionary()) {
          @Override
          protected void connect() {
            connects.incrementAndGet();
            super.connect();
          }
        };
        try {
          Connection connection = pool.get();
          pool.put(connection);
          assertEquals(connects.get(), 1);

          // take the server down for good: the client's QUIC connection dies with it
          server.close();
          while (connection.isOpen()) {
            Thread.sleep(100);
          }

          pool.monitor();

          assertEquals(connects.get(), 1, "monitoring the pool tried to re-establish the QUIC connection");
          assertEquals(pool.getSize(), 0, "the dead connection was not removed");

          // the next request does, and fails because there is no server anymore
          expectThrows(TripRuntimeException.class, pool::get);
          assertEquals(connects.get(), 2, "requesting a connection did not try to re-establish the QUIC connection");
        }
        finally {
          pool.shutdown();
        }
      }
      finally {
        client.close();
        server.close();
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
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

  /**
   * Loads the certificate of a keystore generated by {@link QuicTestSupport#createKeyStore()}.
   *
   * @param keyStore the keystore file
   * @return the certificate
   * @throws Exception if loading failed
   */
  private static X509Certificate certificate(Path keyStore) throws Exception {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStore)) {
      store.load(in, QuicTestSupport.STORE_PASSWORD.toCharArray());
    }
    return (X509Certificate) store.getCertificate(QuicTestSupport.CERTIFICATE_ALIAS);
  }

  /**
   * Waits for a latch the way a hung handshake would: not even an interrupt ends it.
   *
   * @param latch the latch
   */
  private static void awaitIgnoringInterrupts(CountDownLatch latch) {
    for (;;) {
      try {
        latch.await();
        return;
      }
      catch (InterruptedException e) {
        // keep waiting
      }
    }
  }

  private static Dictionary dictionary() {
    return TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

}
