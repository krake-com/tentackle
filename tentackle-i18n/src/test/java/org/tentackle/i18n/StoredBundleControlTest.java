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
import org.tentackle.session.Session;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
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
    StoredBundleFactory.setEnabled(true);
    Session.setCurrentSession(null);
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

  /**
   * Bundles must stay cached by {@link ResourceBundle} as long as loading them did not fail.
   * <p>
   * Only a failed backend load switches to {@link ResourceBundle.Control#TTL_DONT_CACHE}, so that the
   * properties fallback does not outlive the failure. Returning that unconditionally would re-read every
   * bundle on every single lookup.
   */
  @Test
  public void testTimeToLiveIsTheStandardOneWithoutAFailedLoad() {
    assertEquals(control.getTimeToLive(BUNDLE, Locale.ROOT), ResourceBundle.Control.TTL_NO_EXPIRATION_CONTROL);
  }

  @Test
  public void testTimeToLiveIsTheStandardOneAfterASuccessfulLoad() throws Exception {
    control.newBundle(BUNDLE, Locale.ROOT, "java.properties", getClass().getClassLoader(), false);
    assertEquals(control.getTimeToLive(BUNDLE, Locale.ROOT), ResourceBundle.Control.TTL_NO_EXPIRATION_CONTROL);
  }

  @Test
  public void testFormatsAreEmptyWithSession() {
    Session.setCurrentSession(createSessionStub());
    assertEquals(control.getFormats(BUNDLE), List.of(""));
  }

  /**
   * Disabling the {@link StoredBundleFactory} must disable the control as well, since bundles not registered
   * for a named module are loaded through the control and never reach the factory.
   */
  @Test
  public void testFormatsAreTheStandardOnesIfDisabled() {
    Session.setCurrentSession(createSessionStub());
    StoredBundleFactory.setEnabled(false);
    assertEquals(control.getFormats(BUNDLE), ResourceBundle.Control.FORMAT_DEFAULT);
  }

  /**
   * If disabled, the backend must not be accessed at all, even though a session is available.
   * An attempt would fail with the session stub and mark the load as failed (not cached).
   */
  @Test
  public void testNewBundleDoesNotAccessTheBackendIfDisabled() throws Exception {
    Session.setCurrentSession(createSessionStub());
    StoredBundleFactory.setEnabled(false);
    ResourceBundle bundle = control.newBundle(BUNDLE, Locale.ROOT, "java.properties", getClass().getClassLoader(), false);
    assertNotNull(bundle);
    assertEquals(bundle.getString("greeting"), "hello");
    assertNull(control.getDomainContext());
    assertEquals(control.getTimeToLive(BUNDLE, Locale.ROOT), ResourceBundle.Control.TTL_NO_EXPIRATION_CONTROL);
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
  public void testCreateUdkNormalizesASlashedBasename() {
    assertEquals(control.createUDK("org/tentackle/i18n/ControlBundle", Locale.GERMAN),
                 new StoredBundleUDK(BUNDLE, "de"));
  }

  /**
   * A base name may contain underscores itself, so the locale must be taken from the locale,
   * not from the first underscore found in the name.
   */
  @Test
  public void testCreateUdkForABasenameContainingAnUnderscore() {
    assertEquals(control.createUDK("org.tentackle.i18n.Control_Bundle", Locale.ROOT),
                 new StoredBundleUDK("org.tentackle.i18n.Control_Bundle", null));
    assertEquals(control.createUDK("org.tentackle.i18n.Control_Bundle", Locale.GERMANY),
                 new StoredBundleUDK("org.tentackle.i18n.Control_Bundle", "de_DE"));
  }

  /**
   * {@code Texts_de} for the default locale and {@code Texts} for German are different bundles
   * and must not collapse onto the same key.
   */
  @Test
  public void testCreateUdkDoesNotConfuseABasenameSuffixWithALocale() {
    assertNotEquals(control.createUDK(BUNDLE + "_de", Locale.ROOT),
                    control.createUDK(BUNDLE, Locale.GERMAN));
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


  /**
   * Creates a session stub that throws on every method invoked.
   *
   * @return the session
   */
  private Session createSessionStub() {
    return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> {
          throw new UnsupportedOperationException(method.getName());
        });
  }

}
