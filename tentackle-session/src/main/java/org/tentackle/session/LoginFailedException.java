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

import org.tentackle.log.Loggable;
import org.tentackle.log.Logger;
import org.tentackle.misc.Identifiable;

import java.io.Serial;

/**
 * Thrown if a login is refused, because authentication failed or for any other reason.
 * <p>
 * Application servers throw it (or one of its subclasses) to refuse a client's login.
 * It is an expected exception and therefore not logged at all, see {@link #getLogLevel()}.
 *
 * @author harald
 */
public class LoginFailedException extends PersistenceException implements Loggable {

  @Serial
  private static final long serialVersionUID = 1L;

 /**
   * Constructs a new login failed exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public LoginFailedException(Session session) {
    super(session);
  }

  /**
   * Constructs a new login failed exception for a given session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public LoginFailedException(Session session, String message) {
    super(session, message);
  }

  /**
   * Constructs a new login failed exception for a given session with the specified detail message and
   * cause.
   *
   * @param  session the session
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public LoginFailedException(Session session, String message, Throwable cause) {
    super(session, message, cause);
  }

  /**
   * Constructs a new login failed exception for a given session with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other {@link Throwable}s.
   *
   * @param  session the session
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public LoginFailedException(Session session, Throwable cause) {
    super(session, cause);
  }

  /**
   * Constructs a new login failed exception for a given {@link Identifiable}.
   * <p>
   * With <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param object the identifiable
   */
  public LoginFailedException(Identifiable object) {
    super(object);
  }

  /**
   * Constructs a new login failed exception for a given {@link Identifiable} with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the identifiable
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public LoginFailedException(Identifiable object, String message) {
    super(object, message);
  }

  /**
   * Constructs a new login failed exception for a given {@link Identifiable} with the specified detail message and
   * cause.
   *
   * @param  object the identifiable
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public LoginFailedException(Identifiable object, String message, Throwable cause) {
    super(object, message, cause);
  }

  /**
   * Constructs a new login failed exception for a given {@link Identifiable} with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other {@link Throwable}s.
   *
   * @param  object the identifiable
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public LoginFailedException(Identifiable object, Throwable cause) {
    super(object, cause);
  }

  /**
   * Constructs a new login failed exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public LoginFailedException() {
    super();
  }

  /**
   * Constructs a new login failed exception without a session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public LoginFailedException(String message) {
    super(message);
  }

  /**
   * Constructs a new login failed exception without a session with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public LoginFailedException(String message, Throwable cause) {
    super(message, cause);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to suppress logging, since a refused login is an expected exception.
   *
   * @return null
   */
  @Override
  public Logger.Level getLogLevel() {
    return null;
  }

}
