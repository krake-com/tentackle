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
import org.apache.maven.project.MavenProject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.i18n.I18nTestSupport.RecordingLog;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.TestVerifyMojo;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code verify} goal end to end.
 * <p>
 * The goal compares the backend to the property files of the project and grades what it finds:
 * a translation missing for the default locale would throw a {@code MissingResourceException} at
 * runtime and therefore fails the build, while everything else - obsolete bundles and keys left
 * over by a refactoring, translations that have drifted apart, bundles nobody has pushed yet -
 * is reported for the developer to act on.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class VerifyMojoTest extends AbstractI18nMojoTest {

  private TestVerifyMojo mojo;


  /**
   * Creates the mojo.<br>
   * TestNG runs the base class' configuration methods first, so the project is already set up.
   */
  @BeforeMethod(alwaysRun = true)
  public void setUpMojo() {
    mojo = configure(new TestVerifyMojo());
  }


  /**
   * A backend holding exactly what the property files hold is reported as clean.
   */
  @Test
  public void passesIfTheBackendMatchesTheProject() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello", "farewell", "Bye");

    run(mojo);

    assertTrue(getLog().getWarnMessages().isEmpty(), getLog().getWarnMessages().toString());
    assertTrue(getLog().getErrorMessages().isEmpty(), getLog().getErrorMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A translation the translators have edited in the backend differs from the property file and
   * is counted as a pending update, not as a problem.
   */
  @Test
  public void reportsADifferingTranslation() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Welcome");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(), "1 differing translation:"),
               getLog().getInfoMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(), "'greeting'"),
               getLog().getInfoMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 1 update"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A stored bundle no annotation registers any more is left over by a refactoring and is
   * reported as obsolete.
   */
  @Test
  public void reportsAnObsoleteBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "1 obsolete bundle:"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), PACKAGE + ".GoneBundle"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * The keys of an obsolete bundle are not reported on top of it: the bundle goes away with all
   * of them, so counting them again would only inflate the warnings.
   */
  @Test
  public void theKeysOfAnObsoleteBundleAreNotReportedSeparately() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello", "farewell", "Bye");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A key the property file no longer has is obsolete in the backend too.
   */
  @Test
  public void reportsAnObsoleteKey() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello", "gone", "Obsolete");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "1 obsolete bundle key:"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "'gone'"),
               getLog().getWarnMessages().toString());
  }

  /**
   * A bundle that has never been pushed is reported by its resource name.
   */
  @Test
  public void reportsAMissingStoredBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "1 missing stored bundle: " + resourceName(APP_BUNDLE, null)),
               getLog().getWarnMessages().toString());
  }

  /**
   * A key without a translation for the default locale would raise a
   * {@code MissingResourceException} at runtime, so it fails the build.
   */
  @Test
  public void aMissingDefaultTranslationFailsTheBuild() {
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> run(mojo));

    assertEquals(ex.getMessage(), "1 error found");
    assertTrue(RecordingLog.contains(getLog().getErrorMessages(), "1 missing default translation:"),
               getLog().getErrorMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getErrorMessages(), "'farewell'"),
               getLog().getErrorMessages().toString());
  }

  /**
   * The same key missing for another locale is only a warning: the default locale still answers
   * for it.
   */
  @Test
  public void aMissingLocaleTranslationIsOnlyAWarning() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");
    properties(resourceName(APP_BUNDLE, "de"), "greeting=Hallo", "farewell=Tschuess");
    I18nTestSupport.setField(mojo, "locales", "de");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello", "farewell", "Bye");
    storeBundle(APP_BUNDLE, "de", "greeting", "Hallo");

    run(mojo);

    assertTrue(getLog().getErrorMessages().isEmpty(), getLog().getErrorMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "1 missing translation:"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "'farewell'"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "2 bundles processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A stored bundle for a locale the goal was not configured with is not obsolete - it belongs to
   * a bundle that is still in use, only for a language this run does not look at.
   */
  @Test
  public void aBundleOfAnUnconfiguredLocaleIsNotObsolete() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(APP_BUNDLE, "de", "greeting", "Hallo");

    run(mojo);

    assertTrue(getLog().getWarnMessages().isEmpty(), getLog().getWarnMessages().toString());
  }

  /**
   * Its keys are checked against the default locale nevertheless, since a key nobody uses any
   * more is obsolete in every language.
   */
  @Test
  public void anObsoleteKeyOfAnUnconfiguredLocaleIsStillReported() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(APP_BUNDLE, "de", "greeting", "Hallo", "gone", "Veraltet");

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "1 obsolete bundle key:"),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "'gone'"),
               getLog().getWarnMessages().toString());
  }

  /**
   * A registered bundle without a property file cannot be verified against anything and is
   * reported as a warning.
   */
  @Test
  public void aMissingPropertyFileIsAWarning() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    I18nTestSupport.setField(mojo, "locales", "fr");    // but no AppBundle_fr.properties

    run(mojo);

    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "no such properties file: " + resourceName(APP_BUNDLE, "fr")),
               getLog().getWarnMessages().toString());
  }

  /**
   * A locale without a property file is reported once as a missing property file, not as a
   * missing stored bundle on top of it - the bundle may well be stored, e.g. added by a translator.
   */
  @Test
  public void aMissingPropertyFileIsNotAlsoAMissingStoredBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(APP_BUNDLE, "fr", "greeting", "Bonjour");
    I18nTestSupport.setField(mojo, "locales", "fr");    // but no AppBundle_fr.properties

    run(mojo);

    assertFalse(RecordingLog.contains(getLog().getWarnMessages(), "missing stored bundle"),
                getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "2 bundles processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A bundle of the application whose default locale has not been pushed yet is still a bundle of
   * the application: the stored bundles of its other locales are not obsolete.
   */
  @Test
  public void aBundleWithoutAStoredDefaultIsNotObsolete() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");               // default never pushed
    storeBundle(APP_BUNDLE, "de", "greeting", "Hallo");

    run(mojo);

    assertFalse(RecordingLog.contains(getLog().getWarnMessages(), "obsolete bundle"),
                getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * As an aggregator, the goal also verifies the project it is executed on, unless it is a pom.
   * In a single-module project, there are no other projects at all.
   */
  @Test
  public void aSingleModuleProjectIsVerifiedAsWell() throws Exception {
    TestVerifyMojo aggregator = configure(new TestVerifyMojo() {
      @Override
      protected boolean isExecutionRecursive() {
        return false;
      }
    });
    MavenProject project = aggregator.getMavenProject();
    project.setPackaging("jar");
    project.setCollectedProjects(List.of());          // no modules
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");

    run(aggregator);

    assertTrue(getLog().getWarnMessages().isEmpty(), getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

}
