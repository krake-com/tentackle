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

package org.tentackle.update;

import org.testng.annotations.Test;

import org.tentackle.common.StringHelper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests for {@link ClientInfo}, the record a client identifies itself with.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ClientInfoTest {

  @Test
  public void keepsEveryFieldOfTheFullConstructor() {
    ClientInfo info = new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK);
    assertEquals(info.application(), "myapp");
    assertEquals(info.version(), "1.0.0");
    assertEquals(info.platform(), "linux");
    assertEquals(info.architecture(), "amd64");
    assertEquals(info.installationType(), InstallationType.JLINK);
  }

  /**
   * Callers normally only know application, version and installation type - the rest describes
   * the running JVM and is filled in for them.
   */
  @Test
  public void derivesPlatformAndArchitectureFromTheRunningJvm() {
    ClientInfo info = new ClientInfo("myapp", "1.0.0", InstallationType.JPACKAGE);
    assertEquals(info.application(), "myapp");
    assertEquals(info.version(), "1.0.0");
    assertEquals(info.platform(), StringHelper.getPlatform());
    assertEquals(info.architecture(), StringHelper.getArchitecture());
    assertEquals(info.installationType(), InstallationType.JPACKAGE);
  }

  /**
   * A build that cannot update itself still reports in - the server then simply has nothing
   * to offer for it.
   */
  @Test
  public void acceptsAnUnknownInstallationType() {
    assertNull(new ClientInfo("myapp", "1.0.0", null).installationType());
  }

  @Test
  public void comparesByValue() {
    ClientInfo info = new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK);
    assertEquals(info, new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK));
    assertEquals(info.hashCode(), new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK).hashCode());
    assertNotEquals(info, new ClientInfo("myapp", "1.0.0", "linux", "aarch64", InstallationType.JLINK));
    assertNotEquals(info, new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JPACKAGE));
  }

}
