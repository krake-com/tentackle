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

import org.tentackle.session.LoginFailedException;

import java.io.Serial;

/**
 * Thrown by an {@link AuthenticationProvider} that vetoes the credentials it was given.
 * <p>
 * The {@link AuthenticationManager} throws it as well, if no provider authenticated the credentials,
 * or if the user could not be resolved because no {@link UserResolver} is configured.
 * <p>
 * It extends {@link LoginFailedException} so that clients handling a failed login — most notably
 * the desktop client's {@code LoginFailedHandler} — treat it like any other refused login.
 * <p>
 * A provider that simply is not responsible for the credentials must <em>not</em> throw: it returns
 * {@code null} to abstain and let the next provider in the chain try.
 *
 * @author harald
 */
public class AuthenticationException extends LoginFailedException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new authentication exception with the specified detail message.
   *
   * @param message the detail message
   */
  public AuthenticationException(String message) {
    super(message);
  }

  /**
   * Constructs a new authentication exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new authentication exception.<br>
   * No-args constructor mainly for serialization.
   */
  public AuthenticationException() {
    super();
  }

}
