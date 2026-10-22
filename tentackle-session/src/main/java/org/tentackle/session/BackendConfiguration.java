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

import org.tentackle.bind.Bindable;
import org.tentackle.common.Cryptor;
import org.tentackle.log.Logger;
import org.tentackle.validate.validator.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Configuration for a database backend.<br>
 * The configuration is stored via the standard {@link Preferences} and provides
 * the parameters necessary to connect to the database backend.<br>
 * The config refers to a {@link DriverConfiguration} that loads the corresponding driver.
 * <p>
 * Whether username and password are optional depends on the use case. If the configuration
 * is used by a standard DesktopApplication (see fx-rdc module), the user/password is mandatory
 * for local sessions (with a driver) and optional for remote sessions (without a driver).<br>
 * Other application types may enforce their own rules.
 * <p>
 * Notice that the implementation does not depend on the tentackle-database or tentackle-sql module!
 * <p>
 * Passwords are stored encrypted if a {@link Cryptor} is configured for the application.
 */
public class BackendConfiguration extends AbstractSessionConfiguration {

  private static final Logger LOGGER = Logger.get(BackendConfiguration.class);

  private static final String NODE_NAME = "backends";
  private static final String URL_KEY = "url";
  private static final String OPTIONS_KEY = "options";
  private static final String DRIVER_KEY = "driver";
  private static final String USER_KEY = "user";
  private static final String PASSWORD_KEY = "password";
  private static final String DEFAULT_KEY = "default";


  /**
   * Loads all backend configurations for a given application.
   *
   * @param application the application name
   * @param system true if load from system preferences, else user preferences
   * @return the backend configurations
   */
  public static Map<String, BackendConfiguration> getBackendConfigurations(String application, boolean system) {
    Map<String, BackendConfiguration> backends = new TreeMap<>();    // sort by name
    Preferences applicationPrefs = getPrefNode(system).node(application);
    try {
      for (String name : applicationPrefs.childrenNames()) {
        Preferences backendPrefs = applicationPrefs.node(name);
        String driverName = backendPrefs.get(DRIVER_KEY, null);
        String url = backendPrefs.get(URL_KEY, null);
        String options = backendPrefs.get(OPTIONS_KEY, null);
        DriverConfiguration driver = null;
        if (driverName != null) {
          driver = DriverConfiguration.getDriverConfigurations(system).get(driverName);
          if (driver == null) {
            LOGGER.warning("no such driver: {0}", driverName);
            // don't throw exception to allow the user to fix it
          }
        }
        String user = backendPrefs.get(USER_KEY, null);
        String password = backendPrefs.get(PASSWORD_KEY, null);
        BackendConfiguration configuration = new BackendConfiguration(application, name, url, options, driver, user, password);
        configuration.setPersisted(true);
        backends.put(name, configuration);
      }
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot load backend configurations for '" + application + "'", bx);
    }
    return backends;
  }

  /**
   * Removes all backend configurations for the given application.
   *
   * @param application the application name
   * @param system true if remove from system preferences, else user preferences
   */
  public static void removeBackendConfigurations(String application, boolean system) {
    Preferences prefs = getPrefNode(system);
    try {
      prefs.node(application).removeNode();
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("removing backend configurations for '" + application + "' failed", bx);
    }
  }

  /**
   * Sets the default backend configuration.
   *
   * @param backend the configuration, null to clear
   * @param application the application name
   * @param system true if save to system preferences, else user preferences
   */
  public static void setDefault(BackendConfiguration backend, String application, boolean system) {
    Preferences prefs = getPrefNode(system);
    Preferences node = prefs.node(application);
    try {
      if (backend == null) {
        node.remove(DEFAULT_KEY);
      }
      else {
        node.put(DEFAULT_KEY, backend.getName());
      }
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot set default backend configuration", bx);
    }
  }

  /**
   * Gets the default backend configuration.
   *
   * @param application the application name
   * @param system true if load from system preferences, else user preferences
   * @return the configuration, null if no default
   */
  public static BackendConfiguration getDefault(String application, boolean system) {
    String name = getPrefNode(system).node(application).get(DEFAULT_KEY, null);
    BackendConfiguration backend = null;
    if (name != null) {
      backend = getBackendConfigurations(application, system).get(name);
    }
    return backend;
  }

  /**
   * Gets the configuration parent node.
   *
   * @param system true if load from system preferences, else user preferences
   * @return the node
   */
  private static Preferences getPrefNode(boolean system) {
    return getPrefNode(NODE_NAME, system);
  }




