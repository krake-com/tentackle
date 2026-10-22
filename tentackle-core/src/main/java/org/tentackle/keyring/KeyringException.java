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

package org.tentackle.keyring;

import org.tentackle.common.TentackleRuntimeException;

import java.io.Serial;

/**
 * Thrown if the credential store cannot be accessed.
 * <p>
 * A secret that simply does not exist is not an error and never causes this exception.
 *
 * @author harald
 */
public class KeyringException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a keyring exception.
   *
   * @param message the detail message
   */
  public KeyringException(String message) {
    super(message);
  }

  /**
   * Creates a keyring exception with a cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public KeyringException(String message, Throwable cause) {
    super(message, cause);
  }

}
