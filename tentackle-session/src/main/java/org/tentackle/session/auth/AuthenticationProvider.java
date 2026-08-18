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

/**
 * Verifies {@link Credentials} on the server side.
 * <p>
 * This is the pluggable half of Tentackle's authentication: an application server collects all
 * providers along the classpath and asks them, in turn, to authenticate the credentials the client
 * presented. Several providers may coexist, so single sign-on for humans and password logins for
 * batch or service accounts can be served by the same server.
 * <p>
 * Providers are registered via the service SPI and need a public no-arg constructor:
 * <pre>
 *    &#64;Service(AuthenticationProvider.class)
 *    public class MyOidcAuthenticationProvider implements AuthenticationProvider {
 *
 *      &#64;Override
 *      public boolean supports(Credentials credentials) {
 *        return credentials instanceof TokenCredentials;
 *      }
 *
 *      &#64;Override
 *      public Authentication authenticate(Credentials credentials) {
 *        // verify signature, issuer, audience and expiry, then:
 *        return new DefaultAuthentication(subject, credentials);
 *      }
 *    }
 * </pre>
 * A provider that verified the credentials but does not know the application's user leaves the
 * user id at zero and lets the {@link UserResolver} map the principal onto the application's
 * user entity.
 * <p>
 * Providers are instantiated once per server and must be thread-safe.
 *
 * @author harald
 * @see AuthenticationManager
 */
public interface AuthenticationProvider {

  /**
   * Returns whether this provider is responsible for the given credentials.
   *
   * @param credentials the credentials presented by the client, never null
   * @return true if {@link #authenticate(Credentials)} should be invoked
   */
  boolean supports(Credentials credentials);

  /**
   * Authenticates the credentials.
   * <p>
   * Returning {@code null} abstains and lets the next provider in the chain try. Throwing an
   * {@link AuthenticationException} vetoes the login and stops the chain — use it when the
   * credentials were meant for this provider but are invalid.
   *
   * @param credentials the credentials presented by the client, never null
   * @return the proven identity, null to abstain
   * @throws org.tentackle.session.LoginFailedException if the login must be refused
   */
  Authentication authenticate(Credentials credentials);

  /**
   * Gets the priority determining the position in the provider chain.<br>
   * Providers with a lower value are asked first. Providers of equal priority are ordered by
   * class name, so the chain is always deterministic.
   *
   * @return the priority, 0 by default
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Gets the name of this provider.<br>
   * Used for logging and to tell in an {@link Authentication} who authenticated it.
   *
   * @return the name, never null
   */
  default String getName() {
    return getClass().getSimpleName();
  }

}
