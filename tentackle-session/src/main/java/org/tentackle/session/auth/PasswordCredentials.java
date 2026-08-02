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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;

/**
 * The traditional username and password credentials.
 * <p>
 * This is what a {@link org.tentackle.session.SessionInfo} carries unless the application
 * configures another authentication method, and it is what
 * {@link org.tentackle.session.SessionInfo#setPassword(char[])} creates behind the scenes.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class PasswordCredentials implements Credentials, Cloneable {

  /** the scheme name of password credentials. */
  public static final String SCHEME = "password";

  @Serial
  private static final long serialVersionUID = 1L;

  private String userName;      // the name of the user
  private Secret password;      // the user's password


  /**
   * Creates password credentials.
   *
   * @param userName the username, null if none
   * @param password the password, null if none
   */
  public PasswordCredentials(String userName, char[] password) {
    this.userName = userName;
    this.password = new Secret(password);
  }

  /**
   * Creates empty password credentials.<br>
   * Also required by TRIP to deserialize the credentials on the server.
   */
  public PasswordCredentials() {
    this(null, null);
  }


  @Override
  public String getScheme() {
    return SCHEME;
  }

  /**
   * Gets the username.
   *
   * @return the username, null if none
   */
  public String getUserName() {
    return userName;
  }

  /**
   * Sets the username.
   *
   * @param userName the username, null if none
   */
  public void setUserName(String userName) {
    this.userName = userName;
  }

  /**
   * Gets the password.
   *
   * @return the password, null if none or cleared
   */
  public char[] getPassword() {
    return password.get();
  }

  /**
   * Sets the password.
   *
   * @param password the password, null if none
   */
  public void setPassword(char[] password) {
    this.password.set(password);
  }

  /**
   * Returns whether a password is set.
   *
   * @return true if set
   */
  public boolean isPasswordSet() {
    return password.isSet();
  }

  /**
   * Returns whether the password has been cleared.
   *
   * @return true if cleared
   */
  public boolean isPasswordCleared() {
    return password.isCleared();
  }

  @Override
  public void clear() {
    password.clear();
  }

  @Override
  public PasswordCredentials clone() {
    PasswordCredentials credentials;
    try {
      credentials = (PasswordCredentials) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
    credentials.password = password.clone();
    return credentials;
  }

  @Override
  public String toString() {
    return userName + '/' + password;
  }

}
