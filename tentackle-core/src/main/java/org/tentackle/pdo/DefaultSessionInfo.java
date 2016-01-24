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

package org.tentackle.pdo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;
import org.tentackle.common.Constants;
import org.tentackle.common.FileHelper;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.StringHelper;

/**
 * The default implementation for a session info.
 *
 * @author harald
 */
public class DefaultSessionInfo implements SessionInfo, Serializable, Cloneable {

  static final long serialVersionUID = 6002689783347165834L;

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSessionInfo.class);


  private String username;                  // name of the user
  private char[] password;                  // user's password
  private long userId;                      // the user id
  private int userClassId;                  // the user class id
  private String applicationName;           // an optional application name
  private long applicationId;               // application instance id
  private Serializable clientVersionInfo;   // the version info of the client
  private long since;                       // logged in since
  private String propertiesName;            // filename of backend properties
  private Properties properties;            // db properties (send over via RMI)
  private boolean immutable;                // true if session info is immutable
  private Logger.Level immutableLoggingLevel;      // optional logging for immutable violations instead of exception
  private Locale locale;                    // the user's locale
  private boolean cloned;                   // true if session info is cloned


  /**
   * Create sesion info from a username, password and
   * property file holding the connection parameters.
   *
   * @param username is the name of the user, null if {@code System.getProperty("user.name")}
   * @param password is the password, null if none
   * @param propertiesName name of the db-properties, null if {@code "Db"}
   */
  public DefaultSessionInfo(String username, char[] password, String propertiesName)  {
    this.username       = username == null ? System.getProperty("user.name") : username;
    this.password       = password;
    this.propertiesName = propertiesName == null ? Constants.BACKEND_PROPS : propertiesName;
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SessionInfo other = (SessionInfo) obj;
    return !((this.getUserName() == null) ?
                (other.getUserName() != null) :
                !this.getUserName().equals(other.getUserName()));
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 97 * hash + (this.username != null ? this.username.hashCode() : 0);
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
   * @param username the username
   */
  @Override
  public void setUserName (String username)  {
    assertMutable();
    this.username = username;
  }


  /**
   * Gets the username.
   *
   * @return the username
   */
  @Override
  public String getUserName ()  {
    return username;
  }



  /**
   * Sets the password.
   *
   * @param password the password
   */
  @Override
  public void setPassword (char[] password)  {
    assertMutable();
    this.password = password;
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
  public void clearPassword ()  {
    if (password != null) {
      for (int i=0; i < password.length; ++i) {
        password[i] = 0;
      }
      password = null;
    }
  }


  /**
   * Gets the password.
   *
   * @return the password
   */
  @Override
  public char[] getPassword ()  {
    return password;
  }

  /**
   * Gets the password as a string.
   * The password is stored as a character array.
   * If the password is null the empty string will be returned
   * bevaise some dbms drivers will nullp otherwise.
   *
   * @return the password, never null
   */
  @Override
  public String getPasswordAsString ()  {
    return password == null ? "" : new String(password);
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
  public String toString()  {
    StringBuilder buf = new StringBuilder();
    buf.append(username);
    buf.append('[');
    buf.append(userId);
    buf.append("]/");
    buf.append(password == null ? "<no_passwd>" : "<passwd>");
    if (applicationName != null || applicationId != 0) {
      buf.append('/');
      buf.append(applicationName);
      buf.append('[');
      buf.append(applicationId);
      buf.append(']');
    }
    return buf.toString();
  }


  /**
   * Clones a userinfo.
   * The password will be copied if not null.
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
   * to {@code dbPropertiesName} (if it does not contain an extension already).<br>
   * If there is no such file, the properties will be read as a resource according
   * to the classpath.<br>
   * If all failes the db errorhandler
   * will be invoked (which will usually terminate the application).
   *
   * @return the connection properties, never null
   */
  @Override
  public Properties getProperties() {
    if (properties == null) {
      try {
        try {
          properties = FileHelper.loadProperties(propertiesName, false);
        }
        catch (FileNotFoundException e1) {
          // try as resource
          try {
            properties = FileHelper.loadProperties(propertiesName, true);
          }
          catch (FileNotFoundException e2) {
            throw new PersistenceException("backend properties '" + getPropertiesName() + "' not found");
          }
        }
      }
      catch (IOException ex) {
        throw new PersistenceException("reading backend properties '" + getPropertiesName() + "' failed", ex);
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
