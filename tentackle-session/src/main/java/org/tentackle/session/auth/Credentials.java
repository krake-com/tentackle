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

import org.tentackle.misc.Secret;

import java.io.Serializable;

/**
 * The credentials presented by a client to prove its identity.
 * <p>
 * Credentials are attached to the {@link org.tentackle.session.SessionInfo} and travel to the
 * application server via TRIP, where the {@link AuthenticationManager} hands them to the
 * {@link AuthenticationProvider}s that {@link AuthenticationProvider#supports(Credentials) support}
 * them.
 * <p>
 * Tentackle ships {@link PasswordCredentials} (the traditional username/password login) and
 * {@link TokenCredentials} (bearer tokens, such as OAuth2 access tokens or OIDC ID tokens).
 * Applications may add their own, as long as they are serializable and their package is opened
 * to {@code org.tentackle.core} for TRIP.
 * <p>
 * Implementations must keep confidential parts in a {@link Secret}, so that they are encrypted
 * in memory whenever a {@link org.tentackle.common.Cryptor} is configured.
 *
 * @author harald
 */
public interface Credentials extends Serializable {

  /**
   * Gets the name of the authentication scheme these credentials belong to.
   * <p>
   * The scheme is what an {@link AuthenticationProvider} and a {@link CredentialsProvider}
   * agree upon, for example {@value PasswordCredentials#SCHEME} or {@value TokenCredentials#SCHEME}.
   *
   * @return the scheme, never null
   */
  String getScheme();

  /**
   * Clears all confidential parts.<br>
   * Will remove them from memory by overwriting the corresponding arrays.
   */
  void clear();

  /**
   * Clones the credentials.<br>
   * Confidential parts are physically copied, since they may be cleared later.
   *
   * @return the cloned credentials
   */
  Credentials clone();

}
