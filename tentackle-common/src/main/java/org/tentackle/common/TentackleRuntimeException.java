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


package org.tentackle.common;


import java.io.Serial;

/**
 * Tentackle runtime exception.
 * <p>
 * The exception can be marked as temporary (see {@link #setTemporary(boolean)}), i.e., the cause
 * may disappear after some time and the application may retry the operation later.
 * Temporary exceptions can be found within an exception chain via
 * {@link ExceptionHelper#extractTemporaryException(boolean, Throwable)}.
 *
 * @author harald
 */
public class TentackleRuntimeException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;


  /** true if temporary exception. */
  private boolean temporary;

  /**
   * Constructs a new tentackle runtime exception
   * with <code>null</code> as its detail message.
   * The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public TentackleRuntimeException() {
    super();
  }


  /**
   * Constructs a new tentackle runtime exception with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public TentackleRuntimeException(String message) {
    super(message);
  }

  /**
   * Constructs a new tentackle runtime exception with the specified detail message and
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
  public TentackleRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new tentackle runtime exception with the specified cause and a
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
  public TentackleRuntimeException(Throwable cause) {
    super(cause);
  }


  /**
   * Returns whether the cause is temporary and may disappear after some time.
   *
   * @return true if temporary exception, default is false
   */
  public boolean isTemporary() {
    return temporary;
  }

  /**
   * Sets the temporary flag.<br>
   * The cause for a temporary exception may disappear after some time.
   * The application may retry later.
   *
   * @param temporary true if temporary exception
   */
  public void setTemporary(boolean temporary) {
    this.temporary = temporary;
  }

}
