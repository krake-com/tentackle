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

import org.tentackle.common.ServiceFactory;


interface TableConfigurationProviderFactoryHolder {
  TableConfigurationProviderFactory INSTANCE = ServiceFactory.createService(TableConfigurationProviderFactory.class);
}


/**
 * A factory for {@link TableConfigurationProvider}s.
 *
 * @author harald
 */
public interface TableConfigurationProviderFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static TableConfigurationProviderFactory getInstance() {
    return TableConfigurationProviderFactoryHolder.INSTANCE;
  }

  /**
   * Creates a table configuration provider for a given class.
   *
   * @param <T> the type
   * @param clazz the class
   * @return the provider, null if none
   */
  <T> TableConfigurationProvider<T> createTableConfigurationProvider(Class<T> clazz);

}
