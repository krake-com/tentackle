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

import org.tentackle.common.StripMode;
import org.tentackle.fx.CaseConversion;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ModelToViewListener;
import org.tentackle.fx.ShortLongTextCellFactory;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.ViewToModelListener;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.delegate.FxComboBoxDelegate;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.misc.ShortLongText;
import org.tentackle.misc.SubString;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

/**
 * Extended {@link ComboBox}.
 *
 * @author harald
 * @param <T> the type
 */
public class FxComboBox<T> extends ComboBox<T> implements FxTextComponent {

  private Boolean deselectAllowed;
  private boolean shortLongTextEnabled = true;


  /**
   * Returns whether deselect is allowed via the delete or backspace key.<br>
   * If null, it is determined according to type and/or mandatory property.
   * If the type is a primitive or the component is mandatory, deselect is not allowed.
   * Otherwise, it is allowed by default.
   *
   * @return true if allowed, null if determined from type and/or mandatory property
   */
  public Boolean isDeselectAllowed() {
    return deselectAllowed;
  }

  /**
   * Sets whether deselect is allowed via the delete or backspace key.
   *
   * @param deselectAllowed true if allowed, null if determined from type and/or mandatory property
   */
  public void setDeselectAllowed(Boolean deselectAllowed) {
    this.deselectAllowed = deselectAllowed;
  }


  /**
   * Returns whether the two-column list view is enabled for items implementing {@link org.tentackle.misc.ShortLongText}.
   *
   * @return true if enabled (default)
   */
  public boolean isShortLongTextEnabled() {
    return shortLongTextEnabled;
  }

  /**
   * Sets whether the listview popup should display items implementing {@link org.tentackle.misc.ShortLongText}
   * as two columns.<br>
   * This feature is enabled by default.
   *
   * @param shortLongTextEnabled true to enable, false to disable
   */
  public void setShortLongTextEnabled(boolean shortLongTextEnabled) {
    this.shortLongTextEnabled = shortLongTextEnabled;
  }


  /**
   * Filters keyboard events.<br>
   * {@code Shift-BACKSPACE} clears the editor of an editable combo box.
   * {@code DELETE} or {@code BACKSPACE} deselect the value of a non-editable combo box, if deselect is allowed.
   *
   * @param event the key event
   */
  public void filterKeyPressed(KeyEvent event) {
    if (!isDisabled()) {
      if (isEditable()) {
        if (event.getCode() == KeyCode.BACK_SPACE && event.isShiftDown() &&
            !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
          // shift BACKSPACE -> clear editor contents
          event.consume();
          getEditor().clear();
        }
      }
      else {
        // deselect by DELETE- or BACKSPACE-key (with or without shift) in non-editable combo boxes
        if (getDelegate().isDeselectAllowed() &&
            !event.isAltDown() && !event.isControlDown() && !event.isMetaDown() && !event.isShortcutDown() &&
            (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)) {
          event.consume();
          getSelectionModel().clearSelection();
        }
      }
    }
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new ComboBoxListViewSkin<>(this) {
      @Override
      public Node getPopupContent() {
        if (isShortLongTextEnabled() && getCellFactory() == null && isTypeOfShortLongText()) {
          setCellFactory(createShortLongTextCellFactory());
        }
        return super.getPopupContent();
      }
    };
  }

  /**
   * Creates a {@link ShortLongTextCellFactory}.
   *
   * @return the cell factory
   */
  protected ShortLongTextCellFactory<T> createShortLongTextCellFactory() {
    return new ShortLongTextCellFactory<>();
  }

  /**
   * Returns whether the combobox type implements {@link ShortLongText}.
   *
   * @return true if implements {@link ShortLongText}
   */
  protected boolean isTypeOfShortLongText() {
    boolean shortLong = false;
    List<T> items = getItems();
    if (items != null && !items.isEmpty()) {
      // check item
      shortLong = items.get(0) instanceof ShortLongText;
    }
    else {
      Class<?> clazz = getType();
      if (clazz != null) {
        // no items, use the binding type
        shortLong = ShortLongText.class.isAssignableFrom(clazz);
      }
    }
    return shortLong;
  }


  // @wurblet delegate Include --translate $componentIncludeDir/fxtextcomponent.include

  //<editor-fold defaultstate="collapsed" desc="code 'delegate' generated by wurblet Include">//GEN-BEGIN:delegate

  private FxComboBoxDelegate delegate;

  /**
   * Creates a FxComboBox.
   */
  public FxComboBox() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates the delegate.
   *
   * @return the delegate
   */
  protected FxComboBoxDelegate createDelegate() {
    return new FxComboBoxDelegate(this);
  }

