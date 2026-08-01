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

import org.tentackle.misc.Poolable;
import org.tentackle.misc.Provider;
import org.tentackle.misc.TrackedList;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Consumer;


interface SessionThreadLocalHolder {
  ThreadLocal<Session> TL_SESSION = new ThreadLocal<>();
}


/**
 * The session.<br>
 * Describes a persistence and security context.
 *
 * @author harald
 */
public interface Session extends Comparable<Session>, Poolable<Session> {

  /**
   * Gets the session used by the current thread.<br>
   *
   * @return the session, null if the current thread is not using a session
   */
  static Session getCurrentSession() {
    return SessionThreadLocalHolder.TL_SESSION.get();
  }

  /**
   * Sets the session used by the current thread.<br>
   * The session is stored as {@link ThreadLocal}.
   *
   * @param session the session, null to clear
   */
  static void setCurrentSession(Session session) {
    if (session == null) {
      SessionThreadLocalHolder.TL_SESSION.remove();
    }
    else {
      SessionThreadLocalHolder.TL_SESSION.set(session);
    }
  }

  /**
   * Asserts that a thread-local session is set.<br>
   *
   * @throws PersistenceException if the thread-local session is null
   */
  static void assertCurrentSessionValid() {
    getSession();
  }

  /**
   * Gets the thread-local session.<br>
   *
   * @return the session, never null
   * @throws PersistenceException if the thread-local session is null
   */
  static Session getSession() {
    Session session = getCurrentSession();
    if (session == null) {
      throw new PersistenceException("no thread-local session for " + Thread.currentThread());
    }
    return session;
  }


  /**
   * Makes this session the current session for this thread.
   */
  default void makeCurrent() {
    setCurrentSession(this);
  }

  /**
   * Returns whether this session is the thread's local current session.
   *
   * @return true if this is the current session
   */
  default boolean isCurrent() {
    return getCurrentSession() == this;
  }

  /**
   * Clears the current session if this session is current.
   *
   * @throws PersistenceException if this is not the current session
   */
  default void clearCurrent() {
    if (isCurrent()) {
      setCurrentSession(null);
    }
    else  {
      throw new PersistenceException(this, "is not the current session");
    }
  }

  /**
   * Applies this session to a session-dependable object.<br>
   * The method must invoke <code>obj.setSession()</code> only if the session really differs.
   * This prevents infinite loops in object circular references.
   *
   * @param obj the database object, null if ignore
   */
  default void applyTo(SessionDependable obj) {
    if (obj != null && obj.getSession() != this)  {
      obj.setSession(this);
    }
  }

  /**
   * Applies this session to a collection of session dependables.
   *
   * @param list the collection of session dependables
   */
  @SuppressWarnings("unchecked")
  default void applyTo(Collection<? extends SessionDependable> list) {
    if (list != null) {
      for (SessionDependable obj: list)  {
        applyTo(obj);
      }
      if (list instanceof TrackedList) {
        for (SessionDependable obj: ((TrackedList<SessionDependable>) list).getRemovedObjects()) {
          applyTo(obj);
        }
      }
    }
  }

  /**
   * Gets the session's name.
   *
   * @return the session's name
   */
  String getName();

  /**
   * Gets the session ID.<br>
   * Session IDs are unique for sessions with the same URL.<br>
   * For remote sessions, the ID is the same as the one used in remote server.
   *
   * @return the ID assigned to this session
   * @see #getInstanceNumber() for a unique number within the JVM instance
   */
  int getSessionId();

  /**
   * Gets the session's group ID.<br>
   * The session group is unique for sessions with the same URL.
   *
   * @return the ID of the session this session is grouped with, 0 if not member of a session group
   */
  int getSessionGroupId();

  /**
   * Assigns this session to another session building a session group.<br>
   * If the referenced session already belongs to a group, this session will be added to that group.
   * If the referenced session does not belong to a group, it will become the root session of the group
   * and the group id will be the session id of the root session.
   *
   * @param sessionId the ID of the session to build a group with
   */
  void groupWith(int sessionId);

