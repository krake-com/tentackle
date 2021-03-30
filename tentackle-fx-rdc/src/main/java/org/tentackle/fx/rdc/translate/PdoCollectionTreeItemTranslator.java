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

package org.tentackle.fx.rdc.translate;

import javafx.scene.control.TreeItem;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.FxTreeView;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;
import org.tentackle.fx.translate.AbstractValueTranslator;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * PDO to TreeItem translator for {@link FxTreeView} and {@link FxTreeTableView}.<br>
 * Used if the model provides a collection of PDOs.
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = Collection.class, viewClass = TreeItem.class)
public class PdoCollectionTreeItemTranslator<T extends PersistentDomainObject<T>, C extends List<T>>
    extends AbstractValueTranslator<C, TreeItem<T>> {


  private C pdoCollection;

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  @SuppressWarnings("unchecked")
  public PdoCollectionTreeItemTranslator(FxComponent component) {
    super(component);

    Class<T> elemClass = ReflectionHelper.extractGenericInnerTypeClass(component.getGenericType());
    if (elemClass != null) {
      if (component instanceof FxTreeView) {
        FxTreeView<T> treeView = (FxTreeView<T>) component;
        treeView.setShowRoot(false);
        GuiProvider<T> provider = null;
        if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
          provider = GuiProviderFactory.getInstance().createGuiProvider(Pdo.create(elemClass));
        }
        if (provider != null) {
          treeView.setCellFactory(provider.getTreeCellFactory());
        }
        else {
          treeView.setCellFactory(Rdc::createTreeCell);
        }
      }
      else if (component instanceof FxTreeTableView) {
        FxTreeTableView<T> treeTableView = (FxTreeTableView<T>) component;
        treeTableView.setShowRoot(false);
        configureTreeTableView((FxTreeTableView<T>) component, elemClass);
      }
    }
  }

  @Override
  public Function<C, TreeItem<T>> toViewFunction() {
    return m -> {
      pdoCollection = m;
      TreeItem<T> root = new TreeItem<>();    // invisible root (disabled in FxTreeViewDelegate and FxTreeTableViewDelegate)
      if (m != null) {
        for (T pdo : m) {
          root.getChildren().add(Rdc.createTreeItem(pdo));
        }
      }
      return root;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<TreeItem<T>, C> toModelFunction() {
    return v -> {
      if (pdoCollection != null) {
        pdoCollection.clear();
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
            pdoCollection.add(treeItem.getValue());
          }
        }
      }
      return pdoCollection;
    };
  }


  /**
   * Configures the treetableview.
   *
   * @param treeTableView the treetableview
   * @param elemClass the list's elememt class
   */
  protected void configureTreeTableView(FxTreeTableView<T> treeTableView, Class<T> elemClass) {
    if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      // is a PDO: get table configuration from gui provider
      T pdo = Pdo.create(elemClass);
      GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
      TableConfiguration<T> configuration = createTableConfiguration(provider);
      configuration.configure(treeTableView);
    }
    else {
      // determine optional table configuration provider for elemClass
      TableConfigurationProvider<T> provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(elemClass);
      if (provider != null) {
        TableConfiguration<T> configuration = createTableConfiguration(provider);
        configuration.configure(treeTableView);
      }
    }

    // else: no configuration available -> must be done in application explicitly
  }

  /**
   * Creates the table configuration and performs the binding.
   *
   * @param provider the configuration provider
   * @return the table configuration
   */
  protected TableConfiguration<T> createTableConfiguration(TableConfigurationProvider<T> provider) {
    TableConfiguration<T> configuration = provider.createTableConfiguration();
    if (configuration.getBindingType() == TableConfiguration.BINDING.YES) {
      configuration.getBinder().bind();
    }
    else if (configuration.getBindingType() == TableConfiguration.BINDING.INHERITED) {
      configuration.getBinder().bindAllInherited();
    }
    return configuration;
  }

}
