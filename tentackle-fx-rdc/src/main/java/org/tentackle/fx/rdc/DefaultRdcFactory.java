/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc;

import javafx.scene.control.TreeView;

import org.tentackle.common.Service;
import org.tentackle.fx.Fx;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.rdc.crud.PdoCrud;
import org.tentackle.fx.rdc.search.PdoSearch;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * The default RDC factory.
 *
 * @author harald
 */
@Service(RdcFactory.class)
public class DefaultRdcFactory implements RdcFactory {

  /**
   * Creates the RDC factory.
   */
  public DefaultRdcFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoCrud<T> createPdoCrud(T pdo, boolean editable, boolean modal) {
    GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
    PdoEditor<T> editor = provider.createEditor();
    @SuppressWarnings("unchecked")
    PdoCrud<T> crud = Fx.load(PdoCrud.class);
    crud.setEditable(editable);
    crud.setEditor(editor);
    crud.setModal(modal);
    crud.setPdo(pdo);
    return crud;
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoSearch<T> createPdoSearch(T pdo) {
    GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
    PdoFinder<T> finder = provider.createFinder();
    @SuppressWarnings("unchecked")
    PdoSearch<T> search = Fx.load(PdoSearch.class);
    search.setFinder(finder);
    search.setPdo(pdo);
    return search;
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoTreeItem<T> createTreeItem(T pdo) {
    return new PdoTreeItem<>(pdo);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoTreeCell<T> createTreeCell(TreeView<T> treeView) {
    return new PdoTreeCell<>();   // treeView is ignored by default, but applications may override this
  }

  @Override
  public <S, T extends PersistentDomainObject<T>> PdoTreeTableCell<S, T> createTreeTableCell(TableColumnConfiguration<S, T> columnConfig) {
    return new PdoTreeTableCell<>(columnConfig);
  }

  @Override
  public <S, T extends PersistentDomainObject<T>> PdoTableCell<S, T> createTableCell(TableColumnConfiguration<S, T> columnConfig) {
    return new PdoTableCell<>(columnConfig);
  }

  @Override
  public <S> TablePopup<S> createTablePopup(FxTableView<S> table, String preferencesSuffix, String title) {
    return new TablePopup<>(table, preferencesSuffix, title);
  }

  @Override
  public <S> TablePopup<S> createTablePopup(FxTreeTableView<S> treeTable, String preferencesSuffix, String title) {
    return new TablePopup<>(treeTable, preferencesSuffix, title);
  }

}
