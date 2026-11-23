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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Secret;
import org.tentackle.trip.TripPreAuth;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;

/**
 * Credentials to log in with the client certificate of a mutually authenticated TLS connection.
 * <p>
 * The credentials do <em>not</em> contain the certificate. Anything the client sends is just a claim, but
 * the certificate is proven by the TLS handshake, and the server takes it from there: an
 * {@link AuthenticationProvider} gets it from the {@link AuthenticationContext}
 * (see {@link AuthenticationProvider#authenticate(Credentials, AuthenticationContext)}). The credentials
 * merely select the certificate-based authentication and, optionally, carry a secret, such as a PIN, which
 * the provider verifies as a second factor: something the user has, and something the user knows.
 * <p>
 * The password that protects the private key in the client's keystore is something else: it unlocks the
 * key on the client and never leaves it (see {@link CertificateCredentialsProvider}).
 *
 * @author harald
 * @see AbstractCertificateAuthenticationProvider
 */
@TripPreAuth("login")
@TripReferencable(TripReferencable.NEVER)
public class CertificateCredentials implements Credentials, Cloneable {

  /** the scheme name of certificate credentials. */
  public static final String SCHEME = "certificate";

  @Serial
  private static final long serialVersionUID = 1L;

  private Secret secret;      // the optional secret, such as a PIN


  /**
   * Creates certificate credentials.
   *
   * @param secret the secret, null if none
   */
  public CertificateCredentials(char[] secret) {
    this.secret = new Secret(secret);
  }

  /**
   * Creates certificate credentials without a secret.<br>
   * Also required by TRIP to deserialize the credentials on the server.
   */
  public CertificateCredentials() {
    this(null);
  }


  @Override
  public String getScheme() {
    return SCHEME;
  }

  /**
   * Gets the secret.
   *
   * @return a copy of the secret the caller should wipe once done, null if none or cleared
   */
  public char[] getSecret() {
    return secret.get();
  }

  /**
   * Sets the secret.
   *
   * @param secret the secret, null if none
   */
  public void setSecret(char[] secret) {
    this.secret.set(secret);
  }

  /**
   * Returns whether a secret is set.
   *
   * @return true if set
   */
  public boolean isSecretSet() {
    return secret.isSet();
  }

  @Override
  public void clear() {
    secret.clear();
  }

  @Override
  public CertificateCredentials clone() {
    CertificateCredentials credentials;
    try {
      credentials = (CertificateCredentials) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
    credentials.secret = secret.clone();
    return credentials;
  }

  @Override
  public String toString() {
    return SCHEME + '/' + secret;
  }

}
