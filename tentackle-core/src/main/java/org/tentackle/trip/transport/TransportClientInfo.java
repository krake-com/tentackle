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

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Transport;

import java.util.UUID;

/**
 * The client information.
 * <p>
 * Used in servers only and exposed by thread local variable.
 *
 * @param transport the server transport
 * @param dictionary the client's dictionary
 * @param connectionName the client's connection name
 * @param clientId the client's transport UUID
 */
public record TransportClientInfo(Transport transport, Dictionary dictionary, String connectionName, UUID clientId) {

  private static final ThreadLocal<TransportClientInfo> TL_CLIENT_INFO = new ThreadLocal<>();

  /**
   * Gets the client info from the thread local variable.
   *
   * @return the client info, null if not set
   */
  public static TransportClientInfo get() {
    return TL_CLIENT_INFO.get();
  }

  /**
   * Sets the client info to the thread local variable.
   *
   * @param transportClientInfo the client info, null to remove
   */
  public static void set(TransportClientInfo transportClientInfo) {
    if (transportClientInfo == null) {
      TL_CLIENT_INFO.remove();
    }
    else {
      TL_CLIENT_INFO.set(transportClientInfo);
    }
  }


  /**
   * Gets the client host address.<br>
   * Derived from the connection name, which is usually of the form {@code /host:port}.
   *
   * @return the address, null if no connection name
   */
  public String getHost() {
    if (connectionName != null) {
      int startNdx = connectionName.indexOf('/') + 1; // skip the leading '/'
      int endNdx = connectionName.indexOf(':', startNdx);   // cut port number
      return connectionName.substring(startNdx, endNdx == -1 ? connectionName.length() : endNdx);
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(transport).append(" >< ").append(connectionName);
    if (clientId != null) {
      buf.append('|').append(clientId);
    }
    return buf.toString();
  }

}