  /**
   * Close a session.<br>
   * Closing an already closed session is okay and ignored.<br>
   * If this session is the root of a session group, all grouped sessions will be ungrouped.
   * <p>
   * Notice: a non-blocking close can be requested by {@link #setKeepAliveInterval(long)} with
   * an interval &lt; 0.
   */
  @Override
  void close();

  /**
   * Registers a close handler.
   *
   * @param closeHandler the handler
   * @return true if added, false if already registered
   */
  boolean registerCloseHandler(SessionCloseHandler closeHandler);

  /**
   * Unregisters a close handler.
   *
   * @param closeHandler the handler
   * @return true if removed, false if not registered
   */
  boolean unregisterCloseHandler(SessionCloseHandler closeHandler);

  /**
   * Clones a session.<br>
   * If the session is closed, a PersistenceException is thrown.<br>
   * The cloned session will always be non-grouped.
   *
   * @param sessionName the name of the cloned session, null if the same as this session
   * @return the cloned session
   */
  Session clone(String sessionName);

  /**
   * Re-opens a session.<br>
   * If the session is open, it will be closed first.<br>
   * If the session belongs to a group, the re-opened session will be grouped as well.
   * If this session is the root of a session group, all grouped sessions will be ungrouped.
   */
  void reOpen();

  /**
   * Gets the current session info.
   *
   * @return the session info
   */
  SessionInfo getSessionInfo();

  /**
   * Starts a transaction.<br>
   * Does nothing if a transaction is already running!
   * <p>
   * If a transaction is already running and the transactionIsolation or readOnly mode
   * does not correspond to the values at the effective beginning of the transaction,
   * a {@link PersistenceException} is thrown.
   *
   * @param txName is the optional transaction name, null for none
   * @param transactionIsolation the transaction isolation level, null or {@link TransactionIsolation#DEFAULT} if default
   * @param transactionWritability the transaction writabilty, null or {@link TransactionWritability#DEFAULT} if default
   *
   * @return the tx-voucher, 0 if no tx begun
   */
  long begin(String txName, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability);

  /**
   * Starts a transaction.<br>
   * Does nothing if a transaction is already running!
   *
   * @param txName is the optional transaction name, null for none
   *
   * @return the transaction voucher, 0 if no transaction begun
   */
  long begin(String txName);

  /**
   * Starts a transaction.<br> Does nothing if a transaction is already running!
   *
   * @return the transaction voucher, 0 if no transaction begun
   */
  long begin();

  /**
   * Commits a transaction if the corresponding {@link #begin()} has started it.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested transaction)
   *
   * @return true if the transaction was really committed, false if not
   */
  boolean commit(long txVoucher);

  /**
   * Rolls back a transaction if the corresponding {@link #begin()} has started it.<br>
   * The rollback is logged as INFO along with all statements.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested transaction)
   *
   * @return true if the transaction was really rolled back, false if not
   */
  boolean rollback(long txVoucher);

  /**
   * Rolls back a transaction if the corresponding {@link #begin()} has started it.<br>
   * Same as {@link #rollback} but without logging. Used for expected rollbacks
   * or rollbacks not related to persistence exceptions.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested transaction)
   *
   * @return true if the transaction was really rolled back, false if not
   */
  boolean rollbackSilently(long txVoucher);

  /**
   * Determines whether a transaction is currently running.
   *
   * @return true if transaction running.
   */
  boolean isTxRunning();

  /**
   * Gets the transaction name if a transaction is currently running.
   *
   * @return the transaction name, null for none or no transaction running.
   * @see #isTxRunning()
   */
  String getTxName();

  /**
   * Gets the current transaction number.<br>
   * Transaction numbers are unique per session.
   *
   * @return the current transaction number, invalid if no transaction is in progress
   * @see #isTxRunning()
   */
  long getTxNumber();

