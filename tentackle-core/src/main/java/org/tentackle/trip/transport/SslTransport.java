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

package org.tentackle.trip.transport;

import org.tentackle.io.SslParameters;
import org.tentackle.io.TcpServerSocketFactory;
import org.tentackle.io.TcpSocketFactory;
import org.tentackle.trip.TransportService;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.net.URI;

/**
 * Transport implementation for SSL over TCP.<br>
 * <p>
 * Uses the SSL-aware {@link TcpSocketFactory} and {@link TcpServerSocketFactory}, which
 * &mdash; unlike the default JSSE factories &mdash; allow selecting the certificate by its alias
 * and tuning the SSL options. The configuration is taken from the URI query parameters or the
 * {@code tentackle.ssl.*} (and, as a fallback, {@code javax.net.ssl.*}) system properties;
 * see {@link SslParameters}.
 */
@TransportService(SslTransport.PROTOCOL)
public class SslTransport extends TcpTransport {

  /**
   * The protocol name for SSL over TCP.
   */
  public static final String PROTOCOL = "trips";

  private SslParameters sslParameters;    // the resolved SSL parameters, shared by both factories

  /**
   * Creates a new SSL transport.
   *
   * @param uri the URI of the transport
   */
  public SslTransport(URI uri) {
    super(uri);
  }


  @Override
  protected SocketFactory createSocketFactory() {
    return new TcpSocketFactory(getSslParameters());
  }

  @Override
  protected ServerSocketFactory createServerSocketFactory() {
    return new TcpServerSocketFactory(getSslParameters());
  }

  /**
   * Gets the SSL parameters resolved from the transport URI.<br>
   * The parameters (and the SSL context they build) are created once and shared
   * between the client- and server socket factories.
   *
   * @return the SSL parameters
   */
  protected synchronized SslParameters getSslParameters() {
    if (sslParameters == null) {
      sslParameters = new SslParameters(getUri());
    }
    return sslParameters;
  }

}
