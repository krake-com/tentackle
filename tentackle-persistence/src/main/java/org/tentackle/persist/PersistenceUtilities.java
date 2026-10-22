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

import org.tentackle.app.Application;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.log.Logger;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionPoolProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persistence utility methods.<br>
 * This singleton replaces {@link DbUtilities} from the tentackle-database module
 * to make it PDO-aware.
 *
 * @author harald
 */
@Service(DbUtilities.class)
public class PersistenceUtilities extends DbUtilities {

  private static final Logger LOGGER = Logger.get(PersistenceUtilities.class);


  /**
   * Creates the persistence utilities.
   */
  public PersistenceUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create PDOs.
   */
  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T> T createObject(Class<T> clazz) {
    T obj;
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      obj = (T) Pdo.create((Class) clazz);
    }
    else {
      // try the low-level object
      obj = super.createObject(clazz);
    }
    return obj;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to select PDOs. The PDO's domain context is replaced by its valid context.
   */
  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
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
      // maybe a low-level object?
      obj = super.selectObject(session, clazz, objectId, loadLazyReferences);
    }
    return obj;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to take the table of the topmost superclass providing its own table into account.
   */
  @Override
  public String determineTableSerialTableName(DbObjectClassVariables<?> clazzVar) {
    try {
      String tableSerialTableName = null;
      AbstractDbObject<?> po = AbstractDbObject.newInstance(clazzVar.clazz);
      if (po.isTableSerialProvided()) {
        tableSerialTableName = po.getTableName();
        if (clazzVar instanceof PersistentObjectClassVariables<?, ?> cv) {
          // check if the topmost superclass provides its own table: take that
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

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use the connection manager of the application, if it is a {@link ConnectionManagerProvider}.
   */
  @Override
  public ConnectionManager getDefaultConnectionManager() {
    ConnectionManager manager = null;
    Application application = Application.getInstance();
    if (application instanceof ConnectionManagerProvider connectionManagerProvider) {
      manager = connectionManagerProvider.getConnectionManager();
    }
    if (manager == null) {
      manager = super.getDefaultConnectionManager();
    }
    return manager;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use the application, if it is a {@link SessionPoolProvider}.
   */
  @Override
  public SessionPoolProvider getSessionPoolProvider() {
    Application application = Application.getInstance();
    if (application instanceof SessionPoolProvider sessionPoolProvider) {
      return sessionPoolProvider;
    }
    return super.getSessionPoolProvider();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to determine the PDO or operation interface via {@link PdoUtilities}.
   */
  @Override
  public Class<?> getServicedClass(Class<?> implementingClass) {
    return PdoUtilities.getInstance().determineServicedClass(implementingClass);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to release the user's token locks via the {@link LockManager},
   * unless the session's token locks should linger.
   */
  @Override
  public void cleanupRemoteSession(RemoteDbSessionImpl remoteSession) {
    if (!remoteSession.getClientSessionInfo().isLockLingerEnabled()) {   // skip sessions with lock linger enabled (e.g. cloned sessions)
      LockManager.getInstance().cleanupUserTokens(remoteSession.getSession(), remoteSession.getClientSessionInfo().getUserId());
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to notify the {@link LockManager}.
   */
  @Override
  public void notifyRollback(Db session, long txNumber) {
    super.notifyRollback(session, txNumber);
    LockManager.getInstance().rollback(session, txNumber);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to notify the {@link LockManager}.
   */
  @Override
  public void notifyCommit(Db session, long txNumber) {
    super.notifyCommit(session, txNumber);
    LockManager.getInstance().commit(session, txNumber);
  }

  /**
   * Gets the names of the classes to preload the caches for.<br>
   * By default, these are the classes annotated with {@link PreloadCache}, collected
   * at build time and looked up via the service API. The classes are not loaded.
   * <p>
   * Since the caches are only loaded once per JVM, this method does not cache the result.
   *
   * @return the class names, never null
   */
  public String[] getPreloadCacheClassNames() {
    Set<String> names = ServiceFactory.getServiceFinder()
                                      .findServiceConfigurations(PreloadCache.class.getName()).keySet();
    return names.toArray(new String[0]);
  }

  /**
   * Gets the caches to preload the PDOs for.<br>
   * Instantiates the persistence classes returned by {@link #getPreloadCacheClassNames()}
   * and collects their caches. Disabled caches are skipped. Caches that are not preloading
   * and read-only are skipped with a warning.
   *
   * @return the caches, never null
   * @throws PersistenceException if a persistence class cannot be instantiated
   */
  public List<PdoCache<? extends PersistentDomainObject<?>>> getPreloadingCaches() {
    List<PdoCache<? extends PersistentDomainObject<?>>> caches = new ArrayList<>();
    for (String persImplClassName : getPreloadCacheClassNames()) {
      try {
        PersistentObject<?> po = (PersistentObject<?>) Class.forName(persImplClassName).getConstructor().newInstance();
        PdoCache<?> cache = po.getCache();
        if (cache == null) {
          LOGGER.warning("{0} has no cache -> please remove @PreloadCache!", persImplClassName);
        }
        else if (cache.isPreloading() && cache.isReadOnly()) {
          if (cache.isEnabled()) {
            caches.add(cache);
          }
          else {
            LOGGER.info("{0} is disabled -> skipped", cache);
          }
        }
        else {
          LOGGER.warning("{0} is not a preloading read-only cache -> please remove @PreloadCache!", cache);
        }
      }
      catch (InstantiationException | ClassNotFoundException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException e) {
        throw new PersistenceException("cannot instantiate PersistentObject for " + persImplClassName, e);
      }
    }
    return caches;
  }

}
