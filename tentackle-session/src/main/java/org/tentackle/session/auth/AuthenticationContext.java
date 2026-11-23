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
 * What the server itself knows about a client that logs in.
 * <p>
 * Unlike the {@link Credentials}, which the client <em>sends</em> and which are therefore only claims until
 * verified, the context is established by the server's transport. Most notably, it holds the client
 * certificate chain of a mutually authenticated TLS connection, which the TLS handshake has already verified
 * against the server's truststore. An {@link AuthenticationProvider} receives the context along with the
 * credentials (see {@link AuthenticationProvider#authenticate(Credentials, AuthenticationContext)}), so it
 * can authenticate the client by its certificate, or bind other credentials to it.
 * <p>
 * The context is empty for transports without client certificates, and for sessions that are not opened
 * via an application server.
 *
 * @author harald
 * @see CertificateCredentials
 */
public interface AuthenticationContext {

  /**
   * The empty context, without any information about the client.
   */
  AuthenticationContext NONE = new DefaultAuthenticationContext(null, null, List.of());


  /**
   * Gets the verified certificate chain of the client.
   *
   * @return the certificates, leaf first, never null, empty if the client presented no certificate
   */
  List<X509Certificate> getPeerCertificates();

  /**
   * Gets the client's certificate.
   *
   * @return the leaf of the verified certificate chain, null if the client presented no certificate
   */
  default X509Certificate getPeerCertificate() {
    List<X509Certificate> certificates = getPeerCertificates();
    return certificates.isEmpty() ? null : certificates.getFirst();
  }

  /**
   * Gets the address of the client host.
   *
   * @return the host address, null if unknown
   */
  String getClientHost();

  /**
   * Gets the protocol the client connected with.
   *
   * @return the URI scheme of the TRIP transport, such as {@code trips}, null if unknown
   */
  String getProtocol();

}
