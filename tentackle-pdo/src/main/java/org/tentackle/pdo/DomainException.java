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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Identifiable;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * Domain logic runtime exception.
 *
 * @author harald
 */
public class DomainException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Extracts the {@link DomainException} from an exception.
   * <p>
   * Scans the exception chain until it finds a {@link DomainException}.
   *
   * @param e the exception head
   * @return the DomainException, null if none
   */
  public static DomainException extractDomainException(Throwable e) {
    while (e != null) {
      if (e instanceof DomainException) {
        return (DomainException) e;
      }
      e = e.getCause();
    }
    return null;
  }


  private transient Session session;        // the session this exception belongs to
  private Identifiable identifiable;        // the object this exception belongs to


  /**
   * Constructs a new domain runtime exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public DomainException(Session session) {
    super();
    setSession(session);
  }


  /**
   * Constructs a new domain runtime exception for a given session with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public DomainException(Session session, String message) {
    super(message);
    setSession(session);
  }

  /**
   * Constructs a new domain runtime exception for a given session with the specified detail message and
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
  public DomainException(Session session, String message, Throwable cause) {
    super(message, cause);
    setSession(session);
  }

  /**
   * Constructs a new domain runtime exception for a given session with the specified cause and a
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
  public DomainException(Session session, Throwable cause) {
    super(cause);
    setSession(session);
  }



  /**
   * Constructs a new domain runtime exception for a given object
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param identifiable the object
   */
  public DomainException(Identifiable identifiable) {
    super();
    setIdentifiable(identifiable);
  }


  /**
   * Constructs a new domain runtime exception for a given object with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the object
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public DomainException(Identifiable object, String message) {
    super(message);
    setIdentifiable(object);
  }

  /**
   * Constructs a new domain runtime exception for a given object with the specified detail message and
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
  public DomainException(Identifiable object, String message, Throwable cause) {
    super(message, cause);
    setIdentifiable(object);
  }

  /**
   * Constructs a new domain runtime exception for a given object with the specified cause and a
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
  public DomainException(Identifiable object, Throwable cause) {
    super(cause);
    setIdentifiable(object);
  }



  /**
   * Constructs a new domain runtime exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public DomainException() {
    super();
  }


  /**
   * Constructs a new domain runtime exception without a session with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public DomainException(String message) {
    super(message);
  }

  /**
   * Constructs a new domain runtime exception without a session with the specified detail message and
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
  public DomainException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new domain runtime exception without a session with the specified cause and a
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
  public DomainException(Throwable cause) {
    super(cause);
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
   * Gets the identifiable object.
   *
   * @return the object, null if exception is not related to an identifiable
   */
  public Identifiable getIdentifiable() {
    return identifiable;
  }


  /**
   * {@inheritDoc}
   * <p>
   * This is the message plus the optional object with the optional session in braces.
   * <p>
   * Example:
   * <pre>
   *   "no write permission {Product[344/2] at Db1[1]=jdbc:mysql://localhost:3306/invoicer}"
   * </pre>
   * @return the message
   */
  @Override
  public String getMessage() {
    String msg = super.getMessage();
    if (session != null || identifiable != null) {
      msg += " {";
      if (identifiable != null) {
        msg += identifiable.toGenericString();    // toString may fail!
        if (session != null) {
          msg += " at ";
        }
      }
      if (session != null) {
        msg += session.toString();
      }
      msg += "}";
    }
    return msg;
  }


  /**
   * Updates the identifiable if not set so far.<br>
   * Used to add more info for exceptions thrown in a context where
   * the identifiable isn't known.
   *
   * @param object the identifiable object
   */
  public void updateDbObject(Identifiable object) {
    if (this.identifiable == null) {
      setIdentifiable(object);
    }
  }


  /**
   * Sets the identifiable.
   *
   * @param object the identifiable object
   */
  protected void setIdentifiable(Identifiable object) {
    this.identifiable = object;
    if (object instanceof PersistentObject<?>) {
      setSession(((PersistentObject<?>) object).getSession());
    }
  }


  /**
   * Sets the session.
   *
   * @param session the session
   */
  protected void setSession(Session session) {
    this.session = session;
  }
}
