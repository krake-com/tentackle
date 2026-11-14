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

package org.tentackle.persist;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.common.Constants;
import org.tentackle.dbms.Db;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.server.TestCertificateAuthenticationProvider;
import org.tentackle.persist.server.TestServer;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.CertificateCredentials;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the login by a client certificate against a real server via TLS.
 * <p>
 * Covers the whole path: the server's TLS transport verifies the client certificate, the
 * {@link org.tentackle.dbms.trip.RemoteDbSessionImpl} hands it to the
 * {@link org.tentackle.session.auth.AuthenticationManager} in the
 * {@link org.tentackle.session.auth.AuthenticationContext}, and the discovered
 * {@link TestCertificateAuthenticationProvider} maps it onto the user, provided the PIN sent in the
 * {@link CertificateCredentials} is right.
 *
 * @author harald
 */
public class RemoteCertificateAuthenticationTest {

  private static final String STORE_PASSWORD = "secret";

  private Path serverKeyStore;      // CN=localhost
  private Path serverTrustStore;    // the client certificates the server accepts: CN=certuser
  private Path clientKeyStore;      // CN=certuser
  private Path clientTrustStore;    // the server certificate the client accepts: CN=localhost


  @BeforeClass(alwaysRun = true)
  public void createStores() throws Exception {
    serverKeyStore = generateKeyPair("server", "CN=localhost");
    clientKeyStore = generateKeyPair("client", TestCertificateAuthenticationProvider.SUBJECT);
    serverTrustStore = deriveTrustStore(clientKeyStore, "client");
    clientTrustStore = deriveTrustStore(serverKeyStore, "server");
  }

  @AfterClass(alwaysRun = true)
  public void deleteStores() throws Exception {
    for (Path path : new Path[] { serverKeyStore, serverTrustStore, clientKeyStore, clientTrustStore }) {
      if (path != null) {
        Files.deleteIfExists(path);
      }
    }
  }


  @Test(groups = "remote1", dependsOnGroups = "local")
  public void testCertificateAuthentication() throws Exception {
    Reporter.log("RemoteCertificateAuthenticationTest.testCertificateAuthentication <br>", true);

    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    // "want": a client without a certificate gets as far as the provider chain, which must veto it
    String service = "trips://localhost:" + port + "/TestServer?ksfile=" + uriPath(serverKeyStore) +
                     "&kspass=" + STORE_PASSWORD + "&tsfile=" + uriPath(serverTrustStore) +
                     "&tspass=" + STORE_PASSWORD + "&clientauth=want";
    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(TestServer.class,
                                                                                     "--service=" + service));
    try {
      server.awaitReady(30000);
      String url = "trips://localhost:" + port + "/TestServer?tsfile=" + uriPath(clientTrustStore) +
                   "&tspass=" + STORE_PASSWORD;
      String urlWithCertificate = url + "&ksfile=" + uriPath(clientKeyStore) + "&kspass=" + STORE_PASSWORD;

      // the certificate and the PIN identify the user, even though the client never knew who it was
      Db session = login(urlWithCertificate, TestCertificateAuthenticationProvider.PIN);
      try {
        SessionInfo serverInfo = session.getRemoteSession().getClientSessionInfo();
        assertEquals(serverInfo.getUserId(), TestCertificateAuthenticationProvider.USER_ID);
        assertEquals(serverInfo.getUserClassId(), TestCertificateAuthenticationProvider.USER_CLASS_ID);
        assertEquals(serverInfo.getUserName(), TestCertificateAuthenticationProvider.USER_NAME);
      }
      finally {
        session.close();
      }

      // the certificate alone is not enough
      assertThrows(LoginFailedException.class, () -> login(urlWithCertificate, "0000".toCharArray()));
    }
    finally {
      server.stopAndWait();
    }
  }

  @Test(groups = "remote1", dependsOnGroups = "local", dependsOnMethods = "testCertificateAuthentication")
  public void testNoCertificateIsVetoed() throws Exception {
    Reporter.log("RemoteCertificateAuthenticationTest.testNoCertificateIsVetoed <br>", true);

    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    String service = "trips://localhost:" + port + "/TestServer?ksfile=" + uriPath(serverKeyStore) +
                     "&kspass=" + STORE_PASSWORD + "&tsfile=" + uriPath(serverTrustStore) +
                     "&tspass=" + STORE_PASSWORD + "&clientauth=want";
    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(TestServer.class,
                                                                                     "--service=" + service));
    try {
      server.awaitReady(30000);
      String url = "trips://localhost:" + port + "/TestServer?tsfile=" + uriPath(clientTrustStore) +
                   "&tspass=" + STORE_PASSWORD;
      // the PIN is right, but there is no certificate to bind it to
      assertThrows(LoginFailedException.class, () -> login(url, TestCertificateAuthenticationProvider.PIN));
    }
    finally {
      server.stopAndWait();
    }
  }


  /**
   * Opens a remote session by certificate.
   *
   * @param url the URL of the server, with or without a client keystore
   * @param pin the PIN
   * @return the session
   */
  private Db login(String url, char[] pin) {
    SessionInfo sessionInfo = Pdo.createSessionInfo("client");
    sessionInfo.getProperties().setProperty(Constants.BACKEND_URL, url);
    sessionInfo.setCredentials(new CertificateCredentials(pin));
    return (Db) Pdo.createSession(sessionInfo);
  }

  private static String uriPath(Path path) {
    return path.toAbsolutePath().toString().replace('\\', '/');
  }

  private static Path generateKeyPair(String alias, String dname) throws Exception {
    Path keyStore = Files.createTempFile("tentackle-cert-" + alias, ".p12");
    Files.delete(keyStore);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool, "-genkeypair", "-alias", alias, "-dname", dname, "-ext", "san=dns:localhost",
      "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "1",
      "-keystore", keyStore.toString(), "-storetype", "PKCS12",
      "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    assertEquals(process.waitFor(), 0, "keytool failed: " + output);
    return keyStore;
  }

  private static Path deriveTrustStore(Path keyStoreFile, String alias) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry(alias, keyStore.getCertificate(alias));
    Path trustStoreFile = Files.createTempFile("tentackle-cert-trust-" + alias, ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }

}
