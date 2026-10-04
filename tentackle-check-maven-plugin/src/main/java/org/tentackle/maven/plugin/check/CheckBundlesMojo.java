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

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.common.BundleSupport;
import org.tentackle.reflect.ReflectionHelper;

import javax.tools.JavaCompiler.CompilationTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;


/**
 * Verifies all bundle getString() invocations.
 * <p>
 * Finds all invocations of <code>&lt;reference&gt;.getString(&lt;key&gt;)</code>,
 * where <code>&lt;key&gt;</code> is a compile-time constant, i.e. a string literal, a constant variable
 * such as {@code static final String KEY = "foo"}, or a concatenation of those,
 * and <code>&lt;reference&gt;</code> is:
 * <ul>
 * <li>a bundle classname, e.g. BlahBundle.getString("foo") checked against BlahBundle properties.
 * The class is resolved via a single-type import or the package of the invoking class.</li>
 * <li>a method that refers to a bundle with the same name as the class containing the method invocation,
 * e.g. getBundle().getString("foo") in class Blah corresponds to the key "foo" in Blah.properties.</li>
 * <li>a variable or constant: same as method, i.e. the bundle name is that of the containing class,
 * e.g. resources.getString("foo").</li>
 * </ul>
 * Nested classes refer to the bundle of their outermost class.
 * Each referenced bundle must be registered via an annotation such as &#64;Bundle or
 * &#64;FxControllerService (see {@link BundleSupport}), and is loaded for each configured locale.
 * Furthermore, the key sets of each referenced bundle are compared across all locales.
 * <p>
 * The sources are attributed by the compiler, so only invocations that really refer to a bundle count:
 * a static {@code getString(String)} of a bundle class, i.e. annotated with &#64;Bundle or another annotation
 * that registers bundles, or one invoked on a {@link ResourceBundle}.
 * Other invocations, such as {@code ResultSet.getString("column")}, are ignored.
 * If the invoked method or the type of the reference cannot be determined, for example due to compile
 * errors, the invocation is checked nevertheless and logged as a warning.
 * <p>
 * The following cases are not covered by this mojo:
 * <ul>
 * <li>all non-constant keys, e.g. getBundle().getString(text) as often found in enums.
 * They are logged as warnings and need explicit tests.
 * The warnings can be suppressed by annotating the invoking code or any enclosing declaration
 * with {@code @SuppressWarnings("bundle")}.</li>
 * <li>PdoBundles, i.e. translations for &#64;Singular, &#64;Plural. See PdoUtilitiesTest.</li>
 * <li>ValidationBundles, i.e. translations in validators, are covered by the mojo tentackle-check:validations.</li>
 * </ul>
 *
 * @author harald
 */
