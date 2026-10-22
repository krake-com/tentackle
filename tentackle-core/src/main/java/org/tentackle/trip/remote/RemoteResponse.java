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

package org.tentackle.trip.remote;

/**
 * Interface for remote responses.
 *
 * @see InitialRemoteResponse
 * @see IdentifiedRemoteResponse
 */
public interface RemoteResponse {

  /**
   * The throwable if the call failed.
   *
   * @return the throwable, or null if the call was successful
   */
  Throwable throwable();

  /**
   * Returns true if the call was successful.
   *
   * @return true if the call was successful
   */
  default boolean wasSuccessFul() {
    return throwable() == null;
  }

}
