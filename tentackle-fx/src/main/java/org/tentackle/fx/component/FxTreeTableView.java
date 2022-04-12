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

package org.tentackle.fx.component;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ModelToViewListener;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.ViewToModelListener;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.delegate.FxTreeTableViewDelegate;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.TableConfiguration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended TreeTableView.
 *
 * @author harald
 * @param <S> the type
 */
public class FxTreeTableView<S> extends TreeTableView<S> implements FxComponent {

  /**
   * The configuration.<br>
   * Optional but recommended.
   */
  private TableConfiguration<S> configuration;

  /**
   * Currently displayed info notes.
   */
  private List<Note> infoNotes;

  /**
   * Copy to clipboard feature toggle.
   */
  private boolean copyToClipboardEnabled;

  /**
   * Creates an empty TreeTableView.
   * <p>
   * Refer to the {@link TreeTableView} class documentation for details on the default state of other properties.
   */
  public FxTreeTableView() {
    addEventFilter(KeyEvent.ANY, this::filterDangerousKeys);
    addEventHandler(KeyEvent.ANY, this::handleKeyEvent);
  }

  /**
   * Filters dangerous keys.<br>
   * Since the TreeTableViewBehaviour is private, we cannot modify the input map and filter the keys before
   * they reach the key event handler.
   *
   * @param event the key event
   */
  protected void filterDangerousKeys(KeyEvent event) {
    if (event.getCode() == KeyCode.MULTIPLY) {
      // The handler expands all nodes recursively.
      // This is dangerous and may cause an out-of-memory in case of node loops!
      event.consume();
      // Use safe impl instead
      if (event.getEventType() == KeyEvent.KEY_PRESSED) {
        if (event.isShiftDown()) {
          collapseAll();
        }
        else {
          expandAll();
        }
      }
    }
  }

  /**
   * Expands all nodes.
   */
  public void expandAll() {
    FxUtilities.getInstance().expandAll(getRoot());
  }

  /**
   * Collapses all nodes.
   */
  public void collapseAll() {
    if (isShowRoot()) {
      scrollTo(0);
      FxUtilities.getInstance().collapseAll(getRoot());
    }
    else {
      ObservableList<TreeItem<S>> children = getRoot().getChildren();
      if (children != null && !children.isEmpty()) {
        scrollTo(0);
        FxUtilities.getInstance().collapseAll(children);
      }
    }
  }

  /**
   * Gets the table configuration.
   *
   * @return the configuration, null if not configured
   */
  public TableConfiguration<S> getConfiguration() {
    return configuration;
  }

  /**
   * Sets the table configuration.
   *
   * @param configuration the configuration
   */
  public void setConfiguration(TableConfiguration<S> configuration) {
    this.configuration = configuration;
  }

  /**
   * Configures or re-configures the table.<br>
   * Requires a valid table configuration.
   */
  public void configure() {
    if (configuration == null) {
      throw new FxRuntimeException("missing table configuration");
    }
    configuration.configure(this);
  }


  /**
   * Gets the displayed items.
   *
   * @return the items
   */
  public List<S> getItems() {
    List<S> items = new ArrayList<>();
    if (isShowRoot()) {
      getItems(items, getRoot());
    }
    else {
      for (TreeItem<S> treeItem : getRoot().getChildren()) {
        getItems(items, treeItem);
      }
    }
    return items;
  }

  private void getItems(List<S> items, TreeItem<S> treeItem) {
    items.add(treeItem.getValue());
    if (treeItem.isExpanded()) {
      for (TreeItem<S> child : treeItem.getChildren()) {
        getItems(items, child);
      }
    }
  }

  /**
   * Gets all tree items.
   *
   * @return all tree items, whether shown or not
   */
  public List<TreeItem<S>> getTreeItems() {
    List<TreeItem<S>> items = new ArrayList<>();
    if (isShowRoot()) {
      getTreeItems(items, getRoot());
    }
    else {
      for (TreeItem<S> treeItem : getRoot().getChildren()) {
        getTreeItems(items, treeItem);
      }
    }
    return items;
  }

  private void getTreeItems(List<TreeItem<S>> items, TreeItem<S> treeItem) {
    items.add(treeItem);
    for (TreeItem<S> child : treeItem.getChildren()) {
      getTreeItems(items, child);
    }
  }

  /**
   * Gets the selected items.
   *
   * @return the selected items
   */
  public List<S> getSelectedItems() {
    List<S> items = new ArrayList<>();
    for (TreeItem<S> treeItem : getSelectionModel().getSelectedItems()) {
      items.add(treeItem.getValue());
    }
    return items;
  }


