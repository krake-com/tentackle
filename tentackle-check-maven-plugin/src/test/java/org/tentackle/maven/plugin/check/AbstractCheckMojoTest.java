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

package org.tentackle.maven.plugin.check;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.BundleFactory;
import org.tentackle.maven.plugin.check.CheckTestSupport.RecordingLog;
import org.tentackle.maven.plugin.check.CheckTestSupport.TestCheckMojo;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the configuration both check goals share.
 * <p>
 * The locales decide how often every message is checked, so a silently dropped or misspelled
 * locale would quietly reduce the coverage of the whole check.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class AbstractCheckMojoTest {

  private File basedir;
  private File sourceDir;
  private RecordingLog log;
  private TestCheckMojo mojo;
  private ClassLoader savedBundleClassLoader;


  @BeforeMethod
  public void setUp() {
    savedBundleClassLoader = BundleFactory.getInstance().getClassLoader();
    basedir = CheckTestSupport.createTempDir("check-config");
    sourceDir = CheckTestSupport.createDir(basedir, "src/main/java");
    log = new RecordingLog();

    mojo = new TestCheckMojo();
    mojo.setLogger(log);
    CheckTestSupport.setField(mojo, "mavenProject", CheckTestSupport.createProject(basedir));
    CheckTestSupport.setField(mojo, "sourceDir", sourceDir);
    CheckTestSupport.setField(mojo, "classpathElements", CheckTestSupport.classpathElements());
    CheckTestSupport.setField(mojo, "encoding", "UTF-8");
  }

  @AfterMethod
  public void tearDown() {
    BundleFactory.getInstance().setClassLoader(savedBundleClassLoader);
    CheckTestSupport.deleteRecursively(basedir);
  }


  /**
   * The locales are given in the maven notation with an underscore, which is not a language tag.
   */
  @Test
  public void parsesTheConfiguredLocales() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "en_US, de_DE");

    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.of("en", "US"), Locale.of("de", "DE")));
  }

  /**
   * The language tag notation works as well, and so does every documented separator.
   */
  @Test
  public void acceptsLanguageTagsAndEverySeparator() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "en-US;de_DE fr\tit\nes");

    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.of("en", "US"), Locale.of("de", "DE"),
                                          Locale.of("fr"), Locale.of("it"), Locale.of("es")));
  }

  /**
   * The order matters: the fallback locale comes first, which is the one the other bundles are
   * compared against.
   */
  @Test
  public void keepsTheConfiguredOrder() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "de_DE, en_US");

    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.of("de", "DE"), Locale.of("en", "US")));
  }

  /**
   * A locale that is not one is reported instead of silently reducing the checked locales.
   */
  @Test
  public void warnsAboutAnUnusableLocale() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "en_US, 123");

    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.of("en", "US")));
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "no such locale: 123"), log.getWarnMessages().toString());
  }

  /**
   * Without any usable locale the build machine's locale is used - and said so, because it makes
   * the check depend on the machine.
   */
  @Test
  public void fallsBackToTheDefaultLocale() throws Exception {
    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.getDefault()));
    assertTrue(RecordingLog.contains(log.getWarnMessages(), "fallback to default locale " + Locale.getDefault()),
               log.getWarnMessages().toString());
  }

  /**
   * The same happens if every configured locale was unusable.
   */
  @Test
  public void fallsBackIfNoConfiguredLocaleIsUsable() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "123, 456");

    mojo.prepareExecute();

    assertEquals(bundleLocales(), List.of(Locale.getDefault()));
    assertEquals(log.getWarnMessages().size(), 3, log.getWarnMessages().toString());
  }

  /**
   * The bundles are looked up in the project's classpath, not in the plugin's.
   */
  @Test
  public void bundlesAreResolvedAgainstTheProjectClasspath() throws Exception {
    mojo.prepareExecute();

    assertSame(BundleFactory.getInstance().getClassLoader(), mojo.getProcessingClassloader());
  }

  /**
   * {@code -DskipTests} skips the checks along with the tests, and does so before anything is
   * prepared.
   */
  @Test
  public void skipTestsSkipsTheGoalEntirely() throws Exception {
    CheckTestSupport.setField(mojo, "skipTests", true);
    CheckTestSupport.setField(mojo, "locales", "en_US");

    mojo.prepareExecute();

    assertNull(bundleLocales(), "nothing may be prepared");
    assertNull(mojo.getProcessingClassloader());

    assertFalse(CheckTestSupport.callValidate(mojo));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "tests are skipped"), log.getInfoMessages().toString());
  }

  /**
   * Without the flag the goal validates like any other tentackle mojo.
   */
  @Test
  public void validatesLikeAnyOtherGoal() throws Exception {
    mojo.prepareExecute();

    assertTrue(CheckTestSupport.callValidate(mojo));
  }


  private List<Locale> bundleLocales() {
    return CheckTestSupport.getField(mojo, "bundleLocales");
  }

}
