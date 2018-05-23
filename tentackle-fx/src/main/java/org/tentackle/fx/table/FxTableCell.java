/*
 * Tentackle - http://www.tentackle.org.
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

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn.CellEditEvent;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.log.Logger;

/**
 * Extended table cell.
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
public class FxTableCell<S,T> extends TableCell<S,T> {

  private static final Logger LOGGER = Logger.get(FxTableCell.class);

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
    return columnConfiguration.getTableConfiguration().getCellType(columnConfiguration.getType());
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
        editor.setViewObject(translator.toView(getItem()));

        getTableColumn().setOnEditCancel((CellEditEvent<S,T> t) -> {
          getTableView().refresh();
        });

        setText(null);
        setGraphic((Node) editor);

        Node node = editor.getDelegate().getNode();   // effective node getting the keyboard events
        node.setOnKeyPressed(t -> {
          switch (t.getCode()) {
            case ESCAPE:
              cancelEdit();
              t.consume();
              break;

            case TAB:
            case ENTER:
              T value = (T) translator.toModel(editor.getViewObject());
              columnConfiguration.getBinding().setModelValue(value);
              commitEdit(value);
              t.consume();
              break;
          }
        });

        if (editor != node) {
          // for whatever reason, ESC is sometimes not forwarded to the inner component.
          // In such a case, catch the keystroke on the outer one (for ex. DatePicker).
          // @todo: check if still necessary in newer releases then Java 9
          ((Node) editor).setOnKeyPressed(t -> {
            switch (t.getCode()) {
              case ESCAPE:
                cancelEdit();
                t.consume();
                break;
            }
          });
        }

        Platform.runLater(() -> node.requestFocus());
      }
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
   * Sets a flag to focus next cell after commit.
   */
  public void focusNextOnCommit() {
    toNext = true;
  }

  /**
   * Sets a flag to focus previous cell after commit.
   */
  public void focusPreviousOnCommit() {
    toPrevious = true;
  }

  @Override
  public void commitEdit(T newValue) {
    super.commitEdit(newValue);
    Platform.runLater(() -> {
      getTableView().requestFocus();
//      if (toNext) {
//        getTableView().getFocusModel().focusNext();
//      }
//      else if (toPrevious) {
//        getTableView().getFocusModel().focusPrevious();
//      }
    });
  }


}
