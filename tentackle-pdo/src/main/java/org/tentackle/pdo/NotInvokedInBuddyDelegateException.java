/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.pdo;

import org.tentackle.common.TentackleRuntimeException;

/**
 * Exception thrown when an implementation is not invoked in buddy delegate.<br>
 * Should be used in unused method body to avoid direct invocation without interceptor.
 *
 * @author harald
 */
public class NotInvokedInBuddyDelegateException extends TentackleRuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an exception with a default message.
   */
  public NotInvokedInBuddyDelegateException() {
    super("method must be invoked in buddy delegate");
  }

  /**
   * Creates an exception.
   *
   * @param message the message
   */
  NotInvokedInBuddyDelegateException(String message) {
    super(message);
  }

}
