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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.ResourceBundleControlProvider;

/**
 * Default implementation of a bundle factory.
 * <p>
 * Works for modular and non-modular applications.
 * Bundles registered via {@link BundleSupport} for named modules are loaded through the module's {@link ModuleHook}.
 * All other bundles are loaded via {@link ResourceBundle#getBundle}, with the {@link ResourceBundle.Control}
 * of the first {@link ResourceBundleControlProvider} returning one. Controls are used only if this factory runs
 * in an unnamed module, i.e. in a non-modular application: the JDK rejects them for callers in named modules.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(BundleFactory.class)
public class DefaultBundleFactory implements BundleFactory {

  private final List<ResourceBundleControlProvider> providers;    // the control providers, null if none
  private final Map<String, ResourceBundle.Control> controlMap;   // map of basenames to controls
  private final Map<String, BundleSupport> moduleBundlesMap;      // map of basenames to annotated bundles in modules
  private final boolean controlsSupported;                        // true if ResourceBundle.getBundle accepts a control from us
  private ClassLoader classLoader;                                // another classloader, null for default loader

  /**
   * Creates the bundle factory.
   */
  public DefaultBundleFactory() {
    providers = createProviders();
    controlMap = new ConcurrentHashMap<>();
    moduleBundlesMap = new HashMap<>();
    // ResourceBundle.getBundle throws an UnsupportedOperationException if invoked with a control from a named module.
    // The caller is always this class, also for subclasses in other modules, so its module decides.
    controlsSupported = !getClass().getModule().isNamed();
    for (BundleSupport support : BundleSupport.getBundles(getClass().getClassLoader())) {
      if (support.getModuleInfo() != null) {
        moduleBundlesMap.put(support.getBundleName(), support);
        moduleBundlesMap.put(support.getClassName(), support);
      }
    }
  }

  @Override
  public void clearCache() {
    ResourceBundle.clearCache(getClass().getClassLoader());
  }

  @Override
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  @Override
  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public final List<ResourceBundleControlProvider> getProviders() {
    return providers;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Always null if this factory runs in a named module, since the JDK supports controls only for callers in
   * unnamed modules.
   */
  @Override
  public ResourceBundle.Control getControl(String baseName) {
    if (providers != null && controlsSupported) {
      ResourceBundle.Control control = controlMap.get(baseName);
      if (control != null) {
        return control;
      }
      for (ResourceBundleControlProvider provider : providers) {
        control = provider.getControl(baseName);
        if (control != null) {
          controlMap.put(baseName, control);
          return control;
        }
      }
    }
    return null;
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale) {
    baseName = normalizeBasename(baseName);
    BundleSupport support = moduleBundlesMap.get(baseName);
    if (support != null) {
      // classloaders and bundle controls don't work for named modules --> fallback to bundle support
      return getBundle(support, locale);
    }
    else {
      ResourceBundle.Control control = getControl(baseName);
      if (classLoader == null) {
        return control == null ?
                  ResourceBundle.getBundle(baseName, locale) :
                  ResourceBundle.getBundle(baseName, locale, control);
      }
      return control == null ?
                  ResourceBundle.getBundle(baseName, locale, classLoader) :
                  ResourceBundle.getBundle(baseName, locale, classLoader, control);
    }
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader) {
    baseName = normalizeBasename(baseName);
    BundleSupport support = moduleBundlesMap.get(baseName);
    if (support != null) {
      // classloaders and bundle controls don't work for named modules --> fallback to bundle support
      return getBundle(support, locale);
    }
    else {
      ResourceBundle.Control control = getControl(baseName);
      return control == null ?
                ResourceBundle.getBundle(baseName, locale, loader) :
                ResourceBundle.getBundle(baseName, locale, loader, control);
    }
  }


  /**
   * Gets the bundle from the corresponding module according to the bundle support.
   *
   * @param support the bundle support
   * @param locale the locale
   * @return the bundle
   */
  protected ResourceBundle getBundle(BundleSupport support, Locale locale) {
    return support.getModuleInfo().getHook().getBundle(support.getBundleName(), locale);
  }


  /**
   * Creates a list of providers.
   *
   * @return the non-empty list of providers, null if no providers found
   */
  protected List<ResourceBundleControlProvider> createProviders() {
    List<ResourceBundleControlProvider> providerList = null;
    try {
      for (Class<ResourceBundleControlProvider> clazz:
              ServiceFactory.getServiceFinder().findServiceProviders(ResourceBundleControlProvider.class)) {
        if (providerList == null) {
          providerList = new ArrayList<>();
        }
        providerList.add(clazz.getDeclaredConstructor().newInstance());
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           InvocationTargetException | NoSuchMethodException ex) {
      throw new TentackleRuntimeException("cannot load bundle control providers", ex);
    }
    return providerList;
  }

  /**
   * Normalizes the basename.<br>
   * The name is usually a dotted classname or a slashed resource name without a leading slash.
   *
   * @param baseName the name
   * @return the normalized name
   */
  protected String normalizeBasename(String baseName) {
    while (baseName.startsWith("/")) {
      baseName = baseName.substring(1);
    }
    return baseName;
  }

}
