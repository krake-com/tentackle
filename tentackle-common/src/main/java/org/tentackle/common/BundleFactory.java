/*
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


package org.tentackle.common;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;


interface BundleFactory$Singleton {
  BundleFactory INSTANCE = ServiceFactory.createService(BundleFactory.class, DefaultBundleFactory.class);
}


/**
 * The resource bundle factory.
 * <p>
 * Tentackle applications should use {@code BundleFactory.getBundle(...)} instead of
 * {@code ResourceBundle.getBundle(...)} for 3 reasons:
 * <ol>
 * <li>for modular applications:<br>
 * In Jigsaw, ResourceBundles are only visible from their own module by default. Furthermore, ResourceBundleControlProvider
 * is unavailable, i.e. disabled and throws an exception if used. Loading a bundle from another module, as it is the
 * case for a factory located in a framework module, would not work at all. Since Java 9, a workaround is provided
 * via a service provider interface and corresponding uses/provides entries in module-info. Alternatively, the modules
 * can be declared as "open". Either approach is rather cumbersome and may lead to runtime errors or loss of encapsulation.<br>
 * Thanks to Tentackle's {@link ServiceFactory} and {@link ModuleSorter}, the BundleFactory knows to which
 * module a resource belongs to and loads it from the enclosing module.
 * </li>
 * <li>for non-modular applications:<br>
 * because the use cases of {@link java.util.spi.ResourceBundleControlProvider}
 * are still very limited in Java 8. The major restriction is that the providers
 * are loaded from <em>installed</em> extensions only. This requires either a copy the tentackle-jars in
 * the extensions directory of the JRE installation (which is a bad habit in general, because it applies to all applications)
 * or to set the runtime option -Djava.ext.dirs=... to a directory path containing the jars.
 * The latter may be a workaround for applications started from the shell, but is not feasable for webstart
 * applications or applications running within a container.<br>
 * Tentackle's bundle factory uses the same interface {@link java.util.spi.ResourceBundleControlProvider} and semantics
 * to lookup and install providers as the standard JRE, but scans the application classpath instead
 * of the system classpath.<br>
 * See {@code org.tentackle.locale.StoredBundleControlProvider} in tentackle-i18n for an example.
 * </li>
 * <li>
 * The classes providing bundles can be annotated with &#64;{@link Bundle} and will automatically
 * become registered resources via META-INF by means of the tentackle:analyze plugin.
 * Same applies to some framework-related annotations auch as &#64;FxControllerService or
 * &#64;GuiProviderService. The tentackle-i18n plugin can then check during the test phase whether
 * the keys used in the java source are really defined in a resource bundle.
 * </li>
 * </ol>
 *
 *
 * @author harald
 */
public interface BundleFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static BundleFactory getInstance() {
    return BundleFactory$Singleton.INSTANCE;
  }

  /**
   * Gets a resource bundle using the specified base name, the default locale, and the caller's class loader.<br>
   * Replacement for {@link ResourceBundle#getBundle(java.lang.String)}.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   *
   * @return a resource bundle for the given base name and the default locale
   */
  static ResourceBundle getBundle(String baseName) {
    return getInstance().findBundle(baseName, Locale.getDefault());
  }

  /**
   * Gets a resource bundle using the specified base name and locale, and the caller's class loader.<br>
   * Replacement for {@link ResourceBundle#getBundle(java.lang.String, java.util.Locale) }.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale the locale for which a resource bundle is desired
   *
   * @return a resource bundle for the given base name and locale
   */
  static ResourceBundle getBundle(String baseName, Locale locale) {
    return getInstance().findBundle(baseName, locale);
  }

  /**
   * Gets a resource bundle using the specified base name, locale, and class loader.<br>
   * Replacement for {@link ResourceBundle#getBundle(java.lang.String, java.util.Locale, java.lang.ClassLoader) }.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale   the locale for which a resource bundle is desired
   * @param loader   the class loader from which to load the resource bundle
   *
   * @return a resource bundle for the given base name and locale
   */
  static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
    return getInstance().findBundle(baseName, locale, loader);
  }


  /**
   * Gets the classloader to load the bundles.
   *
   * @return the classloader, null if default
   */
  ClassLoader getClassLoader();

  /**
   * Sets the classloader to load the bundles.<br>
   * If set, this classloader will be used for {@link #getBundle(java.lang.String)} and
   * {@link #getBundle(java.lang.String, java.util.Locale)}.
   *
   * @param classLoader the classloader, null if default
   */
  void setClassLoader(ClassLoader classLoader);


  /**
   * Gets the control providers.<br>
   * Method can be used to add a provider explicitly.
   *
   * @return the non-empty list of providers or null, if no providers found
   */
  List<ResourceBundleControlProvider> getProviders();

  /**
   * Gets the control to load the bundle.
   *
   * @param baseName the bundle basename
   * @return the control, null if delegate to ResourceBundle's default
   */
  ResourceBundle.Control getControl(String baseName);

  /**
   * Finds a resource bundle using the specified base name and locale, and the caller's class loader.<br>
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale the locale for which a resource bundle is desired
   *
   * @return a resource bundle for the given base name and locale
   */
  ResourceBundle findBundle(String baseName, Locale locale);

  /**
   * Finds a resource bundle using the specified base name, locale, and class loader.<br>
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale   the locale for which a resource bundle is desired
   * @param loader   the class loader from which to load the resource bundle
   *
   * @return a resource bundle for the given base name and locale
   */
  ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader);

  /**
   * Clears the bundle cache.
   */
  void clearCache();
}
