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

import java.util.Collection;

/**
 * Collected factory methods for the PDOs and related objects.<br>
 * Combines all the pdo factories and enhances code readability.
 *
 * @author harald
 */
public class Pdo {


  // ------------------- methods to create an implementation of a PDO --------------


  /**
   * Creates a PDO.
   *
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the PDO's class
   * @param pdo the PDO
   * @return the created PDO belonging to the same class as the given {@code pdo} and in same context.
   */
  public static <T extends PersistentDomainObject<T>> T create(T pdo) {
    return PdoFactory.getInstance().create(pdo);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the PDO's class
   * @param clazz the class of the PDO, usually an interface
   * @return the created PDO without session and context
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz) {
    return PdoFactory.getInstance().create(clazz);
  }


  /**
   * Creates a PDO.
   *
   * @param <T> the persistent domain object class
   * @param className the name of PDO-class, usually an interface
   * @return the created PDO without session and context
   */
  public static <T extends PersistentDomainObject<T>> T create(String className) {
    return PdoFactory.getInstance().create(className);
  }


  /**
   * Creates a PDO for a given persistence delegate.
   *
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
   * @param clazz the class of the PDO, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context, DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(clazz, context, domainDelegate);
  }


  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the persistent domain object class
   * @param className the name of PDO-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, DomainContext context, DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(className, context, domainDelegate);
  }


  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the persistent domain object class
   * @param clazz the class of the PDO, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(clazz, persistenceDelegate, domainDelegate);
  }


  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the persistent domain object class
   * @param className the name of PDO-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  public static <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    return PdoFactory.getInstance().create(className, persistenceDelegate, domainDelegate);
  }


  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the persistent domain object class
   * @param clazz the pdo class
   * @return the persistent class
   */
  public static <T extends PersistentDomainObject<T>> Class<? extends PersistentObject<T>> getPersistentObjectClass(Class<T> clazz) {
    return PdoFactory.getInstance().getPersistentClass(clazz);
  }


  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the persistent domain object class
   * @param clazz the pdo class
   * @return the domain class
   */
  public static <T extends PersistentDomainObject<T>> Class<? extends DomainObject<T>> getDomainObjectClass(Class<T> clazz) {
    return PdoFactory.getInstance().getDomainClass(clazz);
  }


  // ------------------- methods to create an implementation of an Operation --------------


  /**
   * Creates an operation.
   *
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the operation's class
   * @param opn the operation
   * @return the created operation belonging to the same class as the given operation and in same context.
   */
  public static <T extends Operation<T>> T createOperation(T opn) {
    return OperationFactory.getInstance().create(opn);
  }


  /**
   * Creates an operation.
   *
   * @param <T> the operation's class
   * @param clazz the class of the operation, usually an interface
   * @return the created operation without session and context
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz) {
    return OperationFactory.getInstance().create(clazz);
  }


  /**
   * Creates an operation for a given persistence delegate.
   *
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
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
   * @param <T> the persistent domain object class
   * @param clazz the class of the operation, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, DomainContext context, DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(clazz, context, domainDelegate);
  }


  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the persistent domain object class
   * @param className the name of operation-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, DomainContext context, DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(className, context, domainDelegate);
  }


  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the persistent domain object class
   * @param clazz the class of the operation, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(Class<T> clazz, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(clazz, persistenceDelegate, domainDelegate);
  }


  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the persistent domain object class
   * @param className the name of operation-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  public static <T extends Operation<T>> T createOperation(String className, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    return OperationFactory.getInstance().create(className, persistenceDelegate, domainDelegate);
  }


  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the persistent domain object class
   * @param clazz the operation class
   * @return the persistent class
   */
  public static <T extends Operation<T>> Class<? extends PersistentOperation<T>> getPersistentOperationClass(Class<T> clazz) {
    return OperationFactory.getInstance().getPersistentClass(clazz);
  }


  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the persistent domain object class
   * @param clazz the operation class
   * @return the domain class
   */
  public static <T extends Operation<T>> Class<? extends DomainOperation<T>> getDomainOperationClass(Class<T> clazz) {
    return OperationFactory.getInstance().getDomainClass(clazz);
  }



  // ------------------- methods to create PDO-caches ----------------------------


  /**
   * Creates a PDO Cache.<br>
   * Multiple calls on this method will return the same object reference.
   * <p>
   * If the current PDO tracker is a dummy tracker, the tracker
   * will be replaced.
   *
   * @param <T> the data object class
   * @param clazz is the PersistentDomainObject-class managed by the cache.
   * @param preload is true if preload all objects in domain context of cache.
   * @param readOnly true if cache is readonly (shared)
   * @param checkSecurity true if check read permission
   * @return the cache
   */
  public static <T extends PersistentDomainObject<T>> PdoCache<T> createPdoCache(
          Class<T> clazz, boolean preload, boolean readOnly, boolean checkSecurity) {
    return PdoCacheFactory.getInstance().createCache(clazz, preload, readOnly, checkSecurity);
  }


  /**
   * Removes all objects in ALL caches that refer to a given db.
   * Useful after having closed a db-connection in an RMI-Server, for example.
   *
   * @param session the session (probably closed)
   */
  public static void removeObjectsForSessionInAllPdoCaches(Session session) {
    PdoCacheFactory.getInstance().removeObjectsForSessionInAllCaches(session);
  }


  /**
   * Gets a list of all registered caches.
   *
   * @return all registered caches
   */
  public static Collection<PdoCache<? extends PersistentDomainObject<?>>> getAllPdoCaches() {
    return PdoCacheFactory.getInstance().getAllCaches();
  }


  /**
   * Checks whether a PDO-class provides a cache.
   *
   * @param <T> the PDO class type
   * @param clazz the class
   * @return the cache, null if none
   */
  public static <T extends PersistentDomainObject<T>> PdoCache<T> getPdoCache(Class<T> clazz) {
    return PdoCacheFactory.getInstance().getCache(clazz);
  }


  // ------------------- methods to create session infos --------------


  /**
   * Creates a session info from a username, password and
   * property file holding the connection parameters.
   *
   * @param username is the name of the user, null if {@code System.getProperty("user.name")}
   * @param password is the password, null if none
   * @param propertiesName name of additional properties resource, null if default
   * @return the created session info
   */
  public static SessionInfo createSessionInfo(String username, char[] password, String propertiesName) {
    return SessionInfoFactory.getInstance().create(username, password, propertiesName);
  }


  // ------------------- methods to create sessions --------------


  /**
   * Creates a session for the default session manager.
   *
   * @param sessionInfo the session info
   * @return the created session (still closed)
   */
  public static Session createSession(SessionInfo sessionInfo) {
    return SessionFactory.getInstance().create(sessionInfo);
  }


  /**
   * Creates a session.
   *
   * @param sessionManager the session manager
   * @param sessionInfo the session info
   * @return the created session (still closed)
   */
  public static Session createSession(SessionManager sessionManager, SessionInfo sessionInfo) {
    return SessionFactory.getInstance().create(sessionManager, sessionInfo);
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
    return DomainContextFactory.getInstance().create(null);
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
   * @param <T>
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
    PdoUtilities.getInstance().keepAliveIntervalChanged(session);
  }


  /**
   * Terminates all helper threads.
   */
  public static void terminateHelperThreads() {
    PdoUtilities.getInstance().terminateHelperThreads();
    PdoTracker.getInstance().terminate();
  }


  private Pdo() {
    // no instantiation
  }
}
