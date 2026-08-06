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

package org.tentackle.i18n;

import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.pdo.Pdo;

import java.util.Collections;
import java.util.HashSet;
import java.util.MissingResourceException;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link StoredResourceBundle}.
 * <p>
 * The stored bundles are created from persistence mocks, i.e. without a database.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredResourceBundleTest {

  /**
   * Creates a stored bundle with translations.
   *
   * @param name the bundle name
   * @param locale the locale, null if default
   * @param translations the key/value pairs
   * @return the stored bundle
   */
  private static StoredBundle createBundle(String name, String locale, String... translations) {
    StoredBundle bundle = Pdo.create(StoredBundle.class);
    bundle.setName(name);
    bundle.setLocale(locale);
    for (int i = 0; i < translations.length; i += 2) {
      bundle.setTranslation(translations[i], translations[i + 1]);
    }
    return bundle;
  }


  /**
   * Adds a bundle key directly, bypassing {@code setTranslation}.
   * <p>
   * Simulates a row that reached the database without validation — {@code key} and {@code value} are
   * both {@code @NotNull} in the model, so this cannot be produced through the regular domain API.
   *
   * @param bundle the stored bundle
   * @param key the key, null to simulate a missing key
   * @param value the value, null to simulate a missing value
   */
  private static void addKeyBluntly(StoredBundle bundle, String key, String value) {
    StoredBundleKey bundleKey = Pdo.create(StoredBundleKey.class);
    bundleKey.setKey(key);
    bundleKey.setValue(value);
    bundle.getKeys().add(bundleKey);
  }


  @Test
  public void testEntryWithoutAValueIsIgnoredButTheBundleStillWorks() {
    StoredBundle bundle = createBundle("DemoBundle", null, "greeting", "hello");
    addKeyBluntly(bundle, "broken", null);
    StoredResourceBundle resourceBundle = new StoredResourceBundle(bundle);
    assertEquals(resourceBundle.getString("greeting"), "hello");
    assertNull(resourceBundle.handleGetObject("broken"));
    assertEquals(Collections.list(resourceBundle.getKeys()), Collections.singletonList("greeting"));
  }

  @Test
  public void testEntryWithoutAKeyIsIgnoredButTheBundleStillWorks() {
    StoredBundle bundle = createBundle("DemoBundle", null, "greeting", "hello");
    addKeyBluntly(bundle, null, "orphan");
    StoredResourceBundle resourceBundle = new StoredResourceBundle(bundle);
    assertEquals(resourceBundle.getString("greeting"), "hello");
    assertEquals(Collections.list(resourceBundle.getKeys()), Collections.singletonList("greeting"));
  }

  @Test
  public void testGetString() {
    StoredResourceBundle bundle = new StoredResourceBundle(
            createBundle("DemoBundle", null, "greeting", "hello", "farewell", "bye"));
    assertEquals(bundle.getString("greeting"), "hello");
    assertEquals(bundle.getString("farewell"), "bye");
  }

  @Test
  public void testUnknownKey() {
    StoredResourceBundle bundle = new StoredResourceBundle(createBundle("DemoBundle", null, "greeting", "hello"));
    expectThrows(MissingResourceException.class, () -> bundle.getString("nosuchkey"));
    assertNull(bundle.handleGetObject("nosuchkey"));
  }

  @Test
  public void testToStringIsTheStoredBundleName() {
    StoredResourceBundle bundle = new StoredResourceBundle(createBundle("DemoBundle", "de_DE", "greeting", "hallo"));
    assertEquals(bundle.toString(), "DemoBundle_de_DE");
  }

  @Test
  public void testEmptyBundle() {
    StoredResourceBundle bundle = new StoredResourceBundle(createBundle("DemoBundle", null));
    assertEquals(Collections.list(bundle.getKeys()), Collections.emptyList());
  }

  @Test
  public void testGetKeysWithoutParent() {
    StoredResourceBundle bundle = new StoredResourceBundle(
            createBundle("DemoBundle", null, "greeting", "hello", "farewell", "bye"));
    assertEquals(new HashSet<>(Collections.list(bundle.getKeys())), Set.of("greeting", "farewell"));
  }

  @Test
  public void testParentIsConsultedForMissingKeys() {
    StoredResourceBundle parent = new StoredResourceBundle(
            createBundle("DemoBundle", null, "greeting", "hello", "farewell", "bye"));
    StoredResourceBundle child = new StoredResourceBundle(createBundle("DemoBundle", "de", "greeting", "hallo"));
    child.setParentBundle(parent);
    assertEquals(child.getString("greeting"), "hallo");    // overridden by the child
    assertEquals(child.getString("farewell"), "bye");      // inherited from the parent
  }

  @Test
  public void testGetKeysIncludesTheParentKeysOnlyOnce() {
    StoredResourceBundle parent = new StoredResourceBundle(
            createBundle("DemoBundle", null, "greeting", "hello", "farewell", "bye"));
    StoredResourceBundle child = new StoredResourceBundle(createBundle("DemoBundle", "de", "greeting", "hallo"));
    child.setParentBundle(parent);
    assertEquals(Collections.list(child.getKeys()).size(), 2);
    assertEquals(new HashSet<>(Collections.list(child.getKeys())), Set.of("greeting", "farewell"));
  }

  @Test
  public void testUnknownKeyWithParent() {
    StoredResourceBundle parent = new StoredResourceBundle(createBundle("DemoBundle", null, "greeting", "hello"));
    StoredResourceBundle child = new StoredResourceBundle(createBundle("DemoBundle", "de", "greeting", "hallo"));
    child.setParentBundle(parent);
    expectThrows(MissingResourceException.class, () -> child.getString("nosuchkey"));
  }

  @Test
  public void testHandleGetObjectRequiresAKey() {
    StoredResourceBundle bundle = new StoredResourceBundle(createBundle("DemoBundle", null, "greeting", "hello"));
    expectThrows(NullPointerException.class, () -> bundle.handleGetObject(null));
  }

}