  /**
   * Scrolls the view in such a way that the given row is positioned in the center of the visible rows.
   *
   * @param row the model row index
   */
  public void scrollToCentered(int row) {
    scrollTo(FxUtilities.getInstance().computeScrollToCentered(this, row, getItems().size()));
  }


  /**
   * Saves the column sizes, visibility, view size and sorting to the preferences.
   *
   * @param suffix the configuration suffix, null if none
   * @param system true if save to system prefs, else user prefs
   */
  public void savePreferences(String suffix, boolean system) {
    if (configuration != null) {
      configuration.savePreferences(this, suffix, system);
    }
  }

  /**
   * Loads the column sizes, visibility, view size and sorting from the preferences.
   *
   * @param suffix the configuration suffix, null if none
   * @param system true if load from system prefs only, else user prefs first
   */
  public void loadPreferences(String suffix, boolean system) {
    if (configuration != null) {
      configuration.loadPreferences(this, suffix, system);
    }
  }


  /**
   * Sets the sortable property of all columns.
   *
   * @param sortable true if sortable
   */
  public void setSortable(boolean sortable) {
    for (TreeTableColumn<S,?> col: getColumns()) {
      col.setSortable(sortable);
    }
  }


  /**
   * Sets the reorderable property of all columns.
   *
   * @param reorderable true if reorderable
   */
  public void setReorderable(boolean reorderable) {
    for (TreeTableColumn<S,?> col: getColumns()) {
      col.setReorderable(reorderable);
    }
  }


  /**
   * Configures the table to copy a cell via Ctrl-C to the clipboard.
   *
   * @param copyToClipboardEnabled true to enable
   */
  public void setCopyToClipboardEnabled(boolean copyToClipboardEnabled) {
    this.copyToClipboardEnabled = copyToClipboardEnabled;
  }

  /**
   * Returns whether the copy to clipboard feature is enabled.
   *
   * @return true if enabled
   */
  public boolean isCopyToClipboardEnabled() {
    return copyToClipboardEnabled;
  }

  /**
   * Handles all key events.
   *
   * @param event the key event
   */
  protected void handleKeyEvent(KeyEvent event) {
    if (isCopyToClipboardEnabled()) {
      if (event.getEventType() == KeyEvent.KEY_PRESSED && event.isControlDown() && event.getCode() == KeyCode.C) {
        String text = copyToClipboard();
        if (text != null) {
          // display copied text for as long as the control button is held down (see setOnKeyReleased below)
          Note infoNote = Fx.create(Note.class);
          infoNote.setText(text);
          infoNote.show(this);
          if (infoNotes == null) {
            infoNotes = new ArrayList<>();
          }
          infoNotes.add(infoNote);
        }
      }
      else if (event.getEventType() == KeyEvent.KEY_RELEASED && event.getCode() == KeyCode.CONTROL) {
        if (infoNotes != null) {
          // hide all info notes with the release of the control button
          for (Note infoNote : infoNotes) {
            infoNote.hide();
          }
          infoNotes = null;
        }
      }
    }
  }


  /**
   * Copies the selected cells to the clipboard.
   *
   * @return the copied text, null if nothing copied
   */
  public String copyToClipboard() {
    StringBuilder buf = new StringBuilder();
    int lastRow = -1;
    boolean cellInRowCopied = false;
    String separator = FxUtilities.getInstance().getCsvSeparator();
    for (TreeTablePosition<S,?> pos: getSelectionModel().getSelectedCells()) {
      int row = pos.getRow();
      if (row >= 0) {
        int colMin = pos.getColumn();
        int colMax;
        if (colMin >= 0) {
          colMax = colMin + 1;
        }
        else {
          colMin = 0;
          colMax = getColumns().size();
        }
        if (buf.length() > 0 && lastRow >= 0 && lastRow != row) {
          buf.append('\n');
          cellInRowCopied = false;
        }
        for (int col = colMin; col < colMax; col++) {
          TreeTableColumn<S, ?> treeTableColumn = getVisibleLeafColumn(col);
          Object item = treeTableColumn.getCellData(row);
          if (item != null) {
            String text = null;
            // try to look up the rendered text
            for (Node c : lookupAll(".tree-table-cell")) {    // all visible cells only
              TreeTableCell<?, ?> tc = (TreeTableCell<?, ?>) c;
              if (tc.getItem() == item) {   // == is ok here, must be the same instance!
                text = tc.getText();
                break;
              }
            }
            if (text == null) {
              text = item.toString();   // fallback if no rendered text found
            }
            if (cellInRowCopied) {
              buf.append(separator);
            }
            buf.append(text);
            cellInRowCopied = true;
          }
        }
        lastRow = row;
      }
    }
    if (buf.length() > 0) {
      ClipboardContent cbc = new ClipboardContent();
      String text = buf.toString();
      cbc.putString(text);
      Clipboard.getSystemClipboard().setContent(cbc);
      return text;
    }
    return null;
  }


