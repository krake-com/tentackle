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

import java.lang.ref.WeakReference;

/**
 * A slot in a pool.
 *
 * @param <T> the type of poolable resources
 */
public interface PoolSlot<T extends Poolable<T>> {

  /**
   * Gets the pool the slot belongs to.
   *
   * @return the pool
   */
  Pool<T> getPool();

  /**
   * Gets the slot number.
   *
   * @return the slot number
   */
  int getSlotNumber();

  /**
   * Returns the managed poolable.
   *
   * @return the managed poolable, null if currently lent
   */
  T getPoolable();

  /**
   * Gets the referenced poolable.<br>
   * This is the value of a {@link WeakReference}.
   *
   * @return the poolable, null if no more referenced
   */
  T getReferencedPoolable();

  /**
   * Gets the epochal time of first use.
   *
   * @return the epochal time, 0 if never used
   */
  long getFirstUse();

  /**
   * Gets the time lent.
   *
   * @return the epochal time since in use, 0 if not lent
   */
  long getUsedSince();

  /**
   * Gets the last return time.
   *
   * @return the epochal time last returned, 0 if lent
   */
  long getUnusedSince();

  /**
   * Gets the lending thread string.<br>
   * Avoids the references to the thread.
   *
   * @return the thread name, null if in pool
   */
  String getUsingThreadStr();

  /**
   * Gets the mapped diagnostic context string.
   *
   * @return the MDC, null if in pool
   */
  String getMdcStr();

  /**
   * Closes this slot and the managed poolable.<br>
   * The poolable is closed in a separate thread.
   *
   * @param timeoutMillis the timeout in milliseconds to wait for the closing operation to complete,
   *                      &le; 0 for asynchronous non-blocking operation
   */
  void close(long timeoutMillis);

  /**
   * Marks the poolable used.
   *
   * @param usingThread the using thread
   * @throws org.tentackle.common.TentackleRuntimeException if the slot does not hold the poolable
   */
  void use(Thread usingThread);

  /**
   * Marks the poolable unused.
   *
   * @param poolable the returned poolable
   * @throws org.tentackle.common.TentackleRuntimeException if the poolable does not belong to this slot
   *         or is not referenced anymore
   */
  void unUse(T poolable);

  /**
   * Checks for forgotten puts.
   *
   * @return true if the poolable has been lent but never returned and is not referenced anymore
   */
  boolean isUnreferenced();

  /**
   * Returns whether the resource is obsolete and should be closed.<br>
   * Checked by {@link Pool#monitor()} for unused resources only.
   *
   * @return true if obsolete
   */
  boolean isObsolete();

  /**
   * Returns the number of minutes the poolable has been unused.
   *
   * @param currentTimeMillis the current time to refer to in epochal milliseconds
   * @return the idle minutes, 0 if in use or never used at all
   */
  long getIdleMinutes(long currentTimeMillis);

  /**
   * Returns the number of minutes since the first use of the poolable.
   *
   * @param currentTimeMillis the current time to refer to in epochal milliseconds
   * @return the usage minutes, 0 if never used at all
   */
  long getUsedMinutes(long currentTimeMillis);
}
