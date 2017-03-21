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

/**
 * Returnable session provider.
 * <p>
 * Provides exclusive access to returnable sessions.
 * Used to lease sessions for a certain time and the release them again.<br>
 * Used by session pools, for example.
 * <p>
 * Notice the difference to the {@link ExclusiveSessionProvider}, which provides
 * access to a certain session.
 *
 * @author harald
 */
public interface ReturnableSessionProvider extends SessionProvider {

  /**
   * Returns a session to the provider.
   * <p>
   * Notice: returning a session more than once is allowed.<br>
   * Returning a closed session will remove it from the provider.
   *
   * @param session the session
   */
  void putSession(Session session);

}
