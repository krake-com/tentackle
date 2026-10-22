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

package org.tentackle.update;

import org.tentackle.trip.Remote;

/**
 * TRIP service to provide the update info for an application.
 * <p>
 * The server alone decides which version is offered, where it is downloaded from
 * and which script performs the update. The client only reports who it is.
 *
 * @see AbstractUpdateServiceImpl
 */
public interface UpdateService extends Remote {

  /**
   * Gets the info on how to download and install the update.<br>
   * The client is expected to update only if the returned version differs from its own.
   *
   * @param info the client application info
   * @return the update info
   */
  UpdateInfo getUpdateInfo(ClientInfo info);

}