  // @wurblet delegate Include $currentDir/fxcomponent.include

  //<editor-fold defaultstate="collapsed" desc="code 'delegate' generated by wurblet Include">//GEN-BEGIN:delegate

  private /**/FxTreeTableViewDelegate/**/ delegate;          // @wurblet < Inject ${classname}Delegate

  /**
   * Creates the delegate.
   *
   * @return the delegate
   */
  protected /**/FxTreeTableViewDelegate/**/ createDelegate() {  // @wurblet < Inject ${classname}Delegate
    return new /**/FxTreeTableViewDelegate/**/(this);  // @wurblet < Inject ${classname}Delegate
  }

  @Override
  public /**/FxTreeTableViewDelegate/**/ getDelegate() {  // @wurblet < Inject ${classname}Delegate
    if (delegate == null) {
      setDelegate(createDelegate());
    }
    return delegate;
  }

  /**
   * Sets the delegate.<br>
   * Useful for application specific needs.
   *
   * @param delegate the delegate
   */
  public void setDelegate(/**/FxTreeTableViewDelegate/**/ delegate) {  // @wurblet < Inject ${classname}Delegate
    this.delegate = delegate;
  }

  // @wurblet component Include $currentDir/component.include

  //</editor-fold>//GEN-END:delegate

  //<editor-fold defaultstate="collapsed" desc="code 'component' generated by wurblet Include/Include">//GEN-BEGIN:component

  @Override
  public FxContainer getParentContainer() {
    return getDelegate().getParentContainer();
  }

  @Override
  public void setValueTranslator(ValueTranslator<?,?> valueTranslator) {
    getDelegate().setValueTranslator(valueTranslator);
  }

  @Override
  public ValueTranslator<?,?> getValueTranslator() {
    return getDelegate().getValueTranslator();
  }

  @Override
  public void invalidateSavedView() {
    getDelegate().invalidateSavedView();
  }

  @Override
  public boolean isSavedViewObjectValid() {
    return getDelegate().isSavedViewObjectValid();
  }

  @Override
  public <V> V getViewValue() {
    return getDelegate().getViewValue();
  }

  @Override
  public void setViewValue(Object value) {
    getDelegate().setViewValue(value);
  }

  @Override
  public void setType(Class<?> type) {
    getDelegate().setType(type);
  }

  @Override
  public Class<?> getType() {
    return getDelegate().getType();
  }

  @Override
  public void setGenericType(Type type) {
    getDelegate().setGenericType(type);
  }

  @Override
  public Type getGenericType() {
    return getDelegate().getGenericType();
  }

  @Override
  public void updateView() {
    getDelegate().updateView();
  }

  @Override
  public void updateModel() {
    getDelegate().updateModel();
  }

  @Override
  public void addModelToViewListener(ModelToViewListener listener) {
    getDelegate().addModelToViewListener(listener);
  }

  @Override
  public void removeModelToViewListener(ModelToViewListener listener) {
    getDelegate().removeModelToViewListener(listener);
  }

  @Override
  public void addViewToModelListener(ViewToModelListener listener) {
    getDelegate().addViewToModelListener(listener);
  }

  @Override
  public void removeViewToModelListener(ViewToModelListener listener) {
    getDelegate().removeViewToModelListener(listener);
  }

  @Override
  public void setMandatory(boolean mandatory) {
    getDelegate().setMandatory(mandatory);
  }

  @Override
  public boolean isMandatory() {
    return getDelegate().isMandatory();
  }

  @Override
  public BooleanProperty mandatoryProperty() {
    return getDelegate().mandatoryProperty();
  }

  @Override
  public void setBindingPath(String bindingPath) {
    getDelegate().setBindingPath(bindingPath);
  }

  @Override
  public String getBindingPath() {
    return getDelegate().getBindingPath();
  }

  @Override
  public void setComponentPath(String componentPath) {
    getDelegate().setComponentPath(componentPath);
  }

  @Override
  public String getComponentPath() {
    return getDelegate().getComponentPath();
  }

  @Override
  public void setBinding(FxComponentBinding binding) {
    getDelegate().setBinding(binding);
  }

