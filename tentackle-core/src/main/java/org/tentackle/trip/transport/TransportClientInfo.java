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
import org.tentackle.trip.TripRuntimeException;

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

  /** Number of leading characters of a client transport UUID shown in logs. */
  private static final int REDACTED_LENGTH = 8;

  /**
   * Abbreviates the UUID of a client transport for logging.
   *
   * @param clientId the client transport's UUID
   * @return the abbreviated UUID, "null" if null
   */
  public static String redact(UUID clientId) {
    return clientId == null ? "null" : clientId.toString().substring(0, REDACTED_LENGTH);
  }

  /**
   * Gets the client host address from a connection name.
   *
   * @param connectionName the connection name, usually of the form {@code /host:port}
   * @return the address, null if no connection name
   */
  public static String getHost(String connectionName) {
    if (connectionName != null) {
      int startNdx = connectionName.indexOf('/') + 1;   // skip the leading '/'
      int endNdx = connectionName.lastIndexOf(':'); // cut port number (IPv6 addresses contain colons)
      if (endNdx < startNdx) {
        endNdx = connectionName.length();
      }
      return connectionName.substring(startNdx, endNdx);
    }
    return null;
  }

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
    return getHost(connectionName);
  }

  /**
   * Returns whether the client is authenticated.<br>
   * The state is shared by all connections of the client's transport.
   *
   * @return true if authenticated
   * @see org.tentackle.trip.PreAuthenticationPolicy
   */
  public boolean isAuthenticated() {
    return dictionary != null && dictionary.isAuthenticated();
  }

  /**
   * Marks the client as authenticated.<br>
   * Invoked by the application when the client has logged in successfully.
   * From then on, the {@link org.tentackle.trip.PreAuthenticationPolicy} of the transport
   * no longer applies to any connection of the client's transport.
   *
   * @throws TripRuntimeException if there is no server dictionary
   */
  public void authenticate() {
    if (dictionary == null) {
      throw new TripRuntimeException("no dictionary to authenticate " + this);
    }
    dictionary.setAuthenticated();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(transport).append(" >< ").append(connectionName);
    if (clientId != null) {
      buf.append('|').append(redact(clientId));
    }
    return buf.toString();
  }

}
