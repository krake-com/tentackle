/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.session;

import org.tentackle.common.Constants;
import org.tentackle.common.Cryptor;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.PasswordCredentials;
import org.tentackle.trip.TripReferencable;

import java.io.FileNotFoundException;
import java.io.Serial;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * The default implementation for a session-info.
 * <p>
 * The credentials default to {@link PasswordCredentials}, i.e., the traditional username and
 * password login. Applications using another authentication method replace them via
 * {@link #setCredentials(Credentials)}.
 * <p>
 * Confidential parts of the credentials are encrypted if a {@link Cryptor} is configured.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class DefaultSessionInfo implements SessionInfo, Cloneable {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(DefaultSessionInfo.class);

  private String userName;                      // name of the user
  private Credentials credentials;              // the credentials to authenticate with, never null
  private long userId;                          // the user id
  private int userClassId;                      // the user class id
  private String applicationName;               // an optional application name
  private long applicationId;                   // application instance id
  private String sessionName;                   // optional session name
  private String clientVersion;                 // the version of the client
  private long since;                           // logged in since
  private String propertiesName;                // filename of backend properties
  private EncryptedProperties properties;       // db properties (send over via TRIP)
  private boolean immutable;                    // true if session info is immutable
  private boolean finallyImmutable;             // true if finally immutable
  private Logger.Level immutableLoggingLevel;   // optional logging for immutable violations instead of exception
  private Locale locale;                        // the user's locale
  private String vmInfo;                        // info about the JVM
  private String osInfo;                        // info about the operating system
  private String hostInfo;                      // info about the host(name)
  private TimeZone timeZone;                    // the timezone
  private boolean cloned;                       // true if session info is cloned
  private boolean lockLinger;                   // true if locks are not removed when the session is closed


  /**
   * Creates a session-info from an optional username, optional password, and
   * a property file holding the connection parameters.
   *
   * @param username is the name of the user, null if none
   * @param password is the password, null if none
   * @param propertiesName name of the session's properties file, null if {@code "backend"}
   */
  public DefaultSessionInfo(String username, char[] password, String propertiesName)  {
    credentials = new PasswordCredentials();
    setUserName(username);
    setPassword(password);
    setPropertiesName(propertiesName == null ? Constants.BACKEND_PROPS : propertiesName);
  }

  /**
   * Creates a session-info from a property file holding the connection parameters.
   *
   * @param propertiesName name of the session's properties file, null if {@code "backend"}
   */
  public DefaultSessionInfo(String propertiesName)  {
    this(null, null, propertiesName);
  }

  /**
   * Creates a session-info from the default properties file.<br>
   * The property file's name is {@code "backend"}.
   */
  public DefaultSessionInfo()  {
    this(null, null, null);
  }

  /**
   * Creates a session-info from properties.<br>
   * The username and password are taken from the properties.
   *
   * @param properties the properties
   */
  public DefaultSessionInfo(EncryptedProperties properties)  {
    credentials = new PasswordCredentials();
    setPropertiesName("<internal>");
    setProperties(properties);
    setUserName(properties.getPropertyIgnoreCase(Constants.BACKEND_USER));
    setPassword(properties.getPropertyAsChars(properties.getKeyIgnoreCase(Constants.BACKEND_PASSWORD)));
  }


  /**
   * Session infos are equal if they belong to the same user, i.e. if their usernames are equal.
   * <p>
   * The comparison is deliberately restricted to the username. It makes a {@link #clone()} equal to
   * its original, which is what lets a {@link MultiUserSessionPool} find a user's pool again for a
   * session handed back to it, and lets {@link MultiUserSessionPool#close(SessionInfo)} address a
   * user by a session info created just for that purpose. Comparing the credentials -- which are
   * copied by {@code clone()} and have no value equality -- or any other attribute would break both.
   * <p>
   * Consequently, the username must not be changed while the session info serves as a key.
   *
   * @param obj the other object
   * @return true if obj is a SessionInfo and belongs to the same 'user'
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SessionInfo) {
      return Objects.equals(userName, ((SessionInfo) obj).getUserName());
    }
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Derived from the username alone, see {@link #equals(Object)}.
   */
  @Override
  public int hashCode() {
    int hash = 3;
    hash = 97 * hash + (this.userName != null ? this.userName.hashCode() : 0);
    return hash;
  }


  @Override
  public boolean isImmutable() {
    return immutable;
  }

  @Override
  public void setImmutable(boolean immutable) {
    if (!immutable && finallyImmutable) {
      throw new ImmutableException("session info is finally immutable");
    }
    this.immutable = immutable;
  }

  @Override
  public void setFinallyImmutable() {
    setImmutable(true);
    finallyImmutable = true;
  }

  @Override
  public boolean isFinallyImmutable() {
    return finallyImmutable;
  }

  @Override
  public void setImmutableLoggingLevel(Logger.Level immutableLoggingLevel) {
    this.immutableLoggingLevel = immutableLoggingLevel;
  }

  @Override
  public Logger.Level getImmutableLoggingLevel() {
    return immutableLoggingLevel;
  }


  /**
   * Asserts that this session info is mutable.
   */
  protected void assertMutable() {
    if (isImmutable()) {
      ImmutableException ex = new ImmutableException("session info " + this + " is immutable");
      if (immutableLoggingLevel == null) {
        throw ex;
      }
      LOGGER.log(immutableLoggingLevel, ex.getMessage(), ex);
    }
  }


  @Override
  public void setSince(long since) {
    assertMutable();
    this.since = since;
  }

  @Override
  public long getSince() {
    return since;
  }


  @Override
  public void setUserName (String userName)  {
    assertMutable();
    this.userName = userName;
    if (credentials instanceof PasswordCredentials passwordCredentials) {
      passwordCredentials.setUserName(userName);
    }
  }

  @Override
  public String getUserName()  {
    return userName;
  }


  @Override
  public void setPassword(char[] password)  {
    assertMutable();
    if (credentials instanceof PasswordCredentials passwordCredentials) {
      passwordCredentials.setPassword(password);
    }
    else {
      credentials = new PasswordCredentials(userName, password);
    }
  }

  @Override
  public char[] getPassword() {
    return credentials instanceof PasswordCredentials passwordCredentials ? passwordCredentials.getPassword() : null;
  }

  @Override
  public void clearPassword() {
    credentials.clear();
  }


  @Override
  public Credentials getCredentials() {
    return credentials;
  }

  @Override
  public void setCredentials(Credentials credentials) {
    assertMutable();
    this.credentials = credentials == null ? new PasswordCredentials(userName, null) : credentials;
  }


  @Override
  public String getClientVersion() {
    return clientVersion;
  }

  @Override
  public void setClientVersion(String clientVersion) {
    assertMutable();
    this.clientVersion = clientVersion;
  }


  @Override
  public Locale getLocale() {
    return locale == null ? LocaleProvider.getInstance().getLocale() : locale;
  }

  @Override
  public void setLocale(Locale locale) {
    assertMutable();
    this.locale = locale;
  }


  @Override
  public String getVmInfo() {
    return vmInfo;
  }

  @Override
  public void setVmInfo(String vmInfo) {
    assertMutable();
    this.vmInfo = vmInfo;
  }


  @Override
  public TimeZone getTimeZone() {
    return timeZone;
  }

  @Override
  public void setTimeZone(TimeZone timeZone) {
    assertMutable();
    this.timeZone = timeZone;
  }


  @Override
  public String getOsInfo() {
    return osInfo;
  }

  @Override
  public void setOsInfo(String osInfo) {
    assertMutable();
    this.osInfo = osInfo;
  }


  @Override
  public String getHostInfo() {
    return hostInfo;
  }

  @Override
  public void setHostInfo(String hostInfo) {
    assertMutable();
    this.hostInfo = hostInfo;
  }


  @Override
  public String toString()  {
    StringBuilder buf = new StringBuilder();
    if (sessionName != null) {
      buf.append(sessionName).append(':');
    }
    buf.append(userName);
    buf.append('[');
    buf.append(userId);
    buf.append("]/");
    if (credentials instanceof PasswordCredentials passwordCredentials) {
      if (passwordCredentials.isPasswordCleared()) {
        buf.append("<cleared>");
      }
      else {
        buf.append(passwordCredentials.isPasswordSet() ? "<passwd>" : "<no-passwd>");
      }
    }
    else {
      buf.append(credentials);
    }
    if (applicationName != null) {
      buf.append('/');
      buf.append(applicationName);
    }
    if (applicationId != 0) {
      buf.append('[');
      buf.append(applicationId);
      buf.append(']');
    }
    if (cloned) {
      buf.append('*');
    }
    return buf.toString();
  }


  /**
   * Clones a session-info.<br>
   * The credentials are cloned, i.e., their secrets are physically copied.<br>
   * The properties are cloned as well.<br>
   * Cloned session infos are mutable by default and have no sessionName.
   */
  @Override
  public synchronized DefaultSessionInfo clone() {    // sync: read the properties as getProperties() writes them
    DefaultSessionInfo sessionInfo;
    try {
      sessionInfo = (DefaultSessionInfo) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }

    // physically copy the credentials (as their secrets might be cleared later)
    sessionInfo.credentials = credentials.clone();

    if (properties != null) {
      sessionInfo.properties = properties.clone();
    }

    sessionInfo.sessionName = null;   // session names should be unique, since they identify a session
    sessionInfo.immutable = false;
    sessionInfo.finallyImmutable = false;   // else the clone could never be made mutable again
    sessionInfo.cloned = true;
    sessionInfo.lockLinger = true;    // don't remove locks for cloned sessions

    return sessionInfo;
  }


  @Override
  public boolean isCloned() {
    return cloned;
  }

  @Override
  public void clearCloned() {
    assertMutable();
    cloned = false;
    lockLinger = false;
  }


  @Override
  public String getPropertiesName() {
    return propertiesName;
  }

  @Override
  public void setPropertiesName(String propertiesName) {
    assertMutable();
    this.propertiesName = propertiesName;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The properties are loaded lazily on first access. Since a session info is shared between
   * threads -- by pooled sessions, the keep-alive daemon and the remote server side -- the
   * lazy load is synchronized, so that all of them end up with the very same properties.
   * <p>
   * Notice that this is the one mutation an immutable session info still permits: the loaded
   * properties are what the info was configured with all along, not a change of its state.
   */
  @Override
  public synchronized EncryptedProperties getProperties() {
    if (properties == null) {
      properties = loadProperties(propertiesName);
    }
    return properties;
  }

  @Override
  public synchronized void setProperties(EncryptedProperties properties) {
    assertMutable();
    this.properties = properties;
  }

  @Override
  public void applyProperties() {
    EncryptedProperties props = getProperties();
    String name = props.getPropertyIgnoreCase(Constants.BACKEND_USER);
    if (name != null) {
      setUserName(name);
    }
    char[] pwd = props.getPropertyAsChars(props.getKeyIgnoreCase(Constants.BACKEND_PASSWORD));
    if (pwd != null) {
      setPassword(pwd);
    }
    name = props.getPropertyIgnoreCase(Constants.BACKEND_APPLICATION_NAME);
    if (name != null) {
      setApplicationName(name);
    }
  }

  @Override
  public String getApplicationName() {
    return applicationName;
  }

  @Override
  public void setApplicationName(String application) {
    assertMutable();
    this.applicationName = application;
  }


  @Override
  public void setApplicationId(long applicationId) {
    assertMutable();
    this.applicationId = applicationId;
  }

  @Override
  public long getApplicationId() {
    return applicationId;
  }

  @Override
  public void setSessionName(String sessionName) {
    assertMutable();
    this.sessionName = sessionName;
  }

  @Override
  public String getSessionName() {
    return sessionName;
  }

  @Override
  public void checkServerVersion(String serverVersion) {
    // default is ok
  }


  @Override
  public long getUserId() {
    return userId;
  }

  @Override
  public void setUserId(long userId) {
    assertMutable();
    this.userId = userId;
  }

  @Override
  public int getUserClassId() {
    return userClassId;
  }

  @Override
  public void setUserClassId(int userClassId) {
    assertMutable();
    this.userClassId = userClassId;
  }


  @Override
  public boolean isLockLingerEnabled() {
    return lockLinger;
  }

  @Override
  public void setLockLingerEnabled(boolean enabled) {
    assertMutable();
    lockLinger = enabled;
  }


  /**
   * Loads the properties.
   *
   * @param propertiesName the resource or filename
   * @return the properties
   */
  protected EncryptedProperties loadProperties(String propertiesName) {
    try {
      return PropertiesUtilities.getInstance().load(propertiesName);
    }
    catch (FileNotFoundException e1) {
      throw new PersistenceException("backend properties '" + propertiesName + "' not found");
    }
  }

}
