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
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.translate.CollectionTreeItemTranslator;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;

import java.util.Collection;

/**
 * PDO-aware collection to TreeItem translator for {@link FxTreeView} and {@link FxTreeTableView}.<br>
 * Used if the model provides a collection.
 * The PDOs are displayed as top-level items, with the tree cells or table configuration provided
 * by the element type's {@link GuiProvider}.
 *
 * @author harald
 * @param <T> the PDO type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = Collection.class, viewClass = TreeItem.class)
public class PdoCollectionTreeItemTranslator<T extends PersistentDomainObject<T>, C extends Collection<T>>
    extends CollectionTreeItemTranslator<T, C> {

  private boolean guiProviderExists;

  /**
   * Creates a translator.
   *
   * @param component the component
   */
  public PdoCollectionTreeItemTranslator(FxComponent component) {
    super(component);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void configureComponent() {
    FxComponent component = getComponent();
    Class<T> elemClass = ReflectionHelper.extractGenericInnerTypeClass(component.getGenericType());
    if (elemClass != null && PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      GuiProvider<T> provider = Rdc.createGuiProvider(Pdo.create(elemClass));
      guiProviderExists = true;
      if (component instanceof FxTreeView) {
        FxTreeView<T> treeView = (FxTreeView<T>) component;
        treeView.setShowRoot(false);
        treeView.setCellFactory(provider.getTreeCellFactory());
      }
      else if (component instanceof FxTreeTableView) {
        FxTreeTableView<T> treeTableView = (FxTreeTableView<T>) component;
        treeTableView.setShowRoot(false);
        configureTreeTableView(treeTableView, provider.createTableConfiguration());
      }
    }
    else {
      super.configureComponent();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected TreeItem<T> createTreeItem(Object object) {
    return guiProviderExists ?
           Rdc.createGuiProvider((T) object).createTreeItem() :   // real cast to PersistentDomainObject, see comment in super method
           super.createTreeItem(object);
  }

}
