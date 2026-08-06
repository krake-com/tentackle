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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.pdo.Pdo;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the locale fall-back chain built by
 * {@link StoredBundleFactory#loadStoredBundleChain(StoredBundleUDK, java.util.function.Function)}.
 * <p>
 * The stored bundles are created via the PDO mock, so the tests run without a
 * {@link org.tentackle.session.Session}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleChainTest {

  /** the bundle base name used by all tests. */
  private static final String NAME = "org.tentackle.i18n.ChainBundle";

  private StoredBundleFactory factory;
  private Map<StoredBundleUDK, StoredResourceBundle> storage;

  @BeforeMethod
  public void setUp() {
    factory = new StoredBundleFactory();
    storage = new HashMap<>();
  }


  /**
   * Adds a stored bundle with a single translation to the simulated storage.
   *
   * @param locale the locale, null for the default bundle
   * @param translations the translations as key/value pairs
   */
  private void store(String locale, String... translations) {
    StoredBundle bundle = Pdo.create(StoredBundle.class);
    bundle.setName(NAME);
    bundle.setLocale(locale);
    for (int ndx = 0; ndx < translations.length; ndx += 2) {
      bundle.setTranslation(translations[ndx], translations[ndx + 1]);
    }
    storage.put(new StoredBundleUDK(NAME, locale), new StoredResourceBundle(bundle));
  }

  /**
   * Loads the chain for a locale from the simulated storage.
   *
   * @param locale the locale, null for the default bundle
   * @return the bundle, null if nothing stored
   */
  private StoredResourceBundle load(String locale) {
    return factory.loadStoredBundleChain(new StoredBundleUDK(NAME, locale), storage::get);
  }


  @Test
  public void testNothingStoredAtAll() {
    assertNull(load("de_DE"));
  }

  @Test
  public void testDefaultBundleOnly() {
    store(null, "greeting", "hello");
    assertEquals(load(null).getString("greeting"), "hello");
  }

  @Test
  public void testLocaleFallsBackToAnExistingParentBundle() {
    store(null, "greeting", "hello");
    // nothing stored for de or de_DE -> the default bundle answers
    assertEquals(load("de_DE").getString("greeting"), "hello");
  }

  @Test
  public void testCompleteChainIsLinked() {
    store(null, "greeting", "hello", "farewell", "bye");
    store("de", "greeting", "hallo");
    store("de_DE", "greeting", "gruezi");
    StoredResourceBundle bundle = load("de_DE");
    assertEquals(bundle.getString("greeting"), "gruezi");     // from de_DE
    assertEquals(bundle.getString("farewell"), "bye");        // inherited from the default bundle
  }

  /**
   * A missing intermediate locale must be skipped, not terminate the chain.
   * <p>
   * This is what {@link java.util.ResourceBundle} does for its list of candidate locales.
   */
  @Test
  public void testMissingIntermediateLocaleIsSkipped() {
    store(null, "greeting", "hello", "farewell", "bye");
    // no bundle stored for "de"
    store("de_DE", "greeting", "gruezi");
    StoredResourceBundle bundle = load("de_DE");
    assertEquals(bundle.getString("greeting"), "gruezi");
    assertEquals(bundle.getString("farewell"), "bye");
  }

  @Test
  public void testKeysOfTheWholeChainAreEnumerated() {
    store(null, "greeting", "hello", "farewell", "bye");
    store("de_DE", "greeting", "gruezi");
    assertEquals(java.util.Collections.list(load("de_DE").getKeys()).stream().sorted().toList(),
                 java.util.List.of("farewell", "greeting"));
  }

  @Test
  public void testUnknownKeyThrowsEvenWithAChain() {
    store(null, "greeting", "hello");
    store("de_DE", "greeting", "gruezi");
    expectThrows(java.util.MissingResourceException.class, () -> load("de_DE").getString("nosuchkey"));
  }

  @Test
  public void testMostSpecificBundleWithoutAnyParent() {
    store("de_DE", "greeting", "gruezi");
    StoredResourceBundle bundle = load("de_DE");
    assertEquals(bundle.getString("greeting"), "gruezi");
    expectThrows(java.util.MissingResourceException.class, () -> bundle.getString("farewell"));
  }

}
