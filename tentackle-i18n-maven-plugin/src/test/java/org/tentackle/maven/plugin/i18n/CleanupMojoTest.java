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
import org.testng.annotations.Test;

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.RecordingLog;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.TestCleanupMojo;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code cleanup} goal end to end.
 * <p>
 * The goal is the {@code verify} goal with teeth: it deletes what verify would report as
 * obsolete. Since a refactoring in progress looks exactly like an obsolete bundle, it refuses to
 * touch anything as long as verify has something else to complain about.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CleanupMojoTest extends AbstractI18nMojoTest {

  private TestCleanupMojo mojo;


  /**
   * Creates the mojo.<br>
   * TestNG runs the base class' configuration methods first, so the project is already set up.
   */
  @BeforeMethod(alwaysRun = true)
  public void setUpMojo() {
    mojo = configure(new TestCleanupMojo());
  }


  /**
   * A key no property file holds any more is removed from the backend, the others are left alone.
   */
  @Test
  public void removesAnObsoleteKey() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello", "gone", "Obsolete");

    run(mojo);

    StoredBundle bundle = loadBundle(APP_BUNDLE, null);
    assertNotNull(bundle, "the bundle itself was removed");
    assertEquals(bundle.getTranslation("greeting"), "Hello");
    assertNull(bundle.getTranslation("gone"), "the obsolete key was not removed");
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "1 objects removed from " + URL),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 1 update"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A bundle no annotation registers any more is deleted with all its translations.
   */
  @Test
  public void removesAnObsoleteBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello");

    run(mojo);

    assertNull(loadBundle(PACKAGE + ".GoneBundle", null), "the obsolete bundle was not removed");
    assertNotNull(loadBundle(APP_BUNDLE, null), "the bundle in use was removed");
    // the bundle and its single translation
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(), "2 objects removed from " + URL),
               getLog().getWarnMessages().toString());
  }

  /**
   * Bundles of other locales belong to the same aggregate and are deleted along with it.
   */
  @Test
  public void removesEveryLocaleOfAnObsoleteBundle() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", "de", "greeting", "Hallo");

    run(mojo);

    assertEquals(loadBundles().size(), 1);
    assertNotNull(loadBundle(APP_BUNDLE, null));
  }

  /**
   * A missing default translation is an error, and errors are the sign of a refactoring that is
   * not finished: nothing is removed and the build fails.
   */
  @Test
  public void refusesToCleanUpIfATranslationIsMissing() {
    bundle(APP_BUNDLE, "greeting=Hello", "farewell=Bye");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> run(mojo));

    assertEquals(ex.getMessage(), "1 error found");
    assertNotNull(loadBundle(PACKAGE + ".GoneBundle", null), "cleaned up despite the error");
    assertTrue(RecordingLog.contains(getLog().getErrorMessages(),
                                     "1 error found -> please run tentackle-i18n:verify and fix the errors"),
               getLog().getErrorMessages().toString());
  }

  /**
   * The same goes for a warning, which is what a bundle nobody has pushed yet looks like.
   */
  @Test
  public void refusesToCleanUpIfAStoredBundleIsMissing() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");               // never pushed
    storeBundle(PACKAGE + ".GoneBundle", null, "greeting", "Hello");

    run(mojo);

    assertNotNull(loadBundle(PACKAGE + ".GoneBundle", null), "cleaned up despite the warning");
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "1 warning found -> please run tentackle-i18n:verify and fix the warnings"),
               getLog().getWarnMessages().toString());
  }

  /**
   * With the backend in sync there is nothing to remove and nothing to report.
   */
  @Test
  public void removesNothingIfTheBackendIsInSync() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, null).getTranslation("greeting"), "Hello");
    assertTrue(getLog().getWarnMessages().isEmpty(), getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * As an aggregator, the goal also processes the project it is executed on, unless it is a pom.
   * Otherwise, every stored bundle of a single-module project would look obsolete and be deleted.
   */
  @Test
  public void keepsTheBundlesOfASingleModuleProject() throws Exception {
    TestCleanupMojo aggregator = configure(new TestCleanupMojo() {
      @Override
      protected boolean isExecutionRecursive() {
        return false;
      }
    });
    aggregator.getMavenProject().setPackaging("jar");
    aggregator.getMavenProject().setCollectedProjects(List.of());     // no modules
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");

    run(aggregator);

    assertNotNull(loadBundle(APP_BUNDLE, null), "the bundle in use was removed");
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A locale without a property file is a single warning, which blocks the cleanup.
   */
  @Test
  public void aMissingPropertyFileIsASingleWarning() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(APP_BUNDLE, "fr", "greeting", "Bonjour", "gone", "Obsolete");
    I18nTestSupport.setField(mojo, "locales", "fr");    // but no AppBundle_fr.properties

    run(mojo);

    assertEquals(loadBundle(APP_BUNDLE, "fr").getTranslation("gone"), "Obsolete", "cleaned up despite the warning");
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "1 warning found -> please run tentackle-i18n:verify and fix the warnings"),
               getLog().getWarnMessages().toString());
  }

}
