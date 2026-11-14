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

import org.tentackle.common.ServiceFactory;

import java.util.List;

/**
 * Holder for the authentication manager singleton.
 */
interface AuthenticationManagerHolder {
  AuthenticationManager INSTANCE = ServiceFactory.createService(AuthenticationManager.class,
                                                                DefaultAuthenticationManager.class);
}


/**
 * Drives the chain of {@link AuthenticationProvider}s.
 * <p>
 * The application server invokes it once per login, from
 * {@code RemoteDbSessionImpl.verifySessionInfo(SessionInfo)}, with the credentials the client
 * presented. The manager picks the providers that {@link AuthenticationProvider#supports(Credentials)
 * support} the credentials, asks them in order, and returns the first proven identity. If the
 * identity does not name a user of this application yet, the optional {@link UserResolver} is applied.
 * <p>
 * Authentication is <em>deny by default</em>: if no provider is configured, or none of them
 * authenticates the credentials, a {@link org.tentackle.session.LoginFailedException} is thrown.
 *
 * @author harald
 * @see AuthenticationProvider
 */
public interface AuthenticationManager {

  /**
   * Gets the authentication manager singleton.
   *
   * @return the manager, never null
   */
  static AuthenticationManager getInstance() {
    return AuthenticationManagerHolder.INSTANCE;
  }

  /**
   * Authenticates the credentials presented by a client.<br>
   * Same as {@link #authenticate(Credentials, AuthenticationContext)} with an empty context.
   *
   * @param credentials the credentials, null if the client presented none
   * @return the proven identity, never null
   * @throws org.tentackle.session.LoginFailedException if the login must be refused
   */
  Authentication authenticate(Credentials credentials);

  /**
   * Authenticates the credentials presented by a client within the context of its connection.
   * <p>
   * The context is handed to the providers, see
   * {@link AuthenticationProvider#authenticate(Credentials, AuthenticationContext)}.
   * The default implementation ignores the context and invokes {@link #authenticate(Credentials)},
   * so that managers written before the context existed keep working.
   *
   * @param credentials the credentials, null if the client presented none
   * @param context the context of the client's connection, never null
   * @return the proven identity, never null
   * @throws org.tentackle.session.LoginFailedException if the login must be refused
   */
  default Authentication authenticate(Credentials credentials, AuthenticationContext context) {
    return authenticate(credentials);
  }

  /**
   * Gets the provider chain in the order it is asked.
   * <p>
   * The chain is fixed once the manager is created, since {@link #authenticate(Credentials)}
   * iterates it concurrently. Implementations must therefore not hand out a list a caller
   * could modify.
   *
   * @return the providers, never null, unmodifiable
   */
  List<AuthenticationProvider> getProviders();

}