  @Override
  public FxComboBoxDelegate getDelegate() {
    if (delegate == null) {
      setDelegate(createDelegate());
    }
    return delegate;
  }

  /**
   * Sets the delegate.<br>
   * Useful for application-specific needs.
   *
   * @param delegate the delegate
   */
  public void setDelegate(FxComboBoxDelegate delegate) {
    this.delegate = delegate;
  }

  // @wurblet component Include $componentIncludeDir/component.include

  // @wurblet textcomponent Include $componentIncludeDir/textcomponent.include

  //</editor-fold>//GEN-END:delegate

  //<editor-fold defaultstate="collapsed" desc="code 'textcomponent' generated by wurblet Include/Include">//GEN-BEGIN:textcomponent

  @Override
  public void setColumns(int columns) {
    getDelegate().setColumns(columns);
  }

  @Override
  public int getColumns() {
    return getDelegate().getColumns();
  }

  @Override
  public void setMaxColumns(int maxColumns) {
    getDelegate().setMaxColumns(maxColumns);
  }

  @Override
  public int getMaxColumns() {
    return getDelegate().getMaxColumns();
  }

  @Override
  public void setAutoSelect(boolean autoSelect) {
    getDelegate().setAutoSelect(autoSelect);
  }

  @Override
  public boolean isAutoSelect() {
    return getDelegate().isAutoSelect();
  }

  @Override
  public void setPattern(String pattern) {
    getDelegate().setPattern(pattern);
  }

  @Override
  public String getPattern() {
    return getDelegate().getPattern();
  }

  @Override
  public boolean isLenient() {
    return getDelegate().isLenient();
  }

  @Override
  public void setLenient(boolean lenient) {
    getDelegate().setLenient(lenient);
  }

  @Override
  public void setScale(int scale) {
    getDelegate().setScale(scale);
  }

  @Override
  public int getScale() {
    return getDelegate().getScale();
  }

  @Override
  public void setUnsigned(boolean unsigned) {
    getDelegate().setUnsigned(unsigned);
  }

  @Override
  public boolean isUnsigned() {
    return getDelegate().isUnsigned();
  }

  @Override
  public void setUTC(boolean utc) {
    getDelegate().setUTC(utc);
  }

  @Override
  public boolean isUTC() {
    return getDelegate().isUTC();
  }

  @Override
  public void setCaseConversion(CaseConversion caseConversion) {
    getDelegate().setCaseConversion(caseConversion);
  }

  @Override
  public CaseConversion getCaseConversion() {
    return getDelegate().getCaseConversion();
  }

  @Override
  public void setFiller(char filler) {
    getDelegate().setFiller(filler);
  }

  @Override
  public char getFiller() {
    return getDelegate().getFiller();
  }

  @Override
  public void setStripMode(StripMode stripMode) {
    getDelegate().setStripMode(stripMode);
  }

  @Override
  public StripMode getStripMode() {
    return getDelegate().getStripMode();
  }

  @Override
  public void setTextAlignment(Pos textAlignment) {
    getDelegate().setTextAlignment(textAlignment);
  }

  @Override
  public Pos getTextAlignment() {
    return getDelegate().getTextAlignment();
  }

  @Override
  public void setValidChars(String validChars) {
    getDelegate().setValidChars(validChars);
  }

  @Override
  public String getValidChars() {
    return getDelegate().getValidChars();
  }

  @Override
  public void setInvalidChars(String invalidChars) {
    getDelegate().setInvalidChars(invalidChars);
  }

  @Override
  public String getInvalidChars() {
    return getDelegate().getInvalidChars();
  }

  @Override
  public void setTextConverter(Function<String, String> textConverter) {
    getDelegate().setTextConverter(textConverter);
  }

  @Override
  public Function<String, String> getTextConverter() {
    return getDelegate().getTextConverter();
  }

  @Override
  public void setErrorOffset(Integer errorOffset) {
    getDelegate().setErrorOffset(errorOffset);
  }

  @Override
  public Integer getErrorOffset() {
    return getDelegate().getErrorOffset();
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    getDelegate().mapErrorOffsetToCaretPosition();
  }

  @Override
  public void autoSelect() {
    getDelegate().autoSelect();
  }

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    getDelegate().setAutoCompletion(autoCompletion);
  }

  @Override
  public Function<String, List<List<SubString>>> getAutoCompletion() {
    return getDelegate().getAutoCompletion();
  }

  //</editor-fold>//GEN-END:textcomponent

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
