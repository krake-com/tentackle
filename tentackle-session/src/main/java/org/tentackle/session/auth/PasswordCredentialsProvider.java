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

import org.tentackle.session.SessionInfo;

/**
 * The built-in username/password credentials provider.
 * <p>
 * It is not interactive: the username and the password have already been collected by the login
 * dialog, the command line, {@code backend.properties} or the process environment, so there is
 * nothing left to obtain.
 *
 * @author harald
 */
@CredentialsProviderService(PasswordCredentials.SCHEME)
public class PasswordCredentialsProvider implements CredentialsProvider {

  /**
   * Creates the provider.
   */
  public PasswordCredentialsProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Credentials createCredentials(SessionInfo sessionInfo, CredentialsPrompt prompt) {
    return sessionInfo.getCredentials();
  }

}
