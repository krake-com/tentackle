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

package org.tentackle.io;

/**
 * Policy to reconnect a dead resource.
 *
 * @author harald
 * @param <T> the resource type
 */
public interface ReconnectionPolicy<T> {

  /**
   * Determines what happens to the thread encountering a dead resource.<br>
   * If blocking, the thread must wait until the resource is up again.<br>
   * If non-blocking, the thread will just submit the reconnection.
   *
   * @return true if blocking, false if the thread continues
   */
  boolean isBlocking();

  /**
   * Number of milliseconds to wait before the next connection attempt.<br>
   * Reconnection terminates if the method returns a value &le; 0 or throws some runtime exception.
   *
   * @return the millis, &le; 0 to stop the reconnection attempts
   */
  long timeToReconnect();

  /**
   * Reconnects the resource.<br>
   * Throwing a runtime exception means the attempt failed and will be retried.
   *
   * @param resource the resource to reconnect
   */
  void reconnect(T resource);

}