  private String application;             // the application name
  private String url;                     // the host- or service url
  private String options;                 // other options (multiline!)
  private DriverConfiguration driver;     // the driver, null if remote
  private String user;                    // the username
  private String password;                // the user's password (encrypted if a Cryptor is available)

  /**
   * Creates a backend configuration.
   *
   * @param application the application name
   * @param name the symbolic backend name
   * @param url the host- or service url
   * @param options other options, one per line, null if none
   * @param driver the driver configuration, null if remote or the driver is loaded from the classpath
   * @param user the optional username, null or empty if none
   * @param password the optional password as stored, i.e. already encrypted if a {@link Cryptor} is configured,
   *                 null or empty if none. Use {@link #setPassword(String)} for a plain-text password.
   */
  public BackendConfiguration(String application, String name, String url, String options, DriverConfiguration driver, String user, String password) {
    setName(name);
    this.application = application;
    this.url = url;
    this.options = options;
    this.driver = driver;
    this.user = user;
    this.password = password;
  }

  /**
   * Gets the application name.
   *
   * @return the application name
   */
  @Bindable
  @NotNull
  public String getApplication() {
    return application;
  }

  /**
   * Sets the application name.
   *
   * @param application the application name
   */
  @Bindable
  public void setApplication(String application) {
    this.application = application;
  }


  /**
   * Gets the host- or service url.
   *
   * @return the url
   */
  @Bindable
  @NotNull
  public String getUrl() {
    return url;
  }

  /**
   * Sets the host- or service url.
   *
   * @param url the url
   */
  @Bindable
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Gets other options.<br>
   * Each option goes into a separate line.
   *
   * @return the options
   */
  @Bindable
  public String getOptions() {
    return options;
  }

  /**
   * Sets the options.
   *
   * @param options the options, null if none
   */
  @Bindable
  public void setOptions(String options) {
    this.options = options;
  }

  /**
   * Gets the driver configuration.
   *
   * @return the driver, null if remote or from classpath
   */
  @Bindable
  public DriverConfiguration getDriver() {
    return driver;
  }

  /**
   * Sets the driver configuration.
   *
   * @param driver the driver, null if remote or from classpath
   */
  @Bindable
  public void setDriver(DriverConfiguration driver) {
    this.driver = driver;
  }

  /**
   * Gets the optional username.
   *
   * @return the username
   */
  @Bindable
  public String getUser() {
    return user;
  }

  /**
   * Sets the username.
   *
   * @param user the username
   */
  @Bindable
  public void setUser(String user) {
    this.user = user;
  }

  /**
   * Gets the optional password.<br>
   * The password is decrypted if a {@link Cryptor} is configured.
   *
   * @return the plain-text password, null if none
   */
  @Bindable
  public String getPassword() {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null && password != null) {
      return cryptor.decrypt64(password);
    }
    return password;
  }

  /**
   * Sets the password.<br>
   * The password is stored encrypted if a {@link Cryptor} is configured.
   *
   * @param password the plain-text password, null if none
   */
  @Bindable
  public void setPassword(String password) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null && password != null) {
      this.password = cryptor.encrypt64(password);
    }
    else {
      this.password = password;
    }
  }

  /**
   * Persists this configuration.
   *
   * @param system true if store in system preferences (requires extra permission), else user preferences
   */
  public void persist(boolean system) {
    Preferences prefs = getPrefNode(system);
    Preferences node = prefs.node(application).node(getName());
    try {
      node.put(URL_KEY, url);
      if (options != null) {
        node.put(OPTIONS_KEY, options);
      }
      else {
        node.remove(OPTIONS_KEY);
      }
      if (driver != null) {
        node.put(DRIVER_KEY, driver.getName());
      }
      else {
        node.remove(DRIVER_KEY);
      }
      if (user != null && !user.isEmpty()) {
        node.put(USER_KEY, user);
      }
      else {
        node.remove(USER_KEY);
      }
      if (password != null && !password.isEmpty()) {
        node.put(PASSWORD_KEY, password);
      }
      else {
        node.remove(PASSWORD_KEY);
      }
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot persist backend configuration " + this, bx);
    }
  }

  /**
   * Removes this configuration.
   *
   * @param system true if remove from system preferences (requires extra permission), else user preferences
   */
  public void remove(boolean system) {
    Preferences prefs = getPrefNode(system);
    Preferences node = prefs.node(application).node(getName());
    try {
      node.removeNode();
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot remove backend configuration " + this, bx);
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BackendConfiguration that = (BackendConfiguration) o;
    return Objects.equals(application, that.application) &&
        Objects.equals(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(application, getName());
  }

}