  @Override
  public FxComponentBinding getBinding() {
    return getDelegate().getBinding();
  }

  @Override
  public void setChangeable(boolean changeable) {
    getDelegate().setChangeable(changeable);
  }

  @Override
  public boolean isChangeable() {
    return getDelegate().isChangeable();
  }

  @Override
  public ReadOnlyBooleanProperty changeableProperty() {
    return getDelegate().changeableProperty();
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    getDelegate().setContainerChangeable(containerChangeable);
  }

  @Override
  public void setContainerChangeableIgnored(boolean containerChangeableIgnored) {
    getDelegate().setContainerChangeableIgnored(containerChangeableIgnored);
  }

  @Override
  public boolean isContainerChangeableIgnored() {
    return getDelegate().isContainerChangeableIgnored();
  }

  @Override
  public void setViewModified(boolean viewModified) {
    getDelegate().setViewModified(viewModified);
  }

  @Override
  public boolean isViewModified() {
    return getDelegate().isViewModified();
  }

  @Override
  public BooleanProperty viewModifiedProperty() {
    return getDelegate().viewModifiedProperty();
  }

  @Override
  public void triggerViewModified() {
    getDelegate().triggerViewModified();
  }

  @Override
  public void saveView() {
    getDelegate().saveView();
  }

  @Override
  public Object getSavedViewObject() {
    return getDelegate().getSavedViewObject();
  }

  @Override
  public Object getViewObject() {
    return getDelegate().getViewObject();
  }

  @Override
  public void setViewObject(Object viewObject) {
    getDelegate().setViewObject(viewObject);
  }

  @Override
  public void setBindable(boolean bindable) {
    getDelegate().setBindable(bindable);
  }

  @Override
  public boolean isBindable() {
    return getDelegate().isBindable();
  }

  @Override
  public void setHelpUrl(String helpUrl) {
    getDelegate().setHelpUrl(helpUrl);
  }

  @Override
  public String getHelpUrl() {
    return getDelegate().getHelpUrl();
  }

  @Override
  public void showHelp() {
    getDelegate().showHelp();
  }

  @Override
  public String toGenericString() {
    return getDelegate().toGenericString();
  }

  @Override
  public void setError(String error) {
    getDelegate().setError(error);
  }

  @Override
  public String getError() {
    return getDelegate().getError();
  }

  @Override
  public void setErrorTemporary(boolean errorTemporary) {
    getDelegate().setErrorTemporary(errorTemporary);
  }

  @Override
  public boolean isErrorTemporary() {
    return getDelegate().isErrorTemporary();
  }

  @Override
  public void showErrorPopup() {
    getDelegate().showErrorPopup();
  }

  @Override
  public void hideErrorPopup() {
    getDelegate().hideErrorPopup();
  }

  @Override
  public void setInfo(String info) {
    getDelegate().setInfo(info);
  }

  @Override
  public String getInfo() {
    return getDelegate().getInfo();
  }

  @Override
  public void showInfoPopup() {
    getDelegate().showInfoPopup();
  }

  @Override
  public void hideInfoPopup() {
    getDelegate().hideInfoPopup();
  }

  @Override
  public boolean isModelUpdated() {
    return getDelegate().isModelUpdated();
  }

  @Override
  public void setTableCell(FxTableCell<?,?> tableCell) {
    getDelegate().setTableCell(tableCell);
  }

  @Override
  public FxTableCell<?,?> getTableCell() {
    return getDelegate().getTableCell();
  }

  @Override
  public void setTreeTableCell(FxTreeTableCell<?,?> treeTableCell) {
    getDelegate().setTreeTableCell(treeTableCell);
  }

  @Override
  public FxTreeTableCell<?,?> getTreeTableCell() {
    return getDelegate().getTreeTableCell();
  }

  @Override
  public boolean isListenerSuppressedIfModelUnchanged() {
    return getDelegate().isListenerSuppressedIfModelUnchanged();
  }

  @Override
  public void setListenerSuppressedIfModelUnchanged(boolean listenerSuppressedIfModelUnchanged) {
    getDelegate().setListenerSuppressedIfModelUnchanged(listenerSuppressedIfModelUnchanged);
  }

  @Override
  public boolean isListenerSuppressedIfViewUnchanged() {
    return getDelegate().isListenerSuppressedIfViewUnchanged();
  }

  @Override
  public void setListenerSuppressedIfViewUnchanged(boolean listenerSuppressedIfViewUnchanged) {
    getDelegate().setListenerSuppressedIfViewUnchanged(listenerSuppressedIfViewUnchanged);
  }

  //</editor-fold>//GEN-END:component

}