  /**
   * Gets the transaction nesting level.
   * <p>
   * The nesting level is maintained separately for client and server sessions.
   * Starting a nested transaction for a remote session increments the level
   * only for the remote session, and starting a nested transaction for a server session
   * increments the level only at the server side.
   *
   * @return the nesting level, starting at 1, 0 if no transaction running
   */
  int getTxLevel();

  /**
   * Runs code within a transaction.
   * <p>
   * Provided as an alternative to the {@code @Transaction}-interceptor, if the class holding the implementation of the
   * transaction is not an {@link org.tentackle.reflect.Interceptable}.
   * <p>
   * Usage:
   * <pre>
   * return session.transaction(() -&gt; {
   *    ..do something..
   *    return whatever;
   * });
   * </pre>
   * <code>whatever</code> is returned by <code>session.transaction()</code>. The type is determined
   * by type inference automatically. If there is nothing to return, <code>null</code> must be returned by the envelope:
   * <pre>
   * session.transaction(() -&gt; {
   *    ..do something..
   *    return null;
   * });
   * </pre>
   * The exception is optional. If given, it can be caught as if there was no lambda.
   * <pre>
   * try {
   *   session.transaction(() -&gt; {
   *     throw new MyException("blah");
   *   });
   * }
   * catch (MyException ex) {
   *   ...
   * }
   * </pre>
   *
   * @param <T> the return type
   * @param <E> the exception type
   * @param txName the transaction name, null will derive it from enclosing method
   * @param txe the transaction supplier
   * @return the return value of the transaction supplier
   * @throws E the exception type
   */
  <T, E extends Exception> T transaction(String txName, Provider<T, E> txe) throws E;

  /**
   * Runs code within a transaction.<br>
   * The transaction name is derived from the enclosing method.
   *
   * @param <T> the return type
   * @param <E> the exception type
   * @param txe the transaction supplier
   * @return the return value of the supplier
   * @throws E the exception type
   */
  <T, E extends Exception> T transaction(Provider<T, E> txe) throws E;

  /**
   * Creates an unnamed savepoint in the current transaction.
   *
   * @return the savepoint handle unique within current transaction
   */
  SavepointHandle setSavepoint();

  /**
   * Creates a savepoint with the given name in the current transaction.
   *
   * @param name the savepoint name
   *
   * @return the savepoint handle unique within current transaction
   */
  SavepointHandle setSavepoint(String name);

  /**
   * Undoes all changes made after the given <code>Savepoint</code> object was set.
   *
   * @param handle the savepoint handle
   */
  void rollback(SavepointHandle handle);

  /**
   * Removes the specified <code>Savepoint</code> and subsequent <code>Savepoint</code> objects from the current
   * transaction.
   *
   * @param handle the savepoint handle
   */
  void releaseSavepoint(SavepointHandle handle);

  /**
   * Gets the unique instance number of this Session.
   *
   * @return the instance number
   */
  int getInstanceNumber();

  /**
   * Sets the exclusive owner thread. <p> Allows detecting other threads
   * accidentally using this session.
   *
   * @param ownerThread the owner thread, null to clear
   */
  void setOwnerThread(Thread ownerThread);

  /**
   * Gets the owner thread.
   *
   * @return the exclusive thread, null if none
   */
  Thread getOwnerThread();

  /**
   * Sets the session's alive-state.
   *
   * @param alive is true to signal it's alive, false to clear
   */
  void setAlive(boolean alive);

  /**
   * Checks whether the session is still in use.
   *
   * @return true if the session is still in use, false if not used since last
   * setAlive(false).
   */
  boolean isAlive();

  /**
   * Sets the auto keep-alive interval.<br>
   * If set, the session will be setAlive all keepAliveInterval milliseconds
   * by the {@link SessionKeepAliveDaemon}.
   * Useful for remote sessions to prevent them from being closed by
   * the remote server due to timeout.
   *
   * @param keepAliveInterval the interval in ms, 0 to disable, &lt; 0 to close session
   */
  void setKeepAliveInterval(long keepAliveInterval);

