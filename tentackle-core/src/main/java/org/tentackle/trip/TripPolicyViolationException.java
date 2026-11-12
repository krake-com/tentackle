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

import java.io.Serial;

/**
 * Thrown when a peer that is not authenticated yet violates the {@link PreAuthenticationPolicy}.
 * <p>
 * The server closes the connection, since the rest of the message cannot be trusted.
 */
public class TripPolicyViolationException extends TripRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new policy violation exception.
   *
   * @param message the detail message
   */
  public TripPolicyViolationException(String message) {
    super(message);
  }

}
