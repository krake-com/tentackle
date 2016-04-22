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


package org.tentackle.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.ResourceBundleControlProvider;

/**
 * Default implementation of a bundle factory.<br>
 *
 * @author harald
 */
@Service(BundleFactory.class)   // doesn't work in tt-common, for documentation only
public class DefaultBundleFactory implements BundleFactory {

  private final List<ResourceBundleControlProvider> providers;    // the control providers, null if none
  private final Map<String, ResourceBundle.Control> controlMap;   // map of basenames to controls


  /**
   * Creates the bundle factory.
   */
  public DefaultBundleFactory() {
    providers = createProviders();
    controlMap = new ConcurrentHashMap<>();
  }

  @Override
  public final List<ResourceBundleControlProvider> getProviders() {
    return providers;
  }

  @Override
  public ResourceBundle.Control getControl(String baseName) {
    if (providers != null) {
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
  public ResourceBundle findBundle(String baseName) {
    ResourceBundle.Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName) :
              ResourceBundle.getBundle(baseName, control);
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale) {
    ResourceBundle.Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName, locale) :
              ResourceBundle.getBundle(baseName, locale, control);
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader) {
    ResourceBundle.Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName, locale, loader) :
              ResourceBundle.getBundle(baseName, locale, loader, control);
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
        providerList.add(clazz.newInstance());
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
      throw new TentackleRuntimeException("cannot load bundle control providers", ex);
    }
    return providerList;
  }

}
