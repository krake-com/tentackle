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

package org.tentackle.session;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.misc.Immutable;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.PasswordCredentials;

import java.io.Serializable;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Session information.
 * <p>
 * Describes <em>who</em> and <em>what</em> a session represents: the user, the {@link Credentials}
 * proving the user's identity, the application, the client's environment, and the connection
 * properties. A remote session sends its session info to the server at login, and the server
 * writes back what it determined, such as the user id.
 * <p>
 * A session info is mutable until it is made {@link Immutable immutable}, as server applications do
 * with their own session info. Modifying an immutable session info throws an
 * {@link org.tentackle.misc.ImmutableException}, or just logs it if an immutable logging level is set.
 * <p>
 * Implementations must define {@code equals} and {@code hashCode} by the username alone, so that
 * every session info of a user -- the original, its clones, and one created solely to address that
 * user -- is interchangeable as a key. A {@link MultiUserSessionPool} depends on it to map a user
 * to its sessions.
 *
 * @author harald
 */
public interface SessionInfo extends Immutable, Serializable {

  /**
   * Gets the user id.
   *
   * @return the object ID of the current user
   */
  long getUserId();

  /**
   * Sets the user id.
   *
   * @param userId the object ID of the user
   */
  void setUserId(long userId);

  /**
   * Gets the class id of the current user.
   *
   * @return the class id
   */
  int getUserClassId();

  /**
   * Sets the class id of the current user.
   *
   * @param userClassId the class id
   */
  void setUserClassId(int userClassId);

  /**
   * Gets the username.
   *
   * @return the username
   */
  String getUserName();

  /**
   * Sets the username.
   *
   * @param userName the name of the user
   */
  void setUserName(String userName);

  /**
   * Gets the password.
   *
   * @return the password, null if the credentials are not password-based
   */
  char[] getPassword();

  /**
   * Sets the password.<br>
   * Replaces the current credentials by {@link PasswordCredentials}.
   *
   * @param password the password
   */
  void setPassword(char[] password);

  /**
   * Clears the confidential parts of the credentials.<br>
   * Removes them from memory by overwriting the corresponding arrays.
   * Despite its name, this applies to any kind of credentials, not only passwords.
   *
   * @see Credentials#clear()
   */
  void clearPassword();

  /**
   * Gets the credentials to authenticate with.
   * <p>
   * They are transferred to the application server at login, where the
   * {@link org.tentackle.session.auth.AuthenticationManager} hands them to the configured
   * {@link org.tentackle.session.auth.AuthenticationProvider}s.
   *
   * @return the credentials, never null
   */
  Credentials getCredentials();

  /**
   * Sets the credentials to authenticate with.
   * <p>
   * This is how a client logs in with something other than a username and a password, for
   * example, with a token obtained from an identity provider.
   *
   * @param credentials the credentials, null to reset to empty {@link PasswordCredentials}
   */
  void setCredentials(Credentials credentials);

  /**
   * Gets the epochal time since when logged in.
   *
   * @return logged in since, 0 if not logged in
   */
  long getSince();

  /**
   * Sets the epochal time since when logged in.
   *
   * @param since logged in since, 0 if not logged in
   */
  void setSince(long since);

  /**
   * Gets the name of the application.
   *
   * @return the name, null if none
   */
  String getApplicationName();

  /**
   * Sets the name of the application.
   *
   * @param name the name
   */
  void setApplicationName(String name);

  /**
   * Sets the application id.<br>
   * Should be unique among the same application name.
   *
   * @param applicationId the optional application id
   */
  void setApplicationId(long applicationId);

  /**
   * Returns the application id.
   *
   * @return the id, 0 if none
   */
  long getApplicationId();

  /**
   * Sets an optional session name.<br>
   * May be used to describe the purpose of the session.
   *
   * @param sessionName the session name, null if none
   */
  void setSessionName(String sessionName);

  /**
   * Gets the optional session name.
   *
   * @return the name, null if none
   */
  String getSessionName();

