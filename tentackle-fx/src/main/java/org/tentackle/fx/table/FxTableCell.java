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
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TablePosition;
import javafx.scene.input.KeyEvent;

/**
 * Extended table cell.<br>
 * Renders the item via the column's {@link TableCellType} and edits it via an {@link FxComponent}.
 * After committing with {@code ENTER} or {@code TAB}, the next cell to edit is selected according to
 * the {@link TableConfiguration.EDITMODE}.
 *
 * @param <S> type of the objects contained within the table's item list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public class FxTableCell<S,T> extends TableCell<S,T> {

  private final TableColumnConfiguration<S,T> columnConfiguration;      // column configuration

  private boolean toNext;                           // true if editor was committed with ENTER or TAB
  private boolean toPrevious;                       // true if editor was committed with Shift ENTER/TAB

  /**
   * Creates a table cell.
   *
   * @param columnConfiguration the column configuration
   */
  public FxTableCell(TableColumnConfiguration<S,T> columnConfiguration) {
    this.columnConfiguration = columnConfiguration;
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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void startEdit() {
    if (isEditable() && getTableView().isEditable() && getTableColumn().isEditable()) {
      super.startEdit();
      if (isEditing()) {
        toNext = false;
        toPrevious = false;
        TableCellType<T> cellType = getCellType();
        FxComponent editor = getEditor(cellType);
        editor.setTableCell(this);
        editor.setType(columnConfiguration.getType());
        ValueTranslator translator = FxFactory.getInstance().createValueTranslator(
                editor.getType(), cellType.getEditorType(), editor);
        Object viewObject = translator.toView(getItem());
        editor.setViewObject(viewObject);
        editor.getDelegate().setLastViewObject(viewObject);

        setText(null);
        setGraphic((Node) editor);

        Node node = editor.getDelegate().getNode();   // effective node getting the keyboard events

        EventHandler<KeyEvent> handler = event -> {
          switch (event.getCode()) {
            case ESCAPE:
              cancelEdit();
              getTableView().refresh();
              event.consume();
              break;

            case TAB:
            case ENTER:
              if (event.isShiftDown()) {
                toPrevious = true;
              }
              else {
                toNext = true;
              }
              T value = (T) translator.toModel(editor.getViewObject());
              columnConfiguration.getBinding().setModelValue(value);
              commitEdit(value);
              getTableView().refresh();
              event.consume();
              break;
          }
        };

        node.setOnKeyPressed(handler);

        if (editor != node) {
          // for whatever reason, ESC is sometimes not forwarded to the inner component.
          // In such a case, catch the keystroke on the outer one (for ex. DatePicker).
          // @todo: check if still necessary in newer releases than Java 9
          ((Node) editor).setOnKeyPressed(handler);
        }

        // as in Swing, the focus request must be deferred... hoping 3 loops in the event queue are enough
        Platform.runLater(() -> Platform.runLater(() -> Platform.runLater(node::requestFocus)));
      }
    }
  }

  @Override
  public void commitEdit(T newValue) {
    super.commitEdit(newValue);
    Platform.runLater(() -> {
      switch(columnConfiguration.getTableConfiguration().getEditMode()) {
        case ROW:
          if (toNext) {
            TablePosition<S, T> pos;
            do {
              getTableView().getSelectionModel().selectNext();
              pos = getPosition();
            }
            while (pos != null && !pos.getTableColumn().isEditable());
            editCell();
          }
          else if (toPrevious) {
            TablePosition<S, T> pos;
            do {
              getTableView().getSelectionModel().selectPrevious();
              pos = getPosition();
            }
            while (pos != null && !pos.getTableColumn().isEditable());
            editCell();
          }
          break;

        case COLUMN:
          if (toNext) {
            getTableView().getSelectionModel().selectBelowCell();
            editCell();
          }
          else if (toPrevious) {
            getTableView().getSelectionModel().selectAboveCell();
            editCell();
          }
          break;
      }
      getTableView().requestFocus();
    });
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
   * Gets the table cell type.
   *
   * @return the cell type, never null
   */
  protected TableCellType<T> getCellType() {
    return columnConfiguration.getEffectiveCellType();
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
   * Gets the table position of the currently selected cell.
   *
   * @return the position, null if none selected
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected TablePosition<S, T> getPosition() {
    ObservableList<TablePosition> cells = getTableView().getSelectionModel().getSelectedCells();
    return cells.isEmpty() ? null : cells.get(0);
  }

  /**
   * Edits the currently selected cell.
   */
  protected void editCell() {
    TablePosition<S, T> pos = getPosition();
    if (pos != null) {
      Platform.runLater(() -> getTableView().edit(pos.getRow(), pos.getTableColumn()));
    }
  }

}
