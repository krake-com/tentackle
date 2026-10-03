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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.RecordingLog;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.TestPushMojo;

import java.net.MalformedURLException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code push} goal end to end.
 * <p>
 * The goal is the one that fills the backend: it walks the resource bundles of the project and
 * copies their translations into the {@code StoredBundle}s, leaving what is already there
 * untouched unless {@code override} says otherwise.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PushMojoTest extends AbstractI18nMojoTest {

  private TestPushMojo mojo;


  /**
   * Creates the mojo.<br>
   * TestNG runs the base class' configuration methods first, so the project is already set up.
   */
  @BeforeMethod(alwaysRun = true)
  public void setUpMojo() {
    mojo = configure(new TestPushMojo());
  }


  /**
   * A bundle the backend does not know yet is created along with all its translations.
   */
  @Test
  public void createsAStoredBundleForANewBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");

    run(mojo);

    StoredBundle bundle = loadBundle(APP_BUNDLE, null);
    assertNotNull(bundle, "the bundle was not pushed");
    assertNull(bundle.getLocale(), "the default bundle has no locale");
    assertEquals(bundle.getTranslation("greeting"), "Hello");
    assertEquals(bundle.getTranslation("farewell"), "Bye");
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 2 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A translation that already exists in the backend is the authoritative one: it survives the
   * push, since it is what the translators have edited.
   */
  @Test
  public void keepsAnExistingTranslation() throws Exception {
    storeBundle(APP_BUNDLE, null, "greeting", "Welcome");
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");

    run(mojo);

    StoredBundle bundle = loadBundle(APP_BUNDLE, null);
    assertEquals(bundle.getTranslation("greeting"), "Welcome", "the stored translation was overwritten");
    assertEquals(bundle.getTranslation("farewell"), "Bye", "the new key was not added");
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 1 update"),
               getLog().getInfoMessages().toString());
  }

  /**
   * With {@code override} set, the property files win instead.
   */
  @Test
  public void overrideReplacesAnExistingTranslation() throws Exception {
    storeBundle(APP_BUNDLE, null, "greeting", "Welcome");
    bundle(APP_BUNDLE, "greeting=Hello");
    I18nTestSupport.setField(mojo, "override", true);

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, null).getTranslation("greeting"), "Hello");
  }

  /**
   * Even with {@code override}, an unchanged translation is not counted as an update.
   */
  @Test
  public void anUnchangedTranslationIsNoUpdate() throws Exception {
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    bundle(APP_BUNDLE, "greeting=Hello");
    I18nTestSupport.setField(mojo, "override", true);

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * The extra locales are pushed as bundles of their own, one per locale suffix.
   */
  @Test
  public void pushesTheConfiguredLocales() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    properties(resourceName(APP_BUNDLE, "de"), "greeting=Hallo");
    I18nTestSupport.setField(mojo, "locales", "de");

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, null).getTranslation("greeting"), "Hello");
    StoredBundle german = loadBundle(APP_BUNDLE, "de");
    assertNotNull(german, "the german bundle was not pushed");
    assertEquals(german.getTranslation("greeting"), "Hallo");
    assertEquals(loadBundles().size(), 2);
  }

  /**
   * A locale with a country is processed with the suffix {@code ResourceBundle} uses, i.e. with an
   * underscore: the property file {@code AppBundle_de_DE.properties} is stored for the locale
   * {@code de_DE}, which is what the runtime looks up. The language tag notation is accepted as well.
   */
  @Test(dataProvider = "countryLocales")
  public void aLocaleWithACountryUsesTheResourceBundleSuffix(String locales) throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    properties(resourceName(APP_BUNDLE, "de_DE"), "greeting=Hallo");
    I18nTestSupport.setField(mojo, "locales", locales);

    run(mojo);

    StoredBundle bundle = loadBundle(APP_BUNDLE, "de_DE");
    assertNotNull(bundle, "not stored for the locale suffix de_DE");
    assertEquals(bundle.getTranslation("greeting"), "Hallo");
    assertTrue(getLog().getWarnMessages().isEmpty(), getLog().getWarnMessages().toString());
  }

  /**
   * The notations of a locale with a country.
   *
   * @return the locales parameters
   */
  @DataProvider
  public Object[][] countryLocales() {
    return new Object[][] { { "de_DE" }, { "de-DE" } };
  }

  /**
   * More than one locale can be given, separated by commas, semicolons or whitespace.
   */
  @Test
  public void severalLocalesAreSeparatedByPunctuation() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    properties(resourceName(APP_BUNDLE, "de"), "greeting=Hallo");
    properties(resourceName(APP_BUNDLE, "fr"), "greeting=Bonjour");
    I18nTestSupport.setField(mojo, "locales", "de; fr");

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, "de").getTranslation("greeting"), "Hallo");
    assertEquals(loadBundle(APP_BUNDLE, "fr").getTranslation("greeting"), "Bonjour");
  }

  /**
   * A bundle registered by an annotation but without a property file is a warning, not an error:
   * the build goes on, but the missing resource is named.
   */
  @Test
  public void aMissingPropertyFileIsAWarning() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");         // but no AppBundle_fr.properties
    I18nTestSupport.setField(mojo, "locales", "fr");

    run(mojo);

    assertNull(loadBundle(APP_BUNDLE, "fr"));
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "no such resource: " + resourceName(APP_BUNDLE, "fr")),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "2 bundles processed, 1 warning, 0 errors, 1 update"),
               getLog().getInfoMessages().toString());
  }

  /**
   * Every registered bundle is pushed, not just the first one.
   */
  @Test
  public void pushesAllRegisteredBundles() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    bundle(PACKAGE + ".OtherBundle", "farewell=Bye");

    run(mojo);

    assertEquals(loadBundles().size(), 2);
    assertEquals(loadBundle(PACKAGE + ".OtherBundle", null).getTranslation("farewell"), "Bye");
  }

  /**
   * A project without a single registered bundle is fine - there is simply nothing to push.
   */
  @Test
  public void aProjectWithoutBundlesPushesNothing() throws Exception {
    run(mojo);

    assertTrue(loadBundles().isEmpty());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "0 bundles processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * The password is optional: a user without one is connected all the same.
   */
  @Test
  public void thePasswordIsOptional() throws Exception {
    runScript("CREATE USER IF NOT EXISTS nopassword PASSWORD ''; ALTER USER nopassword ADMIN TRUE;");
    bundle(APP_BUNDLE, "greeting=Hello");
    I18nTestSupport.setField(mojo, "user", "nopassword");
    I18nTestSupport.setField(mojo, "password", null);

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, null).getTranslation("greeting"), "Hello");
  }

  /**
   * A project whose classloader cannot be created fails the build instead of being skipped silently.
   */
  @Test
  public void anInvalidClasspathFailsTheBuild() {
    TestPushMojo brokenMojo = configure(new TestPushMojo() {
      @Override
      protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
        throw new MalformedURLException("invalid classpath element");
      }
    });
    bundle(APP_BUNDLE, "greeting=Hello");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> run(brokenMojo));

    assertEquals(ex.getMessage(), "cannot create project classloader");
    assertTrue(loadBundles().isEmpty());
  }

}
