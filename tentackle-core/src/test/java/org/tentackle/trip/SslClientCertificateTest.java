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

package org.tentackle.trip;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.io.KeyStorePasswordProvider;
import org.tentackle.io.SslParameters;
import org.tentackle.trip.transport.CompressedSslTransport;
import org.tentackle.trip.transport.SslTransport;
import org.tentackle.trip.transport.TransportClientInfo;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that the client certificate of a mutually authenticated TLS connection reaches the server's
 * {@link TransportClientInfo}, and that the keystore password can be provided by the
 * {@link KeyStorePasswordProvider} instead of the configuration.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SslClientCertificateTest {

  private static final String STORE_PASSWORD = "secret";

  public interface CertificateService extends Remote {

    /**
     * Gets the subject of the client certificate as seen by the server.
     *
     * @return the subject DN, null if the client presented no certificate
     */
    String getPeerSubject();

    /**
     * Gets the number of certificates in the chain as seen by the server.
     *
     * @return the chain length
     */
    int getPeerChainLength();

  }

  public static class CertificateServiceImpl implements CertificateService {

    @Override
    public String getPeerSubject() {
      X509Certificate certificate = TransportClientInfo.get().getPeerCertificate();
      return certificate == null ? null : certificate.getSubjectX500Principal().getName();
    }

    @Override
    public int getPeerChainLength() {
      return TransportClientInfo.get().peerCertificates().size();
    }
  }


  private Path serverKeyStore;        // CN=server
  private Path clientKeyStore;        // CN=client
  private Path serverTrustStore;      // trusts CN=client: the client CAs accepted by the server
  private Path clientTrustStore;      // trusts CN=server
  private Path untrustedKeyStore;     // CN=intruder, not trusted by the server

  private final List<Transport> transports = new ArrayList<>();
  private Transport server;
  private int port;


  @BeforeClass
  public void createStores() throws Exception {
    serverKeyStore = generateKeyPair("server");
    clientKeyStore = generateKeyPair("client");
    untrustedKeyStore = generateKeyPair("intruder");
    serverTrustStore = deriveTrustStore(clientKeyStore, "client");
    clientTrustStore = deriveTrustStore(serverKeyStore, "server");
  }

  @AfterClass(alwaysRun = true)
  public void deleteStores() throws Exception {
    for (Path path : new Path[] { serverKeyStore, clientKeyStore, untrustedKeyStore, serverTrustStore, clientTrustStore }) {
      if (path != null) {
        Files.deleteIfExists(path);
      }
    }
  }

  @BeforeMethod
  void noRejectionDelay() {
    RejectionDelay.setInterval(0, 0);
  }

  @AfterMethod(alwaysRun = true)
  void closeTransports() {
    RejectionDelay.setInterval(RejectionDelay.DEFAULT_MIN_DELAY, RejectionDelay.DEFAULT_MAX_DELAY);
    transports.forEach(Transport::close);
    transports.clear();
    if (server != null) {
      server.close();
      server = null;
    }
    KeyStorePasswordProvider.getInstance().clear(clientKeyStore.toString());
  }


  @Test
  void testClientCertificateNeeded() throws Exception {
    startServer(false, "need");
    CertificateService service = connect(false, clientKeyStore, true);
    assertEquals(service.getPeerSubject(), "CN=client");
    assertEquals(service.getPeerChainLength(), 1);    // self-signed
  }

  @Test
  void testCompressedClientCertificateNeeded() throws Exception {
    startServer(true, "need");
    CertificateService service = connect(true, clientKeyStore, true);
    assertEquals(service.getPeerSubject(), "CN=client");
  }

  @Test
  void testClientCertificateWanted() throws Exception {
    startServer(false, "want");
    assertEquals(connect(false, clientKeyStore, true).getPeerSubject(), "CN=client");
  }

  @Test
  void testNoClientCertificateWanted() throws Exception {
    startServer(false, "want");
    CertificateService service = connect(false, null, true);
    assertNull(service.getPeerSubject());
    assertEquals(service.getPeerChainLength(), 0);
  }

  @Test
  void testClientCertificateNotRequested() throws Exception {
    startServer(false, null);
    assertNull(connect(false, clientKeyStore, true).getPeerSubject());
  }

  @Test
  void testNoClientCertificateNeeded() throws Exception {
    startServer(false, "need");
    expectThrows(RuntimeException.class, () -> connect(false, null, true).getPeerSubject());
  }

  @Test
  void testUntrustedClientCertificate() throws Exception {
    startServer(false, "want");
    // a certificate the server does not trust fails the handshake, even if certificates are only wanted
    expectThrows(RuntimeException.class, () -> connect(false, untrustedKeyStore, true).getPeerSubject());
  }

  @Test
  void testKeyStorePasswordFromProvider() throws Exception {
    startServer(false, "need");
    SslParameters parameters = new SslParameters(clientUri(false, clientKeyStore, false));
    assertFalse(parameters.isKeyStorePasswordConfigured());
    assertTrue(parameters.isKeyStorePasswordMissing());
    assertFalse(parameters.checkKeyStorePassword("wrong".toCharArray()));
    assertTrue(parameters.checkKeyStorePassword(STORE_PASSWORD.toCharArray()));

    KeyStorePasswordProvider.getInstance().setPassword(clientKeyStore.toString(), STORE_PASSWORD.toCharArray());
    assertFalse(parameters.isKeyStorePasswordMissing());
    assertEquals(connect(false, clientKeyStore, false).getPeerSubject(), "CN=client");
  }

  @Test
  void testConfiguredKeyStorePasswordWins() throws Exception {
    KeyStorePasswordProvider.getInstance().setPassword(clientKeyStore.toString(), "wrong".toCharArray());
    SslParameters parameters = new SslParameters(clientUri(false, clientKeyStore, true));
    assertTrue(parameters.isKeyStorePasswordConfigured());
    assertFalse(parameters.isKeyStorePasswordMissing());
    assertEquals(new String(parameters.getKeyStorePassword()), STORE_PASSWORD);
  }


  private void startServer(boolean compressed, String clientAuth) throws Exception {
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    URI uri = new URI((compressed ? CompressedSslTransport.PROTOCOL : SslTransport.PROTOCOL) + "://127.0.0.1:" + port +
                      "?ksfile=" + uriPath(serverKeyStore) + "&kspass=" + STORE_PASSWORD +
                      "&tsfile=" + uriPath(serverTrustStore) + "&tspass=" + STORE_PASSWORD +
                      (clientAuth == null ? "" : "&clientauth=" + clientAuth));
    server = compressed ? new CompressedSslTransport(uri) : new SslTransport(uri);
    server.accept();
    server.getRegistry().register(new CertificateServiceImpl(), "certificate");
  }

  private URI clientUri(boolean compressed, Path keyStore, boolean withPassword) throws Exception {
    return new URI((compressed ? CompressedSslTransport.PROTOCOL : SslTransport.PROTOCOL) + "://127.0.0.1:" + port +
                   "?tsfile=" + uriPath(clientTrustStore) + "&tspass=" + STORE_PASSWORD + "&hostcheck=false" +
                   (keyStore == null ? "" : "&ksfile=" + uriPath(keyStore) +
                                            (withPassword ? "&kspass=" + STORE_PASSWORD : "")));
  }

  private CertificateService connect(boolean compressed, Path keyStore, boolean withPassword) throws Exception {
    URI uri = clientUri(compressed, keyStore, withPassword);
    Transport transport = compressed ? new CompressedSslTransport(uri) : new SslTransport(uri);
    transports.add(transport);
    return transport.lookup(CertificateService.class, "certificate");
  }

  private static String uriPath(Path path) {
    return path.toAbsolutePath().toString().replace('\\', '/');
  }

  /**
   * Generates a keystore with a self-signed RSA key pair, alias and CN as given.
   */
  private static Path generateKeyPair(String alias) throws Exception {
    Path keyStore = Files.createTempFile("tentackle-ssl-" + alias, ".p12");
    Files.delete(keyStore);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool,
      "-genkeypair",
      "-alias", alias,
      "-dname", "CN=" + alias,
      "-ext", "san=dns:localhost",
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "1",
      "-keystore", keyStore.toString(),
      "-storetype", "PKCS12",
      "-storepass", STORE_PASSWORD,
      "-keypass", STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    assertEquals(process.waitFor(), 0, "keytool failed: " + output);
    return keyStore;
  }

  /**
   * Derives a truststore containing the given certificate of a keystore as a trusted entry.
   */
  private static Path deriveTrustStore(Path keyStoreFile, String alias) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry(alias, keyStore.getCertificate(alias));
    Path trustStoreFile = Files.createTempFile("tentackle-ssl-trust-" + alias, ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }

}
