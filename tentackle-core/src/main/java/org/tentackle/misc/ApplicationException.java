/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.misc;

import org.tentackle.common.ExceptionHelper;

/**
 * Application Exception.<br>
 *
 * @author  harald
 */
public class ApplicationException extends Exception {

  /* serialUID. */
  private static final long serialVersionUID = 1L;

  /** the default error code **/
  public static final int ERROR_DEFAULT = 1;


  private int errorCode;    // the errorcode of this exception


  /**
   * Creates an ApplicationException.
   *
   * @param message the message, null if none
   * @param errorCode the errorcode
   * @param cause the chained cause, null if none
   */
  public ApplicationException(String message, int errorCode, Throwable cause)  {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Creates an ApplicationException.
   *
   * @param message the message, null if none
   * @param errorCode the errorcode
   */
  public ApplicationException(String message, int errorCode) {
    super(message);
    setErrorCode(errorCode);
  }

  /**
   * Creates an ApplicationException.
   *
   * @param message the message, null if none
   */
  public ApplicationException(String message) {
    this(message, ERROR_DEFAULT);
  }

  /**
   * Creates an ApplicationException.
   *
   * @param message the message, null if none
   * @param cause the chained cause, null if none
   */
  public ApplicationException(String message, Throwable cause) {
    this(message, ERROR_DEFAULT, cause);
  }


  /**
   * Gets the errorcode.
   * @return the errorcode
   */
  public int getErrorCode() {
    return errorCode;
  }

  /**
   * Sets the errorcode.
   * @param errorCode  the errorcode
   */
  public void setErrorCode(int errorCode) {
    this.errorCode = errorCode;
  }


  /**
   * Gets the chain of all messages (if exception is cascaded).<br>
   *
   * Any Exception other than ApplicationException will be stacktraced.
   *
   * @return the message string
   */
  public String getAllMessages()  {
    StringBuilder buf = new StringBuilder();
    String message = getMessage();
    if (message != null) {
      buf.append(message);
    }
    int closeCount = 0;
    Throwable cause = getCause();
    while (cause != null)  {
      buf.append(" [");
      message = cause.getMessage();
      if (message != null) {
        buf.append(message);
      }
      if (!(cause instanceof ApplicationException)) {
        // some severe programming error
        buf.append('\n').append(ExceptionHelper.getStackTraceAsString(this));
      }
      closeCount++;
      cause = cause.getCause();
    }
    for (int i = 0; i < closeCount; i++) {
      buf.append("]");
    }
    return buf.toString();
  }

}
