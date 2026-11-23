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
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

/**
 * Tests that a quiche server requests, verifies and hands over the client certificate.
 * <p>
 * BoringSSL lets a client without a certificate pass the handshake even if the server verifies its peer.
 * {@code clientauth=need} must nevertheless refuse such a client.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicheClientCertificateTest {

  private static final String KWIK_SCHEME = "tripq";

  private static final String CLIENT_ALIAS = "client";


  private Path serverKeyStore;      // CN=localhost, alias quiche
  private Path clientKeyStore;      // CN=client
  private Path clientTrustStore;    // the client CAs accepted by the server: CN=client
  private Path untrustedKeyStore;   // CN=client as well, but a different key the server does not trust

  @BeforeClass
  public void setUp() throws Exception {
    serverKeyStore = QuicheTestSupport.createKeyStore();
    clientKeyStore = createClientKeyStore();
    untrustedKeyStore = createClientKeyStore();
    clientTrustStore = deriveTrustStore(clientKeyStore);
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    for (Path path : new Path[] { serverKeyStore, clientKeyStore, untrustedKeyStore, clientTrustStore }) {
      if (path != null) {
        Files.deleteIfExists(path);
      }
    }
  }


  @Test(timeOut = 90000)
  public void testClientCertificateNeeded() throws Exception {
    Reporter.log("a quiche client presents its certificate to a quiche server that needs one", true);
    assertEquals(exchange("need", QuicheTripTransport.PROTOCOL, clientKeyStore), "CN=client");
  }

  @Test(timeOut = 90000)
  public void testClientCertificateWanted() throws Exception {
    assertEquals(exchange("want", QuicheTripTransport.PROTOCOL, clientKeyStore), "CN=client");
  }

  @Test(timeOut = 90000)
  public void testNoClientCertificateWanted() throws Exception {
    assertNull(exchange("want", QuicheTripTransport.PROTOCOL, null));
  }

  @Test(timeOut = 90000)
  public void testClientCertificateNotRequested() throws Exception {
    assertNull(exchange(null, QuicheTripTransport.PROTOCOL, clientKeyStore));
  }

  @Test(timeOut = 90000)
  public void testNoClientCertificateNeeded() {
    Reporter.log("a quiche server that needs a client certificate refuses a client without one", true);
    expectThrows(RuntimeException.class, () -> exchange("need", QuicheTripTransport.PROTOCOL, null));
  }

  @Test(timeOut = 90000)
  public void testUntrustedClientCertificate() {
    Reporter.log("a quiche server refuses a client certificate it does not trust", true);
    expectThrows(RuntimeException.class, () -> exchange("want", QuicheTripTransport.PROTOCOL, untrustedKeyStore));
  }

  @Test(timeOut = 90000)
  public void testKwikClientCertificateNeeded() throws Exception {
    Reporter.log("a KWIK client presents its certificate to a quiche server that needs one", true);
    try {
      TripFactory.getInstance().createTransport(URI.create(KWIK_SCHEME + "://localhost:1")).close();
    }
    catch (RuntimeException e) {
      throw new SkipException("the KWIK transport (" + KWIK_SCHEME + ") is not on the module path: " + e.getMessage());
    }
    assertEquals(exchange("need", KWIK_SCHEME, clientKeyStore), "CN=client");
  }


  /**
   * Starts a quiche server and asks it for the client certificate it saw.
   *
   * @param clientAuth the server's client-certificate policy, null if none
   * @param clientScheme the scheme of the client transport
   * @param keyStore the client's keystore, null to present no certificate
   * @return the subject of the client certificate as seen by the server
   */
  private String exchange(String clientAuth, String clientScheme, Path keyStore) throws Exception {
    int port = QuicheTestSupport.freeUdpPort();
    Transport server = TripFactory.getInstance().createTransport(URI.create(
      QuicheTripTransport.PROTOCOL + "://localhost:" + port + "?alpnid=" + QuicheTestSupport.ALPN + "&" +
      QuicheTestSupport.keyStoreParameters(serverKeyStore) + "&" + QuicheTestSupport.trustStoreParameters(clientTrustStore) +
      (clientAuth == null ? "" : "&clientauth=" + clientAuth)));
    try {
      server.accept();
      server.getRegistry().register(new CertificateServiceImpl(), "certificate");

      // the server's certificate is self-signed: this test is about the client's
      Transport client = TripFactory.getInstance().createTransport(URI.create(
        clientScheme + "://localhost:" + port + "?alpnid=" + QuicheTestSupport.ALPN +
        "&idle=1&connecttimeout=10&certcheck=false" +
        (keyStore == null ? "" : "&ksfile=" + QuicheTestSupport.uriPath(keyStore) +
                                 "&kspass=" + QuicheTestSupport.STORE_PASSWORD + "&ksalias=" + CLIENT_ALIAS)));
      try {
        return client.lookup(CertificateService.class, "certificate").getPeerSubject();
      }
      finally {
        client.close();
      }
    }
    finally {
      server.close();
    }
  }

  private static Path createClientKeyStore() throws Exception {
    Path keyStore = Files.createTempFile("tentackle-quiche-client", ".p12");
    Files.delete(keyStore);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool, "-genkeypair", "-alias", CLIENT_ALIAS, "-dname", "CN=client",
      "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "1",
      "-keystore", keyStore.toString(), "-storetype", "PKCS12",
      "-storepass", QuicheTestSupport.STORE_PASSWORD, "-keypass", QuicheTestSupport.STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    assertEquals(process.waitFor(), 0, "keytool failed: " + output);
    return keyStore;
  }

  private static Path deriveTrustStore(Path keyStoreFile) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, QuicheTestSupport.STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry(CLIENT_ALIAS, keyStore.getCertificate(CLIENT_ALIAS));
    Path trustStoreFile = Files.createTempFile("tentackle-quiche-client-trust", ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, QuicheTestSupport.STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }

}
