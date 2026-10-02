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

package org.tentackle.session;

import org.tentackle.misc.Pool;

/**
 * A pool of sessions for a given session-info.<br>
 * Allows a single user to use multiple sessions in parallel.<br>
 * The sessions are created via {@link SessionFactory#create(SessionPool, SessionInfo)}.
 *
 * @see SessionPoolFactory
 * @author harald
 */
public interface SessionPool extends Pool<Session> {

  /**
   * Gets the session info.
   *
   * @return the session info
   */
  SessionInfo getSessionInfo();

  /**
   * Gets the session group id.
   *
   * @return the session group ID, 0 if ungrouped
   */
  int getSessionGroupId();

}
