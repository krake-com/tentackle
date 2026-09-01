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
package org.tentackle.common;

import java.io.Serial;

/**
 * Wraps a checked {@link InterruptedException} into a {@link RuntimeException} and sets the interrupted flag.
 */
public class InterruptedRuntimeException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new interrupt exception with {@code null} as its detail message
   * and sets the interrupted flag.
   */
  public InterruptedRuntimeException() {
    super();
    Thread.currentThread().interrupt();
  }

  /**
   * Constructs a new interrupt exception with the specified detail message and
   * cause.
   *
   * @param message the detail message (null allowed)
   * @param cause   the cause (null allowed)
   */
  public InterruptedRuntimeException(String message, Throwable cause) {
    super(message, cause);
    Thread.currentThread().interrupt();
  }

  /**
   * Constructs a new interrupt exception with the specified cause
   *
   * @param cause the cause (null allowed)
   */
  public InterruptedRuntimeException(Throwable cause) {
    super(cause);
    Thread.currentThread().interrupt();
  }

  /**
   * Constructs a new interrupt exception with the specified detail
   * message, cause, suppression enabled or disabled, and writable
   * stack trace enabled or disabled.
   *
   * @param message            the detail message
   * @param cause              the cause (null allowed)
   * @param enableSuppression  whether suppression is enabled or disabled
   * @param writableStackTrace whether the stack trace should be writable
   */
  public InterruptedRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    Thread.currentThread().interrupt();
  }

}
