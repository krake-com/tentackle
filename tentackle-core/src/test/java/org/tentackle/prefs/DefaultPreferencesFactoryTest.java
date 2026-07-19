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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link DefaultPreferencesFactory}.
 * <p>
 * A local subclass with in-memory roots is used so that the tests never touch the operating system's
 * preference store.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultPreferencesFactoryTest {

  /**
   * A factory backed by two fresh in-memory roots, independent of the registered singleton.
   */
  private static class InMemoryFactory extends DefaultPreferencesFactory {
    private final InMemoryPreferences userBacking = new InMemoryPreferences(true);
    private final InMemoryPreferences systemBacking = new InMemoryPreferences(false);

    @Override
    public DefaultPreferences userRoot() {
      return new DefaultPreferences(userBacking);
    }

    @Override
    public DefaultPreferences systemRoot() {
      return new DefaultPreferences(systemBacking);
    }
  }


  @Test
  public void testDefaultFlags() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    assertFalse(factory.isAutoSync());
    assertFalse(factory.isReadOnly());
    assertFalse(factory.isSystemOnly());
  }

  @Test
  public void testFlagSetters() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    factory.setAutoSync(true);
    factory.setReadOnly(true);
    factory.setSystemOnly(true);
    assertTrue(factory.isAutoSync());
    assertTrue(factory.isReadOnly());
    assertTrue(factory.isSystemOnly());
  }

  @Test
  public void testInvalidateDoesNotThrow() {
    new InMemoryFactory().invalidate();
  }

  @Test
  public void testGetUserRootIsCached() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    assertSame(factory.getUserRoot(), factory.getUserRoot());
  }

  @Test
  public void testGetSystemRootIsCached() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    assertSame(factory.getSystemRoot(), factory.getSystemRoot());
  }

  @Test
  public void testUserAndSystemRootsAreDistinct() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    factory.getUserRoot().put("scope", "user");
    factory.getSystemRoot().put("scope", "system");
    assertEquals(factory.getUserRoot().get("scope", null), "user");
    assertEquals(factory.getSystemRoot().get("scope", null), "system");
  }

  @Test
  public void testSystemOnlyMapsUserRootToSystemScope() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    factory.setSystemOnly(true);
    // in system-only mode the user root writes into the system scope
    factory.getUserRoot().put("key", "value");
    assertEquals(factory.getSystemRoot().get("key", null), "value");
  }

  @Test
  public void testUserNodeForPackage() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    DefaultPreferences node = factory.userNodeForPackage(getClass());
    assertEquals(node.absolutePath(), "/org/tentackle/prefs");
    assertTrue(node.isUserNode());
  }

  @Test
  public void testSystemNodeForPackage() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    DefaultPreferences node = factory.systemNodeForPackage(getClass());
    assertEquals(node.absolutePath(), "/org/tentackle/prefs");
    assertFalse(node.isUserNode());
  }

  @Test
  public void testNodeNameForClass() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    assertEquals(factory.nodeName(String.class), "/java/lang");
  }

  @Test
  public void testNodeNameForArrayThrows() {
    DefaultPreferencesFactory factory = new InMemoryFactory();
    assertThrows(IllegalArgumentException.class, () -> factory.nodeName(int[].class));
  }

  @Test
  public void testDefaultRootsAreNotNull() {
    // exercises the real userRoot()/systemRoot() against java.util.prefs (does not write anything)
    DefaultPreferencesFactory factory = new DefaultPreferencesFactory();
    assertNotNull(factory.userRoot());
    assertNotNull(factory.systemRoot());
  }
}
