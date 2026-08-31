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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the backend configuration stored in the user preferences.
 * <p>
 * Complements {@link BackendDriverConfigurationTest}, which covers the plain round trip.
 * Everything here goes into the user preferences under a name of its own and is removed again.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class BackendConfigurationTest {

  private static final String APPLICATION = "BackendConfigurationTest";
  private static final String DRIVER_NAME = "XpDrvBcTst";

  /**
   * Loads the configurations of this test's application.
   *
   * @return the configurations by name
   */
  private static Map<String, BackendConfiguration> load() {
    return BackendConfiguration.getBackendConfigurations(APPLICATION, false);
  }

  /**
   * Creates a backend configuration without a driver, i.e. a remote one.
   *
   * @param name the backend name
   * @return the configuration
   */
  private static BackendConfiguration remoteBackend(String name) {
    return new BackendConfiguration(APPLICATION, name, "tripq://host:1234", null, null, null, null);
  }


  @AfterMethod
  public void cleanup() {
    BackendConfiguration.removeBackendConfigurations(APPLICATION, false);
    DriverConfiguration.getDriverConfigurations(false).values().stream()
        .filter(driver -> DRIVER_NAME.equals(driver.getName()))
        .forEach(driver -> driver.remove(false));
  }


  @Test
  public void testNewConfigurationIsNotPersistedYet() {
    BackendConfiguration backend = remoteBackend("plain");
    assertFalse(backend.isPersisted());
    assertEquals(backend.getApplication(), APPLICATION);
    assertEquals(backend.getName(), "plain");
    assertEquals(backend.toString(), "plain");

    backend.persist(false);
    BackendConfiguration loaded = load().get("plain");
    assertTrue(loaded.isPersisted(), "a configuration read back from the preferences is persisted");
    assertEquals(loaded.getUrl(), "tripq://host:1234");
    assertNull(loaded.getDriver(), "a remote backend has no driver");
    assertNull(loaded.getUser());
    assertNull(loaded.getPassword());
    assertNull(loaded.getOptions());
  }

  /**
   * Re-persisting a configuration whose optional values were cleared must clear them in the
   * preferences too, or the old values would come back on the next load.
   */
  @Test
  public void testClearedValuesAreRemovedFromThePreferences() {
    BackendConfiguration backend = remoteBackend("clearing");
    backend.setUser("hugo");
    backend.setPassword("secret");
    backend.setOptions("readOnly=true");
    backend.setDriver(new DriverConfiguration(DRIVER_NAME, "xx.yy.Driver", "file"));
    backend.getDriver().persist(false);
    backend.persist(false);

    BackendConfiguration loaded = load().get("clearing");
    assertEquals(loaded.getUser(), "hugo");
    assertEquals(loaded.getPassword(), "secret");
    assertEquals(loaded.getOptions(), "readOnly=true");
    assertEquals(loaded.getDriver().getName(), DRIVER_NAME);

    backend.setUser("");        // the empty string clears it as well
    backend.setPassword(null);
    backend.setOptions(null);
    backend.setDriver(null);
    backend.persist(false);

    loaded = load().get("clearing");
    assertNull(loaded.getUser());
    assertNull(loaded.getPassword());
    assertNull(loaded.getOptions());
    assertNull(loaded.getDriver());
  }

  /**
   * A backend referring to a driver that is gone is still loaded, so that the user can fix it.
   */
  @Test
  public void testVanishedDriverDoesNotBreakTheConfiguration() {
    DriverConfiguration driver = new DriverConfiguration(DRIVER_NAME, "xx.yy.Driver", "file");
    driver.persist(false);
    BackendConfiguration backend = remoteBackend("orphan");
    backend.setDriver(driver);
    backend.persist(false);
    driver.remove(false);

    BackendConfiguration loaded = load().get("orphan");
    assertNotEquals(loaded, null, "the backend must still be loadable");
    assertNull(loaded.getDriver(), "the driver is gone");
    assertEquals(loaded.getUrl(), "tripq://host:1234");
  }

  @Test
  public void testDefaultBackend() {
    assertNull(BackendConfiguration.getDefault(APPLICATION, false), "no default configured yet");

    BackendConfiguration backend = remoteBackend("thedefault");
    backend.persist(false);
    BackendConfiguration.setDefault(backend, APPLICATION, false);

    BackendConfiguration loaded = BackendConfiguration.getDefault(APPLICATION, false);
    assertEquals(loaded, backend);

    BackendConfiguration.setDefault(null, APPLICATION, false);
    assertNull(BackendConfiguration.getDefault(APPLICATION, false));
  }

  /**
   * A default pointing at a backend that was removed in the meantime is simply no default.
   */
  @Test
  public void testDefaultOfARemovedBackend() {
    BackendConfiguration backend = remoteBackend("gone");
    backend.persist(false);
    BackendConfiguration.setDefault(backend, APPLICATION, false);
    backend.remove(false);
    assertNull(BackendConfiguration.getDefault(APPLICATION, false));
  }

  @Test
  public void testRemoveDropsOnlyTheGivenBackend() {
    remoteBackend("first").persist(false);
    remoteBackend("second").persist(false);
    assertEquals(load().size(), 2);

    load().get("first").remove(false);
    assertEquals(load().keySet(), java.util.Set.of("second"));
  }

  /**
   * Backends are identified by their application and name, drivers by their name.
   */
  @Test
  public void testEqualsAndHashCode() {
    BackendConfiguration backend = remoteBackend("same");
    assertEquals(backend, remoteBackend("same"));
    assertEquals(backend.hashCode(), remoteBackend("same").hashCode());
    assertNotEquals(backend, remoteBackend("other"));
    assertNotEquals(backend, new BackendConfiguration("OtherApplication", "same",
                                                     "tripq://host:1234", null, null, null, null));
    assertNotEquals(backend, null);

    DriverConfiguration driver = new DriverConfiguration(DRIVER_NAME, "xx.yy.Driver", "file");
    assertEquals(driver, new DriverConfiguration(DRIVER_NAME, "zz.other.Driver", "otherfile"));
    assertEquals(driver.hashCode(), new DriverConfiguration(DRIVER_NAME, "zz.other.Driver", "x").hashCode());
    assertNotEquals(driver, new DriverConfiguration("OtherDriver", "xx.yy.Driver", "file"));
  }

}
