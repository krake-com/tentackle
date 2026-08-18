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

import org.tentackle.misc.Identifiable;

import java.io.Serial;

/**
 * Runtime exception thrown for database constraint violations.
 *
 * @author harald
 */
public class ConstraintException extends PersistenceException {

  @Serial
  private static final long serialVersionUID = 1L;


 /**
   * Constructs a new database constraint violation exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public ConstraintException(Session session) {
    super(session);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public ConstraintException(Session session, String message) {
    super(session, message);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this constraint's detail message.
   *
   * @param  session the session
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public ConstraintException(Session session, String message, Throwable cause) {
    super(session, message, cause);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified cause and a
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
  public ConstraintException(Session session, Throwable cause) {
    super(session, cause);
  }

  /**
   * Constructs a new database constraint violation exception for a given pc object
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param object the persistent object
   */
  public ConstraintException(Identifiable object) {
    super(object);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the persistent object
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public ConstraintException(Identifiable object, String message) {
    super(object, message);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this constraint's detail message.
   *
   * @param  object the persistent object
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public ConstraintException(Identifiable object, String message, Throwable cause) {
    super(object, message, cause);
  }

  /**
   * Constructs a new database constraint violation exception for a given session with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other {@link Throwable}s.
   *
   * @param  object the persistent object
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public ConstraintException(Identifiable object, Throwable cause) {
    super(object, cause);
  }

  /**
   * Constructs a new database constraint exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public ConstraintException() {
    super();
  }

  /**
   * Constructs a new database constraint exception without a session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public ConstraintException(String message) {
    super(message);
  }

  /**
   * Constructs a new database constraint exception without a session with the specified detail message and
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
  public ConstraintException(String message, Throwable cause) {
    super(message, cause);
  }

}
