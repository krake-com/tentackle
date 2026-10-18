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

import org.tentackle.quiche.ffm.PemExporter;
import org.tentackle.quiche.ffm.QuicheConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Common helpers for the QUIC tests.<br>
 * QUIC mandates TLS, so every server-side test needs a keystore holding a server certificate.
 * Rather than checking a certificate into the repository (which would expire), the tests generate
 * a throw-away self-signed one with {@code keytool} at runtime.
 */
@SuppressWarnings("exports")   // the test helpers hand out internal types on purpose
public final class QuicheTestSupport {

  /** The password of the generated keystores. */
  public static final String STORE_PASSWORD = "secret";

  /** The certificate alias within the generated keystores. */
  public static final String CERTIFICATE_ALIAS = "quiche";


  /**
   * Creates a PKCS12 keystore holding a single self-signed RSA certificate under
   * {@link #CERTIFICATE_ALIAS}, usable as a QUIC server certificate.
   *
   * @return the path of the keystore file
   * @throws Exception if generating the keystore failed
   */
  public static Path createKeyStore() throws Exception {
    return createKeyStore("RSA", 2048);
  }

  /**
   * The seconds waited for {@code keytool} to finish generating a keystore.
   * <p>
   * A safety net, not a realistic budget: a local {@code -genkeypair} normally completes in well
   * under a second. Without a bound, a stuck subprocess would hang test setup indefinitely.
   */
  private static final long KEYTOOL_TIMEOUT_SECONDS = 30;

