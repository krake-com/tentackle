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

import javafx.collections.ObservableList;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.translate.ObservableListTranslator;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;

/**
 * List translator.<br>
 * Replaces ObservableListTranslator and adds PDO detection for automatic
 * configuration via GuiProvider for tables.
 *
 * @author harald
 * @param <T> the PDO type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = List.class, viewClass = ObservableList.class)
public class PdoObservableListTranslator<T extends PersistentDomainObject<T>, C extends List<T>>
       extends ObservableListTranslator<T, C> {

  /**
   * Creates a translator for components maintaining a list of objects.
   *
   * @param component the component
   */
  public PdoObservableListTranslator(FxComponent component) {
    super(component);
  }

  @Override
  protected void configureTableView(FxTableView<T> tableView, Class<T> elemClass) {
    if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      // is a PDO: get table configuration from gui provider
      T pdo = Pdo.create(elemClass);
      GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
      TableConfiguration<T> configuration = createTableConfiguration(provider);
      configuration.configure(tableView);
    }
    else {
      super.configureTableView(tableView, elemClass);
    }
  }

  @Override
  protected void configureTreeTableView(FxTreeTableView<T> treeTableView, Class<T> elemClass) {
    if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      // is a PDO: get table configuration from gui provider
      T pdo = Pdo.create(elemClass);
      GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
      TableConfiguration<T> configuration = createTableConfiguration(provider);
      configuration.configure(treeTableView);
    }
    else {
      super.configureTreeTableView(treeTableView, elemClass);
    }
  }

}
