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
 * A TaskDispatcher using a Session.
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
   * Sets the session.
   *
   * @param session the session
   * @throws TaskException if the dispatcher is already running or not invoked from the dispatcher thread
   */
  void setSession(Session session);

  /**
   * Sets the keepalive flag.<br>
   * If set, setAlive() will be invoked periodically to keep the session alive.
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
