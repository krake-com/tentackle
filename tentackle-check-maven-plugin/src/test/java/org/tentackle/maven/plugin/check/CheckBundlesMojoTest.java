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

import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.BundleSupport;
import org.tentackle.maven.plugin.check.CheckTestSupport.RecordingLog;
import org.tentackle.maven.plugin.check.CheckTestSupport.TestBundlesMojo;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code bundles} goal end to end.
 * <p>
 * A temporary project is compiled with the real {@code javac} and checked against real
 * {@code .properties} files on a real classpath, so the test exercises the whole chain: the
 * scanner, the bundle registration via {@code META-INF/bundles}, the resource lookup per locale
 * and the reporting.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CheckBundlesMojoTest {

  private File basedir;
  private File sourceDir;
  private File classesDir;
  private RecordingLog log;
  private TestBundlesMojo mojo;


  @BeforeMethod
  public void setUp() {
    basedir = CheckTestSupport.createTempDir("check-bundles");
    sourceDir = CheckTestSupport.createDir(basedir, "src/main/java");
    classesDir = CheckTestSupport.createDir(basedir, "target/classes");
    log = new RecordingLog();

    mojo = new TestBundlesMojo();
    mojo.setLogger(log);
    CheckTestSupport.setField(mojo, "mavenProject", CheckTestSupport.createProject(basedir));
    CheckTestSupport.setField(mojo, "sourceDir", sourceDir);
    CheckTestSupport.setField(mojo, "classpathElements", CheckTestSupport.classpathElements(classesDir));
    CheckTestSupport.setField(mojo, "encoding", "UTF-8");
    CheckTestSupport.setField(mojo, "locales", "en_US");
  }

  @AfterMethod
  public void tearDown() {
    CheckTestSupport.deleteRecursively(basedir);
  }


  /**
   * Every referenced key resolves: the goal reports what it checked and does not fail.
   */
  @Test
  public void reportsSuccessIfEveryKeyResolves() throws Exception {
    client("greeting", "farewell");
    bundle("com.example.app.AppBundle", "greeting=Hello", "farewell=Bye");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "2 keys checked for en_US in 1 bundle, no errors"),
               log.getInfoMessages().toString());
    assertTrue(log.getErrorMessages().isEmpty(), log.getErrorMessages().toString());
  }

  /**
   * A key without a translation fails the build and is named in the log.
   */
  @Test
  public void aMissingKeyFailsTheBuild() {
    client("greeting", "farewell");
    bundle("com.example.app.AppBundle", "greeting=Hello");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::run);

    assertEquals(ex.getMessage(), "2 keys checked for en_US in 1 bundle, 1 errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "missing resource com.example.app.Client: com.example.app.AppBundle " +
                                     "-> 'farewell' for en_US"),
               log.getErrorMessages().toString());
  }

  /**
   * A bundle that is not registered by an annotation cannot be found at runtime either, no matter
   * whether the properties file happens to be there.
   */
  @Test
  public void anUnregisteredBundleFailsTheBuild() {
    client("greeting");
    properties("com/example/app/AppBundle.properties", "greeting=Hello");   // but no META-INF entry

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::run);

    assertEquals(ex.getMessage(), "1 key checked for en_US in 1 bundle, 2 errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "bundle com.example.app.AppBundle not registered by annotation"),
               log.getErrorMessages().toString());
    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "no bundle com.example.app.AppBundle for en_US"),
               log.getErrorMessages().toString());
  }

  /**
   * A key that only some of the locales provide is reported, since it would throw a
   * {@code MissingResourceException} at runtime for the others.
   */
  @Test
  public void aKeyMissingInOneLocaleFailsTheBuild() {
    CheckTestSupport.setField(mojo, "locales", "en_US, de_DE");
    client("greeting");
    bundle("com.example.app.AppBundle", "greeting=Hello");
    properties("com/example/app/AppBundle_de.properties", "greeting=Hallo", "extra=Extra");

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::run);

    assertEquals(ex.getMessage(), "1 key checked for en_US, de_DE in 1 bundle, 1 errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "key 'extra' for de_DE not defined in all locales of com.example.app.AppBundle"),
               log.getErrorMessages().toString());
  }

  /**
   * The same diff seen from the other end: with the richer locale first, the key is reported as
   * missing in the poorer one.
   */
  @Test
  public void theLocaleOrderDecidesHowTheDiffIsReported() {
    CheckTestSupport.setField(mojo, "locales", "de_DE, en_US");
    client("greeting");
    bundle("com.example.app.AppBundle", "greeting=Hello");
    properties("com/example/app/AppBundle_de.properties", "greeting=Hallo", "extra=Extra");

    expectThrows(MojoExecutionException.class, this::run);

    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "key 'extra' missing in com.example.app.AppBundle(en_US)"),
               log.getErrorMessages().toString());
  }

  /**
   * Locales that agree on every key pass, even though the translations differ.
   */
  @Test
  public void translationsForEveryLocalePass() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "en_US, de_DE");
    client("greeting");
    bundle("com.example.app.AppBundle", "greeting=Hello");
    properties("com/example/app/AppBundle_de.properties", "greeting=Hallo");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 key checked for en_US, de_DE in 1 bundle, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A nested class shares the bundle of its outermost class.
   */
  @Test
  public void aNestedClassUsesTheBundleOfItsOutermostClass() throws Exception {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Outer.java", """
        package com.example.app;

        public class Outer {
          public static class Inner {
            public String hello() {
              return getBundle().getString("greeting");
            }
          }
        }
        """);
    bundle("com.example.app.Outer", "greeting=Hello");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 key checked for en_US in 1 bundle, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * Sources without a single {@code getString()} are fine - the goal is not a code style check.
   */
  @Test
  public void aProjectWithoutBundlesPasses() throws Exception {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Plain.java", """
        package com.example.app;

        public class Plain {
          public int answer() {
            return 42;
          }
        }
        """);

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "0 keys checked for en_US in 0 bundles, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * In debug mode the goal names the bundles it found and loaded.
   */
  @Test
  public void namesTheBundlesInDebugMode() throws Exception {
    log.setDebugEnabled(true);
    client("greeting");
    bundle("com.example.app.AppBundle", "greeting=Hello");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "bundle com.example.app.AppBundle is referenced"),
               log.getInfoMessages().toString());
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "loaded bundle com.example.app.AppBundle, locale en_US"),
               log.getInfoMessages().toString());
  }


  /**
   * An invocation that cannot be resolved is checked anyway, but logged as a warning.
   */
  @Test
  public void warnsAboutAnUnresolvedInvocation() throws Exception {
    client("greeting");     // AppBundle is not part of the sources
    bundle("com.example.app.AppBundle", "greeting=Hello");

    run();

    String location = new File(sourceDir, "com/example/app/Client.java").toPath().toAbsolutePath() + ":[6,31]";
    assertTrue(RecordingLog.contains(log.getWarnMessages(),
                                     location + ": cannot determine whether getString() refers to a bundle, " +
                                     "checked anyway: com.example.app.Client: com.example.app.AppBundle -> 'greeting'"),
               log.getWarnMessages().toString());
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 key checked for en_US in 1 bundle, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A resolved invocation is not warned about.
   */
  @Test
  public void doesNotWarnAboutAResolvedInvocation() throws Exception {
    client("greeting");
    CheckTestSupport.writeFile(sourceDir, "com/example/app/AppBundle.java", """
        package com.example.app;

        @org.tentackle.common.Bundle
        public class AppBundle {
          public static String getString(String key) {
            return key;
          }
        }
        """);
    bundle("com.example.app.AppBundle", "greeting=Hello");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 key checked for en_US in 1 bundle, no errors"),
               log.getInfoMessages().toString());
    assertFalse(RecordingLog.contains(log.getWarnMessages(), "checked anyway"), log.getWarnMessages().toString());
  }

  /**
   * A key given by a constant is checked like a literal.
   */
  @Test
  public void checksAConstantKey() throws Exception {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private static final String KEY = "greeting";

          public String hello(java.util.ResourceBundle bundle) {
            return bundle.getString(KEY);
          }
        }
        """);
    bundle("com.example.app.Client", "greeting=Hello");

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 key checked for en_US in 1 bundle, no errors"),
               log.getInfoMessages().toString());
    assertFalse(RecordingLog.contains(log.getWarnMessages(), "cannot verify"), log.getWarnMessages().toString());
  }

  /**
   * A key that is not a constant cannot be checked and is logged as a warning.
   */
  @Test
  public void warnsAboutANonConstantKey() throws Exception {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello(java.util.ResourceBundle bundle, String key) {
            return bundle.getString(key);
          }
        }
        """);

    run();

    String location = new File(sourceDir, "com/example/app/Client.java").toPath().toAbsolutePath() + ":[5,29]";
    assertTrue(RecordingLog.contains(log.getWarnMessages(),
                                     location + ": cannot verify getString() argument, not a constant: " +
                                     "com.example.app.Client: com.example.app.bundle -> 'key'"),
               log.getWarnMessages().toString());
  }


  /**
   * A class annotated with {@code @NoBundle}, such as the archetype's {@code Version}, needs no registered bundle.
   */
  @Test
  public void ignoresANoBundleClass() throws Exception {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Version.java", """
        package com.example.app;

        import java.util.ResourceBundle;

        @org.tentackle.common.NoBundle
        public class Version {
          public static final String RELEASE = getBundle().getString("version");

          public static ResourceBundle getBundle() {
            return ResourceBundle.getBundle(Version.class.getName());
          }
        }
        """);

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "0 keys checked for en_US in 0 bundles, no errors"),
               log.getInfoMessages().toString());
    assertTrue(log.getErrorMessages().isEmpty(), log.getErrorMessages().toString());
  }


  // ---------------- fixture ----------------

  /**
   * Runs the goal the way {@code execute()} would, minus the global logging handler.
   *
   * @throws Exception if the goal failed
   */
  private void run() throws Exception {
    mojo.prepareExecute();
    assertTrue(CheckTestSupport.callValidate(mojo));
    mojo.executeImpl();
    mojo.finishExecute();
  }

  /**
   * Writes a client class referencing the given keys of {@code AppBundle} of its own package.
   *
   * @param keys the bundle keys
   */
  private void client(String... keys) {
    StringBuilder source = new StringBuilder();
    source.append("package com.example.app;\n\n");
    source.append("public class Client {\n");
    source.append("  public String hello() {\n");
    source.append("    return \"\"\n");
    for (String key : keys) {
      source.append("        + AppBundle.getString(\"").append(key).append("\")\n");
    }
    source.append("        ;\n");
    source.append("  }\n");
    source.append("}\n");
    CheckTestSupport.writeFile(sourceDir, "com/example/app/Client.java", source.toString());
  }

  /**
   * Registers a bundle the way the analyze goal would and writes its properties file.
   *
   * @param bundleName the fully qualified bundle name
   * @param lines the properties
   */
  private void bundle(String bundleName, String... lines) {
    CheckTestSupport.writeFile(classesDir, "META-INF/services/" + BundleSupport.BUNDLE_SERVICE,
                               "org.tentackle.common.Bundle\n");
    CheckTestSupport.writeFile(classesDir, "META-INF/bundles/org.tentackle.common.Bundle",
                               bundleName + "\n");
    properties(bundleName.replace('.', '/') + ".properties", lines);
  }

  /**
   * Writes a properties file to the classes directory.
   *
   * @param path the path relative to the classes directory
   * @param lines the properties
   */
  private void properties(String path, String... lines) {
    CheckTestSupport.writeFile(classesDir, path, String.join("\n", lines) + "\n");
  }

}
