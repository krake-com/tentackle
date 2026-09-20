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
import org.testng.annotations.Test;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the certificate-failure path of {@link QuicheTripConnectionPool}: connecting with
 * {@code certcheck=true} to a server whose certificate is not trusted.<br>
 * The successful paths are covered end-to-end by {@link QuicheRemoteInvocationTest}, and the raw binding
 * this relies on ({@code quiche_conn_local_error}) is covered by
 * {@code org.tentackle.quiche.ffm.HandshakeTest#testCertificateRejectionReportsTheAlertAsLocalError}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicheTripConnectionPoolTest {

  /**
   * A client connecting with {@code certcheck=true} to a server presenting a certificate the client does
   * not trust must fail with a {@link TripRuntimeException} whose cause chain reveals exactly that,
   * matching what the TCP/SSL transport (and {@code tentackle-quic}) throw for the same situation: an
   * {@link SSLHandshakeException}. This is what {@code LoginFailedWithUpdateHandler} looks for to trigger
   * the client auto-update.
   */
  @Test(timeOut = 60000)
  public void rejectsUntrustedCertificateWithCertcheck() throws Exception {
    Reporter.log("rejectsUntrustedCertificateWithCertcheck", true);
    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      int port = QuicheTestSupport.freeUdpPort();
      QuicheTripTransport server = new QuicheTripTransport(URI.create(
        "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        // no tsfile configured -> the JVM's default cacerts apply, which do not trust this self-signed cert
        QuicheTripTransport client = new QuicheTripTransport(URI.create(
          "tripj://localhost:" + port + "?certcheck=true"));
        try {
          TripRuntimeException exception =
            expectThrows(TripRuntimeException.class, () -> new QuicheTripConnectionPool(client, dictionary()));
          Throwable cause = exception.getCause();
          assertTrue(cause instanceof SSLHandshakeException,
                     "expected the cause to be an SSLHandshakeException, got " + cause);
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
   * certificate via {@code tsfile} must connect successfully with {@code certcheck=true}.
   */
  @Test(timeOut = 60000)
  public void acceptsTrustedCertificateWithCertcheck() throws Exception {
    Reporter.log("acceptsTrustedCertificateWithCertcheck", true);
    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      Path trustStore = QuicheTestSupport.deriveTrustStore(serverKeyStore);
      try {
        int port = QuicheTestSupport.freeUdpPort();
        QuicheTripTransport server = new QuicheTripTransport(URI.create(
          "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(serverKeyStore)));
        try {
          server.accept();
          Thread.sleep(1000);   // let the server start accepting connections

          QuicheTripTransport client = new QuicheTripTransport(URI.create(
            "tripj://localhost:" + port + "?certcheck=true&" + QuicheTestSupport.trustStoreParameters(trustStore)));
          try {
            QuicheTripConnectionPool pool = new QuicheTripConnectionPool(client, dictionary());
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
    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      int port = QuicheTestSupport.freeUdpPort();
      QuicheTripTransport server = new QuicheTripTransport(URI.create(
        "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        // no certcheck and no tsfile configured -> the default (true) applies against the JVM's cacerts,
        // which do not trust this self-signed cert
        QuicheTripTransport client = new QuicheTripTransport(URI.create("tripj://localhost:" + port));
        try {
          TripRuntimeException exception =
            expectThrows(TripRuntimeException.class, () -> new QuicheTripConnectionPool(client, dictionary()));
          Throwable cause = exception.getCause();
          assertTrue(cause instanceof SSLHandshakeException,
                     "expected the cause to be an SSLHandshakeException, got " + cause);
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
    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      int port = QuicheTestSupport.freeUdpPort();
      QuicheTripTransport server = new QuicheTripTransport(URI.create(
        "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(serverKeyStore)));
      try {
        server.accept();
        Thread.sleep(1000);   // let the server start accepting connections

        QuicheTripTransport client = new QuicheTripTransport(URI.create(
          "tripj://localhost:" + port + "?certcheck=false"));
        try {
          QuicheTripConnectionPool pool = new QuicheTripConnectionPool(client, dictionary());
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

  private static Dictionary dictionary() {
    return TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

}
