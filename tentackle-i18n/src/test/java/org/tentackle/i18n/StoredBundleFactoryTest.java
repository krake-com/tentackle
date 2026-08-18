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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.common.BundleFactory;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link StoredBundleFactory} and {@link StoredBundleControlProvider}.
 * <p>
 * The tests run without a {@link org.tentackle.session.Session}, i.e. the factory must fall back
 * to the resource bundles from the properties files.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleFactoryTest {

  /** a test bundle in src/test/resources. */
  private static final String BUNDLE = "org.tentackle.i18n.FactoryBundle";

  @AfterMethod
  public void tearDown() {
    StoredBundleFactory.setEnabled(true);
    BundleFactory.getInstance().clearCache();
  }


  @Test
  public void testStoredBundleFactoryIsTheConfiguredBundleFactory() {
    assertEquals(BundleFactory.getInstance().getClass(), StoredBundleFactory.class);
  }

  @Test
  public void testLoadingFromBackendIsEnabledByDefault() {
    assertTrue(StoredBundleFactory.isEnabled());
  }

  @Test
  public void testSetEnabled() {
    StoredBundleFactory.setEnabled(false);
    assertFalse(StoredBundleFactory.isEnabled());
  }

  @Test
  public void testBundleControlIsTheStoredBundleControl() {
    assertEquals(BundleFactory.getInstance().getControl(BUNDLE).getClass(), StoredBundleControl.class);
  }

  @Test
  public void testBundleControlIsCached() {
    assertSame(BundleFactory.getInstance().getControl(BUNDLE), BundleFactory.getInstance().getControl(BUNDLE));
  }

  @Test
  public void testControlProviderAlwaysReturnsTheSameControl() {
    StoredBundleControlProvider provider = new StoredBundleControlProvider();
    ResourceBundle.Control control = provider.getControl(BUNDLE);
    assertEquals(control.getClass(), StoredBundleControl.class);
    assertSame(provider.getControl("org.tentackle.i18n.AnotherBundle"), control);
  }

  @Test
  public void testFindBundleFallsBackToProperties() {
    assertEquals(BundleFactory.getInstance().findBundle(BUNDLE, Locale.ROOT).getString("greeting"), "hello");
  }

  @Test
  public void testFindLocalizedBundle() {
    assertEquals(BundleFactory.getInstance().findBundle(BUNDLE, Locale.GERMANY).getString("greeting"), "hallo");
  }

  @Test
  public void testFindBundleFallsBackToTheDefaultBundle() {
    assertEquals(BundleFactory.getInstance().findBundle(BUNDLE, Locale.FRANCE).getString("greeting"), "hello");
  }

  @Test
  public void testFindBundleWithALeadingSlashInTheBasename() {
    assertEquals(BundleFactory.getInstance().findBundle("/" + BUNDLE, Locale.ROOT).getString("greeting"), "hello");
  }

  @Test
  public void testFindBundleOfAnUnknownBundle() {
    expectThrows(MissingResourceException.class,
                 () -> BundleFactory.getInstance().findBundle("org.tentackle.i18n.NoSuchBundle", Locale.ROOT));
  }

  @Test
  public void testFindBundleWithAnExplicitClassLoader() {
    assertEquals(BundleFactory.getInstance().findBundle(BUNDLE, Locale.ROOT, getClass().getClassLoader())
                              .getString("greeting"), "hello");
  }

}
