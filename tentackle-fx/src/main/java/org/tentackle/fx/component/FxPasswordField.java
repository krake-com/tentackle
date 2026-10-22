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

import org.tentackle.common.StringHelper;
import org.tentackle.common.StripMode;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.fx.CaseConversion;
import org.tentackle.fx.FxContainer;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ModelToViewListener;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.ViewToModelListener;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.delegate.FxPasswordFieldDelegate;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.log.Logger;
import org.tentackle.misc.SubString;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.PasswordField;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;


/**
 * Extended PasswordField with enhanced security.<br>
 * The StringBuilder holding the text input is replaced with blanks when getPassword() is invoked.
 * The application is responsible for clearing the returned char-array as well.<br>
 * Needs "--add-opens javafx.controls/javafx.scene.control=org.tentackle.fx", if the application runs in modular mode.
 * Falls back to a less secure mode and logs a warning, if {@code javafx.controls} is not open.
 * <p>
 * Important: if you use the password character array methods, you must not use standard FX bindings,
 * listeners, etc., as they all work with regular strings. Furthermore, all text processing like
 * maxColumns, filtering, etc. will be disabled, and the internal character buffer is accessed
 * directly bypassing the string-based operations.
 *
 * @author harald
 */
public class FxPasswordField extends PasswordField implements FxTextComponent {

  private static final Logger LOGGER = Logger.get(FxPasswordField.class);

  private StringBuilder characterBuffer;
  private boolean fallbackMode;

  /**
   * Gets the password as a character array and clears the contents in memory.
   *
   * @return the password characters, null if empty
   */
  public char[] getPassword() {
    StringBuilder buf = getCharacterBuffer();
    char[] result;
    if (fallbackMode) {
      Content passwordContent = getContent();
      int length = passwordContent.length();
      result = getContent().get(0, length).toCharArray();   // creates interim String :(
      getContent().delete(0, length, false);
    }
    else {
      result = new char[buf.length()];
      buf.getChars(0, buf.length(), result, 0);
      // scratch in memory
      for (int i = 0; i < buf.length(); i++) {
        buf.setCharAt(i, ' ');
      }
      buf.setLength(0);
    }
    return result.length == 0 ? null : result;
  }

  /**
   * Sets the password.
   *
   * @param password the password character array, null to clear
   */
  public void setPassword(char[] password) {
    StringBuilder buf = getCharacterBuffer();
    if (password != null) {
      if (fallbackMode) {
        setText(String.copyValueOf(password));    // creates a String :(
      }
      else {
        setText(StringHelper.fillRight("", password.length, ' '));    // fake length
        buf.setLength(0);
        buf.append(password);
      }
    }
    else {
      setText(null);
    }
  }


  private StringBuilder getCharacterBuffer() {
    if (characterBuffer == null) {
      Content content = getContent();
      try {
        Field field = content.getClass().getDeclaredField("characters");
        field.setAccessible(true);
        characterBuffer = (StringBuilder) field.get(content);
        setTextFormatter(null);
        setPromptText(null);
        if (getType() == null) {
          setType(char[].class);
        }
      }
      catch (InaccessibleObjectException ex) {
        LOGGER.warning("consider '--add-opens javafx.controls/javafx.scene.control=org.tentackle.fx' to enable secure password input mode");
        fallbackMode = true;
        characterBuffer = new StringBuilder();
      }
      catch (IllegalAccessException | NoSuchFieldException e) {
        throw new TentackleRuntimeException("cannot access character buffer", e);
      }
    }
    return characterBuffer;
  }

  // @wurblet delegate Include --translate $componentIncludeDir/fxtextcomponent.include

  //<editor-fold defaultstate="collapsed" desc="code 'delegate' generated by wurblet Include">//GEN-BEGIN:delegate

  private FxPasswordFieldDelegate delegate;

  /**
   * Creates a FxPasswordField.
   */
  public FxPasswordField() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates the delegate.
   *
   * @return the delegate
   */
  protected FxPasswordFieldDelegate createDelegate() {
    return new FxPasswordFieldDelegate(this);
  }

  @Override
  public FxPasswordFieldDelegate getDelegate() {
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
  public void setDelegate(FxPasswordFieldDelegate delegate) {
    this.delegate = delegate;
  }

  // @wurblet component Include $componentIncludeDir/component.include

  // @wurblet textcomponent Include $componentIncludeDir/textcomponent.include

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

}
