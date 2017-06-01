/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Policy to reconnect a dead resource.
 *
 * @author harald
 * @param <T> the resource type
 */
public interface ReconnectionPolicy<T> {

  /**
   * Determines what happens to the thread encountering a dead resource.<br>
   * If blockung, the thread will wait until the resource is up again.<br>
   * If non-blocking, the thread will simply receive the exception.
   *
   * @return true if blocking, false if thread continues
   */
  boolean isBlocking();

  /**
   * Number of milliseconds to wait before the next connection attempt.<br>
   * Reconnection terminates if the method returns 0 or throws some runtime exception.
   *
   * @return the millis, 0 if stop reconnection attempts
   */
  long timeToReconnect();

  /**
   * Gets the supplier to connect the resource.
   *
   * @return the connector
   */
  Supplier<T> getConnector();

  /**
   * Gets the consumer to apply the reconnected resource.
   *
   * @return the consumer
   */
  Consumer<T> getConsumer();

}
