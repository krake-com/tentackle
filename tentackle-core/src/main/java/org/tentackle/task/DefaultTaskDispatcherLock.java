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
 * Lock for the {@link DefaultTaskDispatcher}.
 *
 * @author harald
 */
public class DefaultTaskDispatcherLock implements TaskDispatcherLock {

  private final TaskDispatcher dispatcher;
  private final Object key;

  /**
   * Creates a dispatcher lock.
   *
   * @param dispatcher the locked dispatcher
   * @param key the locking key
   */
  public DefaultTaskDispatcherLock(TaskDispatcher dispatcher, Object key) {
    this.dispatcher = dispatcher;
    this.key = key;
  }


  @Override
  public TaskDispatcher getTaskDispatcher() {
    return dispatcher;
  }

  @Override
  public Object getKey() {
    return key;
  }

  @Override
  public String toString() {
    return key == null ? "<null>" : key.toString();
  }

}