  /**
   * Creates a PKCS12 keystore holding a single self-signed certificate under
   * {@link #CERTIFICATE_ALIAS}.<br>
   * BoringSSL, which quiche builds on, accepts RSA and EC certificates only; any other key algorithm
   * makes loading the certificate chain fail.
   *
   * @param keyAlgorithm the key algorithm, e.g. {@code RSA} or {@code DSA}
   * @param keySize the key size in bits
   * @return the path of the keystore file
   * @throws Exception if generating the keystore failed
   */
  public static Path createKeyStore(String keyAlgorithm, int keySize) throws Exception {
    Path keyStore = Files.createTempFile("tentackle-quiche-keystore", ".p12");
    Files.delete(keyStore);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool,
      "-genkeypair",
      "-alias", CERTIFICATE_ALIAS,
      "-dname", "CN=localhost",
      "-ext", "san=dns:localhost",
      "-keyalg", keyAlgorithm,
      "-keysize", String.valueOf(keySize),
      "-validity", "1",
      "-keystore", keyStore.toString(),
      "-storetype", "PKCS12",
      "-storepass", STORE_PASSWORD,
      "-keypass", STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("keytool did not finish within " + KEYTOOL_TIMEOUT_SECONDS + " seconds, output so far: " + output);
    }
    assertTrue(process.exitValue() == 0, "keytool failed: " + output);
    return keyStore;
  }

  /**
   * Asserts that the given UDP port can be bound on the loopback interface, i.e. that nothing
   * is holding on to it anymore.
   *
   * @param port the port
   */
  public static void assertPortIsFree(int port) {
    try (DatagramSocket socket = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
      assertEquals(socket.getLocalPort(), port);
    }
    catch (IOException e) {
      fail("UDP port " + port + " is still in use", e);
    }
  }

  /**
   * Determines a UDP port that is currently free on the loopback interface.<br>
   * QUIC servers must be bound to a known port because the client pool derives it from the URI,
   * so the port cannot be left to the OS as it could be for an ephemeral TCP server socket.
   *
   * @return the port number
   * @throws IOException if no port could be allocated
   */
  public static int freeUdpPort() throws IOException {
    try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  /**
   * Renders a filesystem path for embedding in a URI query.<br>
   * Windows paths use backslashes, which are illegal in a URI; forward slashes are valid in a URI
   * query and are accepted by the Windows file APIs as well.
   *
   * @param path the path
   * @return the path usable within a URI query string
   */
  public static String uriPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  /**
   * Builds the keystore-related part of a transport URI query string.
   *
   * @param keyStore the keystore file
   * @return the query string fragment, without a leading separator
   */
  public static String keyStoreParameters(Path keyStore) {
    return "ksfile=" + uriPath(keyStore) + "&kspass=" + STORE_PASSWORD + "&ksalias=" + CERTIFICATE_ALIAS;
  }

  /**
   * Derives a truststore holding the certificate under {@link #CERTIFICATE_ALIAS} of the given keystore
   * as a trusted entry, usable for a client connecting with {@code certcheck=true}.
   *
   * @param keyStore the keystore generated by {@link #createKeyStore()}
   * @return the path of the truststore file
   * @throws Exception if deriving the truststore failed
   */
  public static Path deriveTrustStore(Path keyStore) throws Exception {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keyStore)) {
      store.load(in, STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry(CERTIFICATE_ALIAS, store.getCertificate(CERTIFICATE_ALIAS));
    Path trustStoreFile = Files.createTempFile("tentackle-quiche-truststore", ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }

  /**
   * Builds the truststore-related part of a transport URI query string.
   *
   * @param trustStore the truststore file, e.g. from {@link #deriveTrustStore(Path)}
   * @return the query string fragment, without a leading separator
   */
  public static String trustStoreParameters(Path trustStore) {
    return "tsfile=" + uriPath(trustStore) + "&tspass=" + STORE_PASSWORD;
  }


  /**
   * The ALPN protocol the engine tests use.
   */
  public static final String ALPN = "tripj";

  private static Path certificateChain;     // guarded by the class monitor
  private static Path privateKey;
  private static Path trustedCertificates;

  /**
   * Creates a keystore, exports it to PEM and remembers the files.
   * <p>
   * Done once for the whole test run: generating an RSA key pair takes long enough to be worth sharing,
   * and every test wants the same throw-away certificate.
   *
   * @throws Exception if the keystore could not be created
   */
  public static synchronized void prepareCertificates() throws Exception {
    if (certificateChain == null) {
      Path keyStoreFile = createKeyStore();
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (InputStream in = Files.newInputStream(keyStoreFile)) {
        keyStore.load(in, STORE_PASSWORD.toCharArray());
      }
      certificateChain = PemExporter.writeCertificateChain(keyStore, CERTIFICATE_ALIAS);
      privateKey = PemExporter.writePrivateKey(keyStore, CERTIFICATE_ALIAS, STORE_PASSWORD.toCharArray());
      trustedCertificates = PemExporter.writeTrustedCertificates(keyStore);
      Files.deleteIfExists(keyStoreFile);
    }
  }

  /**
   * Creates a server configuration presenting the test certificate.
   *
   * @return the configuration, to be closed after the connections created from it
   * @throws Exception if the certificate could not be prepared
   */
  public static QuicheConfig serverConfig() throws Exception {
    prepareCertificates();
    return commonConfig()
      .loadCertChain(certificateChain)
      .loadPrivateKey(privateKey)
      .verifyPeer(false);
  }

  /**
   * Creates a client configuration verifying the test certificate.
   *
   * @return the configuration, to be closed after the connections created from it
   * @throws Exception if the certificate could not be prepared
   */
  public static QuicheConfig clientConfig() throws Exception {
    prepareCertificates();
    return commonConfig()
      .loadVerifyLocations(trustedCertificates)
      .verifyPeer(true);
  }

  /**
   * Creates the settings both ends share.
   *
   * @return the configuration
   */
  private static QuicheConfig commonConfig() {
    return new QuicheConfig()
      .setApplicationProtocols(ALPN)
      .setMaxIdleTimeout(30_000)
      .setInitialMaxData(1_000_000)
      .setInitialMaxStreamDataBidiLocal(250_000)
      .setInitialMaxStreamDataBidiRemote(250_000)
      .setInitialMaxStreamsBidi(8)
      .setInitialMaxStreamsUni(0)
      // as QuicheTripTransport does, so that a client can migrate more than once
      .setActiveConnectionIdLimit(8);
  }


  private QuicheTestSupport() {
  }

}
