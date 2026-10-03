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

import org.tentackle.io.SslParameters;
import org.tentackle.quiche.ffm.PemExporter;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the certificate handling of {@link QuicheTripConnectionPool}: connecting with
 * {@code certcheck=true} to a server whose certificate is or is not trusted, the trusted certificates, and
 * the PEM files they are handed to quiche with.<br>
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

  /**
   * Without {@code tsfile}, the server's certificate is checked against the JVM's default CAs, just like
   * {@code tripq} and the TCP transports do &mdash; not against no CAs at all.
   */
  @Test(timeOut = 60000)
  public void trustStoreFallsBackToTheJvmDefaults() throws Exception {
    Reporter.log("trustStoreFallsBackToTheJvmDefaults", true);
    KeyStore trustStore = QuicheTripConnectionPool.trustStore(new SslParameters(URI.create("tripj://localhost:4711")));
    assertNotNull(trustStore, "no default CAs");
    assertTrue(trustStore.size() > 0, "no default CAs");

    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      Path configured = QuicheTestSupport.deriveTrustStore(serverKeyStore);
      try {
        KeyStore explicit = QuicheTripConnectionPool.trustStore(new SslParameters(URI.create(
          "tripj://localhost:4711?" + QuicheTestSupport.trustStoreParameters(configured))));
        assertEquals(explicit.size(), 1, "the configured truststore was not used");
      }
      finally {
        Files.deleteIfExists(configured);
      }
    }
    finally {
      Files.deleteIfExists(serverKeyStore);
    }
  }

  /**
   * Quiche reads the PEM files the keystore and truststore are exported to as soon as they are loaded, so
   * they must not linger in the temporary directory &mdash; least of all the one holding the private key.
   */
  @Test(timeOut = 60000)
  public void pemFilesAreDeletedOnceLoaded() throws Exception {
    Reporter.log("pemFilesAreDeletedOnceLoaded", true);
    Path serverKeyStore = QuicheTestSupport.createKeyStore();
    try {
      Path trustStore = QuicheTestSupport.deriveTrustStore(serverKeyStore);
      try {
        Set<Path> before = pemFiles();
        int port = QuicheTestSupport.freeUdpPort();
        QuicheTripTransport server = new QuicheTripTransport(URI.create(
          "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(serverKeyStore)));
        try {
          server.accept();
          QuicheTripTransport client = new QuicheTripTransport(URI.create(
            "tripj://localhost:" + port + "?certcheck=true&" + QuicheTestSupport.trustStoreParameters(trustStore)));
          try {
            QuicheTripConnectionPool pool = new QuicheTripConnectionPool(client, dictionary());
            Set<Path> left = pemFiles();
            left.removeAll(before);
            assertTrue(left.isEmpty(), "PEM files left behind: " + left);
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
   * Gets the PEM files exported by this module that currently exist in the temporary directory.
   *
   * @return the files
   * @throws IOException if the directory cannot be listed
   */
  private static Set<Path> pemFiles() throws IOException {
    try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return files.filter(file -> {
                    String name = file.getFileName().toString();
                    return name.startsWith(PemExporter.TEMPFILE_PREFIX) && name.endsWith(".pem");
                  })
                  .collect(Collectors.toCollection(HashSet::new));
    }
  }

  private static Dictionary dictionary() {
    return TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

}
