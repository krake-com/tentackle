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

import org.tentackle.session.Session;

import java.io.Serial;


/**
 * PDO cache exception.<br>
 * Thrown by the {@link PdoCache}, for example if a unique violation or a modified key is detected
 * (the cache invalidates itself and retries once), or to prevent cache pollution within a transaction
 * (rolls back the transaction).
 *
 * @author harald
 */
public class PdoCacheException extends PdoRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Constructs a new pdo cache exception for a given session
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param session the session
   */
  public PdoCacheException(Session session) {
    super(session);
  }


  /**
   * Constructs a new pdo cache exception for a given session with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   session the session
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PdoCacheException(Session session, String message) {
    super(session, message);
  }

  /**
   * Constructs a new pdo cache exception for a given session with the specified detail message and
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
  public PdoCacheException(Session session, String message, Throwable cause) {
    super(session, message, cause);
  }

  /**
   * Constructs a new pdo cache exception for a given session with the specified cause and a
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
  public PdoCacheException(Session session, Throwable cause) {
    super(session, cause);
  }



  /**
   * Constructs a new pdo cache exception for a given PDO
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   *
   * @param pdo the PDO
   */
  public PdoCacheException(PersistentDomainObject<?> pdo) {
    super(pdo);
  }


  /**
   * Constructs a new pdo cache exception for a given PDO with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   pdo the PDO
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PdoCacheException(PersistentDomainObject<?> pdo, String message) {
    super(pdo, message);
  }

  /**
   * Constructs a new pdo cache exception for a given PDO with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  pdo the PDO
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PdoCacheException(PersistentDomainObject<?> pdo, String message, Throwable cause) {
    super(pdo, message, cause);
  }

  /**
   * Constructs a new pdo cache exception for a given PDO with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>
   * (which typically contains the class and detail message of
   * <code>cause</code>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  pdo the PDO
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <code>null</code> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   */
  public PdoCacheException(PersistentDomainObject<?> pdo, Throwable cause) {
    super(pdo, cause);
  }



  /**
   * Constructs a new pdo cache exception without a session and
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public PdoCacheException() {
    super();
  }


  /**
   * Constructs a new pdo cache exception without a session with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public PdoCacheException(String message) {
    super(message);
  }

  /**
   * Constructs a new pdo cache exception without a session with the specified detail message and
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
  public PdoCacheException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new pdo cache exception without a session with the specified cause and a
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
  public PdoCacheException(Throwable cause) {
    super(cause);
  }

}
