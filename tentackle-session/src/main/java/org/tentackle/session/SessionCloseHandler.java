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

/**
 * A handler invoked whenever a Session is closed.
 * <p>
 * Used to clean up stuff related to the Session.
 * <p>
 * Important: the close handler MUST NOT invoke any blocking
 * or synchronized methods that could block the closing process!
 *
 * @author harald
 */
public interface SessionCloseHandler {

  /**
   * Invoked before closing.
   *
   * @param session the closing session
   */
  void beforeClose(Session session);

  /**
   * Invoked after closing.
   *
   * @param session the closed session
   */
  void afterClose(Session session);

}
