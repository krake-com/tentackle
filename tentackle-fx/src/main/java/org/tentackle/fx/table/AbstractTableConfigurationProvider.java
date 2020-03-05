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

package org.tentackle.fx.table;

import org.tentackle.common.BundleFactory;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Base implementation of a {@link TableConfigurationProvider}.
 *
 * @param <T> the type
 * @author harald
 */
public abstract class AbstractTableConfigurationProvider<T> implements TableConfigurationProvider<T> {
  
  private Boolean bundleProvided;   // != null if determined according to TableConfigurationProviderService annotation or isBundleProvided
  private ResourceBundle bundle;    // optional resource bundle

  /**
   * Creates a table configuration provider.
   */
  public AbstractTableConfigurationProvider() {
    TableConfigurationProviderService annotation = getClass().getAnnotation(TableConfigurationProviderService.class);
    if (annotation != null) {
      bundleProvided = !annotation.noBundle();
    }
  }

  /**
   * Returns whether a resource bundle is provided.
   *
   * @return true if provided, false if no bundle
   */
  public boolean isBundleProvided() {
    if (bundleProvided == null) {
      try {
        getBundle();
        bundleProvided = true;
      }
      catch (MissingResourceException mx) {
        bundleProvided = false;
      }
    }
    return bundleProvided;
  }

  /**
   * Gets the resource bundle for this provider.
   * <p>
   * Throws {@link java.util.MissingResourceException} if no bundle found.
   *
   * @return the resource bundle
   */
  public ResourceBundle getBundle() {
    if (bundle == null) {
      bundle = BundleFactory.getBundle(getClass().getName());
    }
    return bundle;
  }

}
