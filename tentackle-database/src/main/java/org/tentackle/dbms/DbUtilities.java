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

package org.tentackle.dbms;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.session.Session;
import org.tentackle.session.SessionPool;


interface DbUtilities$Singleton {
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
    return DbUtilities$Singleton.INSTANCE;
  }


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
    catch (Exception ex) {
      throw new IllegalStateException(
              "can't evaluate the name of the table holding the tableserial for " + clazzVar, ex);
    }
  }


  /**
   * Gets the default session manager.
   *
   * @return the session manager, never null
   */
  public ConnectionManager getDefaultSessionManager() {
    return new DefaultConnectionManager();
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

}
