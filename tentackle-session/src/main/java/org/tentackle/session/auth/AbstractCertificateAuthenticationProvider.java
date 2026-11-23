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

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Base class of a provider that authenticates the client by its certificate.
 * <p>
 * The provider is responsible for {@link CertificateCredentials}. Before the concrete provider is asked,
 * it makes sure that the client presented a certificate: if not, the login is vetoed rather than passed on
 * to the next provider, since the client asked for a certificate-based login it cannot prove.
 * <p>
 * The certificate chain has already been verified by the TLS handshake against the server's truststore.
 * What is left to the application is to map the certificate onto its user, and to verify the optional secret:
 * <pre>
 *    &#64;Service(AuthenticationProvider.class)
 *    public class MyCertificateAuthenticationProvider extends AbstractCertificateAuthenticationProvider {
 *
 *      &#64;Override
 *      protected Authentication authenticate(CertificateCredentials credentials, X509Certificate certificate,
 *                                            char[] secret, AuthenticationContext context) {
 *        User user = findUserBySubject(certificate.getSubjectX500Principal().getName());
 *        if (user == null || !user.isPinValid(secret)) {
 *          throw new AuthenticationException("certificate or PIN rejected");
 *        }
 *        Authentication authentication = new DefaultAuthentication(user.getName(), credentials);
 *        authentication.setUserId(user.getId());
 *        authentication.setUserClassId(user.getClassId());
 *        return authentication;
 *      }
 *    }
 * </pre>
 * Notice that a certificate alone only proves possession of the private key. A secret verified by the server,
 * like a PIN, should be required wherever a stolen device must not be enough to log in.
 *
 * @author harald
 */
public abstract class AbstractCertificateAuthenticationProvider implements AuthenticationProvider {

  /**
   * Parent constructor.
   */
  public AbstractCertificateAuthenticationProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  public boolean supports(Credentials credentials) {
    return credentials instanceof CertificateCredentials;
  }

  @Override
  public Authentication authenticate(Credentials credentials, AuthenticationContext context) {
    X509Certificate certificate = context.getPeerCertificate();
    if (certificate == null) {
      throw new AuthenticationException("no client certificate presented");
    }
    CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
    char[] secret = certificateCredentials.getSecret();
    try {
      return authenticate(certificateCredentials, certificate, secret, context);
    }
    finally {
      if (secret != null) {
        Arrays.fill(secret, ' ');
      }
    }
  }


  /**
   * Authenticates the client by its certificate.
   * <p>
   * The whole chain is available via {@link AuthenticationContext#getPeerCertificates()}.
   *
   * @param credentials the credentials, never null
   * @param certificate the verified client certificate, never null
   * @param secret the secret entered by the user, null if none, wiped after this method returns
   * @param context the context of the client's connection, never null
   * @return the proven identity, null to abstain
   * @throws org.tentackle.session.LoginFailedException if the login must be refused
   */
  protected abstract Authentication authenticate(CertificateCredentials credentials, X509Certificate certificate,
                                                 char[] secret, AuthenticationContext context);

  /**
   * Gets the subject alternative names of the given type from a certificate.<br>
   * Useful to map the certificate onto the user by e-mail address ({@code 1}) or DNS name ({@code 2}),
   * see {@link X509Certificate#getSubjectAlternativeNames()}.
   *
   * @param certificate the certificate
   * @param type the general name type, for example 1 for rfc822Name
   * @return the names, never null
   */
  protected List<String> getSubjectAlternativeNames(X509Certificate certificate, int type) {
    try {
      var names = certificate.getSubjectAlternativeNames();
      return names == null ? List.of() :
             names.stream()
                  .filter(name -> name.size() >= 2 && name.get(0) instanceof Integer t && t == type &&
                                  name.get(1) instanceof String)
                  .map(name -> (String) name.get(1))
                  .toList();
    }
    catch (java.security.cert.CertificateParsingException ex) {
      throw new AuthenticationException("malformed subject alternative names", ex);
    }
  }

}
