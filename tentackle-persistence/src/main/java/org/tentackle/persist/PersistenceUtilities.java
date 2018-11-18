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

package org.tentackle.persist;

import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Service;
import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;

/**
 * Persistence utility methods.<br>
 * This singleton replaces {@link DbUtilities} from the tentackle-database module
 * to make it PDO-aware.
 *
 * @author harald
 */
@Service(DbUtilities.class)
public class PersistenceUtilities extends DbUtilities {

  @Override
  @SuppressWarnings("unchecked")
  public <T> T createObject(Class<T> clazz) {
    T obj;
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      obj = (T) Pdo.create((Class) clazz);
    }
    else {
      // try low-level object
      obj = super.createObject(clazz);
    }
    return obj;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T selectObject(Session session, Class<T> clazz, long objectId, boolean loadLazyReferences) {
    T obj = null;
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      PersistentDomainObject<?> pdo = Pdo.create((Class) clazz, session).select(objectId);
      if (pdo != null) {
        pdo.getPersistenceDelegate().setDomainContext(pdo.getPersistenceDelegate().createValidContext());
        if (loadLazyReferences) {
          ((AbstractDbObject) pdo.getPersistenceDelegate()).loadLazyReferences();
        }
        obj = (T) pdo;
      }
    }
    else {
      // may be a low-level object?
      obj = super.selectObject(session, clazz, objectId, loadLazyReferences);
    }
    return obj;
  }

  @Override
  public String determineTableSerialTableName(DbObjectClassVariables<?> clazzVar) {
    try {
      String tableSerialTableName = null;
      AbstractDbObject<?> po = AbstractDbObject.newInstance(clazzVar.clazz);
      if (po.isTableSerialProvided()) {
        tableSerialTableName = po.getTableName();
        if (clazzVar instanceof PersistentObjectClassVariables) {
          // check if the topmost superclass provides its own table: take that
          PersistentObjectClassVariables<?, ?> cv = (PersistentObjectClassVariables) clazzVar;
          while (cv != null) {
            if (cv.tableName != null) {
              tableSerialTableName = cv.tableName;
            }
            cv = cv.superClassVariables;
          }
        }
      }
      return tableSerialTableName;
    }
    catch (RuntimeException ex) {
      throw new IllegalStateException(
              "can't evaluate the name of the table holding the tableserial for " + clazzVar, ex);
    }
  }

  @Override
  public ConnectionManager getDefaultConnectionManager() {
    ConnectionManager manager = null;
    AbstractApplication application = AbstractApplication.getRunningApplication();
    if (application instanceof ConnectionManagerProvider) {
      manager = ((ConnectionManagerProvider) application).getConnectionManager();
    }
    if (manager == null) {
      manager = super.getDefaultConnectionManager();
    }
    return manager;
  }

  @Override
  public SessionPool getDefaultSessionPool() {
    AbstractApplication application = AbstractApplication.getRunningApplication();
    if (application instanceof SessionPoolProvider) {
      return ((SessionPoolProvider) AbstractApplication.getRunningApplication()).getSessionPool();
    }
    return super.getDefaultSessionPool();
  }

  @Override
  public Class<?> getServicedClass(Class<?> implementingClass) {
    return PdoUtilities.getInstance().determineServicedClass(implementingClass);
  }

  @Override
  public void cleanupRemoteSession(RemoteDbSessionImpl remoteSession) {
    if (!remoteSession.getClientSessionInfo().isCloned()) {   // cleanup tokens only once for the main session
      LockManager.getInstance().cleanupUserTokens(remoteSession.getSession(), remoteSession.getClientSessionInfo().getUserId());
    }
  }
}
