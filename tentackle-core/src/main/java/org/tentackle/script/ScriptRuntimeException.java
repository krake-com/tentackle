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


package org.tentackle.script;


import java.io.Serial;

/**
 * Script exception.
 * <p>
 * Script exceptions are unchecked runtime exceptions.
 * 
 * @author harald
 */
public class ScriptRuntimeException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  

  /**
   * Creates a script exception.
   *
   * @param cause the cause for the exception
   */
  public ScriptRuntimeException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a script exception.
   *
   * @param message a detailed message
   * @param cause the cause for the exception
   */
  public ScriptRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a script exception.
   *
   * @param message a detailed message
   */
  public ScriptRuntimeException(String message) {
    super(message);
  }

  /**
   * Creates a script exception.
   */
  public ScriptRuntimeException() {
    super();
  }

}
