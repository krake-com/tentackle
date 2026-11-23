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

package org.tentackle.session.auth;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.io.KeyStorePasswordProvider;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Tests the certificate-based authentication: the client side obtaining the credentials,
 * and the server side authenticating the certificate from the {@link AuthenticationContext}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CertificateAuthenticationTest {

  private static final String STORE_PASSWORD = "secret";
  private static final String PIN = "1234";
  private static final long USER_ID = 4711L;


  /**
   * Accepts CN=hugo with the PIN 1234.
   */
  private static class TestCertificateProvider extends AbstractCertificateAuthenticationProvider {

    @Override
    protected Authentication authenticate(CertificateCredentials credentials, X509Certificate certificate,
                                          char[] secret, AuthenticationContext context) {
      if (!"CN=hugo".equals(certificate.getSubjectX500Principal().getName()) ||
          !Arrays.equals(secret, PIN.toCharArray())) {
        throw new AuthenticationException("certificate or PIN rejected");
      }
      Authentication authentication = new DefaultAuthentication("hugo", credentials);
      authentication.setUserId(USER_ID);
      authentication.setUserClassId(1);
      return authentication;
    }
  }

  /**
   * A prompt answering with scripted secrets.
   */
  private static class ScriptedPrompt implements CredentialsPrompt {

    private final Deque<String> answers;
    private final List<String> labels = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    ScriptedPrompt(String... answers) {
      this.answers = new ArrayDeque<>(Arrays.asList(answers));
    }

    @Override
    public void browse(URI uri) {
      Assert.fail("no browser needed");
    }

    @Override
    public void showMessage(String message) {
      messages.add(message);
    }

    @Override
    public void setProgress(double progress) {
      // not needed
    }

    @Override
    public char[] promptSecret(String label) {
      labels.add(label);
      String answer = answers.poll();
      return answer == null ? null : answer.toCharArray();
    }

    @Override
    public boolean isCanceled() {
      return false;
    }
  }


  private Path keyStoreFile;
  private X509Certificate certificate;


  @BeforeClass
  public void createCertificate() throws Exception {
    keyStoreFile = Files.createTempFile("tentackle-auth-hugo", ".p12");
    Files.delete(keyStoreFile);   // keytool creates it
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool, "-genkeypair", "-alias", "hugo", "-dname", "CN=hugo", "-keyalg", "EC", "-validity", "1",
      "-keystore", keyStoreFile.toString(), "-storetype", "PKCS12",
      "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    Assert.assertEquals(process.waitFor(), 0, "keytool failed: " + output);
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    certificate = (X509Certificate) keyStore.getCertificate("hugo");
  }

  @AfterClass(alwaysRun = true)
  public void deleteCertificate() throws Exception {
    if (keyStoreFile != null) {
      Files.deleteIfExists(keyStoreFile);
    }
  }

  @AfterMethod(alwaysRun = true)
  public void forgetPassword() {
    KeyStorePasswordProvider.getInstance().clear(keyStoreFile.toString());
  }


  private AuthenticationManager manager() {
    return new DefaultAuthenticationManager(List.of(new TestCertificateProvider()));
  }

  private AuthenticationContext context(X509Certificate... certificates) {
    return new DefaultAuthenticationContext("10.0.0.1", "trips", List.of(certificates));
  }

  private SessionInfo sessionInfo(boolean withKeyStorePassword, boolean secretRequired) {
    SessionInfo sessionInfo = new DefaultSessionInfo(null, null, null);
    EncryptedProperties properties = new EncryptedProperties();
    properties.setProperty(Constants.BACKEND_URL, "trips://localhost:28000?ksfile=" +
                           keyStoreFile.toAbsolutePath().toString().replace('\\', '/') +
                           (withKeyStorePassword ? "&kspass=" + STORE_PASSWORD : ""));
    properties.setProperty(Constants.BACKEND_AUTHENTICATION, CertificateCredentials.SCHEME);
    if (secretRequired) {
      properties.setProperty(Constants.BACKEND_AUTHENTICATION_SECRET, "true");
    }
    sessionInfo.setProperties(properties);
    return sessionInfo;
  }


  @Test
  public void testCertificateAndPinAuthenticate() {
    CertificateCredentials credentials = new CertificateCredentials(PIN.toCharArray());
    Authentication authentication = manager().authenticate(credentials, context(certificate));
    Assert.assertEquals(authentication.getName(), "hugo");
    Assert.assertEquals(authentication.getUserId(), USER_ID);
    Assert.assertEquals(authentication.getProviderName(), TestCertificateProvider.class.getSimpleName());
  }

  @Test(expectedExceptions = AuthenticationException.class)
  public void testWrongPinIsRefused() {
    manager().authenticate(new CertificateCredentials("0000".toCharArray()), context(certificate));
  }

  @Test
  public void testMissingCertificateVetoes() {
    List<String> asked = new ArrayList<>();
    AuthenticationProvider fallback = new AuthenticationProvider() {
      @Override
      public boolean supports(Credentials credentials) {
        return true;
      }

      @Override
      public Authentication authenticate(Credentials credentials) {
        asked.add("fallback");
        return null;
      }

      @Override
      public int getPriority() {
        return 1;   // after the certificate provider
      }
    };
    AuthenticationManager manager = new DefaultAuthenticationManager(List.of(new TestCertificateProvider(), fallback));
    CertificateCredentials credentials = new CertificateCredentials(PIN.toCharArray());

    Assert.assertThrows(AuthenticationException.class, () -> manager.authenticate(credentials, context()));
    Assert.assertThrows(AuthenticationException.class, () -> manager.authenticate(credentials));
    // vetoed, not abstained: nobody else may log in a client that asked for a certificate login without one
    Assert.assertTrue(asked.isEmpty());
  }

  @Test
  public void testOtherCredentialsAreNotSupported() {
    Assert.assertFalse(new TestCertificateProvider().supports(new PasswordCredentials("hugo", PIN.toCharArray())));
  }

  @Test
  public void testProviderAsksForKeyStorePasswordAndPin() {
    ScriptedPrompt prompt = new ScriptedPrompt("wrong", STORE_PASSWORD, PIN);
    Credentials credentials = new CertificateCredentialsProvider().createCredentials(sessionInfo(false, true), prompt);

    Assert.assertTrue(credentials instanceof CertificateCredentials);
    Assert.assertEquals(((CertificateCredentials) credentials).getSecret(), PIN.toCharArray());
    Assert.assertEquals(prompt.labels.size(), 3);
    Assert.assertEquals(prompt.messages.size(), 1);   // the wrong password
    // registered for the TLS layer, but never part of the credentials
    Assert.assertEquals(KeyStorePasswordProvider.getInstance().getPassword(keyStoreFile.toString()),
                        STORE_PASSWORD.toCharArray());
    Assert.assertFalse(credentials.toString().contains(STORE_PASSWORD));
  }

  @Test
  public void testProviderWithoutSecretsAsksNothing() {
    ScriptedPrompt prompt = new ScriptedPrompt();
    Credentials credentials = new CertificateCredentialsProvider().createCredentials(sessionInfo(true, false), prompt);

    Assert.assertTrue(credentials instanceof CertificateCredentials);
    Assert.assertFalse(((CertificateCredentials) credentials).isSecretSet());
    Assert.assertTrue(prompt.labels.isEmpty());
    Assert.assertNull(KeyStorePasswordProvider.getInstance().getPassword(keyStoreFile.toString()));
  }

  @Test
  public void testProviderCanceled() {
    Assert.assertNull(new CertificateCredentialsProvider().createCredentials(sessionInfo(false, true), new ScriptedPrompt()));
    Assert.assertNull(new CertificateCredentialsProvider().createCredentials(sessionInfo(true, true), new ScriptedPrompt()));
  }

  @Test(expectedExceptions = AuthenticationException.class)
  public void testProviderGivesUpAfterWrongPasswords() {
    new CertificateCredentialsProvider().createCredentials(sessionInfo(false, false),
                                                           new ScriptedPrompt("wrong", "wronger", "wrongest"));
  }

}
