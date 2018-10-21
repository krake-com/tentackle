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
 * Support to locate bundle services.<br>
 * For annotations providing bundle support, a META-INF/service entry must be created
 * manually in order to be processed by the BundleFactory at runtime as well as the
 * analyze-, checker and i18n maven plugins at build time.
 *
 * @see Bundle
 * @see BundleFactory
 * @author harald
 */
public class BundleSupport {

  /**
   * The service name to find all annotations to mark classes as bundles.<br>
   * Such as &#64;Bundle or &#64;FxControllerService.
   * <p>
   * The bundle service entry may optionally hold the path to the resource bundle property file.
   * If missing, the resource name will be created from the classname.
   */
  public static final String BUNDLE_SERVICE = BundleSupport.class.getName();

  /**
   * Finds all bundle services.
   *
   * @param classLoader the classloader, null if default
   * @return the list of bundle supports
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
        String bundleName;
        switch (parts.size()) {
          case 1:
            bundleName = className;
            break;
          case 2:
            bundleName = parts.get(1);
            break;
          default:
            throw new TentackleRuntimeException("invalid bundle configuration '" + entry.getKey() + "' in " + entry.
                    getValue());
        }
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
   * @param info the optional jigsaw module info,  null if none
   * @param className the name of the annotated class
   * @param bundleName the name of the resource bundle
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
   * Gets the name of the annotated class.
   *
   * @return the annotated class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Gets the bundle name.
   *
   * @return the bundle name
   */
  public String getBundleName() {
    return bundleName;
  }

}
