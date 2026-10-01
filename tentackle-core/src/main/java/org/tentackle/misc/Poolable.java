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
 * A resource that can be managed by a {@link Pool}.
 *
 * @param <T> the type of poolable
 */
public interface Poolable<T extends Poolable<T>> {

  /**
   * Sets the pool.
   *
   * @param pool the pool, null if removed from the pool
   */
  void setPool(Pool<T> pool);

  /**
   * Gets the pool.
   *
   * @return the pool, null if removed from the pool
   */
  Pool<T> getPool();

  /**
   * Sets the unique ID within the pool.<br>
   * The ID is set by the pool when the poolable is lent, returned or removed.
   *
   * @param poolId the pool ID (0 if not lent, &gt; 0 if lent, -1 if removed from pool)
   */
  void setPoolId(int poolId);

  /**
   * Gets the unique ID within the pool.
   *
   * @return the pool ID (0 if not lent, &gt; 0 if lent, -1 if removed from pool)
   */
  int getPoolId();

  /**
   * Returns whether the resource is open and usable.
   *
   * @return true if open
   */
  boolean isOpen();

  /**
   * Closes the resource.
   */
  void close();

}
