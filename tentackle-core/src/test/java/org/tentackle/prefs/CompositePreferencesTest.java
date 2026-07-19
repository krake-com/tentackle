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

package org.tentackle.prefs;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link CompositePreferences}, where the user scope overrides the system scope.
 * <p>
 * Runs against the in-memory {@link TestPreferencesFactory} registered as the {@link PersistedPreferencesFactory}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompositePreferencesTest {

  /** Composite preferences with user scope overriding system scope. */
  private static class UserSystemPrefs extends CompositePreferences {
    UserSystemPrefs() {
      super(false);
    }
  }

  /** Composite preferences bound to the system scope only. */
  private static class SystemOnlyPrefs extends CompositePreferences {
    SystemOnlyPrefs() {
      super(true);
    }
  }


  @BeforeMethod
  public void resetStore() {
    TestPreferencesFactory.resetBackingStore();
  }


  @Test
  public void testIsSystemOnly() {
    assertFalse(new UserSystemPrefs().isSystemOnly());
    assertTrue(new SystemOnlyPrefs().isSystemOnly());
  }

  @Test
  public void testUserAndSystemPrefsAvailability() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertTrue(prefs.getUserPrefs().isUserNode());
    assertFalse(prefs.getSystemPrefs().isUserNode());

    CompositePreferences systemOnly = new SystemOnlyPrefs();
    assertNull(systemOnly.getUserPrefs());
    assertFalse(systemOnly.getSystemPrefs().isUserNode());
  }

  @Test
  public void testUserOverridesSystem() {
    CompositePreferences prefs = new UserSystemPrefs();
    prefs.getSystemPrefs().put("key", "system");
    assertEquals(prefs.getString("key"), "system");

    prefs.setString("key", "user");
    assertEquals(prefs.getString("key"), "user");
    assertEquals(prefs.getUserPrefs().get("key", null), "user");
  }

  @Test
  public void testSetStringEqualToSystemRemovesUserValue() {
    CompositePreferences prefs = new UserSystemPrefs();
    prefs.getSystemPrefs().put("key", "shared");
    prefs.setString("key", "override");
    assertEquals(prefs.getUserPrefs().get("key", null), "override");

    // setting the same value as the system default removes the redundant user entry
    prefs.setString("key", "shared");
    assertNull(prefs.getUserPrefs().get("key", null));
    assertEquals(prefs.getString("key"), "shared");
  }

  @Test
  public void testSetStringNullRemovesUserValue() {
    CompositePreferences prefs = new UserSystemPrefs();
    prefs.getSystemPrefs().put("key", "system");
    prefs.setString("key", "user");
    prefs.setString("key", null);
    assertNull(prefs.getUserPrefs().get("key", null));
    assertEquals(prefs.getString("key"), "system");
  }

  @Test
  public void testGetStringWithDefault() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertEquals(prefs.getString("missing", "def"), "def");
    prefs.setString("key", "value");
    assertEquals(prefs.getString("key", "def"), "value");
  }

  @Test
  public void testGetStringMissingIsNull() {
    assertNull(new UserSystemPrefs().getString("missing"));
  }

  @Test
  public void testInteger() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getInteger("i"));
    assertEquals(prefs.getInt("i", -1), -1);
    prefs.setInteger("i", 42);
    assertEquals(prefs.getInteger("i"), Integer.valueOf(42));
    assertEquals(prefs.getInt("i", -1), 42);
    prefs.setInteger("i", null);
    assertNull(prefs.getInteger("i"));
  }

  @Test
  public void testLong() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getALong("l"));
    assertEquals(prefs.getLong("l", -1L), -1L);
    prefs.setLong("l", 9_000_000_000L);
    assertEquals(prefs.getALong("l"), Long.valueOf(9_000_000_000L));
    assertEquals(prefs.getLong("l", -1L), 9_000_000_000L);
  }

  @Test
  public void testFloat() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getAFloat("f"));
    assertEquals(prefs.getFloat("f", -1f), -1f, 0.0f);
    prefs.setFloat("f", 1.5f);
    assertEquals(prefs.getAFloat("f"), Float.valueOf(1.5f));
    assertEquals(prefs.getFloat("f", -1f), 1.5f, 0.0f);
  }

  @Test
  public void testDouble() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getADouble("d"));
    assertEquals(prefs.getDouble("d", -1d), -1d, 0.0d);
    prefs.setDouble("d", 2.5d);
    assertEquals(prefs.getADouble("d"), Double.valueOf(2.5d));
    assertEquals(prefs.getDouble("d", -1d), 2.5d, 0.0d);
  }

  @Test
  public void testBoolean() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getABoolean("b"));
    assertFalse(prefs.getBoolean("b", false));
    prefs.setBoolean("b", true);
    assertEquals(prefs.getABoolean("b"), Boolean.TRUE);
    assertTrue(prefs.getBoolean("b", false));
  }

  @Test
  public void testByteArray() {
    CompositePreferences prefs = new UserSystemPrefs();
    assertNull(prefs.getByteArray("ba"));
    byte[] def = {9, 9};
    assertTrue(Arrays.equals(prefs.getByteArray("ba", def), def));

    byte[] data = {1, 2, 3};
    prefs.setByteArray("ba", data);
    assertTrue(Arrays.equals(prefs.getByteArray("ba"), data));
    assertTrue(Arrays.equals(prefs.getUserPrefs().getByteArray("ba", null), data));
  }

  @Test
  public void testByteArrayEqualToSystemRemovesUserValue() {
    CompositePreferences prefs = new UserSystemPrefs();
    byte[] shared = {1, 2, 3};
    prefs.getSystemPrefs().putByteArray("ba", shared);
    prefs.setByteArray("ba", new byte[] {7, 7});
    assertTrue(Arrays.equals(prefs.getUserPrefs().getByteArray("ba", null), new byte[] {7, 7}));

    prefs.setByteArray("ba", shared.clone());
    assertNull(prefs.getUserPrefs().getByteArray("ba", null));
    assertTrue(Arrays.equals(prefs.getByteArray("ba"), shared));
  }

  @Test
  public void testByteArrayNullRemovesUserValue() {
    CompositePreferences prefs = new UserSystemPrefs();
    prefs.setByteArray("ba", new byte[] {1});
    prefs.setByteArray("ba", null);
    assertNull(prefs.getByteArray("ba"));
  }

  @Test
  public void testSystemOnlyWritesToSystemScope() {
    CompositePreferences prefs = new SystemOnlyPrefs();
    prefs.setString("key", "value");
    assertEquals(prefs.getString("key"), "value");
    assertEquals(prefs.getSystemPrefs().get("key", null), "value");
  }

  @Test
  public void testSystemOnlyNullRemoves() {
    CompositePreferences prefs = new SystemOnlyPrefs();
    prefs.setString("key", "value");
    prefs.setString("key", null);
    assertNull(prefs.getString("key"));
  }

  @Test
  public void testFlushAndSyncDoNotThrow() throws Exception {
    CompositePreferences prefs = new UserSystemPrefs();
    prefs.setString("key", "value");
    prefs.flush();
    prefs.sync();
    assertEquals(prefs.getString("key"), "value");

    CompositePreferences systemOnly = new SystemOnlyPrefs();
    systemOnly.setString("key", "value");
    systemOnly.flush();
    systemOnly.sync();
    assertEquals(systemOnly.getString("key"), "value");
  }
}
