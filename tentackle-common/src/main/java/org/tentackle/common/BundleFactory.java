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


package org.tentackle.common;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;


interface BundleFactoryHolder {
  BundleFactory INSTANCE = ServiceFactory.createService(BundleFactory.class, DefaultBundleFactory.class);
}


/**
 * The resource bundle factory.
 * <p>
 * Tentackle applications should use {@code BundleFactory.getBundle(...)} instead of
 * {@code ResourceBundle.getBundle(...)} for 3 reasons:
 * <ol>
 * <li>for modular applications:<br>
 * In Jigsaw, ResourceBundles are only visible from their own module by default. Furthermore,
 * {@link ResourceBundle.Control}s and {@link ResourceBundleControlProvider}s are not supported for named modules.
 * Loading a bundle from another module, as is the case for a factory located in a framework module,
 * would not work at all. The JDK provides a workaround via a service provider interface and corresponding
 * uses/provides entries in module-info. Alternatively, the modules can be declared as "open".
 * Either approach is rather cumbersome and may lead to runtime errors or loss of encapsulation.<br>
 * Thanks to Tentackle's {@link ServiceFactory} and {@link ModuleSorter}, the BundleFactory knows which
 * module a resource belongs to and loads it via the {@link ModuleHook} of that module.
 * </li>
 * <li>for non-modular applications:<br>
 * The bundle factory uses the same interface {@link ResourceBundleControlProvider} and semantics
 * to look up and install providers as the JDK, but locates the providers via the {@link ServiceFinder}.
 * The controls are applied to all bundles that are not loaded via a {@link ModuleHook}.<br>
 * See {@code org.tentackle.i18n.StoredBundleControlProvider} in tentackle-i18n for an example.
 * </li>
 * <li>
 * The classes providing bundles can be annotated with &#64;{@link Bundle} and will automatically
 * become registered resources in {@code META-INF/bundles} by means of the analyze goal of the tentackle-maven-plugin.
 * The same applies to some framework-related annotations such as &#64;FxControllerService or
 * &#64;GuiProviderService. The {@code bundles} goal of the tentackle-check-maven-plugin can then check
 * whether the keys used in the Java source are really defined in a resource bundle.
 * </li>
 * </ol>
 * <p>
 * Leading slashes are removed from the base names, i.e., {@code "/org/example/MyBundle"} and
 * {@code "org/example/MyBundle"} denote the same bundle.
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
    return BundleFactoryHolder.INSTANCE;
  }

  /**
   * Gets a resource bundle using the specified base name and the default locale.<br>
   * Replacement for {@link ResourceBundle#getBundle(java.lang.String)}.
   * See {@link #findBundle(String, Locale)} for how the bundle is located.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   *
   * @return a resource bundle for the given base name and the default locale
   */
  static ResourceBundle getBundle(String baseName) {
    return getInstance().findBundle(baseName, Locale.getDefault());
  }

  /**
   * Gets a resource bundle using the specified base name and locale.<br>
   * Replacement for {@link ResourceBundle#getBundle(java.lang.String, java.util.Locale) }.
   * See {@link #findBundle(String, Locale)} for how the bundle is located.
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
   * The returned list can be modified to add a provider explicitly, but only if at least one
   * provider has been found.
   *
   * @return the non-empty list of providers or null, if no providers found
   */
  List<ResourceBundleControlProvider> getProviders();

  /**
   * Gets the control to load the bundle.<br>
   * The control is determined by the first provider returning a non-null control for the given basename.
   *
   * @param baseName the bundle basename
   * @return the control, null if delegate to ResourceBundle's default
   */
  ResourceBundle.Control getControl(String baseName);

  /**
   * Finds a resource bundle using the specified base name and locale.
   * <p>
   * If the bundle is registered for a named module (see {@link Bundle}), it is loaded via the
   * {@link ModuleHook} of that module. Otherwise, it is loaded via {@link ResourceBundle#getBundle}
   * with the classloader set by {@link #setClassLoader(ClassLoader)}, if any, and the control
   * of {@link #getControl(String)}, if any.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale the locale for which a resource bundle is desired
   *
   * @return a resource bundle for the given base name and locale
   */
  ResourceBundle findBundle(String baseName, Locale locale);

  /**
   * Finds a resource bundle using the specified base name, locale, and class loader.<br>
   * Same as {@link #findBundle(String, Locale)}, but uses the given classloader for bundles
   * not registered for a named module.
   *
   * @param baseName the base name of the resource bundle, a fully qualified class name
   * @param locale   the locale for which a resource bundle is desired
   * @param loader   the class loader from which to load the resource bundle
   *
   * @return a resource bundle for the given base name and locale
   */
  ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader);

  /**
   * Clears the bundle cache.<br>
   * The default implementation clears the {@link ResourceBundle} cache for the classloader
   * that loaded the factory.
   */
  void clearCache();
}
