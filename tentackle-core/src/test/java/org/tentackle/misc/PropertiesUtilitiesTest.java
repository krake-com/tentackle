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

package org.tentackle.misc;

import org.tentackle.common.EncryptedProperties;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link PropertiesUtilities}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PropertiesUtilitiesTest {

  private final PropertiesUtilities utilities = PropertiesUtilities.getInstance();

  @Test
  public void testGetInstanceIsSingleton() {
    assertSame(PropertiesUtilities.getInstance(), utilities);
  }

  @Test
  public void testCreateEmpty() {
    EncryptedProperties props = utilities.create();
    assertTrue(props.isEmpty());
  }

  @Test
  public void testCreateWithCapacity() {
    EncryptedProperties props = utilities.create(16);
    assertTrue(props.isEmpty());
    props.setProperty("a", "b");
    assertEquals(props.getProperty("a"), "b");
  }

  @Test
  public void testToEnvironmentVariableSimple() {
    assertEquals(utilities.toEnvironmentVariable("my-Url"), "MYURL");
  }

  @Test
  public void testToEnvironmentVariableDotBecomesUnderscore() {
    assertEquals(utilities.toEnvironmentVariable("tentackle.url"), "TENTACKLE_URL");
  }

  @Test
  public void testToEnvironmentVariableDropsSpecialChars() {
    // spaces, dashes, slashes etc. are removed, dots become underscores, digits are kept
    assertEquals(utilities.toEnvironmentVariable("a.b-c/d 1"), "A_BCD1");
  }

  @Test
  public void testToEnvironmentVariableEmpty() {
    assertEquals(utilities.toEnvironmentVariable(""), "");
  }

  @Test
  public void testToEnvironmentVariableMultipleDots() {
    assertEquals(utilities.toEnvironmentVariable("a.b.c"), "A_B_C");
  }

  @Test
  public void testLoadIfExistsUnknownReturnsNull() {
    assertNull(utilities.loadIfExists("this-resource-does-not-exist-" + System.nanoTime()));
  }

  @Test
  public void testLoadUnknownThrows() {
    assertThrows(FileNotFoundException.class,
                 () -> utilities.load("this-resource-does-not-exist-" + System.nanoTime()));
  }

  @Test
  public void testApplyEnvironmentIgnoresAbsentAndNonExistingKeys() {
    Properties props = new Properties();
    // key that does not correspond to any environment variable
    props.setProperty("nosuchproperty" + System.nanoTime(), "original");
    boolean applied = utilities.applyEnvironment(props, null);
    assertFalse(applied);
  }

  @Test
  public void testApplyEnvironmentSkipsKeysNotStartingWithLetter() {
    Properties props = new Properties();
    props.setProperty("1leading", "original");
    // even if some env var matched, keys not starting with a letter are ignored
    boolean applied = utilities.applyEnvironment(props, null);
    assertFalse(applied);
    assertEquals(props.getProperty("1leading"), "original");
  }

  @Test
  public void testApplyEnvironmentOverridesExistingProperty() {
    // find an existing environment variable whose name is a plain
    // letters/digits identifier starting with a letter, so that
    // toEnvironmentVariable(name) == name
    Map.Entry<String, String> candidate = null;
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      String name = entry.getKey();
      if (name.matches("[A-Za-z][A-Za-z0-9]*") && entry.getValue() != null) {
        candidate = entry;
        break;
      }
    }
    if (candidate == null) {
      // no suitable environment variable available, nothing to assert
      return;
    }

    Properties props = new Properties();
    props.setProperty(candidate.getKey(), "placeholder");
    boolean applied = utilities.applyEnvironment(props, null);
    assertTrue(applied);
    assertEquals(props.getProperty(candidate.getKey()), candidate.getValue());
  }

  @Test
  public void testApplyEnvironmentDoesNotAddNewProperties() {
    Properties props = new Properties();
    boolean applied = utilities.applyEnvironment(props, null);
    // no existing properties, so nothing can be overridden
    assertFalse(applied);
    assertTrue(props.isEmpty());
  }
}
