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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.i18n.I18nTestSupport.RecordingLog;
import org.tentackle.maven.plugin.i18n.I18nTestSupport.TestPullMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@code pull} goal end to end.
 * <p>
 * The goal is the counterpart of {@code push}: it writes the translations of the backend back into
 * the property files of the project, so that what the translators have edited ends up in the VCS.
 * Only the packages of the project itself are touched - a bundle of a dependency has no source to
 * write to.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PullMojoTest extends AbstractI18nMojoTest {

  private TestPullMojo mojo;
  private File packageDir;


  /**
   * Creates the mojo and the package the bundles are pulled into.<br>
   * TestNG runs the base class' configuration methods first, so the project is already set up.
   */
  @BeforeMethod(alwaysRun = true)
  public void setUpMojo() {
    mojo = configure(new TestPullMojo());
    packageDir = I18nTestSupport.createDir(getResourceDir(), PACKAGE.replace('.', '/'));
  }


  /**
   * A bundle without a property file yet gets one, which the goal reports as a warning since it
   * is a file the developer will have to add to the VCS.
   */
  @Test
  public void createsAMissingPropertyFile() throws Exception {
    bundle(APP_BUNDLE);
    storeBundle(APP_BUNDLE, null, "greeting", "Hello", "farewell", "Bye");

    run(mojo);

    assertEquals(translations(propertyFile(APP_BUNDLE, null)), List.of("greeting=Hello", "farewell=Bye"));
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "created resource: " + resourceName(APP_BUNDLE, null)),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 1 warning, 0 errors, 2 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A translation edited in the backend replaces the one in the property file.
   */
  @Test
  public void updatesADivergingTranslation() throws Exception {
    bundle(APP_BUNDLE);
    resource(resourceName(APP_BUNDLE, null), "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Welcome");

    run(mojo);

    assertEquals(translations(propertyFile(APP_BUNDLE, null)), List.of("greeting=Welcome"));
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     resourceName(APP_BUNDLE, null) + ": 'greeting' updated"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A key added in the backend is appended, and the keys that are already in the file keep the
   * order they were written in - the point of {@link OrderedProperties}.
   */
  @Test
  public void appendsANewKeyWithoutReorderingTheFile() throws Exception {
    bundle(APP_BUNDLE);
    resource(resourceName(APP_BUNDLE, null), "zebra=Z", "alpha=A");
    storeBundle(APP_BUNDLE, null, "zebra", "Z", "alpha", "A", "middle", "M");

    run(mojo);

    assertEquals(translations(propertyFile(APP_BUNDLE, null)), List.of("zebra=Z", "alpha=A", "middle=M"));
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     resourceName(APP_BUNDLE, null) + ": 'middle' added"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A file that already holds what the backend holds is not rewritten at all, so that a pull
   * without changes leaves no diff behind.
   */
  @Test
  public void leavesAnUpToDatePropertyFileAlone() throws Exception {
    bundle(APP_BUNDLE);
    File file = resource(resourceName(APP_BUNDLE, null), "greeting=Hello");
    String contents = I18nTestSupport.readFile(file);
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");

    run(mojo);

    assertEquals(I18nTestSupport.readFile(file), contents, "the property file was rewritten");
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * An updated file says where its contents came from.
   */
  @Test
  public void anUpdatedFileNamesTheBackendItWasPulledFrom() throws Exception {
    bundle(APP_BUNDLE);
    resource(resourceName(APP_BUNDLE, null), "greeting=Hello");
    storeBundle(APP_BUNDLE, null, "greeting", "Welcome");

    run(mojo);

    assertTrue(I18nTestSupport.readFile(propertyFile(APP_BUNDLE, null)).contains("pulled from " + URL),
               "the file does not name the backend");
  }

  /**
   * The bundles of the extra locales are pulled into property files of their own.
   */
  @Test
  public void pullsTheConfiguredLocales() throws Exception {
    bundle(APP_BUNDLE);
    I18nTestSupport.setField(mojo, "locales", "de");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");
    storeBundle(APP_BUNDLE, "de", "greeting", "Hallo");

    run(mojo);

    assertEquals(translations(propertyFile(APP_BUNDLE, null)), List.of("greeting=Hello"));
    assertEquals(translations(propertyFile(APP_BUNDLE, "de")), List.of("greeting=Hallo"));
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "2 bundles processed, 2 warnings, 0 errors, 2 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * A bundle nobody has pushed yet has nothing to pull: it is reported as a warning, and no empty
   * property file is left behind.
   */
  @Test
  public void warnsAboutABundleThatIsNotStoredYet() throws Exception {
    bundle(APP_BUNDLE, "greeting=Hello");    // but never pushed

    run(mojo);

    assertFalse(propertyFile(APP_BUNDLE, null).exists(), "wrote a property file for nothing");
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "missing stored bundle: " + resourceName(APP_BUNDLE, null)),
               getLog().getWarnMessages().toString());
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "1 bundle processed, 1 warning, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }

  /**
   * The same for a locale that has not been translated yet: the bundles of the other locales are
   * pulled nevertheless.
   */
  @Test
  public void anUntranslatedLocaleDoesNotStopThePull() throws Exception {
    bundle(APP_BUNDLE);
    I18nTestSupport.setField(mojo, "locales", "de");
    storeBundle(APP_BUNDLE, null, "greeting", "Hello");    // but no german bundle

    run(mojo);

    assertEquals(translations(propertyFile(APP_BUNDLE, null)), List.of("greeting=Hello"));
    assertFalse(propertyFile(APP_BUNDLE, "de").exists(), "wrote a property file for nothing");
    assertTrue(RecordingLog.contains(getLog().getWarnMessages(),
                                     "missing stored bundle: " + resourceName(APP_BUNDLE, "de")),
               getLog().getWarnMessages().toString());
  }

  /**
   * A bundle of a dependency is registered on the classpath just like the project's own ones, but
   * it has no package in the project's resources and is therefore left alone.
   */
  @Test
  public void ignoresABundleOutsideTheProject() throws Exception {
    String otherBundle = "com.example.other.LibBundle";
    bundle(otherBundle);
    storeBundle(otherBundle, null, "greeting", "Hello");

    run(mojo);

    assertFalse(new File(getResourceDir(), "com/example/other").exists(), "wrote outside the project");
    assertTrue(RecordingLog.contains(getLog().getInfoMessages(),
                                     "0 bundles processed, 0 warnings, 0 errors, 0 updates"),
               getLog().getInfoMessages().toString());
  }


  /**
   * Gets the property file of a bundle in the project's resources.
   *
   * @param bundleName the fully qualified bundle name
   * @param locale the locale suffix, null if the default locale
   * @return the file, which need not exist
   */
  private File propertyFile(String bundleName, String locale) {
    String resourceName = resourceName(bundleName, locale);
    return new File(packageDir, resourceName.substring(resourceName.lastIndexOf('/') + 1));
  }

  /**
   * Reads the translations of a property file in the order they are written in.
   *
   * @param file the property file
   * @return the {@code key=value} lines
   */
  private List<String> translations(File file) {
    assertTrue(file.isFile(), "no such property file: " + file);
    List<String> lines = new ArrayList<>();
    for (String line : I18nTestSupport.readFile(file).split("\n")) {
      if (!line.isBlank() && !line.startsWith("#")) {
        lines.add(line);
      }
    }
    return lines;
  }

}
