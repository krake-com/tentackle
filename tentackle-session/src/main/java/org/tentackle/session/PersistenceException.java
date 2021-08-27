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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Identifiable;

import java.rmi.RemoteException;

/**
 * Database runtime exception.
 *
 * @author harald
 */
public class PersistenceException extends TentackleRuntimeException {

  private static final long serialVersionUID = -5355906838551059053L;


  /**
   * Creates a RuntimeException from a {@link RemoteException}.<br>
   * Returns the first RuntimeException in chain.
   * If the first non-RemoteException is not a RuntimeException, the exception
   * is wrapped by a PersistenceException.
   *
   * @param relatedObject the optional related object, only used if remote cause is not a {@link PersistenceException}
   * @param remoteException the remote exception
   * @return the runtime exception
   */
  public static RuntimeException createFromRemoteException(Object relatedObject, RemoteException remoteException) {
    return SessionUtilities.getInstance().createFromRemoteException(relatedObject, remoteException);
  }



  /**
   * Extracts the {@link PersistenceException} from an exception.
   * <p>
   * Scans the exception chain until it finds an {@link PersistenceException}.
   *
   * @param e the exception head
   * @return the PersistenceException, null if none
   */
  public static PersistenceException extractPersistenceException(Throwable e) {
    while (e != null) {
      if (e instanceof PersistenceException) {
        return (PersistenceException) e;
      }
      e = e.getCause();
    }
    return null;
  }


  private transient Session session;        // the db connection this exception belongs to
  private Identifiable identifiable;        // the db object this execption belongs to
  private String lazyMessage;               // null if messsage not computed yet


  /**
   * Constructs a new database runtime exception for a given db connection
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public PersistenceException(Session session) {
    super();
    setSession(session);
  }


  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(Session session, String message) {
    super(message);
    setSession(session);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  session the session
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Session session, String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
    setSession(session);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  session the session
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Session session, Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
    setSession(session);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given db object
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param identifiable the db object
   */
  public PersistenceException(Identifiable identifiable) {
    super();
    setIdentifiable(identifiable);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the object
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(Identifiable object, String message) {
    super(message);
    setIdentifiable(object);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  object the object
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Identifiable object, String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
    setIdentifiable(object);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given db connection with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  object the object
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Identifiable object, Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
    setIdentifiable(object);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception without a db connection and
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public PersistenceException() {
    super();
  }

  /**
   * Constructs a new database runtime exception without a db connection with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PersistenceException(String message) {
    super(message);
  }

  /**
   * Constructs a new database runtime exception without a db connection with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
  }

  /**
   * Constructs a new database runtime exception without a db connection with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PersistenceException(Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
  }


  /**
   * Gets the session.
   *
   * @return the session
   */
  public Session getSession() {
    return session;
  }

  /**
   * Gets the persistent object.
   *
   * @return the object, null if exception is not related to an object
   */
  public Identifiable getIdentifiable() {
    return identifiable;
  }

  @Override
  public String getMessage() {
    if (lazyMessage == null) {
      lazyMessage = SessionUtilities.getInstance().createLazyExceptionMessage(super.getMessage(), getCause(), getIdentifiable(), getSession()).toString();
    }
    return lazyMessage;
  }

  /**
   * Updates the pdo if not set so far.<br>
   * Used to add more info for exceptions thrown in a context where
   * the pdo isn't known.
   *
   * @param object the persistent object
   */
  public void updateDbObject(Identifiable object) {
    if (this.identifiable == null) {
      setIdentifiable(object);
    }
  }


  /**
   * Sets the pdo.
   * @param object the persistent object
   */
  private void setIdentifiable(Identifiable object) {
    this.identifiable = object;
    if (object instanceof SessionProvider) {
      setSession(((SessionProvider) object).getSession());
    }
  }

  /**
   * Sets the db.<br>
   *
   * @param session the session
   */
  private void setSession(Session session) {
    this.session = session;
  }

}
