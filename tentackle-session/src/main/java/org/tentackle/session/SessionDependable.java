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

/**
 * Interface for objects depending on sessions.<br>
 *
 * @see Session
 * @author harald
 */
public interface SessionDependable extends SessionProvider {

  /**
   * Sets the logical session for this object.
   *
   * @param session the session
   */
  void setSession(Session session);

  /**
   * Sets the session to immutable.
   *
   * @param sessionImmutable true if the session cannot be changed anymore
   */
  void setSessionImmutable(boolean sessionImmutable);

  /**
   * Returns whether the session is immutable.
   *
   * @return true if immutable
   */
  boolean isSessionImmutable();

}
