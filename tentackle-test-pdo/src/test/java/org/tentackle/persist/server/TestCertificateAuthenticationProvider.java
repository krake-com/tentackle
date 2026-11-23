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

package org.tentackle.persist.server;

import org.tentackle.common.Service;
import org.tentackle.session.auth.AbstractCertificateAuthenticationProvider;
import org.tentackle.session.auth.Authentication;
import org.tentackle.session.auth.AuthenticationContext;
import org.tentackle.session.auth.AuthenticationException;
import org.tentackle.session.auth.AuthenticationProvider;
import org.tentackle.session.auth.CertificateCredentials;
import org.tentackle.session.auth.DefaultAuthentication;

import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Authenticates the client certificate {@value #SUBJECT} together with the PIN {@code 4711}.
 * <p>
 * Used by {@code RemoteCertificateAuthenticationTest}.
 *
 * @author harald
 */
@Service(AuthenticationProvider.class)
public class TestCertificateAuthenticationProvider extends AbstractCertificateAuthenticationProvider {

  /** the subject of the only accepted client certificate. */
  public static final String SUBJECT = "CN=certuser";

  /** the PIN the user must enter. */
  public static final char[] PIN = "4711".toCharArray();

  /** the name of the user the certificate belongs to. */
  public static final String USER_NAME = "certuser";

  /** the object id of the user the certificate belongs to. */
  public static final long USER_ID = 816L;

  /** the class id of the user the certificate belongs to. */
  public static final int USER_CLASS_ID = 4712;


  /**
   * Creates the provider.
   */
  public TestCertificateAuthenticationProvider() {
    // services need a public no-arg constructor
  }


  @Override
  protected Authentication authenticate(CertificateCredentials credentials, X509Certificate certificate,
                                        char[] secret, AuthenticationContext context) {
    if (!SUBJECT.equals(certificate.getSubjectX500Principal().getName()) || !Arrays.equals(secret, PIN)) {
      throw new AuthenticationException("certificate or PIN rejected");
    }
    Authentication authentication = new DefaultAuthentication(USER_NAME, credentials);
    authentication.setUserId(USER_ID);
    authentication.setUserClassId(USER_CLASS_ID);
    return authentication;
  }

}
