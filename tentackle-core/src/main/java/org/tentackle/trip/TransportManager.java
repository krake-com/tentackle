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

import java.net.URI;


interface TransportManagerHolder {
  TransportManager INSTANCE = TripFactory.getInstance().createTransportManager();
}


/**
 * Maintains transports.
 * <p>
 * Transports are created from their URI, but since the URI-path denotes a remote object
 * maintained by the server's registry, there can be multiple paths maintained by the
 * same transport.
 * <p>
 * Clients can use more than one transport in parallel, and each transport multiple times,
 * which will usually result in multiple connections to the same server endpoint.
 * <p>
 * The transport manager is a singleton.
 */
public interface TransportManager {

  /**
   * Gets the transport manager singleton.
   *
   * @return the transport manager
   */
  static TransportManager getInstance() {
    return TransportManagerHolder.INSTANCE;
  }


  /**
   * Requests a {@code Transport} for the given URI.<br>
   * If there is no transport with the corresponding {@link TransportKey},
   * a new transport will be created.<br>
   * Otherwise, the existing transport will be returned, provided the URI's query parameters
   * are the same as those the transport was created with.
   *
   * @param uri  the URI of the transport
   * @return the transport
   * @throws TripRuntimeException if a transport for the same scheme, host and port is already
   *         in use with different query parameters
   */
  Transport requestTransport(URI uri);

  /**
   * Releases the given transport.<br>
   * When all requests of a {@code Transport} are released, it will be closed.
   *
   * @param transport the transport to be released
   */
  void releaseTransport(Transport transport);

  /**
   * Closes all transports and removes them from the manager.<br>
   * Use this method when the application is shutting down gracefully.
   */
  void closeAllTransports();

}
