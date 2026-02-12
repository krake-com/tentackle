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

/**
 * Return values of remote calls can implement this interface.<br>
 * It is used to programmatically request the client to release or close the transport(s)
 * after having received the return value of a remote method invocation.
 */
public interface ClientTransportCloseRequest {

  /**
   * Requests the client to release the transport from the {@link TransportManager}.<br>
   * This usually closes the transport and all its connections if the
   * transport is used for only a single named remote object.
   *
   * @return true to release the transport, false otherwise
   */
  default boolean isTransportReleaseRequested() {
    return false;
  }

  /**
   * Requests the client to close all its transports.
   *
   * @return true to close all transports, false otherwise
   */
  default boolean isCloseAllTransportsRequested() {
    return false;
  }

}
