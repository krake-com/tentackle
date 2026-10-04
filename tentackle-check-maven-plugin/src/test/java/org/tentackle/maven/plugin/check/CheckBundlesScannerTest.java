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

import org.tentackle.maven.plugin.check.CheckTestSupport.RecordingLog;
import org.tentackle.maven.plugin.check.CheckTestSupport.TestBundlesMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests what the scanner picks up from the syntax tree.
 * <p>
 * The bundle a {@code getString()} call refers to is derived from the imports, the package and
 * the shape of the expression. The attributed types only decide whether the call refers to a
 * bundle at all. These rules are what the tests below pin down; the sources are
 * compiled by the real {@code javac} through the mojo, so the trees are the ones the plugin
 * really sees.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CheckBundlesScannerTest {

  private File basedir;
  private File sourceDir;
  private TestBundlesMojo mojo;


  @BeforeMethod
  public void setUp() {
    basedir = CheckTestSupport.createTempDir("check-scanner");
    sourceDir = CheckTestSupport.createDir(basedir, "src/main/java");

    mojo = new TestBundlesMojo();
    mojo.setLogger(new RecordingLog());
    CheckTestSupport.setField(mojo, "mavenProject", CheckTestSupport.createProject(basedir));
    CheckTestSupport.setField(mojo, "sourceDir", sourceDir);
    CheckTestSupport.setField(mojo, "classpathElements", CheckTestSupport.classpathElements());
    CheckTestSupport.setField(mojo, "encoding", "UTF-8");
    CheckTestSupport.setField(mojo, "locales", "en_US");
  }

  @AfterMethod
  public void tearDown() {
    CheckTestSupport.deleteRecursively(basedir);
  }


  /**
   * A bundle imported from another package is referenced by its fully qualified name.
   */
  @Test
  public void resolvesAnImportedBundle() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        import com.example.i18n.AppBundle;

        public class Client {
          public String hello() {
            return AppBundle.getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.i18n.AppBundle -> 'greeting'"));
    assertEquals(bundleNames(), List.of("com.example.i18n.AppBundle"));
  }

  /**
   * A bundle without an import must live in the same package.
   */
  @Test
  public void resolvesABundleOfTheSamePackage() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return AppBundle.getString("greeting");
          }
        }
        """);

    scan();

    assertEquals(bundleNames(), List.of("com.example.app.AppBundle"));
  }

  /**
   * A method invocation such as {@code getBundle()} refers to the bundle of the class it is
   * called in.
   */
  @Test
  public void aMethodReferenceMeansTheOwnBundle() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return getBundle().getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.getBundle() -> 'greeting'"));
    assertEquals(bundleNames(), List.of("com.example.app.Client"));
  }

  /**
   * So does a variable, which is recognized by its lowercase first letter.
   */
  @Test
  public void aVariableReferenceMeansTheOwnBundle() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private java.util.ResourceBundle resources;

          public String hello() {
            return resources.getString("greeting");
          }
        }
        """);

    scan();

    assertEquals(bundleNames(), List.of("com.example.app.Client"));
  }

  /**
   * And a constant, which is recognized by being all uppercase.
   */
  @Test
  public void aConstantReferenceMeansTheOwnBundle() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private static final java.util.ResourceBundle BUNDLE = null;

          public String hello() {
            return BUNDLE.getString("greeting");
          }
        }
        """);

    scan();

    assertEquals(bundleNames(), List.of("com.example.app.Client"));
  }

  /**
   * A nested class is reported with its enclosing class, so that the message points at the
   * source that has to be fixed.
   */
  @Test
  public void reportsTheEnclosingClassOfANestedClass() throws Exception {
    source("com/example/app/Outer.java", """
        package com.example.app;

        public class Outer {
          public static class Inner {
            public String hello() {
              return getBundle().getString("greeting");
            }
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Outer.Inner: com.example.app.getBundle() -> 'greeting'"));
    assertEquals(bundleNames(), List.of("com.example.app.Outer.Inner"));
  }

  /**
   * Compile-time constants are checked as well: constant variables of the same or another class,
   * final locals, concatenations and parentheses.
   */
  @Test
  public void resolvesConstantKeys() throws Exception {
    source("com/example/app/Keys.java", """
        package com.example.app;

        public class Keys {
          public static final String GREETING = "greeting";
        }
        """);
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private static final String KEY = "hello";
          private static final String PREFIX = "client.";

          public String hello(java.util.ResourceBundle bundle) {
            final String local = "local";
            return bundle.getString(KEY) + bundle.getString(Keys.GREETING) + bundle.getString(local) +
                   bundle.getString(PREFIX + "title") + bundle.getString((PREFIX + KEY));
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.bundle -> 'hello'",
                                 "com.example.app.Client: com.example.app.bundle -> 'greeting'",
                                 "com.example.app.Client: com.example.app.bundle -> 'local'",
                                 "com.example.app.Client: com.example.app.bundle -> 'client.title'",
                                 "com.example.app.Client: com.example.app.bundle -> 'client.hello'"));
    assertEquals(unverified(), List.of());
  }

  /**
   * Keys that are not compile-time constants cannot be checked and are reported as unverified.
   */
  @Test
  public void reportsNonConstantKeys() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private static final String COMPUTED = String.valueOf("computed");
          private String field = "field";

          public String hello(java.util.ResourceBundle bundle, String key) {
            return bundle.getString(key) + bundle.getString(field) + bundle.getString(COMPUTED) +
                   bundle.getString("prefix." + key);
          }
        }
        """);

    assertEquals(scan(), List.of());
    assertEquals(unverified(), List.of("com.example.app.Client: com.example.app.bundle -> 'key'",
                                       "com.example.app.Client: com.example.app.bundle -> 'field'",
                                       "com.example.app.Client: com.example.app.bundle -> 'COMPUTED'",
                                       "com.example.app.Client: com.example.app.bundle -> '\"prefix.\" + key'"));
  }

  /**
   * {@code @SuppressWarnings("bundle")} on the invoking code or any enclosing declaration suppresses
   * the unverified keys. Other tokens don't.
   */
  @Test
  public void suppressesUnverifiedKeys() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        import java.util.ResourceBundle;

        public class Client {
          @SuppressWarnings("bundle")
          public String method(ResourceBundle bundle, String key) {
            return bundle.getString(key);
          }

          public String local(ResourceBundle bundle, String key) {
            @SuppressWarnings({ "unused", "bundle" })
            String text = bundle.getString(key);
            return text;
          }

          @SuppressWarnings("unchecked")
          public String other(ResourceBundle bundle, String key) {
            return bundle.getString(key);
          }
        }
        """);
    source("com/example/app/Outer.java", """
        package com.example.app;

        @SuppressWarnings("bundle")
        public class Outer {
          static class Inner {
            String hello(java.util.ResourceBundle bundle, String key) {
              return bundle.getString(key);
            }
          }
        }
        """);

    scan();

    assertEquals(unverified(), List.of("com.example.app.Client: com.example.app.bundle -> 'key'"));
    assertTrue(bundleScanner().getUnverifiedKeys().getFirst().getLocation().endsWith("Client.java:[19,29]"),
               bundleScanner().getUnverifiedKeys().getFirst().getLocation());
  }

  /**
   * An unresolved invocation is still checked if suppressed, just not reported as unresolved.
   */
  @Test
  public void suppressesUnresolvedInvocations() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          @SuppressWarnings("bundle")
          public String hello() {
            return unknown.getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.unknown -> 'greeting'"));
    assertEquals(unresolved(), List.of());
  }

  /**
   * A class annotated with {@code @NoBundle} only looks like a bundle: its invocations are skipped,
   * including those of nested classes and those with non-constant keys.
   */
  @Test
  public void skipsNoBundleClasses() throws Exception {
    source("com/example/app/Version.java", """
        package com.example.app;

        import java.util.ResourceBundle;

        @org.tentackle.common.NoBundle
        public class Version {
          private static final String VERSION_STR = "version";

          public static final String RELEASE = getBundle().getString(VERSION_STR);

          public static ResourceBundle getBundle() {
            return ResourceBundle.getBundle(Version.class.getName());
          }

          static class Inner {
            String get(String key) {
              return getBundle().getString(key);
            }
          }
        }
        """);

    assertEquals(scan(), List.of());
    assertEquals(skipped(), List.of("com.example.app.Version: com.example.app.getBundle() -> 'version'"));
    assertEquals(unverified(), List.of());
  }

  /**
   * A non-constant argument of a {@code getString} that is not a bundle lookup is not reported.
   */
  @Test
  public void ignoresNonConstantKeysOfNonBundles() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String name(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
            return rs.getString(column);
          }
        }
        """);

    assertEquals(scan(), List.of());
    assertEquals(unverified(), List.of());
  }

  /**
   * Only the single-argument {@code getString} of a resource bundle is a bundle lookup.
   */
  @Test
  public void ignoresOtherMethods() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return other.getText("greeting") + other.getString("a", "b");
          }

          private static Other other;

          static class Other {
            String getText(String key) { return key; }
            String getString(String key, String def) { return def; }
          }
        }
        """);

    assertEquals(scan(), List.of());
  }

  /**
   * Every occurrence is reported, so that a bundle with several missing keys does not hide
   * behind the first one.
   */
  @Test
  public void collectsEveryOccurrence() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return getBundle().getString("greeting") + getBundle().getString("farewell");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.getBundle() -> 'greeting'",
                                 "com.example.app.Client: com.example.app.getBundle() -> 'farewell'"));
  }


  /**
   * A {@code getString} of something that is not a bundle, such as a {@code ResultSet}, is ignored.
   */
  @Test
  public void ignoresNonBundleReceivers() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String name(java.sql.ResultSet rs) throws java.sql.SQLException {
            return rs.getString("name");
          }
        }
        """);

    assertEquals(scan(), List.of());
    assertEquals(skipped(), List.of("com.example.app.Client: com.example.app.rs -> 'name'"));
  }

  /**
   * So is the {@code getString} of an object returned by a method, if that is not a bundle.
   */
  @Test
  public void ignoresAMethodReturningANonBundle() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return getOther().getString("greeting");
          }

          private Other getOther() {
            return new Other();
          }

          static class Other {
            String getString(String key) { return key; }
          }
        }
        """);

    assertEquals(scan(), List.of());
  }

  /**
   * Subclasses of {@code ResourceBundle} are bundles as well.
   */
  @Test
  public void acceptsAResourceBundleSubclass() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private java.util.PropertyResourceBundle bundle;

          public String hello() {
            return bundle.getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.bundle -> 'greeting'"));
    assertEquals(unresolved(), List.of());
  }

  /**
   * The static {@code getString} of a bundle class is a bundle lookup.
   */
  @Test
  public void acceptsAStaticBundleClass() throws Exception {
    source("com/example/i18n/AppBundle.java", """
        package com.example.i18n;

        @org.tentackle.common.Bundle
        public class AppBundle {
          public static String getString(String key) {
            return key;
          }
        }
        """);
    source("com/example/app/Client.java", """
        package com.example.app;

        import com.example.i18n.AppBundle;

        public class Client {
          public String hello() {
            return AppBundle.getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.i18n.AppBundle -> 'greeting'"));
    assertEquals(unresolved(), List.of());
  }

  /**
   * The static {@code getString} of a class that is not a bundle class is ignored.
   */
  @Test
  public void ignoresAStaticNonBundleClass() throws Exception {
    source("com/example/app/Helper.java", """
        package com.example.app;

        public class Helper {
          public String hello() {
            return Helper.getString("blah");
          }

          public static String getString(String dummy) {
            return dummy;
          }
        }
        """);

    assertEquals(scan(), List.of());
    assertEquals(skipped(), List.of("com.example.app.Helper: com.example.app.Helper -> 'blah'"));
    assertEquals(unresolved(), List.of());
  }

  /**
   * A bundle registered by another annotation of the bundle analyze handler, such as
   * {@code @FxControllerService}, counts as well.
   */
  @Test
  public void acceptsAnotherBundleAnnotation() throws Exception {
    source("com/example/app/MyBundleAnnotation.java", """
        package com.example.app;

        @org.tentackle.common.Analyze("org.tentackle.buildsupport.FxControllerBundleAnalyzeHandler")
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        public @interface MyBundleAnnotation {
        }
        """);
    source("com/example/app/Helper.java", """
        package com.example.app;

        @MyBundleAnnotation
        public class Helper {
          public String hello() {
            return Helper.getString("blah");
          }

          public static String getString(String dummy) {
            return dummy;
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Helper: com.example.app.Helper -> 'blah'"));
  }

  /**
   * If the type of the reference cannot be determined, the key is checked nevertheless.
   */
  @Test
  public void collectsAnUnresolvedReceiver() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          public String hello() {
            return unknown.getString("greeting");
          }
        }
        """);

    assertEquals(scan(), List.of("com.example.app.Client: com.example.app.unknown -> 'greeting'"));
    assertEquals(skipped(), List.of());
    assertEquals(unresolved(), List.of("com.example.app.Client: com.example.app.unknown -> 'greeting'"));
  }


  // ---------------- fixture ----------------

  private void source(String path, String contents) {
    CheckTestSupport.writeFile(sourceDir, path, contents);
  }

  /**
   * Runs the annotation processing and returns the collected bundle keys.
   *
   * @return the keys in the order they were found
   * @throws Exception if processing failed
   */
  private List<String> scan() throws Exception {
    mojo.prepareExecute();
    assertTrue(CheckTestSupport.callValidate(mojo));
    mojo.executeImpl();

    List<String> keys = new ArrayList<>();
    for (BundleKey bundleKey : bundleKeys()) {
      keys.add(bundleKey.toString());
    }
    return keys;
  }

  private List<BundleKey> bundleKeys() {
    return bundleScanner().getBundleKeys();
  }

  private CheckBundlesScanner bundleScanner() {
    return CheckTestSupport.getField(mojo, "bundleScanner");
  }

  /**
   * Gets the ignored {@code getString} invocations.
   *
   * @return the ignored keys
   */
  private List<String> skipped() {
    CheckBundlesScanner scanner = CheckTestSupport.getField(mojo, "bundleScanner");
    List<String> keys = new ArrayList<>();
    for (BundleKey bundleKey : scanner.getSkippedKeys()) {
      keys.add(bundleKey.toString());
    }
    return keys;
  }

  /**
   * Gets the collected keys whose invocation could not be resolved.
   *
   * @return the unresolved keys
   */
  private List<String> unresolved() {
    CheckBundlesScanner scanner = CheckTestSupport.getField(mojo, "bundleScanner");
    List<String> keys = new ArrayList<>();
    for (BundleKey bundleKey : scanner.getUnresolvedKeys()) {
      keys.add(bundleKey.toString());
    }
    return keys;
  }

  /**
   * Gets the bundle invocations whose key is not a compile-time constant.
   *
   * @return the unverified keys
   */
  private List<String> unverified() {
    CheckBundlesScanner scanner = CheckTestSupport.getField(mojo, "bundleScanner");
    List<String> keys = new ArrayList<>();
    for (BundleKey bundleKey : scanner.getUnverifiedKeys()) {
      keys.add(bundleKey.toString());
    }
    return keys;
  }

  /**
   * Gets the bundle names the collected keys resolve to.
   *
   * @return the bundle names
   */
  private List<String> bundleNames() {
    List<String> names = new ArrayList<>();
    for (BundleKey bundleKey : bundleKeys()) {
      names.add(bundleKey.getBundleName());
    }
    return names;
  }

}
