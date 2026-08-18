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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the authentication provider chain.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DefaultAuthenticationManagerTest {

  private static final long USER_ID = 4711L;
  private static final int USER_CLASS_ID = 1001;


  /**
   * A provider that is asked in a recorded order and may accept, abstain or veto.
   */
  private static class TestProvider implements AuthenticationProvider {

    private final String name;
    private final int priority;
    private final boolean supported;
    private final Authentication result;      // null to abstain
    private final RuntimeException veto;      // != null to veto
    private final List<String> asked;         // records who was asked

    TestProvider(String name, int priority, boolean supported, Authentication result,
                 RuntimeException veto, List<String> asked) {
      this.name = name;
      this.priority = priority;
      this.supported = supported;
      this.result = result;
      this.veto = veto;
      this.asked = asked;
    }

    @Override
    public boolean supports(Credentials credentials) {
      return supported;
    }

    @Override
    public Authentication authenticate(Credentials credentials) {
      asked.add(name);
      if (veto != null) {
        throw veto;
      }
      return result;
    }

    @Override
    public int getPriority() {
      return priority;
    }

    @Override
    public String getName() {
      return name;
    }
  }


  private static Credentials credentials() {
    return new PasswordCredentials("hugo", "secret".toCharArray());
  }

  private static Authentication authentication(Credentials credentials) {
    Authentication authentication = new DefaultAuthentication("hugo", credentials);
    authentication.setUserId(USER_ID);
    authentication.setUserClassId(USER_CLASS_ID);
    return authentication;
  }

  private static AuthenticationManager manager(AuthenticationProvider... providers) {
    return new DefaultAuthenticationManager(List.of(providers));
  }


  @Test
  public void testProvidersAreOrderedByPriority() {
    List<String> asked = new ArrayList<>();
    Credentials credentials = credentials();
    AuthenticationManager manager = manager(
        new TestProvider("last", 10, true, null, null, asked),
        new TestProvider("first", -10, true, null, null, asked),
        new TestProvider("middle", 0, true, authentication(credentials), null, asked));

    Assert.assertEquals(manager.getProviders().stream().map(AuthenticationProvider::getName).toList(),
                        List.of("first", "middle", "last"));
    manager.authenticate(credentials);
    // "last" is never asked, since "middle" already authenticated
    Assert.assertEquals(asked, List.of("first", "middle"));
  }

  @Test
  public void testProviderChainCannotBeModified() {
    AuthenticationProvider extra = new TestProvider("extra", 0, true, null, null, new ArrayList<>());
    AuthenticationManager manager = manager(
        new TestProvider("only", 0, true, null, null, new ArrayList<>()));

    // authenticate() iterates the chain concurrently: a caller must not be able to change it
    Assert.assertThrows(UnsupportedOperationException.class, () -> manager.getProviders().add(extra));
    Assert.assertThrows(UnsupportedOperationException.class, () -> manager.getProviders().clear());
    Assert.assertEquals(manager.getProviders().size(), 1);
  }

  @Test
  public void testProviderChainIsDetachedFromTheGivenList() {
    List<AuthenticationProvider> given = new ArrayList<>();
    given.add(new TestProvider("only", 0, true, null, null, new ArrayList<>()));
    AuthenticationManager manager = new DefaultAuthenticationManager(given);

    given.clear();    // must not disarm the manager

    Assert.assertEquals(manager.getProviders().size(), 1);
  }

  @Test
  public void testUnsupportedProvidersAreNotAsked() {
    List<String> asked = new ArrayList<>();
    Credentials credentials = credentials();
    AuthenticationManager manager = manager(
        new TestProvider("unsupported", 0, false, authentication(credentials), null, asked),
        new TestProvider("supported", 1, true, authentication(credentials), null, asked));

    manager.authenticate(credentials);
    Assert.assertEquals(asked, List.of("supported"));
  }

  @Test
  public void testAuthenticationCarriesTheUser() {
    Credentials credentials = credentials();
    Authentication authentication = manager(
        new TestProvider("password", 0, true, authentication(credentials), null, new ArrayList<>()))
        .authenticate(credentials);

    Assert.assertEquals(authentication.getName(), "hugo");
    Assert.assertEquals(authentication.getUserId(), USER_ID);
    Assert.assertEquals(authentication.getUserClassId(), USER_CLASS_ID);
    Assert.assertEquals(authentication.getProviderName(), "password");
    Assert.assertSame(authentication.getCredentials(), credentials);
  }

  @Test(expectedExceptions = LoginFailedException.class)
  public void testEmptyChainRefusesTheLogin() {
    manager().authenticate(credentials());
  }

  @Test(expectedExceptions = LoginFailedException.class)
  public void testAbstainingChainRefusesTheLogin() {
    manager(new TestProvider("abstains", 0, true, null, null, new ArrayList<>())).authenticate(credentials());
  }

  @Test(expectedExceptions = LoginFailedException.class)
  public void testMissingCredentialsRefuseTheLogin() {
    manager(new TestProvider("any", 0, true, authentication(credentials()), null, new ArrayList<>())).authenticate(null);
  }

  @Test
  public void testVetoStopsTheChain() {
    List<String> asked = new ArrayList<>();
    Credentials credentials = credentials();
    AuthenticationManager manager = manager(
        new TestProvider("vetoes", 0, true, null, new CredentialsExpiredException("expired"), asked),
        new TestProvider("never asked", 1, true, authentication(credentials), null, asked));

    Assert.assertThrows(CredentialsExpiredException.class, () -> manager.authenticate(credentials));
    Assert.assertEquals(asked, List.of("vetoes"));
  }

  @Test(expectedExceptions = LoginFailedException.class)
  public void testUnresolvedUserRefusesTheLogin() {
    // no UserResolver is configured in this module, so an authentication without a user id cannot succeed
    Credentials credentials = credentials();
    manager(new TestProvider("no user", 0, true, new DefaultAuthentication("hugo", credentials), null, new ArrayList<>()))
        .authenticate(credentials);
  }

  @Test
  public void testUserResolverFillsInTheUser() {
    Credentials credentials = credentials();
    AuthenticationManager manager = new DefaultAuthenticationManager(
        List.of(new TestProvider("token", 0, true, new DefaultAuthentication("hugo", credentials), null, new ArrayList<>()))) {
      @Override
      protected void resolveUser(Authentication authentication) {
        authentication.setUserId(USER_ID);
        authentication.setUserClassId(USER_CLASS_ID);
      }
    };

    Authentication authentication = manager.authenticate(credentials);
    Assert.assertEquals(authentication.getUserId(), USER_ID);
    Assert.assertEquals(authentication.getUserClassId(), USER_CLASS_ID);
  }

  @Test
  public void testManagerIsProvidedByTheServiceApi() {
    Assert.assertTrue(AuthenticationManager.getInstance() instanceof DefaultAuthenticationManager);
  }

}