@Mojo(name = "bundles",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class CheckBundlesMojo extends AbstractCheckMojo {

  private final CheckBundlesScanner bundleScanner;


  /**
   * Creates a mojo.
   */
  public CheckBundlesMojo() {
    bundleScanner = new CheckBundlesScanner();
  }


  /**
   * {@inheritDoc}
   * <p>
   * No annotation processors are needed, since the sources are attributed and scanned
   * by {@link #runCompilationTask(CompilationTask)}.
   */
  @Override
  protected String getDefaultProcOption() {
    return "-proc:none";
  }

  /**
   * Attributes the sources and scans them for {@code getString} invocations.
   *
   * @param task the compilation task
   */
  @Override
  protected void runCompilationTask(CompilationTask task) {
    JavacTask javacTask = (JavacTask) task;
    try {
      Iterable<? extends CompilationUnitTree> units = javacTask.parse();
      javacTask.analyze();    // errors are reported as diagnostics
      bundleScanner.scan(javacTask, units);
    }
    catch (IOException iox) {
      throw new UncheckedIOException(iox);
    }
  }


  @Override
  public void finishExecute() throws MojoExecutionException {

    for (BundleKey key: bundleScanner.getUnresolvedKeys()) {
      getLogger().warn("{}: cannot determine whether getString() refers to a bundle, checked anyway: {}",
                       key.getLocation(), key);
    }
    for (BundleKey key: bundleScanner.getUnverifiedKeys()) {
      getLogger().warn("{}: cannot verify getString() argument, not a constant: {}", key.getLocation(), key);
    }
    if (verbosityLevel.isDebug()) {
      for (BundleKey key: bundleScanner.getSkippedKeys()) {
        getLogger().info("ignored getString() invocation, not a bundle: {}", key);
      }
    }

    // find referenced bundle names (avoids unnecessary loading of all bundles in the classpath)
    Set<String> usedBundleNames = new HashSet<>();   // bundles used by the application
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      String bundleName = ReflectionHelper.getOutermostClassName(key.getBundleName());
      if (usedBundleNames.add(bundleName) && verbosityLevel.isDebug()) {
        getLogger().info("bundle {} is referenced", bundleName);
      }
    }

    // key locale:bundlename
    record NameKey(Locale locale, String name) {
      @Override
      public String toString() {
        return name + "(" + locale + ")";
      }
    }

    // load referenced bundles in given locales
    Map<NameKey, ResourceBundle> bundleMap = new HashMap<>();    // <locale:bundlename> : <bundle>

    // for all annotations like @Bundle or @FxControllerService (need a META-INF/bundles file!)
    for (BundleSupport bundleSupport: BundleSupport.getBundles(getProcessingClassloader())) {
      String bundleName = bundleSupport.getBundleName();
      if (usedBundleNames.contains(bundleName)) {
        for (Locale bundleLocale: bundleLocales) {
          try {
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, bundleLocale, getProcessingClassloader());
            if (verbosityLevel.isDebug()) {
              getLogger().info("loaded bundle {}, locale {}, annotated in class {}",
                      bundleName, bundleLocale, bundleSupport.getClassName());
            }
            bundleMap.putIfAbsent(new NameKey(bundleLocale, bundleName), bundle);
          }
          catch (RuntimeException rex) {
            getLogger().error("could not load bundle {}: {}", bundleName, rex.getMessage());
          }
        }
      }
    }

    int errors = 0;           // total errors
    int keyCount = 0;         // number of keys checked

    // check that each key finds a resource
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      keyCount++;
      String bundleName = ReflectionHelper.getOutermostClassName(key.getBundleName());
      for (Locale bundleLocale: bundleLocales) {
        ResourceBundle bundle = bundleMap.get(new NameKey(bundleLocale, bundleName));
        if (bundle == null) {
          getLogger().error("bundle {} not registered by annotation", bundleName);
          errors++;
        }
        else {
          try {
            bundle.getString(key.getKey());
          }
          catch (MissingResourceException mx) {
            getLogger().error("missing resource {} for {}", key, bundleLocale);
            errors++;
          }
        }
      }
    }

    // find diffs in bundles: the keys of the first locale are compared against those of all others.
    // Notice: due to the nature of bundles, missing keys are not detected if provided
    // by a parent bundle (for ex. bundle_de.properties misses a key provided by the fallback bundle.properties).
    // However, this check will detect missing keys that would cause a MissingResourceException at runtime.
    for (String bundleName: usedBundleNames) {
      Set<String> keys = null;
      for (Locale bundleLocale: bundleLocales) {
        NameKey nameKey = new NameKey(bundleLocale, bundleName);
        ResourceBundle bundle = bundleMap.get(nameKey);
        if (bundle == null) {
          getLogger().error("no bundle {} for {}", bundleName, bundleLocale);
          errors++;
        }
        else {
          if (keys == null) {
            keys = bundle.keySet();
          }
          else {
            Set<String> bundleKeys = bundle.keySet();
            for (String key: keys) {
              if (!bundleKeys.contains(key)) {
                getLogger().error("key '{}' missing in {}", key, nameKey);
                errors++;
              }
            }
            for (String key: bundle.keySet()) {
              if (!keys.contains(key)) {
                getLogger().error("key '{}' for {} not defined in all locales of {}", key, bundleLocale, bundleName);
                errors++;
              }
            }
          }
        }
      }
    }


    // show results
    StringBuilder localestr = new StringBuilder();
    for (Locale bundleLocale : bundleLocales) {
      if (!localestr.isEmpty()) {
        localestr.append(", ");
      }
      localestr.append(bundleLocale);
    }
    String msg = keyCount + " key" + (keyCount == 1 ? "" : "s") + " checked for " + localestr + " in " +
                 usedBundleNames.size() + " bundle" + (usedBundleNames.size() == 1 ? "" : "s") + ", ";

    if (errors > 0) {
      msg += errors + " errors";
      getLogger().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLogger().info(msg + "no errors");
    }
  }

}
