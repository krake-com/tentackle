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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;

import java.io.Serial;
import java.util.UUID;

/**
 * Trip exception if a remote delegate is not registered.
 */
public class TripNoSuchDelegateException extends TripRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final UUID delegateId;

  /**
   * Constructs a new trip exception.
   *
   * @param message the detail message
   * @param delegateId the ID of the delegate that was not found
   */
  @CanonicalConstructor
  public TripNoSuchDelegateException(@CanonicalName("message") String message,
                                     @CanonicalName("delegateId") UUID delegateId) {
    super(message);
    this.delegateId = delegateId;
  }

  /**
   * Constructs a new trip exception.
   *
   * @param delegateId the ID of the delegate that was not found
   */
  public TripNoSuchDelegateException(UUID delegateId) {
    this("No remote delegate registered for ID " + delegateId, delegateId);
  }

  /**
   * Gets the ID of the delegate that was not found.
   *
   * @return the delegate ID
   */
  public UUID getDelegateId() {
    return delegateId;
  }

  @TripIgnore
  @Override
  public Throwable getCause() {
    return super.getCause();
  }

}
