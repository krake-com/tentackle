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

package org.tentackle.session.auth;

import java.io.Serial;

/**
 * Thrown when the credentials were valid but have expired.
 * <p>
 * This is the signal for the client to obtain fresh credentials via
 * {@link CredentialsProvider#refresh(Credentials)} and to retry the login, rather than to prompt
 * the user again. The desktop client's {@code LoginFailedHandler} does exactly that, and such a
 * retry does not count against the number of login attempts.
 *
 * @author harald
 */
public class CredentialsExpiredException extends AuthenticationException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new credentials expired exception with the specified detail message.
   *
   * @param message the detail message
   */
  public CredentialsExpiredException(String message) {
    super(message);
  }

  /**
   * Constructs a new credentials expired exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public CredentialsExpiredException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new credentials expired exception.<br>
   * No-args constructor mainly for serialization.
   */
  public CredentialsExpiredException() {
    super();
  }

}
