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

package org.tentackle.io;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for the SSL-aware TCP socket factories.<br>
 * Verifies that the server presents the certificate selected by its alias &mdash; the feature the
 * default JSSE factories lack &mdash; and that the configuration is resolved from the URI query
 * parameters as well as the {@code javax.net.ssl.*} system-property fallback.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SslSocketFactoryTest {

  private static final String STORE_PASSWORD = "secret";

  private Path keyStoreFile;    // PKCS12 with two key entries: "alpha" (CN=alpha) and "beta" (CN=beta)
  private Path trustStoreFile;  // PKCS12 with both certificates as trusted entries

  private Path singleKeyStoreFile;    // PKCS12 with a single key entry: "solo" (CN=solo)
  private Path singleTrustStoreFile;  // PKCS12 trusting the single certificate

  @BeforeClass
  public void setUp() throws Exception {
    keyStoreFile = Files.createTempFile("tentackle-ssl-keystore", ".p12");
    Files.delete(keyStoreFile);   // keytool creates it
    generateKeyPair(keyStoreFile, "alpha");
    generateKeyPair(keyStoreFile, "beta");
    trustStoreFile = deriveTrustStore(keyStoreFile, "alpha", "beta");

    singleKeyStoreFile = Files.createTempFile("tentackle-ssl-single-keystore", ".p12");
    Files.delete(singleKeyStoreFile);
    generateKeyPair(singleKeyStoreFile, "solo");
    singleTrustStoreFile = deriveTrustStore(singleKeyStoreFile, "solo");
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStoreFile);
    Files.deleteIfExists(trustStoreFile);
    Files.deleteIfExists(singleKeyStoreFile);
    Files.deleteIfExists(singleTrustStoreFile);
  }


  @Test
  public void selectsCertificateByAliasFromQuery() throws Exception {
    // the default factory would present the first certificate; here we pin each alias explicitly
    assertEquals(handshakeAndGetServerCn(serverParametersForAlias("alpha"), clientParameters()), "alpha");
    assertEquals(handshakeAndGetServerCn(serverParametersForAlias("beta"), clientParameters()), "beta");
  }

  @Test
  public void usesSingleKeyEntryWithoutAlias() throws Exception {
    // no ksalias given: the store's only key entry must be selected automatically (legacy behavior)
    SslParameters server = new SslParameters(URI.create(
      "trips://localhost:0?ksfile=" + uriPath(singleKeyStoreFile) + "&kspass=" + STORE_PASSWORD));
    SslParameters client = new SslParameters(URI.create(
      "trips://localhost:0?tsfile=" + uriPath(singleTrustStoreFile) + "&tspass=" + STORE_PASSWORD));
    assertEquals(handshakeAndGetServerCn(server, client), "solo");
  }

  @Test
  public void resolvesKeystoreFromJavaxSystemPropertyFallback() throws Exception {
    // ksfile/kspass come from the legacy javax.net.ssl.* properties, the alias from the query
    String savedStore = System.getProperty("javax.net.ssl.keyStore");
    String savedPass = System.getProperty("javax.net.ssl.keyStorePassword");
    try {
      System.setProperty("javax.net.ssl.keyStore", keyStoreFile.toString());
      System.setProperty("javax.net.ssl.keyStorePassword", STORE_PASSWORD);
      SslParameters server = new SslParameters(URI.create("trips://localhost:0?ksalias=beta"));
      assertEquals(handshakeAndGetServerCn(server, clientParameters()), "beta");
    }
    finally {
      restore("javax.net.ssl.keyStore", savedStore);
      restore("javax.net.ssl.keyStorePassword", savedPass);
    }
  }


  private SslParameters serverParametersForAlias(String alias) {
    return new SslParameters(URI.create(
      "trips://localhost:0?ksfile=" + uriPath(keyStoreFile) + "&kspass=" + STORE_PASSWORD + "&ksalias=" + alias));
  }

  private SslParameters clientParameters() {
    return new SslParameters(URI.create(
      "trips://localhost:0?tsfile=" + uriPath(trustStoreFile) + "&tspass=" + STORE_PASSWORD));
  }

  /**
   * Renders a filesystem path for embedding in a URI query.<br>
   * Windows paths use backslashes, which are illegal in a URI; forward slashes are valid in a URI
   * query and are accepted by the Windows file APIs as well, so the path stays usable on either platform.
   */
  private static String uriPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  /**
   * Performs a real TLS handshake between a server (using {@code serverParameters}) and a client
   * (using {@code clientParameters}) over the loopback interface and returns the CN of the
   * certificate the server presented.
   */
  private String handshakeAndGetServerCn(SslParameters serverParameters, SslParameters clientParameters)
         throws Exception {
    TcpServerSocketFactory serverSocketFactory = new TcpServerSocketFactory(serverParameters);
    try (SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(0)) {
      int port = serverSocket.getLocalPort();
      AtomicReference<Throwable> serverError = new AtomicReference<>();
      Thread serverThread = new Thread(() -> {
        try (Socket accepted = serverSocket.accept()) {
          ((SSLSocket) accepted).startHandshake();
          accepted.getOutputStream().write(42);
          accepted.getOutputStream().flush();
        }
        catch (Throwable t) {
          serverError.set(t);
        }
      });
      serverThread.setDaemon(true);
      serverThread.start();

      TcpSocketFactory clientSocketFactory = new TcpSocketFactory(clientParameters);
      X509Certificate serverCertificate;
      try (SSLSocket clientSocket = (SSLSocket) clientSocketFactory.createSocket("localhost", port)) {
        clientSocket.startHandshake();
        assertEquals(clientSocket.getInputStream().read(), 42);
        serverCertificate = (X509Certificate) clientSocket.getSession().getPeerCertificates()[0];
      }

      serverThread.join(5000);
      if (serverError.get() != null) {
        fail("server handshake failed", serverError.get());
      }
      return commonName(serverCertificate);
    }
  }

  /**
   * Extracts the CN from an X.500 subject principal such as {@code CN=beta}.
   */
  private static String commonName(X509Certificate certificate) {
    String name = certificate.getSubjectX500Principal().getName();
    for (String part : name.split(",")) {
      String trimmed = part.trim();
      if (trimmed.startsWith("CN=")) {
        return trimmed.substring(3);
      }
    }
    fail("no CN in " + name);
    return null;
  }

  /**
   * Generates a self-signed RSA key pair with the given alias (and matching CN) into the keystore.
   */
  private static void generateKeyPair(Path keyStore, String alias) throws Exception {
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool,
      "-genkeypair",
      "-alias", alias,
      "-dname", "CN=" + alias,
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
    assertTrue(process.waitFor() == 0, "keytool failed: " + output);
  }

  /**
   * Derives a truststore containing the named certificates from the given keystore as trusted entries.
   */
  private static Path deriveTrustStore(Path keyStoreFile, String... aliases) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    for (String alias : aliases) {
      trustStore.setCertificateEntry(alias, keyStore.getCertificate(alias));
    }
    Path trustStoreFile = Files.createTempFile("tentackle-ssl-truststore", ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }

  private static void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    }
    else {
      System.setProperty(key, value);
    }
  }

}