  /**
   * Clones a session-info.<br>
   * The credentials are physically copied, so clearing the original does not clear the clone.
   * The cloned session info is mutable, flagged as {@link #isCloned() cloned} and has
   * {@link #isLockLingerEnabled() lock linger} enabled.
   *
   * @return the cloned session info
   */
  SessionInfo clone();

  /**
   * Clears the cloned flag.<br>
   * Useful if the session-info should no longer be treated as cloned.
   */
  void clearCloned();

  /**
   * Checks whether this session info is cloned.
   *
   * @return true if cloned
   */
  boolean isCloned();

  /**
   * Sets the client's version.
   *
   * @param clientVersion the client version
   */
  void setClientVersion(String clientVersion);

  /**
   * Gets the client version.
   *
   * @return the client version
   */
  String getClientVersion();

  /**
   * Sets the connection properties.
   *
   * @param properties the connection properties.
   */
  void setProperties(EncryptedProperties properties);

  /**
   * Gets the connection properties.<br>
   * If the properties are not set so far, they are loaded for {@link #getPropertiesName()} via
   * {@link org.tentackle.misc.PropertiesUtilities#load(String)}, which applies all
   * {@link org.tentackle.misc.PropertiesResource}s in the order of their priority (by default,
   * a properties file and a resource from the classpath or module path).<br>
   * If nothing is found, a {@link PersistenceException} is thrown.
   *
   * @return the connection properties, never null
   */
  EncryptedProperties getProperties();

  /**
   * Gets the name of the property file.
   *
   * @return the filename
   */
  String getPropertiesName();

  /**
   * Sets the name of the property file.<br>
   * (without the extension {@code .properties})
   *
   * @param propertiesName the filename
   */
  void setPropertiesName(String propertiesName);

  /**
   * Applies the properties to this session info.<br>
   * Sets user, password, and application-name from properties, if given.
   */
  void applyProperties();

  /**
   * Gets the session's locale.<br>
   * Returns the locale of the {@link org.tentackle.common.LocaleProvider} if not set explicitly
   * via {@link #setLocale(java.util.Locale)}.
   *
   * @return the locale, never null
   */
  Locale getLocale();

  /**
   * Sets the session's locale.
   *
   * @param locale the locale, null to use the locale of the {@link org.tentackle.common.LocaleProvider}
   */
  void setLocale(Locale locale);

  /**
   * Gets the info string describing the JVM.
   *
   * @return the jvm info
   */
  String getVmInfo();

  /**
   * Sets the info string describing the JVM.
   *
   * @param vmInfo the jvm info
   */
  void setVmInfo(String vmInfo);

  /**
   * Gets the operating system info.
   *
   * @return the OS info
   */
  String getOsInfo();

  /**
   * Sets the operating system info.
   *
   * @param osInfo the OS info
   */
  void setOsInfo(String osInfo);

  /**
   * Gets the host info.
   *
   * @return the hostname or similar info
   */
  String getHostInfo();

  /**
   * Sets the host info.
   *
   * @param hostInfo the info
   */
  void setHostInfo(String hostInfo);

  /**
   * Gets the timezone.
   *
   * @return the timezone, null if not set
   */
  TimeZone getTimeZone();

  /**
   * Sets the timezone.
   *
   * @param timeZone the timezone
   */
  void setTimeZone(TimeZone timeZone);

  /**
   * Checks the version of the server.
   * <p>
   * Invoked by the client after connecting to the server.
   * The default implementation does nothing.
   *
   * @param serverVersion the server's version
   * @throws VersionIncompatibleException if versions are not compatible
   */
  void checkServerVersion(String serverVersion);

  /**
   * Determines whether token-locks are removed when the session is closed.<br>
   * By default, locks are not removed for cloned sessions.
   *
   * @return true if leave locks untouched, false to remove locks
   */
  boolean isLockLingerEnabled();

  /**
   * Sets whether token-locks are removed when the session is closed.
   *
   * @param enabled true if leave locks untouched, false to remove locks
   */
  void setLockLingerEnabled(boolean enabled);

}
