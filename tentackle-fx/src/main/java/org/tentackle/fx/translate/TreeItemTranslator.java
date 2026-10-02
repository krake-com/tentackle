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

package org.tentackle.fx.translate;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.table.DefaultTableConfigurationProvider;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;

import javafx.scene.control.TreeItem;
import java.util.function.Function;

/**
 * TreeItem translator for {@link FxTreeView} and
 * {@link org.tentackle.fx.component.FxTreeTableView}.<br>
 * Used if the model provides a single object, which becomes the root item.
 * The tree items are created via the {@link TableConfiguration}, if available.
 *
 * @author harald
 * @param <T> the object type
 */
@ValueTranslatorService(modelClass = Object.class, viewClass = TreeItem.class)
public class TreeItemTranslator<T> extends AbstractValueTranslator<T, TreeItem<T>> {

  private boolean providerLoaded;
  private TableConfigurationProvider<T> provider;
  private boolean configurationLoaded;
  private TableConfiguration<T> configuration;

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  public TreeItemTranslator(FxComponent component) {
    super(component);
    configureComponent();
  }

  @Override
  public Function<T, TreeItem<T>> toViewFunction() {
    return this::createTreeItem;
  }

  @Override
  public Function<TreeItem<T>, T> toModelFunction() {
    return TreeItem::getValue;
  }



  /**
   * Configures the component if possible.
   */
  @SuppressWarnings("unchecked")
  protected void configureComponent() {
    FxComponent component = getComponent();
    if (component instanceof FxTreeTableView) {
      FxTreeTableView<T> treeTableView = (FxTreeTableView<T>) component;
      configureTreeTableView(treeTableView, getTableConfiguration());
    }
  }

  /**
   * Gets the cached {@link TableConfigurationProvider}.
   *
   * @return null if no provider available
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected TableConfigurationProvider<T> getTableConfigurationProvider() {
    if (!providerLoaded) {
      Class elemClass = getComponent().getType();
      if (elemClass != null) {
        provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(elemClass);
        if (provider == null && getComponent().isBindable()) {
          // create default provider w/o bundle and standard binding
          provider = new DefaultTableConfigurationProvider<>(elemClass, false, TableConfiguration.BINDING.YES);
        }
      }
      providerLoaded = true;
    }
    return provider;
  }

  /**
   * Gets the table configuration.
   *
   * @return the config, null if not available
   */
  protected TableConfiguration<T> getTableConfiguration() {
    if (!configurationLoaded) {
      TableConfigurationProvider<T> provider = getTableConfigurationProvider();
      if (provider != null) {
        configuration = provider.createTableConfiguration();
      }
      configurationLoaded = true;
    }
    return configuration;
  }

  /**
   * Creates a tree item.<br>
   * Uses the {@link TableConfigurationProvider} if available.
   *
   * @param object the object
   * @return the tree item
   */
  protected TreeItem<T> createTreeItem(T object) {
    TableConfiguration<T> configuration = getTableConfiguration();  // if available, this will also work for FxTreeView
    return configuration != null ? configuration.createTreeItem(object) : new TreeItem<>(object);
  }

  /**
   * Configures the treetable view.
   *
   * @param treeTableView the treetable view
   * @param configuration the table configuration, null if none
   */
  protected void configureTreeTableView(FxTreeTableView<T> treeTableView, TableConfiguration<T> configuration) {
    if (configuration != null) {
      configuration.configure(treeTableView);
      if (configuration.getBindingType() == TableConfiguration.BINDING.YES) {
        configuration.getBinder().bind();
      }
      else if (configuration.getBindingType() == TableConfiguration.BINDING.INHERITED) {
        configuration.getBinder().bindAllInherited();
      }
    }

    // else: no configuration available -> must be done in application explicitly
  }

}
