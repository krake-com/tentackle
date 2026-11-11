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
import org.tentackle.io.UriHelper;

import java.net.URI;
import java.util.Objects;
import java.util.function.Function;

/**
 * The {@link Transport} key.
 * <p>
 * Transports are unique by scheme, host, and port.<br>
 * The query parameters configure the transport and are carried along, but they are not part
 * of the key's identity: there cannot be more than one transport for the same scheme, host and port.
 * <p>
 * The key is local to the JVM and never sent via TRIP, since the query parameters may hold passwords.
 *
 * @param scheme the transport scheme
 * @param host the transport host
 * @param port the transport port
 * @param queryParameters the optional URI-query parameters, null if none
 */
@TripSerializable(false)
public record TransportKey(String scheme, String host, int port, String queryParameters) {

  /**
   * Creates a transport key from a URI.
   *
   * @param uri the URI
   */
  public TransportKey(URI uri) {
    this(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getQuery());
  }

  /**
   * Returns whether the query parameters of this key are the same as those of another key.<br>
   * The order of the parameters doesn't matter, and a missing query equals an empty one.
   *
   * @param other the other key
   * @return true if same query parameters
   */
  public boolean isSameQuery(TransportKey other) {
    return Objects.equals(UriHelper.getQueryParameters(queryParameters, Function.identity()),
                          UriHelper.getQueryParameters(other.queryParameters, Function.identity()));
  }

  /**
   * Compares scheme, host and port only.
   *
   * @param o the other object
   * @return true if equal
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof TransportKey(String otherScheme, String otherHost, int otherPort, _) &&
           port == otherPort && Objects.equals(scheme, otherScheme) && Objects.equals(host, otherHost);
  }

  /**
   * Hashes scheme, host and port only.
   *
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(scheme, host, port);
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
    // not the query parameters: they may hold passwords
    return buf.toString();
  }

}
