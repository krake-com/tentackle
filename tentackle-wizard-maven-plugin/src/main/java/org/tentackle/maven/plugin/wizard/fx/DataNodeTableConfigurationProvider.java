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

package org.tentackle.maven.plugin.wizard.fx;

import javafx.scene.control.TreeItem;

import org.tentackle.fx.table.DefaultTableConfigurationProvider;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProviderService;

/**
 * Configuration provider for DataNode tables and tree tables.
 *
 * @param <T> the node type
 */
@TableConfigurationProviderService(value = DataNode.class, noBundle = true)
public class DataNodeTableConfigurationProvider<T extends DataNode> extends DefaultTableConfigurationProvider<T> {

  @Override
  protected TableConfiguration<T> createTableConfiguration(Class<T> clazz, String name) {
    return new DataNodeTableConfiguration<>(clazz, name) {
      @Override
      @SuppressWarnings({ "unchecked", "rawtypes" })
      public TreeItem<T> createTreeItem(T object) {
        return new DataNodeTreeItem(object, getPredicate());
      }
    };
  }

}
