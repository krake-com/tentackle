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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link StoredBundleControl}.
 * <p>
 * The tests run without a {@link org.tentackle.session.Session}, i.e. nothing can be loaded from a database
 * and the control must behave like a plain {@link ResourceBundle.Control}.
 */
@SuppressWarnings({"exports", "missing-explicit-ctor"})
public class StoredBundleControlTest {

  /** a test bundle in src/test/resources. */
  private static final String BUNDLE = "org.tentackle.i18n.ControlBundle";

  private StoredBundleControl control;

  @BeforeMethod
  public void setUp() {
    control = new StoredBundleControl();
  }

  @AfterMethod
  public void tearDown() {
    StoredBundleControl.setFallbackToProperties(true);
    ResourceBundle.clearCache(getClass().getClassLoader());
  }


  @Test
  public void testFallbackToPropertiesIsEnabledByDefault() {
    assertTrue(StoredBundleControl.isFallbackToProperties());
  }

  @Test
  public void testSetFallbackToProperties() {
    StoredBundleControl.setFallbackToProperties(false);
    assertFalse(StoredBundleControl.isFallbackToProperties());
  }

  @Test
  public void testNoDomainContextWithoutSession() {
    assertNull(control.getDomainContext());
  }

  @Test
  public void testFormatsAreTheStandardOnesWithoutSession() {
    assertEquals(control.getFormats(BUNDLE), ResourceBundle.Control.FORMAT_DEFAULT);
  }

  @Test
  public void testCreateUdkForTheDefaultLocale() {
    assertEquals(control.createUDK(BUNDLE, Locale.ROOT), new StoredBundleUDK(BUNDLE, null));
  }

  @Test
  public void testCreateUdkForALanguage() {
    assertEquals(control.createUDK(BUNDLE, Locale.GERMAN), new StoredBundleUDK(BUNDLE, "de"));
  }

  @Test
  public void testCreateUdkForALanguageAndCountry() {
    assertEquals(control.createUDK(BUNDLE, Locale.GERMANY), new StoredBundleUDK(BUNDLE, "de_DE"));
  }

  @Test
  public void testLoadStoredBundleWithoutSession() {
    assertNull(control.loadStoredBundle(new StoredBundleUDK(BUNDLE, null)));
  }

  @Test
  public void testNewBundleFallsBackToProperties() throws Exception {
    ResourceBundle bundle = control.newBundle(BUNDLE, Locale.ROOT, "java.properties", getClass().getClassLoader(), false);
    assertNotNull(bundle);
    assertEquals(bundle.getString("greeting"), "hello");
  }

  @Test
  public void testNewBundleFallsBackToPropertiesForALocale() throws Exception {
    ResourceBundle bundle = control.newBundle(BUNDLE, Locale.GERMAN, "java.properties", getClass().getClassLoader(), false);
    assertNotNull(bundle);
    assertEquals(bundle.getString("greeting"), "hallo");
  }

  /**
   * The format is empty if determined by {@link StoredBundleControl#getFormats(String)} while a session was open.
   */
  @Test
  public void testNewBundleTreatsAnEmptyFormatAsProperties() throws Exception {
    ResourceBundle bundle = control.newBundle(BUNDLE, Locale.ROOT, "", getClass().getClassLoader(), false);
    assertNotNull(bundle);
    assertEquals(bundle.getString("greeting"), "hello");
  }

  @Test
  public void testNewBundleReturnsNullForAnUnknownBundle() throws Exception {
    assertNull(control.newBundle("org.tentackle.i18n.NoSuchBundle", Locale.ROOT, "java.properties",
                                 getClass().getClassLoader(), false));
  }

  @Test
  public void testNewBundleReturnsNullIfFallbackToPropertiesIsDisabled() throws Exception {
    StoredBundleControl.setFallbackToProperties(false);
    assertNull(control.newBundle(BUNDLE, Locale.ROOT, "java.properties", getClass().getClassLoader(), false));
  }

  @Test
  public void testNewBundleRequiresABaseName() {
    expectThrows(NullPointerException.class,
                 () -> control.newBundle(null, Locale.ROOT, "java.properties", getClass().getClassLoader(), false));
  }

  @Test
  public void testNewBundleRequiresALocale() {
    expectThrows(NullPointerException.class,
                 () -> control.newBundle(BUNDLE, null, "java.properties", getClass().getClassLoader(), false));
  }

  @Test
  public void testNewBundleRequiresAFormatAndLoader() {
    expectThrows(IllegalArgumentException.class,
                 () -> control.newBundle(BUNDLE, Locale.ROOT, null, getClass().getClassLoader(), false));
    expectThrows(IllegalArgumentException.class,
                 () -> control.newBundle(BUNDLE, Locale.ROOT, "java.properties", null, false));
  }

}
