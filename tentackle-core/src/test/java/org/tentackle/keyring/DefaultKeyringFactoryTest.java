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

package org.tentackle.keyring;

import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link DefaultKeyringFactory}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DefaultKeyringFactoryTest {

  @AfterMethod(alwaysRun = true)
  public void clearProperty() {
    System.clearProperty(KeyringFactory.KEYRING_PROPERTY);
  }

  @Test
  public void testServiceFactory() {
    Reporter.log("without tentackle-keyring, the default factory is used", true);
    assertSame(KeyringFactory.getInstance().getClass(), DefaultKeyringFactory.class);
  }

  @Test
  public void testForced() {
    Reporter.log("a keyring can be forced by name", true);
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, MemoryKeyring.NAME);
    DefaultKeyringFactory factory = new DefaultKeyringFactory();
    Keyring keyring = factory.getKeyring();
    assertEquals(keyring.getName(), MemoryKeyring.NAME);
    assertSame(factory.getKeyring(), keyring);
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testForcedUnknown() {
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, "no-such-keyring");
    new DefaultKeyringFactory().getKeyring();
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testForcedUnavailable() {
    Reporter.log("forcing an unusable keyring is an error, not a silent fallback", true);
    // the module's tests provide no cryptor
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, PreferencesKeyring.NAME);
    new DefaultKeyringFactory().getKeyring();
  }

  @Test
  public void testFallback() {
    Reporter.log("without a cryptor, the secrets are kept in memory", true);
    Keyring keyring = new DefaultKeyringFactory().getKeyring();
    assertEquals(keyring.getName(), MemoryKeyring.NAME);
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testPlatformKeyringNotInCore() {
    Reporter.log("the keyrings of the OS need tentackle-keyring", true);
    System.setProperty(KeyringFactory.KEYRING_PROPERTY, "libsecret");
    new DefaultKeyringFactory().getKeyring();
  }

}
