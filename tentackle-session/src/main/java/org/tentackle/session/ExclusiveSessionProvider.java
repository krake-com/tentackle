/*
 * Tentackle - http://www.tentackle.org.
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
 * An exclusive session provider.
 * <p>
 * Provides exclusive access to a certain session.
 * Once a session is requested, it cannot be requested by any other thread
 * until it is released. If a thread requests a session more than once,
 * it must release it the appropriate number of times until it gets released.
 * <p>
 * Notice the difference to the {@link ReturnableSessionProvider}, which provides
 * access to a pool of sessions.
 *
 * @author harald
 */
public interface ExclusiveSessionProvider {

  /**
   * Requests a session for exclusive use.
   *
   * @return the session
   */
  Session requestSession();

  /**
   * Releases the session.
   *
   * @param session the session
   * @return true if released
   */
  boolean releaseSession(Session session);

}
