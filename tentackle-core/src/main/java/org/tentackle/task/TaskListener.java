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
 * A listener for {@link Task}-related events.
 * <p>
 * The listener can be registered to a {@link TaskDispatcher}
 * or a {@link Task}.
 *
 * @author harald
 */
public interface TaskListener {

  /**
   * Invoked whenever a task is started.
   *
   * @param task the task started
   */
  void started(Task task);

  /**
   * Invoked whenever a task completes.
   *
   * @param task the task completed
   */
  void completed(Task task);

}
