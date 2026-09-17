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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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
public final class QuicTestSupport {

  /** The password of the generated keystores. */
  public static final String STORE_PASSWORD = "secret";

  /** The certificate alias within the generated keystores. */
  public static final String CERTIFICATE_ALIAS = "quic";


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
   * KWIK accepts RSA and EC certificates only; any other key algorithm makes its
   * {@code ServerConnector.Builder.build()} fail with a {@code CertificateException}.
   *
   * @param keyAlgorithm the key algorithm, e.g. {@code RSA} or {@code DSA}
   * @param keySize the key size in bits
   * @return the path of the keystore file
   * @throws Exception if generating the keystore failed
   */
  public static Path createKeyStore(String keyAlgorithm, int keySize) throws Exception {
    Path keyStore = Files.createTempFile("tentackle-quic-keystore", ".p12");
    Files.delete(keyStore);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool,
      "-genkeypair",
      "-alias", CERTIFICATE_ALIAS,
      "-dname", "CN=localhost",
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


  private QuicTestSupport() {
  }

}
