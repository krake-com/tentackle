/*
 * Tentackle - http://www.tentackle.org
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

import java.io.Serializable;

/**
 * Holder for a session.
 *
 * @author harald
 */
public interface SessionHolder extends Serializable {

  /**
   * Gets the session.<br>
   * The threadlocal session is returned if the session is null.
   *
   * @return the session.
   */
  Session getSession();

  /**
   * Sets the session.
   *
   * @param session the session, null if threadlocal
   */
  void setSession(Session session);

  /**
   * Returns whether the session is thread local.
   *
   * @return true if threadlocal
   */
  boolean isSessionThreadLocal();

  /**
   * Sets the session to immutable.
   *
   * @param sessionImmutable true if session cannot be changed anymore
   */
  void setSessionImmutable(boolean sessionImmutable);

  /**
   * Returns whether the session is immutable.
   *
   * @return true if immutable
   */
  boolean isSessionImmutable();

  /**
   * The SessionInfo extends the UserInfo by a SecurityManager.
   *
   * @return the SessionInfo associated to the Db.
   */
  SessionInfo getSessionInfo();

  /**
   * Gets the unique instance number of the Session.
   * <p>
   * Delegating {@code getSession().getInstanceNumber()} to
   * {@code getSessionInstanceNumber} allows to override
   * getSession and dynamically retrieve the thread's session
   * without changing the sorting order.
   *
   * @return the session's instance number
   */
  int getSessionInstanceNumber();

}
