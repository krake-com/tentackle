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

import javafx.scene.control.TreeItem;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;
import org.tentackle.reflect.ReflectionHelper;

import java.util.Collection;
import java.util.function.Function;

/**
 * Collection to TreeItem translator for {@link FxTreeView} and {@link FxTreeTableView}.<br>
 * Used if the model provides a collection. The elements become the children of an invisible root item.
 * The tree items are created via the {@link TableConfiguration}, if available.
 *
 * @author harald
 * @param <T> the object type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = Collection.class, viewClass = TreeItem.class)
public class CollectionTreeItemTranslator<T, C extends Collection<T>> extends AbstractValueTranslator<C, TreeItem<T>> {


  private boolean providerLoaded;
  private TableConfigurationProvider<T> provider;
  private boolean configurationLoaded;
  private TableConfiguration<T> configuration;
  private C collection;

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  public CollectionTreeItemTranslator(FxComponent component) {
    super(component);
    configureComponent();
  }

  @Override
  public Function<C, TreeItem<T>> toViewFunction() {
    return m -> {
      collection = m;
      TreeItem<T> root = new TreeItem<>();    // invisible root (disabled in FxTreeViewDelegate and FxTreeTableViewDelegate)
      if (m != null) {
        for (T elem : m) {
          root.getChildren().add(createTreeItem(elem));
        }
      }
      return root;
    };
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Function<TreeItem<T>, C> toModelFunction() {
    return v -> {
      if (collection != null) {
        collection.clear();
        TreeItem<T> root;
        if (getComponent() instanceof FxTreeView) {
          root = ((FxTreeView) getComponent()).getRoot();
        }
        else if (getComponent() instanceof FxTreeTableView) {
          root = ((FxTreeTableView) getComponent()).getRoot();
        }
        else {
          root = null;
        }
        if (root != null) {
          for (TreeItem<T> treeItem : root.getChildren()) {
            collection.add(treeItem.getValue());
          }
        }
      }
      return collection;
    };
  }


  /**
   * Configures the component if possible.
   */
  @SuppressWarnings("unchecked")
  protected void configureComponent() {
    FxComponent component = getComponent();
    Class<T> elemClass = ReflectionHelper.extractGenericInnerTypeClass(component.getGenericType());
    if (elemClass != null) {
      if (component instanceof FxTreeView) {
        FxTreeView<T> treeView = (FxTreeView<T>) component;
        treeView.setShowRoot(false);
      }
      else if (component instanceof FxTreeTableView) {
        FxTreeTableView<T> treeTableView = (FxTreeTableView<T>) component;
        treeTableView.setShowRoot(false);
        configureTreeTableView(treeTableView, getTableConfiguration());
      }
    }
  }

  /**
   * Gets the cached {@link TableConfigurationProvider}.
   *
   * @return null if no provider available
   */
  protected TableConfigurationProvider<T> getTableConfigurationProvider() {
    if (!providerLoaded) {
      Class<T> elemClass = ReflectionHelper.extractGenericInnerTypeClass(getComponent().getGenericType());
      if (elemClass != null) {
        provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(elemClass);
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
  @SuppressWarnings("unchecked")
  protected TreeItem<T> createTreeItem(Object object) {     // we cannot write T object, because method is overridden...
    // ... in PdoCollectionTreeItemTranslator. The overridden method will type-erase T to PersistentDomainObject
    // and assert this with a cast. This cast would fail if T is not a PDO. The two casts below are just for
    // the compiler since they type-erase to Object.
    TableConfiguration<T> configuration = getTableConfiguration();  // if available, this will also work for FxTreeView
    return configuration != null ? configuration.createTreeItem((T) object) : new TreeItem<>((T) object);
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
