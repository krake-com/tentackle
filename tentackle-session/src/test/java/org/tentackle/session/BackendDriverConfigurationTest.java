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

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tests driver preferences config.
 */
public class BackendDriverConfigurationTest {

  private static final String APPLICATION_NAME = "BackendConfigurationTest";
  private static final String DRIVER_NAME_1 = "XpDrvTSt1";
  private static final String DRIVER_NAME_2 = "XpDrvTSt2";

  @Test
  public void testConfigs() {
    DriverConfiguration driver = new DriverConfiguration(DRIVER_NAME_1, "xx.yy.Driver1", "file1");
    driver.persist(false);
    driver = new DriverConfiguration(DRIVER_NAME_2, "xx.yy.Driver2", "file2");
    driver.persist(false);
    driver = DriverConfiguration.getDriverConfigurations(false).get(DRIVER_NAME_1);
    Assert.assertNotNull(driver);
    driver.remove(false);
    driver = DriverConfiguration.getDriverConfigurations(false).get(DRIVER_NAME_2);
    Assert.assertNotNull(driver);

    BackendConfiguration backend = new BackendConfiguration(APPLICATION_NAME, "backend1", "url", null, driver, "harr", "none");
    backend.persist(false);
    backend = new BackendConfiguration(APPLICATION_NAME, "backend2", "url", null, driver, "harr", "none");
    backend.persist(false);
    Collection<BackendConfiguration> backends = BackendConfiguration.getBackendConfigurations(APPLICATION_NAME, false).values();
    Assert.assertEquals(backends.size(), 2);
    backend = backends.iterator().next();
    Assert.assertEquals(backend.getDriver().getName(), DRIVER_NAME_2);

    driver.remove(false);
  }

  @AfterClass
  public void cleanup() {
    BackendConfiguration.removeBackendConfigurations(APPLICATION_NAME, false);
  }

}
