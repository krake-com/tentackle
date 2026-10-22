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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Support to locate bundle services.
 * <p>
 * Annotations marking classes as bundles, such as &#64;{@link Bundle} or &#64;FxControllerService,
 * must be listed in {@code META-INF/services/org.tentackle.common.BundleSupport} of the module declaring
 * the annotation. This entry must be created manually in order to be processed by the {@link BundleFactory}
 * at runtime as well as by the tentackle-, check- and i18n-maven-plugins at build time.
 * <p>
 * For each such annotation, the analyze goal of the tentackle-maven-plugin writes the annotated classes to
 * {@code META-INF/bundles/<annotation classname>}, one entry per line, in the form
 * <pre>
 *   bundleName
 * </pre>
 * or, if the bundle is registered under an additional key name (see {@link Bundle#value()}),
 * <pre>
 *   keyName = bundleName
 * </pre>
 * Colons and spaces are accepted as separators as well.
 *
 * @see Bundle
 * @see BundleFactory
 * @author harald
 */
public class BundleSupport {

  /**
   * The service name to find all annotations to mark classes as bundles.<br>
   * Such as &#64;Bundle or &#64;FxControllerService.
   */
  public static final String BUNDLE_SERVICE = BundleSupport.class.getName();

  /**
   * Finds all bundle services.
   *
   * @param classLoader the classloader, null if default
   * @return the list of bundle supports
   * @throws TentackleRuntimeException if an entry is malformed
   */
  public static List<BundleSupport> getBundles(ClassLoader classLoader) {
    if (classLoader == null) {
      classLoader = BundleSupport.class.getClassLoader();
    }
    List<BundleSupport> bundles = new ArrayList<>();
    for (Map.Entry<String, URL> providerEntry :
              ServiceFactory.getServiceFinder(classLoader, Constants.DEFAULT_SERVICE_PATH).
                findServiceConfigurations(BundleSupport.BUNDLE_SERVICE).entrySet()) {

      String serviceName = providerEntry.getKey();

      // for all bundles annotated with such an annotation
      for (Map.Entry<String, URL> entry :
              ServiceFactory.getServiceFinder(classLoader, Constants.BUNDLE_SERVICE_PATH).
                findServiceConfigurations(serviceName).entrySet()) {
        ModuleInfo info = ModuleSorter.INSTANCE.getModuleInfo(entry.getValue());
        List<String> parts = StringHelper.split(entry.getKey(), ":= ");
        // figure out the bundle name
        String className = parts.get(0);
        String bundleName = switch (parts.size()) {
          case 1 -> className;
          case 2 -> parts.get(1);
          default -> throw new TentackleRuntimeException("invalid bundle configuration '" + entry.getKey() +
                                                         "' in " + entry.getValue());
        };
        bundles.add(new BundleSupport(info, className, bundleName));
      }
    }
    return bundles;
  }



  private final ModuleInfo info;
  private final String className;
  private final String bundleName;

  /**
   * Creates a bundle support object.
   *
   * @param info the optional jigsaw module info, null if none
   * @param className the key name the bundle is registered for, usually the same as the bundle name
   * @param bundleName the name of the resource bundle, i.e., the name of the annotated class
   */
  public BundleSupport(ModuleInfo info, String className, String bundleName) {
    this.info = info;
    this.className = className;
    this.bundleName = bundleName;
  }

  /**
   * Gets the module info.
   *
   * @return the optional module info, null if none
   */
  public ModuleInfo getModuleInfo() {
    return info;
  }

  /**
   * Gets the key name the bundle is registered for.<br>
   * This is the same as the bundle name, unless the bundle was registered under an additional key name
   * (see {@link Bundle#value()}).
   *
   * @return the key name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Gets the bundle name.<br>
   * This is the name of the annotated class.
   *
   * @return the bundle name
   */
  public String getBundleName() {
    return bundleName;
  }

}
