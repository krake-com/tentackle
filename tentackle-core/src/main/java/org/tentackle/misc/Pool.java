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

package org.tentackle.misc;

/**
 * A pool managing poolable resources.
 *
 * @param <T> the type of poolable resources
 */
public interface Pool<T extends Poolable<T>> {

  /**
   * Gets a resource from the pool.<br>
   * The pool is enlarged if all resources are in use.
   *
   * @return the resource, never null
   * @throws org.tentackle.common.TentackleRuntimeException if the pool is shut down or exhausted
   */
  T get();

  /**
   * Returns a resource to the pool.
   * <p>
   * Notice: returning a resource more than once is allowed.<br>
   * Returning a closed resource removes it from the pool.
   * Returning a resource to a pool that is shut down closes the resource.
   *
   * @param poolable the resource
   * @throws org.tentackle.common.TentackleRuntimeException if the resource is not managed by this pool
   */
  void put(T poolable);

  /**
   * Gets the name of the pool.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the initial size.
   *
   * @return the initial size
   */
  int getInitialSize();

  /**
   * Gets the minimum increment to enlarge the pool.
   *
   * @return the increment size
   */
  int getIncrementSize();

  /**
   * Gets the minimum size of the pool.
   *
   * @return the minimum size, &lt; 0 to automatically shut down the pool when all resources are closed
   */
  int getMinimumSize();

  /**
   * Gets the maximum size of the pool.
   *
   * @return the maximum size, 0 = unlimited
   */
  int getMaximumSize();

  /**
   * Gets the current number of resources in the pool.
   *
   * @return the pool size
   */
  int getSize();

  /**
   * Gets the idle timeout in minutes.<br>
   * Resources unused for that long are closed, as long as the pool is larger than its minimum size.
   *
   * @return the idle timeout, 0 = unlimited
   */
  long getIdleMinutes();

  /**
   * Gets the usage timeout in minutes.<br>
   * Resources are closed when the given time has elapsed since their first use.
   *
   * @return the usage timeout, 0 = unlimited
   */
  long getUsageMinutes();

  /**
   * Gets the timeout to wait for closing resources on shutdown.<br>
   * The default is some reasonable value (1 s).
   *
   * @return the timeout in milliseconds, &le; 0 for asynchronous non-blocking operation
   */
  long getShutdownTimeout();

  /**
   * Shuts down the pool.<br>
   * Closes all resources.
   *
   * @throws org.tentackle.common.TentackleRuntimeException if the pool is already shut down
   */
  void shutdown();

  /**
   * Returns whether the pool was shut down.
   *
   * @return true, if shut down
   */
  boolean isShutdown();

  /**
   * Runs a monitoring operation.<br>
   * Invoked periodically by the {@link PoolTimeoutThread} to close
   * unused and timed out resources.
   */
  void monitor();

}
