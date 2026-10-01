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

package org.tentackle.trip;

import org.tentackle.common.StringHelper;

import java.net.URI;

/**
 * The {@link Transport} key.
 * <p>
 * Transports are unique by scheme, host, port and query parameters.
 *
 * @param scheme the transport scheme
 * @param host the transport host
 * @param port the transport port
 * @param queryParameters the optional URI-query parameters, null if none
 */
public record TransportKey(String scheme, String host, int port, String queryParameters) {

  /**
   * Creates a transport key from a URI.
   *
   * @param uri the URI
   */
  public TransportKey(URI uri) {
    this(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getQuery());
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder().append(scheme).append("://");
    if (!StringHelper.isAllWhitespace(host)) {
      buf.append(host);
    }
    else {
      buf.append('*');
    }
    buf.append(":");
    if (port > 0) {
      buf.append(port);
    }
    else {
      buf.append('*');
    }
    if (!StringHelper.isAllWhitespace(queryParameters)) {
      buf.append('?').append(queryParameters);
    }
    return buf.toString();
  }

}
