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

package org.tentackle.dbms.trip;

import org.tentackle.session.SessionClosedException;

import java.io.Serial;

/**
 * TRIP servers should throw a RemoteSessionClosedException if the remote session is already closed.
 *
 * @author harald
 */
public class RemoteSessionClosedException extends SessionClosedException {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Constructs a new session closed exception without a session and
   * with <code>null</code> as its detail message.
   */
  public RemoteSessionClosedException() {
    super();
  }


  /**
   * Constructs a new session closed exception without a session and with the specified detail message.
   *
   * @param   message   the detail message.
   */
  public RemoteSessionClosedException(String message) {
    super(message);
  }


  /**
   * Constructs a new session closed exception without a session and with the specified detail message and
   * cause.
   *
   * @param  message the detail message
   * @param  cause the cause
   */
  public RemoteSessionClosedException(String message, Throwable cause) {
    super(message, cause);
  }

}
