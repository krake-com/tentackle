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

package org.tentackle.trip;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;

import java.io.Serial;

/**
 * Trip runtime exception.
 */
public class TripRuntimeException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new trip runtime exception.
   *
   * @param message the detail message
   * @param cause   the cause
   */
  @CanonicalConstructor
  public TripRuntimeException(@CanonicalName("message") String message,
                              @CanonicalName("cause") Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new trip runtime exception with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a
   * call to {@link #initCause}.
   *
   * @param message the detail message
   */
  public TripRuntimeException(String message) {
    super(message);
  }

  /**
   * Constructs a new trip runtime exception.
   *
   * @param cause the cause
   */
  public TripRuntimeException(Throwable cause) {
    super(cause);
  }

}
