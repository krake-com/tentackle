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

package org.tentackle.persist.server;

import org.tentackle.common.Service;
import org.tentackle.session.auth.Authentication;
import org.tentackle.session.auth.AuthenticationException;
import org.tentackle.session.auth.AuthenticationProvider;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.DefaultAuthentication;
import org.tentackle.session.auth.TokenCredentials;

import java.util.Arrays;

/**
 * Stands in for a single sign-on provider.
 * <p>
 * Accepts exactly one token, so that {@link org.tentackle.persist.RemoteAuthenticationTest} can
 * check both the accepted and the refused login. Since it knows the user, no {@code UserResolver}
 * is needed.
 * <p>
 * Registered via {@code @Service}: the {@code test-analyze} goal of the tentackle-maven-plugin
 * generates the service descriptor into {@code target/generated-test-resources}, which is
 * a test resource directory of this module.
 *
 * @author harald
 */
@Service(AuthenticationProvider.class)
public class TestAuthenticationProvider implements AuthenticationProvider {

  /** the only token this provider accepts. */
  public static final char[] VALID_TOKEN = "valid-token".toCharArray();

  /** the name of the principal the valid token belongs to. */
  public static final String USER_NAME = "tokenuser";

  /** the object id of the principal the valid token belongs to. */
  public static final long USER_ID = 815L;

  /** the class id of the principal the valid token belongs to. */
  public static final int USER_CLASS_ID = 4711;


  /**
   * Creates the provider.
   */
  public TestAuthenticationProvider() {
    // services need a public no-arg constructor
  }


  @Override
  public boolean supports(Credentials credentials) {
    return credentials instanceof TokenCredentials;
  }

  @Override
  public Authentication authenticate(Credentials credentials) {
    if (!Arrays.equals(((TokenCredentials) credentials).getToken(), VALID_TOKEN)) {
      throw new AuthenticationException("invalid token");
    }
    Authentication authentication = new DefaultAuthentication(USER_NAME, credentials);
    authentication.setUserId(USER_ID);
    authentication.setUserClassId(USER_CLASS_ID);
    return authentication;
  }

}
