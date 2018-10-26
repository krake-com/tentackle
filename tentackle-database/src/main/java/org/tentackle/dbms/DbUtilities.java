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

import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.io.ReconnectionPolicy;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.Session;
import org.tentackle.session.SessionPool;

import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;


interface DbUtilitiesHolder {
  DbUtilities INSTANCE = ServiceFactory.createService(DbUtilities.class, DbUtilities.class);
}


/**
 * Persistence utility methods.<br>
 * This singleton is provided mainly to allow a clean separation between the
 * lower- and higher level persistence layer implementations.<br>
 * It is replaced by {@code PersistenceUtilities} from the tentackle-persistence module
 * to make it PDO-aware.
 *
 * @author harald
 */
@Service(DbUtilities.class)   // defaults to self
public class DbUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DbUtilities getInstance() {
    return DbUtilitiesHolder.INSTANCE;
  }



  private ConnectionManager connectionManager;


  /**
   * Creates a sessionless object for given class.
   *
   * @param <T> the object type
   * @param clazz the object class
   * @return the initialized object, null if clazz is not a persistence class
   */
  @SuppressWarnings("unchecked")
  public <T> T createObject(Class<T> clazz) {
    T object = null;
    if (AbstractDbObject.class.isAssignableFrom(clazz)) {
      object = (T) AbstractDbObject.newInstance((Class) clazz);
    }
    return object;
  }


  /**
   * Loads an object from the database.
   *
   * @param <T> the object type
   * @param clazz the object class
   * @param session the session
   * @param objectId the object id
   * @param loadLazyReferences true if load lazy references
   * @return the object, null if no such object
   */
  @SuppressWarnings("unchecked")
  public <T> T selectObject(Session session, Class<T> clazz, long objectId, boolean loadLazyReferences) {
    T object = null;
    if (AbstractDbObject.class.isAssignableFrom(clazz)) {
      object = (T) AbstractDbObject.newInstance(session, (Class) clazz).selectObject(objectId);
      // load any lazy references that may be necessary for replay on the remote side
      if (object != null && loadLazyReferences) {
        ((AbstractDbObject) object).loadLazyReferences();
      }
    }
    return object;
  }


  /**
   * Determines whether table serial is valid for this pdo class.
   *
   * @param clazzVar the class variables
   * @return the tablename holding the tableserial, null if no tableserial
   */
  public String determineTableSerialTableName(DbObjectClassVariables<?> clazzVar) {
    try {
      String tableSerialTableName = null;
      AbstractDbObject<?> po = AbstractDbObject.newInstance(clazzVar.clazz);
      if (po.isTableSerialProvided()) {
        tableSerialTableName = po.getTableName();
      }
      return tableSerialTableName;
    }
    catch (RuntimeException ex) {
      throw new IllegalStateException(
              "can't evaluate the name of the table holding the tableserial for " + clazzVar, ex);
    }
  }


  /**
   * Gets the default connection manager.
   *
   * @return the connection manager, never null
   */
  public synchronized ConnectionManager getDefaultConnectionManager() {
    if (connectionManager == null) {
      connectionManager = new DefaultConnectionManager();
    }
    return connectionManager;
  }


  /**
   * Gets the default session pool.
   *
   * @return the session pool, null if none (default)
   */
  public SessionPool getDefaultSessionPool() {
    return null;
  }

  /**
   * Determines the serviced class.
   *
   * @param implementingClass the implementing class
   * @return the serviced class, null if none
   */
  public Class<?> getServicedClass(Class<?> implementingClass) {
    // overridden in PersistenceUtilities
    return null;
  }


  /**
   * Creates a reconnection policy for a given session.
   *
   * @param session the session
   * @param blocking true if reconnection blocks the current thread, else non-blocking in background
   * @param millis the (minimum) time in milliseconds between retries
   * @return the policy
   */
  public ReconnectionPolicy<Db> createReconnectionPolicy(Db session, boolean blocking, long millis) {
    return new ReconnectionPolicy<>() {

      @Override
      public String toString() {
        return session.toString();
      }

      @Override
      public boolean isBlocking() {
        return blocking;
      }

      @Override
      public long timeToReconnect() {
        return millis;
      }

      @Override
      public Supplier<Db> getConnector() {
        return () -> session;
      }

      @Override
      public Consumer<Db> getConsumer() {
        return Db::reOpen;
      }
    };
  }


  /**
   * Performs any clean up when a remote user session is closed.
   *
   * @param remoteSession the remote session
   */
  public void cleanupRemoteSession(RemoteDbSessionImpl remoteSession) {
    // the default does nothing
  }

  /**
   * Applies the given backend configuration to properties.
   *
   * @param backendConfiguration the backend configuration
   * @param properties the session properties.
   */
  public void applyBackendConfiguration(BackendConfiguration backendConfiguration, Properties properties) {
    if (backendConfiguration.getUser() != null) {
      properties.setProperty(Constants.BACKEND_USER, backendConfiguration.getUser());
    }
    if (backendConfiguration.getPassword() != null) {
      properties.setProperty(Constants.BACKEND_PASSWORD, backendConfiguration.getPassword());
    }
    properties.setProperty(Constants.BACKEND_URL, backendConfiguration.getUrl());
    if (backendConfiguration.getDriver() != null) {
      // ex.: "org.postgresql.Driver:jar:file:/usr/share/java/postgresql.jar!/"
      properties.setProperty(Constants.BACKEND_DRIVER,
          backendConfiguration.getDriver().getDriver() + ":jar:file:" + backendConfiguration.getDriver().getUrl() + "!/");
    }
    if (backendConfiguration.getOptions() != null) {
      for (String line: backendConfiguration.getOptions().split("\n")) {
        String key;
        String value;
        int ndx = line.indexOf("=");
        if (ndx >= 0) {
          key = line.substring(0, ndx).trim();
          value = line.substring(ndx + 1).trim();
        }
        else {
          key = line.trim();
          value = "";
        }
        properties.setProperty(key, value);
      }
    }
  }

}
