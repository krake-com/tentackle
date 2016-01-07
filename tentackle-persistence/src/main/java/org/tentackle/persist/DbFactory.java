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


package org.tentackle.persist;

import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Service;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionCloseHandler;
import org.tentackle.pdo.SessionFactory;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.SessionManager;
import org.tentackle.pdo.SessionManagerProvider;

/**
 * Session factory for Db instances.
 *
 * @author harald
 */
@Service(SessionFactory.class)
public class DbFactory implements SessionFactory, SessionManagerProvider {


  private ConnectionManager connectionManager;    // the default connection manager


  @Override
  public Session create(SessionInfo sessionInfo) {
    return new Db(getSessionManager(), sessionInfo);
  }

  @Override
  public Session create(SessionManager sessionManager, SessionInfo sessionInfo) {
    return new Db((ConnectionManager) sessionManager, sessionInfo);
  }

  @Override
  public boolean registerGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return Db.registerGlobalCloseHandler(closeHandler);
  }

  @Override
  public boolean unregisterGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return Db.unregisterGlobalCloseHandler(closeHandler);
  }

  @Override
  public synchronized ConnectionManager getSessionManager() {
    if (connectionManager == null) {
      AbstractApplication application = AbstractApplication.getRunningApplication();
      if (application instanceof SessionManagerProvider) {
        connectionManager = (ConnectionManager) ((SessionManagerProvider) application).getSessionManager();
      }
      if (connectionManager == null) {
        connectionManager = new DefaultConnectionManager();
      }
    }
    return connectionManager;
  }

}
