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
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.table.edit.TableCellEdit;
import org.tentackle.fx.table.edit.TableCellRef;
import org.tentackle.fx.table.edit.TableEditContext;
import org.tentackle.fx.table.edit.TableEditHandler;
import org.tentackle.fx.table.edit.TableEditTrigger;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

/**
 * The editing logic shared by {@link FxTableCell} and {@link FxTreeTableCell}.<br>
 * Shows the editor, applies the entered value to the model and consults the
 * {@link TableEditHandler} of the table configuration.
 *
 * @param <S> type of the objects contained within the table's item list
 * @param <T> type of the content in all cells in this column
 * @author harald
 */
abstract class TableCellEditSupport<S, T> {

  /** key of the node property holding the support currently listening to the focus of an editor. */
  private static final Object FOCUS_OWNER_KEY = new Object();

  private final TableColumnConfiguration<S,T> columnConfiguration;    // column configuration

  private TableCellRef<S> cell;                     // the edited cell, null if the row is unknown
  private T oldValue;                               // the value when editing started
  private FxComponent editor;                       // the editor
  @SuppressWarnings("rawtypes")
  private ValueTranslator translator;               // translates between the model and the editor
  private Node focusNode;                           // != null if the editor's focus is monitored
  private ChangeListener<Boolean> focusListener;    // the focus listener
  private boolean escaped;                          // true if canceled with ESC
  private boolean committing;                       // true while the value is committed
  private boolean errorShown;                       // true if the editor shows a verification error
  private TableCellEdit<S, T> appliedEdit;          // the edit applied to the model, not yet reported to the handler

  /**
   * Creates the edit support.
   *
   * @param columnConfiguration the column configuration
   */
  TableCellEditSupport(TableColumnConfiguration<S,T> columnConfiguration) {
    this.columnConfiguration = columnConfiguration;
  }

  /**
   * Gets the edit context of the view.
   *
   * @return the context, null if the view provides none
   */
  abstract TableEditContext<S> getEditContext();

  /**
   * Gets the row object of the cell.
   *
   * @return the row object, null if unknown
   */
  abstract S getRowObject();

  /**
   * Gets the cell's item.
   *
   * @return the item
   */
  abstract T getItem();

  /**
   * Gets the table cell type.
   *
   * @return the cell type
   */
  abstract TableCellType<T> getCellType();

  /**
   * Gets the editor of the cell.
   *
   * @return the editor
   */
  abstract FxComponent getEditor();

  /**
   * Shows the editor in the cell.
   *
   * @param editor the editor
   */
  abstract void showEditor(FxComponent editor);

  /**
   * Returns whether the cell is in edit mode.
   *
   * @return true if editing
   */
  abstract boolean isEditing();

  /**
   * Returns whether any cell of the view is in edit mode.
   *
   * @return true if editing
   */
  abstract boolean isViewEditing();

  /**
   * Commits the edit of the cell.
   *
   * @param value the new value
   */
  abstract void commitEdit(T value);

  /**
   * Cancels the edit of the cell.
   */
  abstract void cancelEdit();

  /**
   * Refreshes the view.
   */
  abstract void refreshView();

  /**
   * Requests the focus for the view.
   */
  abstract void requestViewFocus();


  /**
   * Returns whether the edit handler allows editing the cell.
   *
   * @return true if editable
   */
  boolean isCellEditable() {
    TableEditContext<S> context = getEditContext();
    S row = getRowObject();
    if (context != null && row != null) {
      TableEditHandler<S> handler = context.getEditHandler();
      return handler == null || handler.isCellEditable(context, new TableCellRef<>(row, columnConfiguration));
    }
    return true;
  }

