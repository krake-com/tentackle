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

package org.tentackle.quiche.engine;

import org.tentackle.quiche.ffm.QuicheException;

import java.io.Serial;

/**
 * Thrown when no further stream can be opened because the peer's limit is reached.
 * <p>
 * Distinguished from an ordinary failure because it is temporary: closing one of the streams already open
 * makes room for another one, so the caller may well succeed on a later attempt.
 */
public class StreamLimitException extends QuicheException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with {@code null} as its detail message.
   */
  public StreamLimitException() {
    super();
  }

  /**
   * Creates an exception.
   *
   * @param message the message
   */
  public StreamLimitException(String message) {
    super(message);
  }

}
