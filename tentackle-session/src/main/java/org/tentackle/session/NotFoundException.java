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
 * Runtime exception thrown if objects that should exist are not found in the database.
 *
 * @author harald
 */
public class NotFoundException extends PersistenceException {

  @Serial
  private static final long serialVersionUID = 1L;

  private long persistedSerial;     // 0 if unknown, -1 if object not in database, else the object's serial in db


 /**
   * Constructs a new database object not found exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public NotFoundException(Session session) {
    super(session);
  }

  /**
   * Constructs a new database object not found exception for a given session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public NotFoundException(Session session, String message) {
    super(session, message);
  }

  /**
   * Constructs a new database object not found exception for a given session with the specified detail message and
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
  public NotFoundException(Session session, String message, Throwable cause) {
    super(session, message, cause);
  }

  /**
   * Constructs a new database object not found exception for a given session with the specified cause and a
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
  public NotFoundException(Session session, Throwable cause) {
    super(session, cause);
  }

  /**
   * Constructs a new database object not found exception for a given {@link Identifiable}
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param object the identifiable
   */
  public NotFoundException(Identifiable object) {
    super(object);
  }

  /**
   * Constructs a new database object not found exception for a given {@link Identifiable} with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the identifiable
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public NotFoundException(Identifiable object, String message) {
    super(object, message);
  }

  /**
   * Constructs a new database object not found exception for a given {@link Identifiable} with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   object the identifiable
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   * @param   persistedSerial the serial of the object in the database, -1 if no such object, 0 if unknown
   */
  public NotFoundException(Identifiable object, String message, long persistedSerial) {
    super(object, message);
    this.persistedSerial = persistedSerial;
  }

  /**
   * Constructs a new database object not found exception for a given {@link Identifiable} with the specified detail message and
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
  public NotFoundException(Identifiable object, String message, Throwable cause) {
    super(object, message, cause);
  }

  /**
   * Constructs a new database object not found exception for a given {@link Identifiable} with the specified cause and a
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
  public NotFoundException(Identifiable object, Throwable cause) {
    super(object, cause);
  }

  /**
   * Constructs a new database object not found exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public NotFoundException() {
    super();
  }

  /**
   * Constructs a new database object not found exception without a session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Constructs a new database object not found exception without a session with the specified detail message and
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
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Gets the effective serial of the object persisted in the database.
   *
   * @return 0 if unknown, -1 if no such object persisted at all, else the persisted serial
   */
  public long getPersistedSerial() {
    return persistedSerial;
  }

}
