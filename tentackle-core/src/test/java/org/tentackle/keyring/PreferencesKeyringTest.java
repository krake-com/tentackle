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

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.tentackle.common.Cryptor;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link PreferencesKeyring}.
 * <p>
 * Uses a node of its own, removed afterward, and a cryptor of its own, since the module provides none.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class PreferencesKeyringTest extends AbstractKeyringContractTest {

  private final String node = PreferencesKeyring.NODE + "-test-" + UUID.randomUUID();   // not below NODE: removed completely
  private final Cryptor cryptor = new Cryptor("0123456789abcdef", "test passphrase");

  @Override
  protected Keyring createKeyring() {
    return new PreferencesKeyring() {
      @Override
      protected Preferences getNode() {
        return Preferences.userRoot().node(node);
      }

      @Override
      protected Cryptor getCryptor() {
        return cryptor;
      }
    };
  }

  @AfterClass(alwaysRun = true)
  public void removeNode() throws BackingStoreException {
    Preferences.userRoot().node(node).removeNode();
  }

  @Test
  public void testProperties() {
    assertFalse(keyring.isSecure());
    assertTrue(keyring.isPersistent());
    assertTrue(((PreferencesKeyring) keyring).isAvailable());
  }

  @Test
  public void testEncrypted() {
    PreferencesKeyring prefsKeyring = (PreferencesKeyring) keyring;
    var id = id("user");
    keyring.store(id, null, "plain-secret".toCharArray());
    String stored = Preferences.userRoot().node(node).get(prefsKeyring.getKey(id), null);
    assertFalse(stored.contains("plain-secret"));
  }

  @Test
  public void testNoCryptor() {
    PreferencesKeyring noCryptor = new PreferencesKeyring() {
      @Override
      protected Cryptor getCryptor() {
        return null;
      }
    };
    assertFalse(noCryptor.isAvailable());
    assertNull(Cryptor.getInstance(), "the module's tests must not provide a cryptor");
  }

}
