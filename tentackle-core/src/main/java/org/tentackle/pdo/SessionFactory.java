/**
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


package org.tentackle.pdo;

import org.tentackle.common.ServiceFactory;


interface SessionFactory$Singleton {
  SessionFactory INSTANCE = ServiceFactory.createService(SessionFactory.class);
}


/**
 * Factory for {@link Session}s.
 *
 * @author harald
 */
public interface SessionFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static SessionFactory getInstance() {
    return SessionFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a session using the default session manager.
   *
   * @param sessionInfo the session info
   * @return the created session (still closed)
   */
  Session create(SessionInfo sessionInfo);

  /**
   * Creates a session.
   *
   * @param sessionManager the session manager
   * @param sessionInfo the session info
   * @return the created session (still closed)
   */
  Session create(SessionManager sessionManager, SessionInfo sessionInfo);

  /**
   * Registers a global close handler for all sessions.
   *
   * @param closeHandler the handler
   * @return true if added, false if already registered
   */
  boolean registerGlobalCloseHandler(SessionCloseHandler closeHandler);

  /**
   * Unregisters a global close handler for all sessions.
   *
   * @param closeHandler the handler
   * @return true if removed, false if not registered
   */
  boolean unregisterGlobalCloseHandler(SessionCloseHandler closeHandler);

}
