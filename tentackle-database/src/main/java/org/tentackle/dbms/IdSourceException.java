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

package org.tentackle.dbms;

import org.tentackle.session.PersistenceException;

import java.io.Serial;

/**
 * Runtime exception thrown for id source failures.
 *
 * @author harald
 */
public class IdSourceException extends PersistenceException {

  @Serial
  private static final long serialVersionUID = 1L;


 /**
   * Constructs a new id source exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param db the session
   */
  public IdSourceException(Db db) {
    super(db);
  }

  /**
   * Constructs a new id source exception for a given session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   db the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public IdSourceException(Db db, String message) {
    super(db, message);
  }

  /**
   * Constructs a new id source exception for a given session with the specified detail message and
   * cause.
   *
   * @param  db the session
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause
   */
  public IdSourceException(Db db, String message, Throwable cause) {
    super(db, message, cause);
  }

  /**
   * Constructs a new id source exception for a given session with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).
   *
   * @param  db the session
   * @param  cause the cause
   */
  public IdSourceException(Db db, Throwable cause) {
    super(db, cause);
  }

  /**
   * Constructs a new id source exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public IdSourceException() {
    super();
  }

  /**
   * Constructs a new id source exception without a session with the specified detail message.
   * The cause is not initialized and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public IdSourceException(String message) {
    super(message);
  }

  /**
   * Constructs a new id source exception without a session with the specified detail message and
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
  public IdSourceException(String message, Throwable cause) {
    super(message, cause);
  }

}