  /**
   * Shows the editor.<br>
   * Invoked when the cell switched to edit mode.
   */
  @SuppressWarnings("unchecked")
  void startEdit() {
    escaped = false;
    committing = false;
    appliedEdit = null;
    S row = getRowObject();
    cell = row == null ? null : new TableCellRef<>(row, columnConfiguration);
    oldValue = getItem();

    editor = getEditor();
    editor.setType(columnConfiguration.getType());
    translator = FxFactory.getInstance().createValueTranslator(
        editor.getType(), getCellType().getEditorType(), editor);
    Object viewObject = translator.toView(oldValue);
    editor.setViewObject(viewObject);
    editor.getDelegate().setLastViewObject(viewObject);

    showEditor(editor);

    Node node = editor.getDelegate().getNode();   // effective node getting the keyboard events

    EventHandler<KeyEvent> handler = event -> {
      switch (event.getCode()) {
        case ESCAPE:
          escaped = true;
          cancelEdit();
          refreshView();
          event.consume();
          break;

        case TAB:
        case ENTER:
          commit(event.isShiftDown() ? TableEditTrigger.PREVIOUS : TableEditTrigger.NEXT);
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

    TableEditContext<S> context = getEditContext();
    if (context != null && cell != null && getHandler(context).isCommitOnFocusLost(context, cell)) {
      addFocusListener(node);
    }

    // as in Swing, the focus request must be deferred... hoping 3 loops in the event queue are enough
    Platform.runLater(() -> Platform.runLater(() -> Platform.runLater(node::requestFocus)));
  }

  /**
   * Notifies the edit handler.<br>
   * Invoked after the cell's edit was committed.
   */
  void committed() {
    removeFocusListener();
    clearError();
    TableCellEdit<S, T> edit = appliedEdit;
    appliedEdit = null;
    Platform.runLater(() -> {
      TableEditContext<S> context = getEditContext();
      TableCellRef<S> next = null;
      if (edit != null && context != null) {
        next = getHandler(context).committed(context, edit);
        refresh(context);
      }
      if (next != null && edit.trigger() != TableEditTrigger.FOCUS_LOST) {
        context.edit(next);
      }
      else if (edit == null || edit.trigger() != TableEditTrigger.FOCUS_LOST) {
        requestViewFocus();
      }
    });
  }

  /**
   * Applies the value to the model if the edit is canceled because the editor lost the focus.<br>
   * Invoked before the cell's edit is canceled.
   */
  void canceling() {
    TableEditContext<S> context = getEditContext();
    if (!committing && !escaped && focusNode != null && context != null && cell != null) {
      try {
        TableCellEdit<S, T> edit = new TableCellEdit<>(cell, oldValue, getEditorValue(), TableEditTrigger.FOCUS_LOST);
        if (getHandler(context).verify(context, edit) == null) {
          applyToModel(edit.newValue());
          appliedEdit = edit;
        }
      }
      catch (RuntimeException ex) {
        // the input cannot be converted or is refused by the model: cancel
        appliedEdit = null;
      }
    }
  }

  /**
   * Notifies the edit handler.<br>
   * Invoked after the cell's edit was canceled.
   */
  void canceled() {
    if (committing) {
      return;
    }
    removeFocusListener();
    clearError();
    TableCellEdit<S, T> edit = appliedEdit;
    appliedEdit = null;
    TableCellRef<S> canceledCell = cell;
    TableEditContext<S> context = getEditContext();
    if (context != null && canceledCell != null) {
      Platform.runLater(() -> {
        TableEditHandler<S> handler = getHandler(context);
        if (edit != null) {
          handler.committed(context, edit);   // focus lost: the user already moved on
          refresh(context);
        }
        else {
          TableCellRef<S> next = handler.canceled(context, canceledCell);
          refresh(context);
          context.edit(next);
        }
      });
    }
  }


  /**
   * Commits the value of the editor.
   *
   * @param trigger the reason
   */
  private void commit(TableEditTrigger trigger) {
    T value = getEditorValue();
    TableEditContext<S> context = getEditContext();
    TableCellEdit<S, T> edit = null;
    if (context != null && cell != null) {
      edit = new TableCellEdit<>(cell, oldValue, value, trigger);
      String message = getHandler(context).verify(context, edit);
      if (message != null) {
        editor.setError(message);
        editor.showErrorPopup();
        errorShown = true;
        return;
      }
    }
    applyToModel(value);
    appliedEdit = edit;
    committing = true;
    try {
      commitEdit(value);
    }
    finally {
      committing = false;
    }
    refreshView();
  }

  @SuppressWarnings("unchecked")
  private T getEditorValue() {
    return (T) translator.toModel(editor.getViewObject());
  }

  private void applyToModel(T value) {
    FxTableBinding<S, T> binding = columnConfiguration.getBinding();
    if (cell != null) {
      // don't rely on the row the cell value factory was invoked for last
      binding.setBoundRootObject(cell.row());
    }
    binding.setModelValue(value);
  }

  private TableEditHandler<S> getHandler(TableEditContext<S> context) {
    TableEditHandler<S> handler = context.getEditHandler();
    return handler == null ? FxFactory.getInstance().simpleTableEditHandler() : handler;
  }

  /**
   * Refreshes the view, unless a cell is being edited.<br>
   * A refresh recreates the cells and would cancel the edit. The values are shown as soon as that edit ends.
   */
  private void refresh(TableEditContext<S> context) {
    if (!isViewEditing()) {
      context.refresh();
    }
  }

  private void clearError() {
    if (errorShown) {
      errorShown = false;
      editor.hideErrorPopup();
      editor.setError(null);
    }
  }

  private void addFocusListener(Node node) {
    // editors are shared between the cells: there must be only one listener at a time
    if (node.getProperties().get(FOCUS_OWNER_KEY) instanceof TableCellEditSupport<?,?> other) {
      other.removeFocusListener();
    }
    focusNode = node;
    focusListener = (_, _, focused) -> {
      if (!focused) {
        // deferred: the new focus owner is not known yet
        Platform.runLater(this::focusLost);
      }
    };
    node.focusedProperty().addListener(focusListener);
    node.getProperties().put(FOCUS_OWNER_KEY, this);
  }

  private void removeFocusListener() {
    if (focusNode != null) {
      focusNode.focusedProperty().removeListener(focusListener);
      if (focusNode.getProperties().get(FOCUS_OWNER_KEY) == this) {
        focusNode.getProperties().remove(FOCUS_OWNER_KEY);
      }
      focusNode = null;
      focusListener = null;
    }
  }

  private void focusLost() {
    if (focusNode != null && isEditing() && !isFocusWithinEditor()) {
      cancelEdit();   // applies the value to the model, see canceling()
    }
  }

  /**
   * Returns whether the focus is still within the editor.<br>
   * The effective node loses the focus if the window is deactivated or if the focus moves to another
   * part of a composite editor, such as the button of a date picker.
   */
  private boolean isFocusWithinEditor() {
    Scene scene = focusNode.getScene();
    Node owner = scene == null ? null : scene.getFocusOwner();
    while (owner != null) {
      if (owner == focusNode || owner == editor) {
        return true;
      }
      owner = owner.getParent();
    }
    return false;
  }

}
