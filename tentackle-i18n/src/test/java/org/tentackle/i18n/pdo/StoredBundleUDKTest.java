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

import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.pdo.Pdo;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StoredBundleUDK}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleUDKTest {

  @Test
  public void testCreateFromPdo() {
    StoredBundle bundle = Pdo.create(StoredBundle.class);
    bundle.setName("org.tentackle.i18n.DemoBundle");
    bundle.setLocale("de");
    assertEquals(new StoredBundleUDK(bundle), new StoredBundleUDK("org.tentackle.i18n.DemoBundle", "de"));
  }

  @Test
  public void testEquality() {
    assertEquals(new StoredBundleUDK("bundle", "de"), new StoredBundleUDK("bundle", "de"));
    assertNotEquals(new StoredBundleUDK("bundle", "de"), new StoredBundleUDK("bundle", "en"));
    assertNotEquals(new StoredBundleUDK("bundle", null), new StoredBundleUDK("bundle", "de"));
  }

  @Test
  public void testCompareToIsZeroForEqualKeys() {
    assertEquals(new StoredBundleUDK("bundle", "de").compareTo(new StoredBundleUDK("bundle", "de")), 0);
    assertEquals(new StoredBundleUDK("bundle", null).compareTo(new StoredBundleUDK("bundle", null)), 0);
  }

  @Test
  public void testCompareToOrdersByNameFirst() {
    assertTrue(new StoredBundleUDK("alpha", "en").compareTo(new StoredBundleUDK("beta", "de")) < 0);
    assertTrue(new StoredBundleUDK("beta", "de").compareTo(new StoredBundleUDK("alpha", "en")) > 0);
  }

  @Test
  public void testCompareToOrdersByLocaleIfNamesAreEqual() {
    assertTrue(new StoredBundleUDK("bundle", "de").compareTo(new StoredBundleUDK("bundle", "en")) < 0);
    assertTrue(new StoredBundleUDK("bundle", "en").compareTo(new StoredBundleUDK("bundle", "de")) > 0);
  }

  @Test
  public void testCompareToSortsTheDefaultLocaleFirst() {
    List<StoredBundleUDK> keys = new ArrayList<>(List.of(new StoredBundleUDK("bundle", "en"),
                                                         new StoredBundleUDK("bundle", "de")));
    keys.add(new StoredBundleUDK("bundle", null));
    keys.sort(null);
    assertEquals(keys, List.of(new StoredBundleUDK("bundle", null),
                               new StoredBundleUDK("bundle", "de"),
                               new StoredBundleUDK("bundle", "en")));
  }

}
