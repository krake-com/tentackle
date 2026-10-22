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

import java.io.Serial;

/**
 * The root of all persistence-related runtime exceptions.
 * <p>
 * The exception optionally refers to the {@link Session} and/or the {@link Identifiable} it belongs to.
 * The session is transient, i.e. it does not travel to another JVM. The message is created lazily on
 * the first invocation of {@link #getMessage()} and extended by the identifiable and session, if any,
 * see {@link SessionUtilities#createLazyExceptionMessage(String, Throwable, Identifiable, Session)}.
 * <p>
 * The constructors with a cause pass it through {@link SessionUtilities#alignExceptionCause(Throwable)}.
 * Those with a session or identifiable additionally determine the temporary status via
 * {@link SessionUtilities#alignTemporaryExceptionStatus(PersistenceException)}.
 *
 * @author harald
 */
public class PersistenceException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


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


  private transient Session session;        // the session this exception belongs to
  private Identifiable identifiable;        // the db object this exception belongs to
  private String lazyMessage;               // null if message not computed yet


  /**
   * Constructs a new database runtime exception for a given session
   * with <code>null</code> as its detail message.
   *
   * @param session the session
   */
  public PersistenceException(Session session) {
    super();
    setSession(session);
  }

  /**
   * Constructs a new database runtime exception for a given session with the specified detail message.
   *
   * @param session the session
   * @param message the detail message.
   */
  public PersistenceException(Session session, String message) {
    super(message);
    setSession(session);
  }

  /**
   * Constructs a new database runtime exception for a given session with the specified detail message and
   * cause.
   *
   * @param  session the session
   * @param  message the detail message
   * @param  cause the cause
   */
  public PersistenceException(Session session, String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
    setSession(session);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given session with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>.
   *
   * @param  session the session
   * @param  cause the cause
   */
  public PersistenceException(Session session, Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
    setSession(session);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given {@link Identifiable}
   * with <code>null</code> as its detail message.
   *
   * @param identifiable an identifiable object
   */
  public PersistenceException(Identifiable identifiable) {
    super();
    setIdentifiable(identifiable);
  }

  /**
   * Constructs a new database runtime exception for a given {@link Identifiable} with the specified detail message.
   *
   * @param   object the identifiable
   * @param   message the detail message.
   */
  public PersistenceException(Identifiable object, String message) {
    super(message);
    setIdentifiable(object);
  }

  /**
   * Constructs a new database runtime exception for a given {@link Identifiable} with the specified detail message and
   * cause.
   *
   * @param  object the identifiable
   * @param  message the detail message
   * @param  cause the cause
   */
  public PersistenceException(Identifiable object, String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
    setIdentifiable(object);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception for a given {@link Identifiable} with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>.
   *
   * @param  object the identifiable
   * @param  cause the cause
   */
  public PersistenceException(Identifiable object, Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
    setIdentifiable(object);
    SessionUtilities.getInstance().alignTemporaryExceptionStatus(this);
  }

  /**
   * Constructs a new database runtime exception without a session or identifiable and
   * with <code>null</code> as its detail message.
   */
  public PersistenceException() {
    super();
  }

  /**
   * Constructs a new database runtime exception without a session or identifiable for the specified detail message.
   *
   * @param message the detail message.
   */
  public PersistenceException(String message) {
    super(message);
  }

  /**
   * Constructs a new database runtime exception without a session or identifiable with the specified detail message and
   * cause.
   *
   * @param  message the detail message
   * @param  cause the cause
   */
  public PersistenceException(String message, Throwable cause) {
    super(message, SessionUtilities.getInstance().alignExceptionCause(cause));
  }

  /**
   * Constructs a new database runtime exception without a session or identifiable with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>.
   *
   * @param  cause the cause
   */
  public PersistenceException(Throwable cause) {
    super(SessionUtilities.getInstance().alignExceptionCause(cause));
  }


  /**
   * Gets the session.
   *
   * @return the session, null if unknown or the exception was transferred from another JVM
   */
  public Session getSession() {
    return session;
  }

  /**
   * Gets the persistent object.
   *
   * @return the object, null if the exception is not related to an object
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
   * Evaluates the message for {@link #getMessage()}.<br>
   * Used to evaluate the message text within the transaction if the logging
   * happens later, when the transaction is already rolled back, or
   * the exception is logged at the remote side.
   *
   * @return this exception
   */
  public PersistenceException evaluate() {
    getMessage();
    return this;
  }

  /**
   * Sets the identifiable if not set so far.<br>
   * Used to add more info to exceptions thrown in a context where
   * the object isn't known.<br>
   * If the identifiable is a {@link SessionProvider}, its session is taken over as well.
   *
   * @param object the persistent object
   */
  public void updateDbObject(Identifiable object) {
    if (this.identifiable == null) {
      setIdentifiable(object);
    }
  }


  /**
   * Sets the identifiable and, if it provides one, the session.
   *
   * @param object the persistent object
   */
  private void setIdentifiable(Identifiable object) {
    this.identifiable = object;
    if (object instanceof SessionProvider) {
      setSession(((SessionProvider) object).getSession());
    }
  }

  /**
   * Sets the session.
   *
   * @param session the session
   */
  private void setSession(Session session) {
    this.session = session;
  }

}
