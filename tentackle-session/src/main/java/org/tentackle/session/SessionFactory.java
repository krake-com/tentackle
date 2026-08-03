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

import org.tentackle.common.ServiceFactory;


interface SessionFactoryHolder {
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
    return SessionFactoryHolder.INSTANCE;
  }

  /**
   * Creates a session with the default session info.
   *
   * @return the open session
   */
  Session create();

  /**
   * Creates a session.
   *
   * @param sessionInfo the session info
   * @return the open session
   */
  Session create(SessionInfo sessionInfo);

  /**
   * Creates a session for a pool.<br>
   * Notice that this method is provided for {@link SessionPool} implementations only.
   * Applications must use {@link SessionPool#get()} instead.
   *
   * @param sessionPool the pool requesting a new session
   * @param sessionInfo the session info
   * @return the open session
   */
  Session create(SessionPool sessionPool, SessionInfo sessionInfo);

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
