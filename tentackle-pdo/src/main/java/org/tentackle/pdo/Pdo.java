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

package org.tentackle.pdo;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionInfoFactory;
import org.tentackle.session.SessionUtilities;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Collected factory methods for the PDOs and related objects.<br>
 * Combines all the pdo factories and enhances code readability.
 *
 * @author harald
 */
public class Pdo {

  // ------------------- methods to create an implementation of a PDO --------------

  /**
   * Creates a snapshot from a pdo.
   *
   * @param pdo the pdo
   * @param <T> the PDO type
   * @return the snapshot, null if pdo is null
   */
  public static <T extends PersistentDomainObject<T>> T createSnapshot(T pdo) {
    return pdo == null ? null : pdo.getPersistenceDelegate().createSnapshot();
  }

  /**
   * Reverts a PDO to a given snapshot.<br>
   * If the original pdo is null, a copy of the snapshot will be created.
   *
   * @param pdo the original PDO to be reverted, null if copy
   * @param snapshot the snapshot
   * @param <T> the PDO type
   * @return the reverted PDO, null if snapshot was null
   */
  public static <T extends PersistentDomainObject<T>> T revertToSnapshot(T pdo, T snapshot) {
    T revertedPdo = null;
    if (snapshot != null) {
      boolean asCopy = pdo == null;
      revertedPdo = asCopy ? snapshot.on() : pdo;
      revertedPdo.getPersistenceDelegate().setCopy(asCopy);
      revertedPdo.getPersistenceDelegate().revertToSnapshot(snapshot);
    }
    return revertedPdo;
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param context the domain context
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context) {
    return PdoFactory.getInstance().create(clazz, context);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param session the session
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, Session session) {
    return PdoFactory.getInstance().create(clazz, session);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param context the domain context
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, DomainContext context) {
    return PdoFactory.getInstance().create(className, context);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param session the session
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, Session session) {
    return PdoFactory.getInstance().create(className, session);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @return the created PDO belonging to the same class as the given {@code pdo} and in same context.
   */
  public static <T extends PersistentDomainObject<T>> T create(T pdo) {
    return PdoFactory.getInstance().create(pdo);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @return the created PDO without session and context
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz) {
    return PdoFactory.getInstance().create(clazz);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the created PDO without session and context
   */
  public static <T extends PersistentDomainObject<T>> T create(String className) {
    return PdoFactory.getInstance().create(className);
  }


  /**
   * Creates a PDO for a given persistence delegate.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate) {
    return PdoFactory.getInstance().create(clazz, persistenceDelegate);
  }


  /**
   * Creates a PDO for a given persistence delegate.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate) {
    return PdoFactory.getInstance().create(className, persistenceDelegate);
  }


  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context,
                                                               DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(clazz, context, domainDelegate);
  }


  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, DomainContext context,
                                                               DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(className, context, domainDelegate);
  }


  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate,
                                                               DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(clazz, persistenceDelegate, domainDelegate);
  }


  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate,
                                                               DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(className, persistenceDelegate, domainDelegate);
  }


  // ------------------- methods to create an implementation of an Operation --------------


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param context the domain context
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, DomainContext context) {
    return OperationFactory.getInstance().create(clazz, context);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param session the session
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, Session session) {
    return OperationFactory.getInstance().create(clazz, session);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param context the domain context
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, DomainContext context) {
    return OperationFactory.getInstance().create(className, context);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param session the session
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, Session session) {
    return OperationFactory.getInstance().create(className, session);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param op the operation
   * @return the created operation belonging to the same class as the given operation and in same context.
   */
  public static <T extends Operation<T>> T createOperation(T op) {
    return OperationFactory.getInstance().create(op);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @return the created operation without session and context
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz) {
    return OperationFactory.getInstance().create(clazz);
  }


  /**
   * Creates an operation for a given persistence delegate.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, PersistentOperation<T> persistenceDelegate) {
    return OperationFactory.getInstance().create(clazz, persistenceDelegate);
  }


  /**
   * Creates an operation for a given persistence delegate.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, PersistentOperation<T> persistenceDelegate) {
    return OperationFactory.getInstance().create(className, persistenceDelegate);
  }


  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, DomainContext context,
                                                           DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(clazz, context, domainDelegate);
  }


  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, DomainContext context,
                                                           DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(className, context, domainDelegate);
  }


  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, PersistentOperation<T> persistenceDelegate,
                                                           DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(clazz, persistenceDelegate, domainDelegate);
  }


  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, PersistentOperation<T> persistenceDelegate,
                                                           DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(className, persistenceDelegate, domainDelegate);
  }


  // ------------------- methods to create PDO-caches ----------------------------


  /**
   * Creates a preloading PDO-cache.<br>
   * Since PDO-caches are singletons per PDO-type, invoking this method
   * more than once for the same class will throw a {@link org.tentackle.session.PersistenceException}.
   *
   * @param <T> the PDO type
   * @param clazz is the PDO-class managed by the cache
   * @param readOnly true if the cache is readonly
   * @return the cache
   */
  public static <T extends PersistentDomainObject<T>> PdoCache<T> createCache(
          Class<T> clazz, boolean readOnly) {
    return PdoCacheFactory.getInstance().createCache(clazz, readOnly);
  }


  /**
   * Creates an index for a PDO-cache.
   *
   * @param name the index name
   * @param selectFunction the function to select a PDO by key
   * @param extractFunction the function to extract the key from a PDO
   * @return the index
   * @param <T> the PDO type
   * @param <C> the key type
   */
  public static <T extends PersistentDomainObject<T>, C extends Comparable<? super C>> PdoCacheIndex<T, C> createCacheIndex(
    String name, BiFunction<DomainContext, C, T> selectFunction, Function<T, C> extractFunction) {
    return PdoCacheFactory.getInstance().createCacheIndex(name, selectFunction, extractFunction);
  }


  /**
   * Removes all objects in ALL caches that refer to a given session.<br>
   * Useful after having closed a session in a TRIP-Server, for example.
   *
   * @param session the session (probably closed)
   */
  public static void removeObjectsForSessionInAllCaches(Session session) {
    PdoCacheFactory.getInstance().removeObjectsForSessionInAllCaches(session);
  }


  /**
   * Gets a list of all registered caches.
   *
   * @return all registered caches
   */
  public static Collection<PdoCache<? extends PersistentDomainObject<?>>> getAllCaches() {
    return PdoCacheFactory.getInstance().getAllCaches();
  }


  /**
   * Checks whether a PDO-class provides a cache.
   *
   * @param <T> the PDO type
   * @param clazz the class
   * @return the cache, null if none
   */
  public static <T extends PersistentDomainObject<T>> PdoCache<T> getCache(Class<T> clazz) {
    return PdoCacheFactory.getInstance().getCache(clazz);
  }


  // ------------------- methods to create session infos --------------


  /**
   * Creates a session info from a username, password and
   * property file holding the connection parameters.
   *
   * @param username is the name of the user, null if none
   * @param password is the password, null if none
   * @param propertiesName name of the property resources, null if default {@code "backend"}
   * @return the created session info
   */
  public static SessionInfo createSessionInfo(String username, char[] password, String propertiesName) {
    return SessionInfoFactory.getInstance().create(username, password, propertiesName);
  }

  /**
   * Creates a session info from a property file holding the connection parameters.
   *
   * @param propertiesName name of the property resources, null if default {@code "backend"}
   * @return the created session info
   */
  public static SessionInfo createSessionInfo(String propertiesName) {
    return SessionInfoFactory.getInstance().create(propertiesName);
  }

  /**
   * Creates a session info from a properties object.
   *
   * @param properties the properties
   * @return the created session info
   */
  public static SessionInfo createSessionInfo(EncryptedProperties properties) {
    return SessionInfoFactory.getInstance().create(properties);
  }

  /**
   * Creates a session info from the default property file {@code "backend"} holding the connection parameters.
   *
   * @return the created session info
   */
  public static SessionInfo createSessionInfo() {
    return SessionInfoFactory.getInstance().create();
  }

  // ------------------- methods to create sessions --------------


  /**
   * Creates a session.
   *
   * @param sessionInfo the session info
   * @return the open session
   */
  public static Session createSession(SessionInfo sessionInfo) {
    return SessionFactory.getInstance().create(sessionInfo);
  }

  /**
   * Creates a session with a default session info {@code "backend"}.
   *
   * @return the open session
   */
  public static Session createSession() {
    return createSession(createSessionInfo());
  }

  /**
   * Registers a global close handler for all sessions.
   *
   * @param closeHandler the handler
   * @return true if added, false if already registered
   */
  public static boolean registerGlobalSessionCloseHandler(SessionCloseHandler closeHandler) {
    return SessionFactory.getInstance().registerGlobalCloseHandler(closeHandler);
  }


  /**
   * Unregisters a global close handler for all sessions.
   *
   * @param closeHandler the handler
   * @return true if removed, false if not registered
   */
  public static boolean unregisterGlobalSessionCloseHandler(SessionCloseHandler closeHandler) {
    return SessionFactory.getInstance().unregisterGlobalCloseHandler(closeHandler);
  }


  // ------------------- methods to create domain contexts --------------


  /**
   * Creates a domain context with thread-local session.
   *
   * @return the domain context
   */
  public static DomainContext createDomainContext() {
    return DomainContextFactory.getInstance().create();
  }


  /**
   * Creates a domain context.
   *
   * @param session the session, null if thread-local
   * @return the domain context
   */
  public static DomainContext createDomainContext(Session session) {
    return DomainContextFactory.getInstance().create(session);
  }


  /**
   * Creates a domain context.
   *
   * @param session the session, null if thread-local
   * @param sessionImmutable true if session cannot be changed anymore
   * @return the domain context
   */
  public static DomainContext createDomainContext(Session session, boolean sessionImmutable) {
    return DomainContextFactory.getInstance().create(session, sessionImmutable);
  }


  // ---------------------------- helper methods ---------------------------------


  /**
   * Gets the tablename of given class.
   *
   * @param <T> the PDO type
   * @param clazz the class
   * @return the name
   */
  public static <T extends PersistentDomainObject<T>> String getTableName(Class<T> clazz) {
    return PdoUtilities.getInstance().getTableName(clazz);
  }


  /**
   * Handles the change of a keep alive interval of a session.
   *
   * @param session the session
   */
  public static void keepAliveIntervalChanged(Session session) {
    SessionUtilities.getInstance().keepAliveIntervalChanged(session);
  }


  /**
   * Terminates all helper threads.
   */
  public static void terminateHelperThreads() {
    SessionUtilities.getInstance().terminateHelperThreads();
    ModificationTracker.getInstance().terminate();
  }


  /**
   * Adds a listener for a modification on given PDO classes.<br>
   * This is just a convenience wrapper for {@link PdoListener} to make use of lambdas.
   *
   * @param handler the handler of the {@link ModificationEvent}
   * @param classes the PDO classes
   * @return the registered PDO listener
   */
  public static PdoListener listen(Consumer<ModificationEvent> handler, Class<?>... classes) {
    return PdoUtilities.getInstance().listen(handler, classes);
  }

  /**
   * Adds a listener for a modification on given PDO classes.<br>
   * This is just a convenience wrapper for {@link PdoListener} to make use of lambdas.<br>
   * Same as {@link #listen(Consumer, Class[])}, but for handlers that don't need to refer to the
   * {@link ModificationEvent}.
   *
   * @param handler the handler to be invoked
   * @param classes the PDO classes
   * @return the registered PDO listener
   */
  public static PdoListener listen(Runnable handler, Class<?>... classes) {
    return PdoUtilities.getInstance().listen(handler, classes);
  }

  /**
   * Unregisters a registered PDO listener.
   *
   * @param listener the listener to remove
   */
  public static void unlisten(PdoListener listener) {
    PdoUtilities.getInstance().unlisten(listener);
  }


  private Pdo() {
    // no instantiation
  }
}
