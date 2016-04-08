/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.maven;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.common.Constants;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;


/**
 * Processor to verify all bundle getString() invocations.
 * <p>
 * Finds all invocations of <code>&lt;reference&gt;.getString(&lt;string-literal&gt;)</code>.<br>
 * Where <code>&lt;reference&gt;</code> is:
 * <ul>
 * <li>a classname: is treated as a bundle classname (e.g. AppFxRdcBundle)</li>
 * <li>a method: it is assumed to be a method like getBundle() that refers to a bundle
 * with the same name as the class containing the method invocation</li>
 * <li>a variable or constant: same as method, i.e. the bundle name is that of the containing class</li>
 * </ul>
 *
 * If a class must not be checked for bundle keys, it needs to be excluded from the fileset.
 * <p>
 * The following bundles are not covered by this mojo: (TODO)
 * <ul>
 * <li>PdoBundles, i.e. translations for &#64;Singular, &#64;Plural: must be covered by a test. See PdoUtilitiesTest.</li>
 * <li>ValidationBundles, i.e. translations in validators (message=...)</li>
 * </ul>
 *
 * @author harald
 */
@Mojo(name = "bundles",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckBundlesMojo extends AbstractTestMojo {

  private final CheckBundlesScanner bundleScanner;
	private final CheckBundlesProcessor processor;


  /**
   * Creates a mojo.
   */
  public CheckBundlesMojo() {
    bundleScanner = new CheckBundlesScanner();
    processor = new CheckBundlesProcessor(bundleScanner);
    addProcessor(processor);
  }



  @Override
  public void finishExecute() throws MojoExecutionException {

    // find referenced bundle names (avoids unnecessary loading of all bundles in the classpath)
    Set<String> usedBundleNames = new HashSet<>();   // bundles used by the application
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      usedBundleNames.add(key.getBundleName());
    }


    // load referenced bundles in given locales
    Map<NameKey, ResourceBundle> bundleMap = new HashMap<>();    // <class containing the bundle reference> : <bundle>

    // for all annotations like @Bundle or @FxControllerService (need a META-INF/services file!)
    for (Map.Entry<String, URL> providerEntry :
            ServiceFactory.getServiceFinder(getProcessingClassloader(), Constants.DEFAULT_SERVICE_PATH).
              findServiceConfigurations(getClass().getName()).entrySet()) {
      String serviceName = providerEntry.getKey();

      // for all bundles annotated with such an annotation
      for (Map.Entry<String, URL> entry :
              ServiceFactory.getServiceFinder(getProcessingClassloader(), Constants.BUNDLE_SERVICE_PATH).
                findServiceConfigurations(serviceName).entrySet()) {

        String configuration = entry.getKey();
        List<String> parts = BasicStringHelper.split(configuration, ":= ");
        String bundleName = parts.get(0);
        if (usedBundleNames.contains(bundleName)) {
          for (Locale bundleLocale: bundleLocales) {
            try {
              ResourceBundle bundle = ResourceBundle.getBundle(bundleName, bundleLocale, getProcessingClassloader());
              switch (parts.size()) {
                case 1:
                  bundleMap.putIfAbsent(new NameKey(bundleLocale, bundleName), bundle);
                  break;
                case 2:
                  bundleMap.putIfAbsent(new NameKey(bundleLocale, parts.get(1)), bundle);
                  break;
                default:
                  throw new TentackleRuntimeException("invalid bundle configuration '" + configuration + "' in " + entry.
                          getValue());
              }
            }
            catch (RuntimeException rex) {
              getLog().error("could not load bundle " + bundleName);
            }
          }
        }
      }
    }

    int errors = 0;           // total errors
    int keyCount = 0;         // number of keys checked

    // check that each key finds a resource
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      keyCount++;
      String bundleName = key.getBundleName();
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
            getLog().error("missing resource " + key);
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
      if (localestr.length() > 0) {
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



  /**
   * Key locale:bundlename.
   */
  private static class NameKey {

    private final Locale locale;
    private final String name;

    private NameKey(Locale locale, String name) {
      this.locale = locale;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + "(" + locale + ")";
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 59 * hash + Objects.hashCode(this.locale);
      hash = 59 * hash + Objects.hashCode(this.name);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final NameKey other = (NameKey) obj;
      if (!Objects.equals(this.name, other.name)) {
        return false;
      }
      return Objects.equals(this.locale, other.locale);
    }

  }

}