  /**
   * Gets the auto keep-alive interval.
   *
   * @return the interval in ms, 0 if disabled
   */
  long getKeepAliveInterval();

  /**
   * Checks whether this session is pooled.
   *
   * @return true if pooled, false if not pooled
   */
  boolean isPooled();

  /**
   * Gets the persistence locality of this session.<br>
   * Sessions may refer to persistence layers not running in the current JVM.
   * A session is called <em>remote</em> if the physical connection to the database backend
   * is located in another JVM. It is called <em>local</em> if the backend is
   * connected to the current JVM.
   *
   * @return true if remote, false if local
   * @see #getRemoteSession()
   */
  boolean isRemote();

  /**
   * Gets the remote session.
   *
   * @return the remote session
   * @throws PersistenceException if the session is not remote
   * @see #isRemote()
   */
  RemoteSession getRemoteSession();

  /**
   * Preloads the remote delegates of the entities marked as preload candidates.
   * <p>
   * Remote sessions create the delegates lazily, one roundtrip per entity class.
   * For applications with a large entity model running on links with a high latency,
   * this may slow down the application startup substantially. Preloading fetches the delegates
   * of all candidates in a single roundtrip.
   * <p>
   * Since not all sessions benefit from preloading, it is up to the application to decide
   * per session whether to invoke this method. It is usually invoked right after the session
   * has been created.
   */
  void preloadRemoteDelegates();

  /**
   * Gets the connection url.
   *
   * @return the url
   */
  String getUrl();

  /**
   * Gets the dispatcher for this session.<br>
   * The dispatcher can be used to submit asynchronous tasks serially.
   * The returned dispatcher is configured to shut down if idle for a given timeout.
   *
   * @return the dispatcher
   */
  SessionTaskDispatcher getDispatcher();

  /**
   * Sets a session property.<br>
   * Session properties are application-specific.<br>
   * For remote sessions, the properties are delegated to the corresponding
   * local session at the remote side. Thus, session properties can be used
   * to share some application state between the client and the server.
   *
   * @param <T> the value's type
   * @param key the property key
   * @param value the property value, null to remove the property
   * @return the old value, null if the key is new
   */
  <T extends Serializable> T setProperty(String key, T value);

  /**
   * Gets a session property.
   *
   * @param <T> the value's type
   * @param key the property key
   * @return the property value, null if no such property
   */
  <T extends Serializable> T getProperty(String key);

  /**
   * Removes all session properties.
   */
  void clearProperties();

  /**
   * Registers a post-commit.<br>
   * Post-commits are invoked <em>after</em> the successful physical commit of a transaction.
   * They must be registered from within a transactional method, i.e., a transaction must be running.<br>
   * They are executed in reverse order of their transaction level, but per level in registration order,
   * which is the expected behavior for nested transactions.<br>
   * Post-commits are intended for non-transactional purposes, such as firing events. Any attempt
   * to register a post-commit directly or indirectly from within another post-commit will throw a
   * {@link PersistenceException}.<br>
   * Notice that post-commits are executed in the same JVM they were registered.
   *
   * @param postCommit the post-commit
   */
  void postCommit(Consumer<Session> postCommit);

  /**
   * Registers a pre-commit.<br>
   * Pre-commits are invoked immediately <em>before</em> the physical commit of a transaction.
   * They must be registered from within a transactional method, i.e., a transaction must be running.<br>
   * They are executed in reverse order of their transaction level, but per level in registration order,
   * which is the expected behavior for nested transactions.<br>
   * Pre-commits are intended for final computations that must be performed at the end of a
   * transaction immediately before committing.
   * Any attempt to register a pre-commit directly or indirectly from within another pre-commit will throw a
   * {@link PersistenceException}.<br>
   * Notice that pre-commits are executed in the same JVM they were registered.
   *
   * @param preCommit the pre-commit
   */
  void preCommit(Consumer<Session> preCommit);

}
