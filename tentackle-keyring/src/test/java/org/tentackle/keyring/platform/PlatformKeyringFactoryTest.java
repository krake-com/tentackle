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

package org.tentackle.keyring.platform;

import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.keyring.Keyring;
import org.tentackle.keyring.KeyringException;
import org.tentackle.keyring.KeyringFactory;
import org.tentackle.keyring.MemoryKeyring;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Tests the {@link PlatformKeyringFactory}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class PlatformKeyringFactoryTest {

  @AfterMethod(alwaysRun = true)
  public void clearProperty() {
    System.clearProperty(KeyringFactory.KEYRING_PROPERTY);
  }

  @Test
  public void testServiceFactory() {
    Reporter.log("the module replaces the default factory of tentackle-core", true);
    assertSame(KeyringFactory.getInstance().getClass(), PlatformKeyringFactory.class);
  }

  @Test
  public void testForcedFallback() {
    Reporter.log("the keyrings of tentackle-core can still be forced", true);
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, MemoryKeyring.NAME);
    assertEquals(new PlatformKeyringFactory().getKeyring().getName(), MemoryKeyring.NAME);
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testForcedUnknown() {
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, "no-such-keyring");
    new PlatformKeyringFactory().getKeyring();
  }

  @Test
  public void testForeignPlatforms() {
    Reporter.log("the keyrings of other platforms fail cleanly", true);
    PlatformKeyringFactory factory = new PlatformKeyringFactory();
    String platform = factory.getPlatformKeyringName();
    for (String name : List.of(MacKeychain.NAME, WindowsCredentialManager.NAME, LibSecretKeyring.NAME)) {
      if (!name.equals(platform)) {
        assertThrows(KeyringException.class, () -> factory.createKeyring(name));
      }
    }
  }

  @Test
  public void testSelection() {
    Reporter.log("some keyring is always found", true);
    Keyring keyring = new PlatformKeyringFactory().getKeyring();
    assertNotNull(keyring);
    Reporter.log("selected: " + keyring.getName(), true);
  }

}
