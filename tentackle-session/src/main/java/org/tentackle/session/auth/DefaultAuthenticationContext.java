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
import java.util.List;

/**
 * The default implementation of an {@link AuthenticationContext}.
 *
 * @param clientHost the address of the client host, null if unknown
 * @param protocol the protocol the client connected with, null if unknown
 * @param peerCertificates the verified certificate chain of the client, leaf first, null or empty if none
 * @author harald
 */
public record DefaultAuthenticationContext(String clientHost, String protocol, List<X509Certificate> peerCertificates)
       implements AuthenticationContext {

  /**
   * Creates an authentication context.
   *
   * @param clientHost the address of the client host, null if unknown
   * @param protocol the protocol the client connected with, null if unknown
   * @param peerCertificates the verified certificate chain of the client, leaf first, null or empty if none
   */
  public DefaultAuthenticationContext {
    peerCertificates = peerCertificates == null ? List.of() : List.copyOf(peerCertificates);
  }

  @Override
  public List<X509Certificate> getPeerCertificates() {
    return peerCertificates;
  }

  @Override
  public String getClientHost() {
    return clientHost;
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  @Override
  public String toString() {
    X509Certificate certificate = getPeerCertificate();
    return protocol + "://" + clientHost +
           (certificate == null ? "" : " [" + certificate.getSubjectX500Principal().getName() + ']');
  }

}
