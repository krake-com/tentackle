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

package org.tentackle.dbms;

import org.tentackle.common.Service;
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionInfoFactory;
import org.tentackle.session.SessionPool;

/**
 * Session factory for Db instances.
 *
 * @author harald
 */
@Service(SessionFactory.class)
public class DbFactory implements SessionFactory {

  /**
   * Creates the session factory.
   */
  public DbFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Session create() {
    return create(SessionInfoFactory.getInstance().create());
  }

  @Override
  public Session create(SessionInfo sessionInfo) {
    return new Db(getConnectionManager(sessionInfo), sessionInfo);
  }

  @Override
  public Session create(SessionPool sessionPool, SessionInfo sessionInfo) {
    return new Db(((DbPool) sessionPool).getConnectionManager(), sessionInfo);
  }

  @Override
  public boolean registerGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return Db.registerGlobalCloseHandler(closeHandler);
  }

  @Override
  public boolean unregisterGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return Db.unregisterGlobalCloseHandler(closeHandler);
  }

  /**
   * Gets the connection manager.
   * <p>
   * If the application uses different connection managers, the session info
   * must contain all the necessary information to distinguish the managers.
   * The default implementation returns the single default connection manager.
   * <p>
   * Notice that only local connections need a connection manager. Remote sessions
   * will simply ignore it.
   *
   * @param sessionInfo the session info
   * @return the connection manager, null if remote
   * @see DbUtilities#getDefaultConnectionManager()
   */
  protected synchronized ConnectionManager getConnectionManager(SessionInfo sessionInfo) {
    return DbUtilities.getInstance().getDefaultConnectionManager();
  }

}
