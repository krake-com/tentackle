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

package org.tentackle.quiche.ffm;

import java.io.Serial;

/**
 * Thrown when a call into the native quiche library fails.
 */
public class QuicheException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The {@code QUICHE_ERR_} code, or 0 if this failure did not come with one.
   */
  private final long errorCode;

  /**
   * Creates an exception with {@code null} as its detail message and no error code.
   */
  public QuicheException() {
    super();
    this.errorCode = 0;
  }

  /**
   * Creates an exception.
   *
   * @param message the message
   */
  public QuicheException(String message) {
    this(message, 0);
  }

  /**
   * Creates an exception for a quiche return code.
   *
   * @param message the message
   * @param errorCode the {@code QUICHE_ERR_} code
   */
  public QuicheException(String message, long errorCode) {
    super(errorCode == 0 ? message : message + ": " + Quiche.errorName(errorCode));
    this.errorCode = errorCode;
  }

  /**
   * Creates an exception.
   *
   * @param message the message
   * @param cause the cause
   */
  public QuicheException(String message, Throwable cause) {
    super(message, cause);
    this.errorCode = 0;
  }

  /**
   * Gets the {@code QUICHE_ERR_} code.
   *
   * @return the code, 0 if this failure did not come with one
   */
  public long getErrorCode() {
    return errorCode;
  }

}
