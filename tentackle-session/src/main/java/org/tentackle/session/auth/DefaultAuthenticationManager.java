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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.session.LoginFailedException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The default authentication manager.
 * <p>
 * Collects all {@link AuthenticationProvider}s along the classpath and orders them by
 * {@link AuthenticationProvider#getPriority() priority}, then by class name, so that the chain is
 * deterministic regardless of the order in which the modules happen to be discovered.
 *
 * @author harald
 */
@Service(AuthenticationManager.class)
public class DefaultAuthenticationManager implements AuthenticationManager {

  private static final Logger LOGGER = Logger.get(DefaultAuthenticationManager.class);

  private final List<AuthenticationProvider> providers;    // the provider chain, in evaluation order


  /**
   * Creates an authentication manager.<br>
   * Collects all AuthenticationProvider services.
   */
  public DefaultAuthenticationManager() {
    this(discoverProviders());
  }

  /**
   * Creates an authentication manager for an explicit chain.<br>
   * Useful for applications that wire their providers themselves, and for tests.
   *
   * @param providers the providers, in any order
   */
  protected DefaultAuthenticationManager(List<AuthenticationProvider> providers) {
    this.providers = new ArrayList<>(providers);
    // we need a predictable evaluation order
    this.providers.sort(Comparator.comparingInt(AuthenticationProvider::getPriority)
                                  .thenComparing(provider -> provider.getClass().getName()));
    if (this.providers.isEmpty()) {
      LOGGER.warning("no authentication provider configured: all logins will be refused");
    }
    else {
      LOGGER.info("authentication providers: {0}", this.providers.stream().map(AuthenticationProvider::getName).toList());
    }
  }


  /**
   * Collects all authentication providers along the classpath.
   *
   * @return the providers, never null
   */
  protected static List<AuthenticationProvider> discoverProviders() {
    List<AuthenticationProvider> providers = new ArrayList<>();
    try {
      for (Class<AuthenticationProvider> providerClass:
           ServiceFactory.getServiceFinder().findServiceProviders(AuthenticationProvider.class)) {
        providers.add(providerClass.getDeclaredConstructor().newInstance());
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           InvocationTargetException | NoSuchMethodException ex) {
      throw new TentackleRuntimeException("authentication providers could not be determined", ex);
    }
    return providers;
  }


  @Override
  public List<AuthenticationProvider> getProviders() {
    return providers;
  }

  @Override
  public Authentication authenticate(Credentials credentials) {
    if (credentials == null) {
      throw new AuthenticationException("no credentials presented");
    }

    Authentication authentication = null;
    AuthenticationProvider authenticatingProvider = null;

    for (AuthenticationProvider provider: providers) {
      if (provider.supports(credentials)) {
        authentication = provider.authenticate(credentials);   // may throw to veto the login
        if (authentication != null) {
          authenticatingProvider = provider;
          break;
        }
        LOGGER.fine("{0} abstained from authenticating {1} credentials", provider.getName(), credentials.getScheme());
      }
    }

    if (authentication == null) {
      // don't log the credentials, they may contain the secret
      throw new AuthenticationException("no authentication provider accepted the " +
                                        credentials.getScheme() + " credentials");
    }

    if (authentication instanceof DefaultAuthentication defaultAuthentication &&
        defaultAuthentication.getProviderName() == null) {
      defaultAuthentication.setProviderName(authenticatingProvider.getName());
    }

    resolveUser(authentication);

    return authentication;
  }


  /**
   * Determines the application's user for a proven identity.
   * <p>
   * Providers that know the application's user (such as a password provider that looked it up
   * anyway) set the ids themselves. For all others the application's {@link UserResolver} is applied.
   *
   * @param authentication the proven identity
   */
  protected void resolveUser(Authentication authentication) {
    if (authentication.getUserId() == 0) {
      UserResolver userResolver = UserResolver.getInstance();
      if (userResolver == null) {
        throw new AuthenticationException(
            "authenticated by " + authentication.getProviderName() +
            ", but the user is unknown and no UserResolver is configured");
      }
      userResolver.resolveUser(authentication);
      if (authentication.getUserId() == 0) {
        throw new LoginFailedException("no user for authenticated principal");
      }
    }
  }

}
