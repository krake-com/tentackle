/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.swing;

/**
 * GUI Runtime Exception.
 * Will usually terminate the application if not explicitly caught.
 * @see GUIExceptionHandler
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class GUIRuntimeException extends RuntimeException {

  /**
   * Constructs a new database runtime exception with <code>null</code> as its
   * detail message.  The cause is not initialized, and may subsequently be
   * initialized by a call to {@link #initCause}.
   */
  public GUIRuntimeException() {
    super();
  }

  /**
   * Constructs a new database runtime exception with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param   message   the detail message. The detail message is saved for
   *          later retrieval by the {@link #getMessage()} method.
   */
  public GUIRuntimeException(String message) {
    super(message);
  }

  /**
   * Constructs a new database runtime exception with the specified detail message and
   * cause.  <p>Note that the detail message associated with
   * <code>cause</code> is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  message the detail message (which is saved for later retrieval
   *         by the {@link #getMessage()} method).
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public GUIRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new database runtime exception with the specified cause and a
   * detail message of <tt>(cause==null ? null : cause.toString())</tt>
   * (which typically contains the class and detail message of
   * <tt>cause</tt>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param  cause the cause (which is saved for later retrieval by the
   *         {@link #getCause()} method).  (A <tt>null</tt> value is
   *         permitted, and indicates that the cause is nonexistent or
   *         unknown.)
   * @since  1.4
   */
  public GUIRuntimeException(Throwable cause) {
    super(cause);
  }

}
