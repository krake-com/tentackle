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
 * The scanner works on the parsed sources only - it never resolves a symbol - which is why the
 * bundle a {@code getString()} call refers to has to be derived from the imports, the package and
 * the shape of the expression. These rules are what the tests below pin down; the sources are
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
    mojo.setLog(new RecordingLog());
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
   * Only string literals can be checked - anything else is silently ignored, which the mojo's
   * javadoc calls out as needing an explicit unit test.
   */
  @Test
  public void ignoresNonLiteralKeys() throws Exception {
    source("com/example/app/Client.java", """
        package com.example.app;

        public class Client {
          private static final String KEY = "greeting";

          public String hello(String key) {
            return getBundle().getString(key) + getBundle().getString(KEY);
          }
        }
        """);

    assertEquals(scan(), List.of());
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
    CheckBundlesScanner scanner = CheckTestSupport.getField(mojo, "bundleScanner");
    return scanner.getBundleKeys();
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
