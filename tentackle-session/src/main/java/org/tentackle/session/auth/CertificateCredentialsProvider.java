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
import org.tentackle.io.KeyStorePasswordProvider;
import org.tentackle.io.SslParameters;
import org.tentackle.session.SessionBundle;
import org.tentackle.session.SessionInfo;

import java.net.URI;
import java.util.Arrays;

/**
 * Obtains the {@link CertificateCredentials} to log in with the client certificate.
 * <p>
 * The certificate itself is presented by the TLS connection, which is configured by the
 * transport URI ({@code ksfile}, {@code ksalias}, see {@link SslParameters}). Up to two secrets
 * may be needed in addition, and both are asked for via {@link CredentialsPrompt#promptSecret(String)}:
 * <ol>
 *   <li>the <b>password of the client keystore</b>, if the URI configures a keystore but not its password.
 *   It is verified right away, so a typo is reported before the connection is established, and registered
 *   at the {@link KeyStorePasswordProvider}, where the TLS layer picks it up. It unlocks the private key
 *   on the client and never leaves it.</li>
 *   <li>a <b>secret verified by the server</b>, such as a PIN, if {@value Constants#BACKEND_AUTHENTICATION_SECRET}
 *   is {@code true}. It is sent with the credentials and checked by the server's
 *   {@link AuthenticationProvider} as a second factor.</li>
 * </ol>
 * Selected in {@code backend.properties} by:
 * <pre>
 *   url=trips://server.example.com:28000?ksfile=/home/me/me.p12&amp;tsfile=/etc/myapp/trust.p12&amp;tspass=...
 *   authentication=certificate
 *   authenticationSecret=true
 * </pre>
 *
 * @author harald
 */
@CredentialsProviderService(CertificateCredentials.SCHEME)
public class CertificateCredentialsProvider implements CredentialsProvider {

  /** the maximum number of attempts to enter the keystore password. */
  public static final int MAX_PASSWORD_ATTEMPTS = 3;


  /**
   * Creates the provider.
   */
  public CertificateCredentialsProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * {@inheritDoc}
   * <p>
   * The login view shows a sign-in button instead of the username and password fields:
   * the user is determined by the certificate.
   *
   * @return true
   */
  @Override
  public boolean isInteractive() {
    return true;
  }

  @Override
  public Credentials createCredentials(SessionInfo sessionInfo, CredentialsPrompt prompt) {
    SslParameters sslParameters = getSslParameters(sessionInfo);
    if (sslParameters != null && sslParameters.isKeyStorePasswordMissing() && !unlockKeyStore(sslParameters, prompt)) {
      return null;    // canceled
    }
    if (prompt.isCanceled()) {
      return null;
    }
    char[] secret = null;
    if (isSecretRequired(sessionInfo)) {
      secret = prompt.promptSecret(SessionBundle.getString("PIN"));
      if (secret == null) {
        return null;  // canceled
      }
    }
    try {
      return new CertificateCredentials(secret);    // copies the secret
    }
    finally {
      if (secret != null) {
        Arrays.fill(secret, ' ');
      }
    }
  }


  /**
   * Gets the SSL parameters of the connection to the server.
   *
   * @param sessionInfo the session info
   * @return the SSL parameters, null if the session does not connect to a server via a URI
   */
  protected SslParameters getSslParameters(SessionInfo sessionInfo) {
    String url = sessionInfo.getProperties().getPropertyIgnoreCase(Constants.BACKEND_URL);
    if (url == null || url.startsWith(Constants.BACKEND_JDBC_URL_INTRO) || url.startsWith(Constants.BACKEND_JNDI_URL_INTRO)) {
      return null;
    }
    return new SslParameters(URI.create(url));
  }

  /**
   * Returns whether the user must enter a secret verified by the server.
   *
   * @param sessionInfo the session info
   * @return true if {@value Constants#BACKEND_AUTHENTICATION_SECRET} is {@code true}
   */
  protected boolean isSecretRequired(SessionInfo sessionInfo) {
    return Boolean.parseBoolean(sessionInfo.getProperties().getPropertyIgnoreCase(Constants.BACKEND_AUTHENTICATION_SECRET));
  }

  /**
   * Asks the user for the keystore password and registers it at the {@link KeyStorePasswordProvider}.
   *
   * @param sslParameters the SSL parameters
   * @param prompt the user interaction
   * @return true if unlocked, false if the user canceled
   * @throws AuthenticationException if the password was wrong {@value #MAX_PASSWORD_ATTEMPTS} times
   */
  protected boolean unlockKeyStore(SslParameters sslParameters, CredentialsPrompt prompt) {
    for (int attempt = 0; attempt < MAX_PASSWORD_ATTEMPTS; attempt++) {
      if (prompt.isCanceled()) {
        return false;
      }
      char[] password = prompt.promptSecret(SessionBundle.getString("CERTIFICATE PASSWORD"));
      if (password == null) {
        return false;
      }
      try {
        if (sslParameters.checkKeyStorePassword(password)) {
          KeyStorePasswordProvider.getInstance().setPassword(sslParameters.getKeyStoreFile(), password);
          return true;
        }
      }
      finally {
        Arrays.fill(password, ' ');
      }
      prompt.showMessage(SessionBundle.getString("WRONG PASSWORD, PLEASE TRY AGAIN"));
    }
    throw new AuthenticationException("wrong password for keystore " + sslParameters.getKeyStoreFile());
  }

}
