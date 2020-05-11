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
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.ModelToViewListener;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.ViewToModelListener;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.delegate.FxTableViewDelegate;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TotalsTableView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended TableView.
 *
 * @author harald
 * @param <S> the row type
 */
public class FxTableView<S> extends TableView<S> implements FxComponent {

  /**
   * The configuration.<br>
   * Optional but recommended.
   */
  private TableConfiguration<S> configuration;

  /**
   * The optional totals table.
   */
  private TotalsTableView<S> totalsTableView;

  /**
   * Currently displayed info notes.
   */
  private List<Note> infoNotes;


  /**
   * Creates a default TableView control with no content.
   * <p>
   * Refer to the {@link TableView} class documentation for details on the default state of other properties.
   */
  public FxTableView() {
    super();
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
   * Gets the totals table.
   *
   * @return null if no totals (default)
   */
  public TotalsTableView<S> getTotalsTableView() {
    return totalsTableView;
  }

  /**
   * Sets the totals table.
   *
   * @param totalsTableView null to clear
   */
  public void setTotalsTableView(TotalsTableView<S> totalsTableView) {
    this.totalsTableView = totalsTableView;
  }


  /**
   * Saves the column sizes, visability, view size and sorting to the preferences.
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
   * Loads the column sizes, visability, view size and sorting from the preferences.
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
    for (TableColumn<S,?> col: getColumns()) {
      col.setSortable(sortable);
    }
  }


  /**
   * Configures the table to copy a cell via Crtl-C to the clipboard.
   */
  public void enableCopyToClipboard() {
    setOnKeyPressed(e -> {
      if (e.isControlDown() && e.getCode() == KeyCode.C) {
        String text = copyToClipboard();
        if (text != null) {
          // display copied text for as long as the control button is held down (see setOnKeyReleased below)
          Note infoNote = new Note(Note.Position.CENTER, Note.Type.INFO);
          infoNote.setText(text);
          infoNote.show(this);
          if (infoNotes == null) {
            infoNotes = new ArrayList<>();
          }
          infoNotes.add(infoNote);
        }
      }
    });
    setOnKeyReleased(e -> {
      if (e.getCode() == KeyCode.CONTROL) {
        if (infoNotes != null) {
          // hide all info notes with the release of the control button
          for (Note infoNote : infoNotes) {
            infoNote.hide();
          }
          infoNotes = null;
        }
      }
    });
  }


  /**
   * Copies the selected cells to the clipboard.<br>
   * Multiple cells are separated by tabs.
   *
   * @return the copied text, null if nothing copied
   */
  @SuppressWarnings("unchecked")
  public String copyToClipboard() {
    StringBuilder buf = new StringBuilder();
    for (TablePosition<S,?> pos: getSelectionModel().getSelectedCells()) {
      Object item = getColumns().get(pos.getColumn()).getCellData(pos.getRow());
      if (item != null) {
        String text = null;
        // try to lookup the rendered text
        for (Node c: lookupAll(".table-cell")) {    // all visible cells only
          TableCell<?, ?> tc=(TableCell<?, ?>) c;
          if (tc.getItem() == item) {   // == is ok here, must be the same instance!
            text = tc.getText();
            break;
          }
        }
        if (buf.length() > 0) {
          buf.append('\t');
        }
        if (text == null) {
          text = item.toString();   // fallback if no rendered text found
        }
        buf.append(text);
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

  private /**/FxTableViewDelegate/**/ delegate;          // @wurblet < Inject ${classname}Delegate

  /**
   * Creates the delegate.
   *
   * @return the delegate
   */
  protected /**/FxTableViewDelegate/**/ createDelegate() {  // @wurblet < Inject ${classname}Delegate
    return new /**/FxTableViewDelegate/**/(this);  // @wurblet < Inject ${classname}Delegate
  }

  @Override
  public /**/FxTableViewDelegate/**/ getDelegate() {  // @wurblet < Inject ${classname}Delegate
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
  public void setDelegate(/**/FxTableViewDelegate/**/ delegate) {  // @wurblet < Inject ${classname}Delegate
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
  public void setContainerChangableIgnored(boolean containerChangeableIgnored) {
    getDelegate().setContainerChangableIgnored(containerChangeableIgnored);
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

  //</editor-fold>//GEN-END:component

}
