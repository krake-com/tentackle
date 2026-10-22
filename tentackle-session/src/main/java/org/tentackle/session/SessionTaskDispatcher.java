/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.session;

import org.tentackle.task.TaskDispatcher;
import org.tentackle.task.TaskException;

/**
 * A {@link TaskDispatcher} using a {@link Session}.<br>
 * The tasks are executed serially by the dispatcher thread, which owns the session exclusively.
 *
 * @author harald
 */
public interface SessionTaskDispatcher extends TaskDispatcher {

  /**
   * Gets the session.
   *
   * @return the session
   */
  Session getSession();

  /**
   * Sets the session.<br>
   * The dispatcher thread itself may change the session at any time. Other threads may
   * change it only as long as the dispatcher is not running.
   *
   * @param session the session
   * @throws TaskException if invoked from another thread while the dispatcher is running
   */
  void setSession(Session session);

  /**
   * Sets the keepalive flag.<br>
   * If set, {@link Session#setAlive(boolean)} is invoked whenever the dispatcher is about to sleep,
   * i.e. periodically while idle, to keep the session alive.
   *
   * @param sessionKeptAlive true if ping enabled
   */
  void setSessionKeptAlive(boolean sessionKeptAlive);

  /**
   * Gets the keepalive flag.
   *
   * @return true if ping enabled
   */
  boolean isSessionKeptAlive();

  /**
   * Returns whether the session should be closed on termination.
   *
   * @return true to close, false to leave it open
   */
  boolean isSessionClosedOnTermination();

  /**
   * Sets whether the session should be closed on termination.
   *
   * @param sessionClosedOnTermination true to close, false to leave it open
   */
  void setSessionClosedOnTermination(boolean sessionClosedOnTermination);

}
