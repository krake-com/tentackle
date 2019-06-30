/*
 * Tentackle - http://www.tentackle.org.
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
import org.tentackle.common.FileHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ImmutableException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;

/**
 * The default implementation for a session info.
 * <p>
 * The password is encrypted if a {@link Cryptor} is configured.
 *
 * @author harald
 */
public class DefaultSessionInfo implements SessionInfo, Serializable, Cloneable {

  static final long serialVersionUID = 6002689783347165834L;

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSessionInfo.class);

  private String userName;                      // name of the user
  private char[] password;                      // user's password
  private String encryptedPassword;             // base64 encrypted password if a Cryptor is available
  private long userId;                          // the user id
  private int userClassId;                      // the user class id
  private String applicationName;               // an optional application name
  private long applicationId;                   // application instance id
  private Serializable clientVersionInfo;       // the version info of the client
  private long since;                           // logged in since
  private String propertiesName;                // filename of backend properties
  private Properties properties;                // db properties (send over via RMI)
  private boolean immutable;                    // true if session info is immutable
  private Logger.Level immutableLoggingLevel;   // optional logging for immutable violations instead of exception
  private Locale locale;                        // the user's locale
  private String vmInfo;                        // info about the JVM
  private String osInfo;                        // info about the operating system
  private String hostInfo;                      // info about the host(name)
  private TimeZone timeZone;                    // the timezone
  private boolean cloned;                       // true if session info is cloned


  /**
   * Creates a session info from an optional username, optional password and
   * a property file holding the connection parameters.
   *
   * @param username is the name of the user, null if {@code System.getProperty("user.name")}
   * @param password is the password, null if none
   * @param propertiesName name of the session's properties file, null if {@code "backend"}
   */
  public DefaultSessionInfo(String username, char[] password, String propertiesName)  {
    this.userName       = username == null ? System.getProperty("user.name") : username;
    this.password       = password;
    this.propertiesName = propertiesName == null ? Constants.BACKEND_PROPS : propertiesName;
  }

  /**
   * Creates a session info from a property file holding the connection parameters.
   *
   * @param propertiesName name of the session's properties file, null if {@code "backend"}
   */
  public DefaultSessionInfo(String propertiesName)  {
    this(null, null, propertiesName);
  }

  /**
   * Creates a session info from the default properties file.<br>
   * The property file's name is {@code "backend"}.
   */
  public DefaultSessionInfo()  {
    this(null, null, null);
  }

  /**
   * Creates a session from a properties object.
   *
   * @param properties the properties
   */
  public DefaultSessionInfo(Properties properties)  {
    this.propertiesName = "<internal>";
    this.properties = properties;
    this.userName = properties.getProperty(Constants.BACKEND_USER);
    String pass = properties.getProperty(Constants.BACKEND_PASSWORD);
    if (pass != null && !pass.isEmpty()) {
      this.password = pass.toCharArray();
    }
  }


  /**
   * Session infos are equal if their login credentials are equal.<br>
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
    this.immutable = immutable;
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


  /**
   * Sets the epochal time when the user logged in.
   *
   * @param since logged in since
   */
  @Override
  public void setSince(long since) {
    assertMutable();
    this.since = since;
  }

  /**
   * Gets the time since when logged in.
   *
   * @return logged in since, null if not logged in
   */
  @Override
  public long getSince() {
    return since;
  }


  /**
   * Sets the username
   *
   * @param userName the username
   */
  @Override
  public void setUserName (String userName)  {
    assertMutable();
    this.userName = userName;
  }


  /**
   * Gets the username.
   *
   * @return the username
   */
  @Override
  public String getUserName()  {
    return userName;
  }



  /**
   * Sets the password.
   *
   * @param password the password
   */
  @Override
  public void setPassword(char[] password)  {
    assertMutable();
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      encryptedPassword = password == null ? null : cryptor.encrypt64(password);
      this.password = null;
    }
    else {
      this.password = password;
      this.encryptedPassword = null;
    }
  }


  /**
   * Clears the password.
   * Password should be cleared when no more used
   * to remove them physically from memory.
   * This is the reason why we store passwords as a
   * character array and not a string.<p>
   * Sadly enough, the JDBC api requires passwords as strings :-(
   */
  @Override
  public void clearPassword() {
    StringHelper.blank(password);
    encryptedPassword = null;
  }


  /**
   * Gets the password.
   *
   * @return the password
   */
  @Override
  public char[] getPassword() {
    if (encryptedPassword != null) {
      return Cryptor.getInstance().decrypt64ToChars(encryptedPassword);
    }
    return password;
  }


  /**
   * Gets the client version info.
   *
   * @return the version info
   */
  @Override
  public Serializable getClientVersionInfo() {
    return clientVersionInfo;
  }


  /**
   * Sets the client's version info.
   *
   * @param clientVersionInfo the version info
   */
  @Override
  public void setClientVersionInfo(Serializable clientVersionInfo) {
    assertMutable();
    this.clientVersionInfo = clientVersionInfo;
  }

  @Override
  public Locale getLocale() {
    return locale == null ? Locale.getDefault() : locale;
  }

  @Override
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  @Override
  public String getVmInfo() {
    return vmInfo;
  }

  @Override
  public void setVmInfo(String vmInfo) {
    this.vmInfo = vmInfo;
  }

  @Override
  public TimeZone getTimeZone() {
    return timeZone;
  }

  @Override
  public void setTimeZone(TimeZone timeZone) {
    this.timeZone = timeZone;
  }

  @Override
  public String getOsInfo() {
    return osInfo;
  }

  @Override
  public void setOsInfo(String osInfo) {
    this.osInfo = osInfo;
  }

  @Override
  public String getHostInfo() {
    return hostInfo;
  }

  @Override
  public void setHostInfo(String hostInfo) {
    this.hostInfo = hostInfo;
  }


  @Override
  public String toString()  {
    StringBuilder buf = new StringBuilder();
    buf.append(userName);
    buf.append('[');
    buf.append(userId);
    buf.append("]/");
    buf.append(password == null ? "<no_passwd>" : "<passwd>");
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
   * Clones a userinfo.<br>
   * The password is copied, if not null.<br>
   * The properties are cloned as well.<br>
   * Cloned session infos are mutable by default.
   */
  @Override
  public DefaultSessionInfo clone() {
    DefaultSessionInfo ui;
    try {
      ui = (DefaultSessionInfo) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new InternalError();    // should never happen
    }

    if (password != null) {
      // physically copy the password (as it might be cleared)
      ui.password = new char[password.length];
      System.arraycopy(password, 0, ui.password, 0, password.length);
    }

    if (properties != null) {
      ui.properties = (Properties) properties.clone();
    }

    ui.immutable = false;
    ui.cloned = true;

    return ui;
  }


  /**
   * Checks whether this DefaultSessionInfo is cloned.
   *
   * @return true if cloned
   */
  @Override
  public boolean isCloned() {
    return cloned;
  }

  /**
   * Cleares the cloned flag.
   * Useful if the userinfo should no longer be treated as cloned.
   */
  @Override
  public void clearCloned() {
    assertMutable();
    cloned = false;
  }


  /**
   * Gets the name of the property file.
   *
   * @return the filename
   */
  @Override
  public String getPropertiesName() {
    return propertiesName;
  }


  /**
   * Sets the name of the property file, i.e.
   * without the extension {@code .properties}.
   *
   * @param propertiesName the filename
   */
  @Override
  public void setPropertiesName(String propertiesName) {
    assertMutable();
    this.propertiesName = propertiesName;
  }


  /**
   * Gets the connection properties.<br>
   * If the properties are not set so far, the method will load
   * the properties file by adding the extension {@code ".properties"}
   * to {@code propertiesName} (if it does not contain an extension already).<br>
   * If there is no such file, the properties will be read as a resource according
   * to the classpath.<br>
   * If all fails the db errorhandler will be invoked (which will usually terminate the application).
   * <p>
   * However, if {@code propertiesName} is null, empty properties are returned.
   *
   * @return the connection properties, never null
   */
  @Override
  public Properties getProperties() {
    if (properties == null) {
      try {
        try {
          properties = FileHelper.loadProperties(propertiesName);
        }
        catch (FileNotFoundException e1) {
          throw new PersistenceException("backend properties '" + propertiesName + "' not found");
        }
      }
      catch (IOException ex) {
        throw new PersistenceException("reading backend properties '" + propertiesName + "' failed", ex);
      }
    }
    return properties;
  }


  /**
   * Sets the connection properties.
   *
   * @param properties the connection properties.
   */
  @Override
  public void setProperties(Properties properties) {
    assertMutable();
    this.properties = properties;
  }


  @Override
  public void applyProperties() {
    String name = getProperties().getProperty(Constants.BACKEND_USER);
    if (name != null) {
      setUserName(name);
    }
    char[] pwd = StringHelper.toCharArray(getProperties().getProperty(Constants.BACKEND_PASSWORD));
    if (pwd != null) {
      setPassword(pwd);
    }
  }


  /**
   * Gets the application name.
   *
   * @return the name
   */
  @Override
  public String getApplicationName() {
    return applicationName;
  }


  /**
   * Sets the application name.
   * Application server may need that to classify the client.
   *
   * @param application the name
   */
  @Override
  public void setApplicationName(String application) {
    assertMutable();
    this.applicationName = application;
  }


  /**
   * Sets the application id.<br>
   * Should be unique among the same application name.
   *
   * @param applicationId the optional application id
   */
  @Override
  public void setApplicationId(long applicationId) {
    this.applicationId = applicationId;
  }

  /**
   * Returns the application id.
   *
   * @return the id, 0 if none
   */
  @Override
  public long getApplicationId() {
    return applicationId;
  }


  /**
   * Checks the version information (of the server).
   * <p>
   * The default implementation does nothing.
   * It is invoked from {@link Session#open()} for remote connections.
   *
   * @param serverVersionInfo the server's version info
   * @throws VersionInfoIncompatibleException if versions are not compatible
   */
  @Override
  public void checkServerVersionInfo(Serializable serverVersionInfo) throws VersionInfoIncompatibleException {
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

}
