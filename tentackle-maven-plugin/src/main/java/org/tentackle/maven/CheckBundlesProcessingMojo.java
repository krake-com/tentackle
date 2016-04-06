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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
 * Currently, the following bundles are not covered by this mojo: (TODO)
 * <ul>
 * <li>PdoBundles, i.e. translations for &#64;Singular, &#64;Plural</li>
 * <li>ValidationBundles, i.e. translations in validators (message=...)</li>
 * </ul>
 *
 * @author harald
 */
@Mojo(name = "check-bundles",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckBundlesProcessingMojo extends AbstractTentackleAnnotationProcessingMojo {

  private final CheckBundlesScanner bundleScanner;
	private final CheckBundlesProcessor processor;

  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java sources of the current project.
   * If this is not desired, filesets must be used.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}",
             property = "tentackle.sourceDir",
             required = true)
  protected File sourceDir;

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;


  /**
   * The locales to check the bundles against.<br>
   * Defaults to the current locale.<br>
   * The fallback locale should come first.
   */
  @Parameter
  protected String locales;

  /**
   * Classloader for ResourceBundle.getBundle().
   */
  private BundleClassLoader bundleLoader;


  /**
   * Creates a mojo.
   */
  public CheckBundlesProcessingMojo() {
    bundleScanner = new CheckBundlesScanner();
    processor = new CheckBundlesProcessor(bundleScanner);
    addProcessor(processor);
  }


  @Override
  public void prepareExecute() {
    setMojoParameters(sourceDir, classpathElements);
    try {
      bundleLoader = new BundleClassLoader((URLClassLoader) getClass().getClassLoader(), classpathElements);
    }
    catch (MalformedURLException mx) {
      getLog().error("cannot create bundle classloader", mx);
    }
  }

  @Override
  public void finishExecute() throws MojoExecutionException {

    List<Locale> bundleLocales = new ArrayList<>();
    if (locales != null) {
      StringTokenizer stok = new StringTokenizer(locales, ",; \n\r\t");
      while (stok.hasMoreTokens()) {
        String localeName = stok.nextToken().replace('_', '-');   // transform en_US to en-US
        Locale bundleLocale = Locale.forLanguageTag(localeName);
        if (bundleLocale != null && !bundleLocale.toString().isEmpty()) {
          bundleLocales.add(bundleLocale);
        }
        else {
          getLog().warn("no such locale: " + localeName);
        }
      }
    }
    if (bundleLocales.isEmpty()) {
      Locale bundleLocale = Locale.getDefault();
      getLog().warn("fallback to default locale " + bundleLocale);
      bundleLocales.add(bundleLocale);
    }


    Map<NameKey, ResourceBundle> bundleMap = new HashMap<>();    // <class containing the bundle reference> : <bundle>

    for (String serviceName: new String[] {"org.tentackle.common.Bundle",
                                           "org.tentackle.fx.FxControllerService"} ) {

      for (Map.Entry<String, URL> entry : ServiceFactory.getServiceFinder(bundleLoader, Constants.BUNDLE_SERVICE_PATH).
              findServiceConfigurations(serviceName).entrySet()) {

        String configuration = entry.getKey();
        List<String> parts = BasicStringHelper.split(configuration, ":= ");
        String bundleName = parts.get(0);
        for (Locale bundleLocale: bundleLocales) {
          try {
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, bundleLocale, bundleLoader);
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

    int errors = 0;           // total errors

    Set<String> usedBundleNames = new HashSet<>();   // bundles used by the application

    int keyCount = 0;
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
          usedBundleNames.add(bundleName);
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

    // find diffs in bundles
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


    StringBuilder localestr = new StringBuilder();
    for (Locale bundleLocale : bundleLocales) {
      if (localestr.length() > 0) {
        localestr.append(", ");
      }
      localestr.append(bundleLocale);
    }
    String msg = keyCount + " key" + (keyCount > 1 ? "s" : "") + " checked for " + localestr + " in " +
                 usedBundleNames.size() + " bundle" + (usedBundleNames.size() > 1 ? "s" : "") + ", ";

    if (errors > 0) {
      msg += errors + " errors";
      getLog().error(msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLog().info(msg + "no errors");
    }
  }



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


  /**
   * Classloader to load the bundles.<br>
   * Necessary since the compliler's classpath options don't change the task's classloader.
   */
  private static class BundleClassLoader extends URLClassLoader {

    /**
     * Create from another classloader.
     *
     * @param classLoader the task's classloader
     */
    private BundleClassLoader(URLClassLoader classLoader, List<String> classpathElements) throws MalformedURLException {
      super(classLoader.getURLs());
      if (classpathElements != null) {
        for (String classpathElement : classpathElements) {
          File classPathFile = new File(classpathElement);
          URL url = classPathFile.toURI().toURL();
          addURL(url);
        }
      }
    }

    @Override
    public void addURL(URL url) {
      super.addURL(url);
    }
  }

}
