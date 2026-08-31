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

import org.testng.SkipException;
import org.testng.annotations.Test;

import java.net.URL;
import java.security.CodeSource;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@link DynamicDriver}.
 * <p>
 * A driver loaded from a URL at runtime is invisible to the {@link DriverManager}, which refuses
 * anything not loaded by the system class loader — hence the wrapper. The test loads the H2 driver a
 * second time from its own jar, which is the closest thing to the real use case that the build can
 * reach.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DynamicDriverTest {

  /**
   * Locates the jar of the H2 driver already on the classpath.
   *
   * @return the driver classname and its loading url, as {@link DynamicDriver#load} expects it
   */
  private String h2DriverName() {
    try {
      Driver driver = DriverManager.getDriver("jdbc:h2:mem:dynamicdrivertest");
      CodeSource codeSource = driver.getClass().getProtectionDomain().getCodeSource();
      if (codeSource == null || codeSource.getLocation() == null) {
        throw new SkipException("the H2 driver is not loaded from a location that can be re-loaded");
      }
      URL location = codeSource.getLocation();
      if (!location.toString().endsWith(".jar")) {
        throw new SkipException("the H2 driver is not packaged as a jar: " + location);
      }
      return driver.getClass().getName() + ":" + location;
    }
    catch (SQLException sqx) {
      throw new SkipException("no H2 driver registered", sqx);
    }
  }


  @Test
  public void testMalformedName() {
    // no colon at all -> nothing to split into a classname and a url
    assertTrue(expectThrows(BackendException.class, () -> DynamicDriver.load("org.h2.Driver"))
                   .getMessage().contains("malformed"));
    assertTrue(expectThrows(BackendException.class, () -> DynamicDriver.load(":file:/nowhere.jar"))
                   .getMessage().contains("malformed"), "an empty classname is malformed as well");
  }

  @Test
  public void testUnloadableDriver() {
    assertTrue(expectThrows(BackendException.class,
                            () -> DynamicDriver.load("no.such.Driver:file:/no/such.jar"))
                   .getMessage().contains("no.such.Driver"));
    // a url that is not even a url
    expectThrows(BackendException.class, () -> DynamicDriver.load("no.such.Driver:not a url"));
  }

  @Test
  public void testUnloadRejectsForeignDriver() throws SQLException {
    Driver driver = DriverManager.getDriver("jdbc:h2:mem:dynamicdrivertest");
    assertTrue(expectThrows(BackendException.class, () -> DynamicDriver.unload(driver))
                   .getMessage().contains("DynamicDriver.load"),
               "a driver the DriverManager brought in itself must not be unloaded through here");
  }

  /**
   * Loads a driver from a jar, delegates to it and unloads it again.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testLoadAndUnload() throws SQLException {
    String name = h2DriverName();
    Driver driver = DynamicDriver.load(name);
    try {
      assertNotNull(driver);
      assertTrue(Collections.list(DriverManager.getDrivers()).contains(driver),
                 "the loaded driver must be registered with the DriverManager");

      // everything is delegated to the wrapped driver
      assertTrue(driver.acceptsURL("jdbc:h2:mem:whatever"));
      assertFalse(driver.acceptsURL("jdbc:postgresql://localhost/x"));
      assertTrue(driver.getMajorVersion() > 0);
      assertTrue(driver.getMinorVersion() >= 0);
      assertNotNull(driver.getPropertyInfo("jdbc:h2:mem:whatever", new Properties()));
      driver.jdbcCompliant();
      driver.getParentLogger();

      // and it really connects
      try (java.sql.Connection connection = driver.connect("jdbc:h2:mem:dynamicdriverload", new Properties())) {
        assertNotNull(connection);
        assertFalse(connection.isClosed());
      }
    }
    finally {
      DynamicDriver.unload(driver);
    }

    assertFalse(Collections.list(DriverManager.getDrivers()).contains(driver),
                "the unloaded driver must be gone again");
  }

  /**
   * The driver name is the property an application configures, so the parsing of the colon that
   * separates the classname from the URL is worth a look: the URL contains colons itself.
   */
  @Test
  public void testOnlyTheFirstColonSeparates() {
    String name = h2DriverName();
    assertTrue(name.substring(name.indexOf(':') + 1).startsWith("file:"),
               "everything behind the first colon is the url: " + name);
    assertEquals(name.substring(0, name.indexOf(':')), "org.h2.Driver");
  }

}
