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


package org.tentackle.session;

import java.io.Serializable;
import java.util.Collection;
import org.tentackle.misc.TrackedList;


interface Session$ThreadLocal {
  /**
   * The session of the current thread.
   */
  ThreadLocal<Session> TL_SESSION = new ThreadLocal<>();
}


/**
 * The session all PDOs live in.
 *
 * @author harald
 */
public interface Session extends Comparable<Session> {

  /**
   * Gets the session used by the current thread.<br>
   *
   * @return the session, null if current thread is not using a session
   */
  static Session getCurrentSession() {
    return Session$ThreadLocal.TL_SESSION.get();
  }

  /**
   * Sets the session used by the current thread.<br>
   * The session is stored as {@link ThreadLocal}.
   *
   * @param session the db
   */
  static void setCurrentSession(Session session) {
    Session$ThreadLocal.TL_SESSION.set(session);
  }

  /**
   * Asserts that a thread local session is set.<br>
   * @throws PersistenceException if thread-local session is null
   */
  static void assertCurrentSessionValid() {
    getSession();
  }

  /**
   * Gets the thread local session.<br>
   * @return the session, never null
   * @throws PersistenceException if thread-local session is null
   */
  static Session getSession() {
    Session session = getCurrentSession();
    if (session == null) {
      throw new PersistenceException("no thread local session for " + Thread.currentThread());
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
   * Applies this session to a session dependable object.<br>
   * The method must invoke <tt>obj.setSession()</tt> only if the session really differs.
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
  default void applyTo(Collection<? extends SessionDependable> list) {
    if (list != null) {
      for (SessionDependable obj: list)  {
        applyTo(obj);
      }
      if (list instanceof TrackedList) {
        @SuppressWarnings("unchecked")
        Collection<SessionDependable> removedObjects = ((TrackedList<SessionDependable>) list).getRemovedObjects();
        if (removedObjects != null) {
          for (SessionDependable obj: removedObjects) {
            applyTo(obj);
          }
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
   * Gets the session manager.
   *
   * @return the session manager
   */
  SessionManager getSessionManager();

  /**
   * Gets the session ID.
   *
   * @return the ID the session manager assigned to this session
   */
  int getSessionId();

  /**
   * Gets the session's group ID.<br>
   *
   * @return the group id, 0 if not member of a session group
   */
  int getSessionGroupId();

  /**
   * Assigns this session to another session building a session group.
   *
   * @param sessionId the ID of the session to build a group with
   * @return the group id
   */
  int groupWith(int sessionId);

  /**
   * Opens a session.<br>
   * Opening an already open session throws a {@link PersistenceException}.
   */
  void open();

  /**
   * Close a session.<br>
   * Closing an already closed session is okay and ignored.
   * <p>
   * Notice: a non-blocking close can be requested by {@link #setKeepAliveInterval(long)} with
   * an interval &lt; 0.
   */
  void close();

  /**
   * Clones a session.<br>
   * If the session is open, the new session will be open as well.
   * If the session is closed, the cloned session will be closed.
   *
   * @return the cloned session
   */
  Session clone();

  /**
   * Gets the current user info.
   *
   * @return the SessionInfo
   */
  SessionInfo getSessionInfo();

  /**
   * Starts a transaction.<br> Does nothing if a transaction is already running!
   *
   * @param txName is the optional transaction name, null if none
   *
   * @return the tx-voucher, 0 if no tx begun
   */
  long begin(String txName);

  /**
   * Starts a transaction.<br> Does nothing if a transaction is already running!
   *
   * @return the tx-voucher, 0 if no tx begun
   */
  long begin();

  /**
   * Commits a transaction if the corresponding begin() has started it.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested tx)
   *
   * @return true if tx was really committed, false if not.
   */
  boolean commit(long txVoucher);

  /**
   * Rolls back a transaction if the corresponding begin() has started it.<br>
   * The rollback is logged as INFO along with all statements.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested tx)
   *
   * @return true if tx was really rolled back, false if not.
   */
  boolean rollback(long txVoucher);

  /**
   * Rolls back a transaction if the corresponding begin() has started it.<br>
   * Same as {@link #rollback} but without logging. Used for expected rollbacks
   * or rollbacks not related to persistence exceptions.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested tx)
   *
   * @return true if tx was really rolled back, false if not.
   */
  boolean rollbackSilently(long txVoucher);

  /**
   * Determines whether a tx is currently running.
   *
   * @return true if tx running.
   */
  boolean isTxRunning();

  /**
   * Gets the transaction name if a tx is currently running.
   *
   * @return the tx name, null if none or no tx running.
   * @see #isTxRunning()
   */
  String getTxName();

  /**
   * Runs a transaction provided by a transaction envelope.
   *
   * @param <T> the return type
   * @param txe the transaction envelope
   * @param txName the transaction name, null if none
   * @return the return value of the envelope
   */
  <T> T transaction(TransactionEnvelope<T> txe, String txName);

  /**
   * Runs a transaction provided by a transaction envelope.
   *
   * @param <T> the return type
   * @param txe the transaction envelope
   * @return the return value of the envelope
   */
  <T> T transaction(TransactionEnvelope<T> txe);

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
   * Sets the exclusive owner thread. <p> Allows to detect other threads
   * accidently using this session.
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
   * Sets the session's alive state.
   *
   * @param alive is true to signal it's alive, false to clear
   */
  void setAlive(boolean alive);

  /**
   * Checks whether the session is still in use.
   *
   * @return true if session still in use, false if not used since last
   * setAlive(false).
   */
  boolean isAlive();

  /**
   * Sets the auto keep alive interval.<br>
   * If set, the session will be setAlive every keepAliveInterval milliseconds
   * by the {@link SessionKeepAliveDaemon}.
   * Useful for remote sessions to prevent them from being closed by
   * the remote server due to timeout.
   *
   * @param keepAliveInterval the interval in ms, 0 to disable, &lt; 0 to close session
   */
  void setKeepAliveInterval(long keepAliveInterval);


  /**
   * Gets the auto keep alive interval.
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
   * Gets the session pool.
   *
   * @return the db pool, null = not pooled
   */
  SessionPool getPool();

  /**
   * Gets the connection state.
   *
   * @return true if session is open, else false
   */
  boolean isOpen();

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
   * @throws PersistenceException if session is not remote
   * @see #isRemote()
   */
  RemoteSession getRemoteSession();

  /**
   * Gets the url.
   *
   * @return the url
   */
  String getUrl();

  /**
   * Gets the dispatcher for this session.<br>
   * The dispatcher can be used to submit asynchroneous tasks in a serial manner.
   * The returned dispatcher is configured to shutdown if idle for a given timeout.
   *
   * @return the dispatcher
   */
  SessionTaskDispatcher getDispatcher();

  /**
   * Sets a session property.<br>
   * Session properties are application specific.<br>
   * For remote sessions, the properties are delegated to the corresponding
   * local session at the remote side. Thus, session properties can be used
   * to share some application state between the client and the server.
   *
   * @param <T> the value's type
   * @param key the property key
   * @param value the property value
   * @return the old value, null if key is new
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

}
