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

import org.tentackle.common.Constants;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.session.SessionInfo;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holder for the credentials providers, by method name.
 */
interface CredentialsProviderHolder {

  @SuppressWarnings("unchecked")
  private static Map<String, CredentialsProvider> loadProviders() {
    Map<String, CredentialsProvider> providers = new HashMap<>();
    for (Map.Entry<String, String> entry: ServiceFactory.getServiceFinder()
                                                        .createNameMap(CredentialsProvider.class.getName()).entrySet()) {
      String method = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      try {
        Class<CredentialsProvider> providerClass = (Class<CredentialsProvider>) Class.forName(entry.getValue());
        providers.put(method, providerClass.getDeclaredConstructor().newInstance());
      }
      catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException ex) {
        throw new TentackleRuntimeException("cannot create credentials provider for method " + method, ex);
      }
    }
    return providers;
  }

  Map<String, CredentialsProvider> INSTANCES = loadProviders();
}


/**
 * Obtains the {@link Credentials} on the client side, before the session is opened.
 * <p>
 * This is the client-side counterpart of the {@link AuthenticationProvider}. For the built-in
 * username/password login there is nothing to obtain — the login dialog collected it already — but a
 * single sign-on method needs to run a whole protocol first: open the identity provider's
 * authorization endpoint in a browser, wait for the redirect, exchange the code for a token. That
 * happens here, and the resulting credentials are then attached to the {@link SessionInfo} and
 * travel to the server.
 * <p>
 * Providers are registered by the name of the authentication method and need a public no-arg
 * constructor:
 * <pre>
 *    &#64;CredentialsProviderService("oidc")
 *    public class MyOidcCredentialsProvider implements CredentialsProvider {
 *
 *      &#64;Override
 *      public boolean isInteractive() {
 *        return true;
 *      }
 *
 *      &#64;Override
 *      public Credentials createCredentials(SessionInfo sessionInfo, CredentialsPrompt prompt) {
 *        prompt.browse(authorizationEndpoint);
 *        ...
 *        return new TokenCredentials(accessToken);
 *      }
 *    }
 * </pre>
 * The method to use is configured by the {@value Constants#BACKEND_AUTHENTICATION} key in
 * {@code backend.properties} and defaults to {@value Constants#BACKEND_PASSWORD}.
 *
 * @author harald
 * @see AuthenticationProvider
 */
public interface CredentialsProvider {

  /**
   * Gets the credentials provider for an authentication method.
   *
   * @param method the name of the method, null for {@value Constants#BACKEND_PASSWORD}
   * @return the provider, never null
   * @throws TentackleRuntimeException if there is no provider for that method
   */
  static CredentialsProvider getInstance(String method) {
    String name = method == null ? Constants.BACKEND_PASSWORD : method;
    CredentialsProvider provider = CredentialsProviderHolder.INSTANCES.get(name);
    if (provider == null) {
      throw new TentackleRuntimeException("no credentials provider for authentication method '" + name + "'");
    }
    return provider;
  }

  /**
   * Gets the credentials provider configured for a session.<br>
   * Determined by the {@value Constants#BACKEND_AUTHENTICATION} key of the session's properties.
   *
   * @param sessionInfo the session info
   * @return the provider, never null
   */
  static CredentialsProvider getInstance(SessionInfo sessionInfo) {
    return getInstance(sessionInfo.getProperties().getPropertyIgnoreCase(Constants.BACKEND_AUTHENTICATION));
  }

  /**
   * Gets the names of all configured authentication methods.
   *
   * @return the method names, never null
   */
  static Set<String> getMethods() {
    return CredentialsProviderHolder.INSTANCES.keySet();
  }

  /**
   * Creates the credentials to log in with.
   *
   * @param sessionInfo the session info being logged in with
   * @param prompt the user interaction, never null
   * @return the credentials, null if the user cancelled
   */
  Credentials createCredentials(SessionInfo sessionInfo, CredentialsPrompt prompt);

  /**
   * Returns whether obtaining the credentials requires an interaction with the user.
   * <p>
   * Interactive providers replace the username and password fields of the login dialog by a
   * single sign-on button and run {@link #createCredentials(SessionInfo, CredentialsPrompt)}
   * in the background.
   *
   * @return true if interactive, false by default
   */
  default boolean isInteractive() {
    return false;
  }

  /**
   * Renews expired credentials.
   * <p>
   * Invoked when the server refused the login with a {@link CredentialsExpiredException}, so that
   * a token can be refreshed without asking the user again.
   * <p>
   * The default implementation returns null, meaning "cannot renew, start over".
   *
   * @param credentials the expired credentials
   * @return the renewed credentials, null if they cannot be renewed
   */
  default Credentials refresh(Credentials credentials) {
    return null;
  }

}
