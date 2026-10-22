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

package org.tentackle.misc;

import org.tentackle.common.TentackleRuntimeException;

import java.io.Serial;

/**
 * Runtime exception thrown on an attempt to modify an immutable object.
 *
 * @author harald
 */
public class ImmutableException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new immutable exception
   * with <code>null</code> as its detail message.
   */
  public ImmutableException() {
    super();
  }


  /**
   * Constructs a new immutable exception with the specified detail message.
   *
   * @param   message   the detail message.
   */
  public ImmutableException(String message) {
    super(message);
  }

  /**
   * Constructs a new immutable exception with the specified detail message and
   * cause.
   *
   * @param  message the detail message
   * @param  cause the cause
   */
  public ImmutableException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new immutable exception with the specified cause and a
   * detail message of <code>(cause==null ? null : cause.toString())</code>.
   *
   * @param  cause the cause
   */
  public ImmutableException(Throwable cause) {
    super(cause);
  }

}
