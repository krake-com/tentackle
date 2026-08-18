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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * The proven identity of a client, i.e., the outcome of a successful authentication.
 * <p>
 * An {@link AuthenticationProvider} creates it from the {@link Credentials} it verified.
 * If the provider knows the application's user (as the built-in password provider does), it sets
 * {@link #setUserId(long)} and {@link #setUserClassId(int)} right away. Otherwise, the usual case for
 * a generic single sign-on provider, which only knows the subject of a token — it leaves them at zero
 * and the application's {@link UserResolver} fills them in.
 *
 * @author harald
 */
public interface Authentication extends Serializable {

  /**
   * Gets the name of the authenticated principal.
   * <p>
   * The username for a password login, the token's subject, or preferred username for a token login.
   * It is copied to the session info after a successful login, so it is the name the application
   * and its audit trail will see.
   *
   * @return the principal's name, null if unknown
   */
  String getName();

  /**
   * Gets the credentials that were verified.
   *
   * @return the credentials, never null
   */
  Credentials getCredentials();

  /**
   * Gets the name of the provider that authenticated the credentials.
   *
   * @return the provider name, null if unknown
   */
  String getProviderName();

  /**
   * Gets the attributes describing the principal.
   * <p>
   * For a token login these are typically the verified claims. The map is a convenient place for a
   * {@link UserResolver} to look up the application's user, and for the application's
   * {@code SecurityManager} to derive additional grantees.
   *
   * @return the attributes, never null
   */
  Map<String, Object> getAttributes();

  /**
   * Gets the roles or groups asserted by the identity provider.
   * <p>
   * These are the <em>external</em> roles, not Tentackle's security rules. It is up to the
   * application to map them onto its own grantees.
   *
   * @return the roles, never null
   */
  Set<String> getRoles();

  /**
   * Gets the epochal time this authentication expires.
   *
   * @return the expiry in epochal millis, 0 if it does not expire
   */
  long getExpiresAt();

  /**
   * Gets the object id of the authenticated user.
   *
   * @return the user id, 0 if not resolved yet
   */
  long getUserId();

  /**
   * Sets the object id of the authenticated user.
   *
   * @param userId the user id
   */
  void setUserId(long userId);

  /**
   * Gets the class id of the authenticated user.
   *
   * @return the user class id, 0 if not resolved yet
   */
  int getUserClassId();

  /**
   * Sets the class id of the authenticated user.
   *
   * @param userClassId the user class id
   */
  void setUserClassId(int userClassId);

}
