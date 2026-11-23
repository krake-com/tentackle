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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

  public static final String LOGIN_REFUSED = "login refused";

  private final List<AuthenticationProvider> providers;    // the provider chain, in evaluation order, immutable


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
   * <p>
   * The chain is copied and frozen: {@link #authenticate(Credentials)} iterates it on every login,
   * from every request thread, and must never see it change underneath.
   *
   * @param providers the providers, in any order
   */
  protected DefaultAuthenticationManager(List<AuthenticationProvider> providers) {
    List<AuthenticationProvider> chain = new ArrayList<>(providers);
    // we need a predictable evaluation order
    chain.sort(Comparator.comparingInt(AuthenticationProvider::getPriority)
                         .thenComparing(provider -> provider.getClass().getName()));
    this.providers = List.copyOf(chain);
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
    return authenticate(credentials, AuthenticationContext.NONE);
  }

  @Override
  public Authentication authenticate(Credentials credentials, AuthenticationContext context) {
    Objects.requireNonNull(context, "missing authentication context");
    if (credentials == null) {
      throw new AuthenticationException("no credentials presented");
    }

    Authentication authentication = null;
    AuthenticationProvider authenticatingProvider = null;

    for (AuthenticationProvider provider: providers) {
      if (provider.supports(credentials)) {
        authentication = provider.authenticate(credentials, context);   // may throw to veto the login
        if (authentication != null) {
          authenticatingProvider = provider;
          break;
        }
        LOGGER.fine("{0} abstained from authenticating {1} credentials", provider.getName(), credentials.getScheme());
      }
    }

    if (authentication == null) {
      // don't log the credentials, they may contain the secret
      LOGGER.info("no authentication provider accepted the {0} credentials", credentials.getScheme());
      throw new AuthenticationException(LOGIN_REFUSED);
    }

    if (authentication instanceof DefaultAuthentication defaultAuthentication &&
        defaultAuthentication.getProviderName() == null) {
      defaultAuthentication.setProviderName(authenticatingProvider.getName());
    }

    String name = authentication.getName();
    if (name == null || name.isBlank()) {
      // the session's username would otherwise remain the one claimed by the client
      LOGGER.warning("{0} authenticated the {1} credentials without a principal name",
                     authenticatingProvider.getName(), credentials.getScheme());
      throw new AuthenticationException(LOGIN_REFUSED);
    }

    resolveUser(authentication);

    return authentication;
  }


  /**
   * Determines the application's user for a proven identity.
   * <p>
   * Providers that know the application's user (such as a password provider that looked it up
   * anyway) set the ids themselves. For all others the application's {@link UserResolver} is applied.
   * <p>
   * The reason of a refusal is logged, but not told to the client: it would learn that its credentials
   * are valid.
   *
   * @param authentication the proven identity
   */
  protected void resolveUser(Authentication authentication) {
    if (authentication.getUserId() == 0) {
      UserResolver userResolver = UserResolver.getInstance();
      if (userResolver == null) {
        LOGGER.warning("{0} authenticated {1}, but the user is unknown and no UserResolver is configured",
                       authentication.getProviderName(), authentication.getName());
        throw new AuthenticationException(LOGIN_REFUSED);
      }
      userResolver.resolveUser(authentication);
      if (authentication.getUserId() == 0) {
        LOGGER.info("no user for principal {0} authenticated by {1}",
                    authentication.getName(), authentication.getProviderName());
        throw new AuthenticationException(LOGIN_REFUSED);
      }
    }
  }

}
