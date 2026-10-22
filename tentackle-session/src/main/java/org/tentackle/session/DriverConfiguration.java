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
import org.tentackle.validate.validator.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Configuration for a backend driver.<br>
 * The configuration is stored via the standard {@link Preferences} and provides
 * the parameters necessary to load the driver.
 * <p>
 * Notice that the implementation does not depend on the tentackle-database or tentackle-sql module!
 */
public class DriverConfiguration extends AbstractSessionConfiguration {

  private static final String NODE_NAME = "drivers";
  private static final String DRIVER_KEY = "driver";
  private static final String URL_KEY = "url";


  /**
   * Loads all driver configurations.
   *
   * @param system true if load from system preferences, else user preferences
   * @return the driver configurations
   */
  public static Map<String, DriverConfiguration> getDriverConfigurations(boolean system) {
    Map<String, DriverConfiguration> drivers = new TreeMap<>();   // sort by name
    Preferences applicationPrefs = getPrefNode(system);
    try {
      for (String name : applicationPrefs.childrenNames()) {
        Preferences driverPrefs = applicationPrefs.node(name);
        String driver = driverPrefs.get(DRIVER_KEY, null);
        String url = driverPrefs.get(URL_KEY, null);
        if (driver != null && url != null) {
          DriverConfiguration configuration = new DriverConfiguration(name, driver, url);
          configuration.setPersisted(true);
          drivers.put(name, configuration);
        }
        // else ignore incomplete configurations silently
      }
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot load driver configurations", bx);
    }
    return drivers;
  }

  /**
   * Removes all driver configurations.
   *
   * @param system true if remove from system preferences, else user preferences
   */
  public static void removeDriverConfigurations(boolean system) {
    Preferences prefs = getPrefNode(system);
    try {
      prefs.removeNode();
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("removing driver configurations failed", bx);
    }
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



  private String driver;      // the driver classname
  private String url;         // the URL to load the driver

  /**
   * Creates a driver configuration.
   *
   * @param name the symbolic driver name
   * @param driver the driver's classname
   * @param url the url to load the driver
   */
  public DriverConfiguration(String name, String driver, String url) {
    setName(name);
    this.driver = driver;
    this.url = url;
  }


  /**
   * Gets the driver's classname.
   *
   * @return the classname
   */
  @Bindable
  @NotNull
  public String getDriver() {
    return driver;
  }

  /**
   * Sets the driver's classname.
   *
   * @param driver the classname
   */
  @Bindable
  public void setDriver(String driver) {
    this.driver = driver;
  }

  /**
   * Gets the url to load the driver.
   *
   * @return the url
   */
  @Bindable
  @NotNull
  public String getUrl() {
    return url;
  }

  /**
   * Sets the url to load the driver.
   *
   * @param url the url
   */
  @Bindable
  public void setUrl(String url) {
    this.url = url;
  }


  /**
   * Persists this configuration.
   *
   * @param system true if store in system preferences (requires extra permission), else user preferences
   */
  public void persist(boolean system) {
    Preferences prefs = getPrefNode(system);
    Preferences node = prefs.node(getName());
    try {
      node.put(DRIVER_KEY, driver);
      node.put(URL_KEY, url);
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot persist driver configuration " + this, bx);
    }
  }

  /**
   * Removes this configuration.
   *
   * @param system true if remove from system preferences (requires extra permission), else user preferences
   */
  public void remove(boolean system) {
    Preferences prefs = getPrefNode(system);
    Preferences node = prefs.node(getName());
    try {
      node.removeNode();
      prefs.flush();
    }
    catch (BackingStoreException bx) {
      throw new PersistenceException("cannot remove driver configuration " + this, bx);
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DriverConfiguration that = (DriverConfiguration) o;
    return Objects.equals(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName());
  }

}
