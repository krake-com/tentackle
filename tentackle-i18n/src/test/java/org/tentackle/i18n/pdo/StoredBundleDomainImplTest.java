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

package org.tentackle.i18n.pdo;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.misc.TrackedList;
import org.tentackle.pdo.Pdo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StoredBundleDomainImpl}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleDomainImplTest {

  private StoredBundle bundle;
  private TrackedList<StoredBundleKey> keys;

  @BeforeMethod
  public void setUp() {
    bundle = Pdo.create(StoredBundle.class);
    bundle.setName("org.tentackle.i18n.DemoBundle");
    keys = bundle.getKeys();
  }


  @Test
  public void testFactoryWiresTheDomainDelegate() {
    assertEquals(bundle.getDomainDelegate().getClass(), StoredBundleDomainImpl.class);
  }

  @Test
  public void testFactoryWiresThePersistenceMock() {
    assertEquals(bundle.getPersistenceDelegate().getClass(), StoredBundlePersistenceMock.class);
  }

  @Test
  public void testToStringWithoutLocale() {
    assertEquals(bundle.toString(), "org.tentackle.i18n.DemoBundle");
  }

  @Test
  public void testToStringWithLocale() {
    bundle.setLocale("de_DE");
    assertEquals(bundle.toString(), "org.tentackle.i18n.DemoBundle_de_DE");
  }

  @Test
  public void testGetTranslationOfUnknownKey() {
    assertNull(bundle.getTranslation("greeting"));
  }

  @Test
  public void testSetTranslationAddsKey() {
    bundle.setTranslation("greeting", "hello");
    assertEquals(keys.size(), 1);
    assertEquals(keys.get(0).getKey(), "greeting");
    assertEquals(keys.get(0).getValue(), "hello");
    assertEquals(bundle.getTranslation("greeting"), "hello");
  }

  @Test
  public void testSetTranslationUpdatesExistingKey() {
    bundle.setTranslation("greeting", "hello");
    bundle.setTranslation("greeting", "hi");
    assertEquals(keys.size(), 1);
    assertEquals(bundle.getTranslation("greeting"), "hi");
  }

  @Test
  public void testSetTranslationRemovesKeyIfValueIsNull() {
    bundle.setTranslation("greeting", "hello");
    bundle.setTranslation("farewell", "bye");
    bundle.setTranslation("greeting", null);
    assertEquals(keys.size(), 1);
    assertEquals(keys.get(0).getKey(), "farewell");
    assertNull(bundle.getTranslation("greeting"));
  }

  @Test
  public void testSetTranslationOfUnknownKeyToNullIsIgnored() {
    bundle.setTranslation("greeting", null);
    assertTrue(keys.isEmpty());
  }

  @Test
  public void testTranslationCacheIsInvalidatedBySetTranslation() {
    bundle.setTranslation("greeting", "hello");
    assertEquals(bundle.getTranslation("greeting"), "hello");   // fills the cache
    bundle.setTranslation("greeting", "hi");
    assertEquals(bundle.getTranslation("greeting"), "hi");
  }

  @Test
  public void testGetTranslationSeesDirectlyAddedKeys() {
    StoredBundleKey key = Pdo.create(StoredBundleKey.class);
    key.setKey("greeting");
    key.setValue("hello");
    keys.add(key);
    assertEquals(bundle.getTranslation("greeting"), "hello");
  }

  @Test
  public void testUniqueDomainKeyIsProvided() {
    assertTrue(bundle.isUniqueDomainKeyProvided());
    assertEquals(bundle.getUniqueDomainKeyType(), StoredBundleUDK.class);
  }

  @Test
  public void testGetUniqueDomainKey() {
    bundle.setLocale("de");
    assertEquals(bundle.getUniqueDomainKey(), new StoredBundleUDK("org.tentackle.i18n.DemoBundle", "de"));
  }

  @Test
  public void testSetUniqueDomainKey() {
    bundle.setUniqueDomainKey(new StoredBundleUDK("org.tentackle.i18n.OtherBundle", "fr"));
    assertEquals(bundle.getName(), "org.tentackle.i18n.OtherBundle");
    assertEquals(bundle.getLocale(), "fr");
  }

  @Test
  public void testSingularAndPlural() {
    assertEquals(bundle.getSingular(), "bundle");
    assertEquals(bundle.getPlural(), "bundles");
  }

}
