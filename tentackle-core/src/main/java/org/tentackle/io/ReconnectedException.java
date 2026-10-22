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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;

import java.io.Serial;

/**
 * Exception thrown if a resource has been reconnected.<br>
 * When an application catches this exception, it indicates that
 * the resource has been reconnected while the cause holds
 * the exception that triggers the reconnection, i.e., the original resource failure.
 * <p>
 * Notice that a ReconnectedException only makes sense for
 * a blocking {@link ReconnectionPolicy}. For an example, see {@code DbModificationTracker#lockInternal}.
 * Non-blocking policies, by nature, run in a separate background thread, so the
 * thread facing the original exception must retry periodically on its own to check whether
 * the resource became available again.
 *
 * @author harald
 */
public class ReconnectedException extends TentackleRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new reconnected exception with <code>null</code> as its detail message.
   * The cause is not initialized and may subsequently be initialized by a call to
   * {@link #initCause}.
   */
  public ReconnectedException() {
    super();
  }

  /**
   * Constructs a new reconnected exception with the specified detail message and
   * cause.
   *
   * @param  message the detail message
   * @param  cause the original cause that triggered the reconnection
   */
  public ReconnectedException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new reconnected exception with the specified cause.
   *
   * @param  cause the original cause that triggered the reconnection
   */
  public ReconnectedException(Throwable cause) {
    super(cause);
  }

}
