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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.tentackle.common.BundleSupport;
import org.tentackle.reflect.ReflectionHelper;

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
 * Finds all invocations of <code>&lt;reference&gt;.getString(&lt;string-literal&gt;)</code>.<br>
 * Where <code>&lt;reference&gt;</code> is:
 * <ul>
 * <li>a bundle classname, e.g. BlahBundle.getString("foo") checked against BlahBundle properties.</li>
 * <li>a method that refers to a bundle with the same name as the class containing the method invocation,
 * e.g. getBundle().getString("foo") in class Blah corresponds to the key "foo" in Blah.properties.</li>
 * <li>a variable or constant: same as method, i.e. the bundle name is that of the containing class,
 * e.g. resources.getString("foo").</li>
 * </ul>
 *
 * If a class must not be checked for bundle keys, it needs to be excluded from the fileset.
 * <p>
 * The following cases are not covered by this mojo:
 * <ul>
 * <li>all non-literals, e.g. getBundle().getString(text) as often found in enums. Needs explicit tests.</li>
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
    addProcessor(new CheckBundlesProcessor(bundleScanner));
  }


  @Override
  public void finishExecute() throws MojoExecutionException {

    // find referenced bundle names (avoids unnecessary loading of all bundles in the classpath)
    Set<String> usedBundleNames = new HashSet<>();   // bundles used by the application
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      String bundleName = ReflectionHelper.getOutermostClassName(key.getBundleName());
      if (usedBundleNames.add(bundleName) && verbosityLevel.isDebug()) {
        getLog().info("bundle " + bundleName + " is referenced");
      }
    }

    // Key locale:bundlename
    record NameKey(Locale locale, String name) {
      @Override
      public String toString() {
        return name + "(" + locale + ")";
      }
    }

    // load referenced bundles in given locales
    Map<NameKey, ResourceBundle> bundleMap = new HashMap<>();    // <class containing the bundle reference> : <bundle>

    // for all annotations like @Bundle or @FxControllerService (need a META-INF/bundles file!)
    for (BundleSupport bundleSupport: BundleSupport.getBundles(getProcessingClassloader())) {
      String bundleName = bundleSupport.getBundleName();
      if (usedBundleNames.contains(bundleName)) {
        for (Locale bundleLocale: bundleLocales) {
          try {
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, bundleLocale, getProcessingClassloader());
            if (verbosityLevel.isDebug()) {
              getLog().info("loaded bundle " + bundleName + ", locale " + bundleLocale +
                            ", annotated in class " + bundleSupport.getClassName());
            }
            bundleMap.putIfAbsent(new NameKey(bundleLocale, bundleName), bundle);
          }
          catch (RuntimeException rex) {
            getLog().error("could not load bundle " + bundleName + ": " + rex.getMessage());
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
          getLog().error("bundle " + bundleName + " not registered by annotation");
          errors++;
        }
        else {
          try {
            bundle.getString(key.getKey());
          }
          catch (MissingResourceException mx) {
            getLog().error("missing resource " + key + " for " + bundleLocale);
            errors++;
          }
        }
      }
    }

    // find diffs in bundles.
    // Notice: due to the nature of bundles, missing keys are not detected if provided
    // by a parent bundle (for ex. bundle_de.properties misses a key provided by the fallback bundle.properties).
    // However, this check will detect missing keys that would cause a MissingResourceException at runtime.
    for (String bundleName: usedBundleNames) {
      Set<String> keys = null;
      for (Locale bundleLocale: bundleLocales) {
        NameKey nameKey = new NameKey(bundleLocale, bundleName);
        ResourceBundle bundle = bundleMap.get(nameKey);
        if (bundle == null) {
          getLog().error("no bundle " + bundleName + " for " + bundleLocale);
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
                getLog().error("key '" + key + "' missing in " + nameKey);
                errors++;
              }
            }
            for (String key: bundle.keySet()) {
              if (!keys.contains(key)) {
                getLog().error("key '" + key + "' for " + bundleLocale + " not defined in all locales of " + bundleName);
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
      getLog().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLog().info(msg + "no errors");
    }
  }

}
