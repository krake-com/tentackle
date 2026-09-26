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
import org.testng.SkipException;
import org.testng.annotations.Test;

import org.tentackle.keyring.AbstractKeyring;
import org.tentackle.keyring.Keyring;

import static org.testng.Assert.assertTrue;

/**
 * Tests the credential store of the current operating system.
 * <p>
 * Only run with {@code mvn -Pkeyring test}, since it needs an unlocked credential store, and entries
 * left behind by an aborted run show up in the user's keychain.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PlatformKeyringTest extends AbstractKeyringContractTest {

  @Override
  protected Keyring createKeyring() {
    if (!Boolean.getBoolean("keyring.native")) {
      throw new SkipException("OS credential store not tested, run with -Pkeyring");
    }
    PlatformKeyringFactory factory = new PlatformKeyringFactory();
    AbstractKeyring platform = factory.createKeyring(factory.getPlatformKeyringName());
    if (!platform.isAvailable()) {
      throw new SkipException(platform.getName() + " not available");
    }
    Reporter.log("testing " + platform.getName(), true);
    return platform;
  }

  @Test
  public void testProperties() {
    assertTrue(keyring.isSecure());
    assertTrue(keyring.isPersistent());
  }

}
