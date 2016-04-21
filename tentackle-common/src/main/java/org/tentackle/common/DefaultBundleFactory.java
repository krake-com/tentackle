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
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.spi.ResourceBundleControlProvider;

/**
 * Default implementation of a bundle factory.<br>
 *
 * @author harald
 */
@Service(BundleFactory.class)
public class DefaultBundleFactory implements BundleFactory {

  private List<ResourceBundleControlProvider> providers;    // the control providers


  /**
   * Creates the bundle factory.
   */
  public DefaultBundleFactory() {
    try {
      for (Class<ResourceBundleControlProvider> clazz:
              ServiceFactory.getServiceFinder().findServiceProviders(ResourceBundleControlProvider.class)) {
        if (providers == null) {
          providers = new ArrayList<>();
        }
        providers.add(clazz.newInstance());
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
      throw new TentackleRuntimeException("cannot load bundle control providers", ex);
    }
  }


  @Override
  public ResourceBundle findBundle(String baseName) {
    Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName) :
              ResourceBundle.getBundle(baseName, control);
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale) {
    Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName, locale) :
              ResourceBundle.getBundle(baseName, locale, control);
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader) {
    Control control = getControl(baseName);
    return control == null ?
              ResourceBundle.getBundle(baseName, locale, loader) :
              ResourceBundle.getBundle(baseName, locale, loader, control);
  }


  /**
   * Gets the control to load the bundle.
   *
   * @param baseName the bundle basename
   * @return the control, null if delegate to ResourceBundle's default
   */
  private ResourceBundle.Control getControl(String baseName) {
    if (providers != null) {
      for (ResourceBundleControlProvider provider : providers) {
        ResourceBundle.Control control = provider.getControl(baseName);
        if (control != null) {
          return control;
        }
      }
    }
    return null;
  }

}
