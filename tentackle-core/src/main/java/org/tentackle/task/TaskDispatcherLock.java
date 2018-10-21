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

package org.tentackle.task;

/**
 * The lock of a task dispatcher.
 * <p>
 * This is just a holder for the locked resources.
 *
 * @author harald
 */
public interface TaskDispatcherLock {

  /**
   * Gets the locking key.
   *
   * @return the key, null if no key
   */
  Object getKey();

  /**
   * Gets the locked dispatcher.
   *
   * @return the dispatcher
   */
  TaskDispatcher getTaskDispatcher();

}
