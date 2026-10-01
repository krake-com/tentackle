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

package org.tentackle.sql;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads a JDBC driver dynamically at runtime via a URL.<br>
 * Because DriverManager will refuse to use a driver not loaded by the system-classloader,
 * we must wrap the URL-loaded driver by such a class.
 */
public class DynamicDriver implements Driver {

  /**
   * Loads a JDBC driver dynamically and registers it at the {@link DriverManager}.<br>
   * The name consists of the driver's classname, followed by a colon, followed by the URL to load it.<br>
   * Example:
   * <pre>org.postgresql.Driver:jar:file:/usr/share/java/postgresql.jar!/</pre>
   *
   * @param name the driver classname and loading url
   * @return the loaded driver, never null
   * @throws BackendException if the name is malformed or loading the driver failed
   */
  public static Driver load(String name) {
    int ndx = name.indexOf(':');
    if (ndx > 0) {
      try {
        String classname = name.substring(0, ndx);
        URL url = new URI(name.substring(ndx + 1)).toURL();
        URLClassLoader ucl = new URLClassLoader(new URL[]{url});
        Driver driver = new DynamicDriver((Driver) Class.forName(classname, true, ucl).getConstructor().newInstance());
        DriverManager.registerDriver(driver);
        return driver;
      }
      catch (InvocationTargetException | NoSuchMethodException | ClassNotFoundException | SQLException |
             InstantiationException | IllegalAccessException | URISyntaxException | MalformedURLException ex) {
        throw new BackendException("loading driver '" + name + "' failed", ex);
      }
    }
    else {
      throw new BackendException("malformed jdbc driver name: " + name);
    }
  }


  /**
   * Unloads the driver.<br>
   * Deregisters the driver from the {@link DriverManager}.
   *
   * @param driver the driver previously loaded via {@link #load(String)}
   * @throws BackendException if the driver was not loaded via {@link #load(String)} or deregistration failed
   */
  public static void unload(Driver driver) {
    if (driver instanceof DynamicDriver) {
      try {
        DriverManager.deregisterDriver(driver);
      }
      catch (SQLException e) {
        throw new BackendException("unloading JDBC driver failed", e);
      }
    }
    else {
      throw new BackendException("driver not loaded via DynamicDriver.load");
    }
  }


  private final Driver driver;

  private DynamicDriver(Driver driver) {
    this.driver = driver;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    return driver.connect(url, info);
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return driver.acceptsURL(url);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return driver.getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    return driver.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return driver.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return driver.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return driver.getParentLogger();
  }
}
