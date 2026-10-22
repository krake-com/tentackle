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

package org.tentackle.fx.table;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.table.edit.TableEditContext;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableRow;

/**
 * Extended tree table cell.<br>
 * Renders the item via the column's {@link TableCellType} and edits it via an {@link FxComponent}.
 * After committing with {@code ENTER} or {@code TAB}, the
 * {@link org.tentackle.fx.table.edit.TableEditHandler} of the table configuration is notified and determines
 * the next cell to edit.
 *
 * @param <S> type of the objects contained within the tree table's item list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public class FxTreeTableCell<S,T> extends TreeTableCell<S,T> {

  private final TableColumnConfiguration<S,T> columnConfiguration;      // column configuration

  private final TableCellEditSupport<S, T> editSupport;                 // the editing logic

  /**
   * Creates a table cell.
   *
   * @param columnConfiguration the column configuration
   */
  public FxTreeTableCell(TableColumnConfiguration<S,T> columnConfiguration) {
    this.columnConfiguration = columnConfiguration;
    this.editSupport = new EditSupport();
    setEditable(columnConfiguration.isEditable());
  }

  /**
   * Gets the column configuration.
   *
   * @return the configuration
   */
  public TableColumnConfiguration<S, T> getColumnConfiguration() {
    return columnConfiguration;
  }

  @Override
  public void startEdit() {
    if (isEditable() && getTreeTableView().isEditable() && getTableColumn().isEditable() && editSupport.isCellEditable()) {
      super.startEdit();
      if (isEditing()) {
        editSupport.startEdit();
      }
    }
  }

  @Override
  public void commitEdit(T newValue) {
    boolean editing = isEditing();
    super.commitEdit(newValue);
    if (editing) {
      editSupport.committed();
    }
  }

  @Override
  public void cancelEdit() {
    if (isEditing()) {
      editSupport.canceling();
      super.cancelEdit();
      editSupport.canceled();
    }
    else {
      super.cancelEdit();
    }
  }


  @Override
  protected void updateItem(T item, boolean empty) {
    if (item == getItem()) {
      return;
    }
    super.updateItem(item, empty);

    if (item == null) {
      setText(null);
      setGraphic(null);
    }
    else if (item instanceof Node) {
      setText(null);
      setGraphic((Node) item);
    }
    else {
      getCellType().updateItem(this, item);
    }
  }

  /**
   * Gets the editor for this table cell.
   *
   * @param cellType the table cell type
   * @return the editor
   */
  protected FxComponent getEditor(TableCellType<T> cellType) {
    FxComponent editor = columnConfiguration.getEditor();
    if (editor == null) {
      editor = cellType.getEditor();
    }
    return editor;
  }

  /**
   * Gets the table cell type.
   *
   * @return the cell type, never null
   */
  protected TableCellType<T> getCellType() {
    return columnConfiguration.getEffectiveCellType();
  }

  /**
   * Gets the table position of the currently selected cell.
   *
   * @return the position, null if none selected
   */
  protected TreeTablePosition<S, ?> getPosition() {
    ObservableList<TreeTablePosition<S, ?>> cells = getTreeTableView().getSelectionModel().getSelectedCells();
    return cells.isEmpty() ? null : cells.get(0);
  }

  /**
   * Edits the currently selected cell.
   */
  protected void editCell() {
    TreeTablePosition<S, ?> pos = getPosition();
    if (pos != null) {
      Platform.runLater(() -> getTreeTableView().edit(pos.getRow(), pos.getTableColumn()));
    }
  }

  /**
   * Binds the editing logic to this cell.
   */
  private class EditSupport extends TableCellEditSupport<S,T> {

    EditSupport() {
      super(columnConfiguration);
    }

    @Override
    @SuppressWarnings("unchecked")
    TableEditContext<S> getEditContext() {
      return getTreeTableView() instanceof FxTreeTableView<?> view ? ((FxTreeTableView<S>) view).getEditContext() : null;
    }

    @Override
    S getRowObject() {
      TreeTableRow<S> row = getTableRow();
      return row == null ? null : row.getItem();
    }

    @Override
    T getItem() {
      return FxTreeTableCell.this.getItem();
    }

    @Override
    TableCellType<T> getCellType() {
      return FxTreeTableCell.this.getCellType();
    }

    @Override
    FxComponent getEditor() {
      return FxTreeTableCell.this.getEditor(getCellType());
    }

    @Override
    void showEditor(FxComponent editor) {
      editor.setTreeTableCell(FxTreeTableCell.this);
      setText(null);
      setGraphic((Node) editor);
    }

    @Override
    boolean isEditing() {
      return FxTreeTableCell.this.isEditing();
    }

    @Override
    boolean isViewEditing() {
      return getTreeTableView().getEditingCell() != null;
    }

    @Override
    void commitEdit(T value) {
      FxTreeTableCell.this.commitEdit(value);
    }

    @Override
    void cancelEdit() {
      FxTreeTableCell.this.cancelEdit();
    }

    @Override
    void refreshView() {
      getTreeTableView().refresh();
    }

    @Override
    void requestViewFocus() {
      getTreeTableView().requestFocus();
    }
  }

}
