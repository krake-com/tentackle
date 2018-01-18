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

package org.tentackle.fx;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import org.tentackle.fx.translate.ValueStringTranslator;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Delegate implementing FxComponent.
 *
 * @author harald
 */
public abstract class FxTextComponentDelegate extends FxComponentDelegate
       implements FxTextComponent, UnaryOperator<TextFormatter.Change> {

  /**
   * The text alignment.
   */
  private Pos textAlignment;

  /**
   * maximum number of characters.
   */
  private int maxColumns;

  /**
   * true if autoselect on focus gained
   */
  private boolean autoSelect;

  /**
   * The format pattern.
   */
  private String pattern;

  /**
   * The numeric scale.
   */
  private int scale;

  /**
   * Unsigned numeric field.
   */
  private boolean unsigned;

  /**
   * UTC timezone field.
   */
  private boolean utc;

  /**
   * The case conversion.
   */
  private CaseConversion caseConversion;

  /**
   * The filler character.
   */
  private char filler = ' ';

  /**
   * Valid characters.
   */
  private String validChars;

  /**
   * Invalid characters.
   */
  private String invalidChars;

  /**
   * Generic text converter.
   */
  private Function<String,String> textConverter;

  /**
   * triggerViewModified propagated.
   */
  private boolean viewModifiedTriggered;

  /**
   * The error offset within the text field.
   */
  private Integer errorOffset;



  /**
   * Gets the text component.
   *
   * @return the text component
   */
  protected FxTextComponent getTextComponent() {
    return (FxTextComponent) getComponent();
  }


  @Override
  protected void updateChangeable(boolean changeable) {
    if (getComponent() instanceof TextInputControl) {
      // map to editable property
      ((TextInputControl) getComponent()).setEditable(changeable);
      getNode().setFocusTraversable(changeable);
      // mandatory is only shown if component is changeable as well
      updateMandatoryStyle(isMandatory());
    }
    else {
      // use disabled property
      super.updateChangeable(changeable);
    }
  }

  @Override
  protected void updateMandatoryStyle(boolean mandatory) {
    if (getComponent() instanceof TextInputControl && !((TextInputControl) getComponent()).isEditable()) {
      mandatory = false;
    }
    super.updateMandatoryStyle(mandatory);
  }



  @Override
  protected ReadOnlyBooleanWrapper createChangeableProperty(boolean changeable) {
    ReadOnlyBooleanWrapper changeableProperty;
    if (getComponent() instanceof TextInputControl) {
      changeableProperty =  new ReadOnlyBooleanWrapper(changeable);
      // if the editable property changes, change the changeable property as well
      changeableProperty.bind(((TextInputControl) getComponent()).editableProperty());
    }
    else {
      // use disabled property
      changeableProperty = super.createChangeableProperty(changeable);
    }
    return changeableProperty;
  }

  @Override
  public void setContainerChangeable(boolean containerChangeable) {
    if (getComponent() instanceof TextInputControl) {
      if (!isContainerChangeableIgnored()) {
        // map to editable property
        ((TextInputControl) getComponent()).setEditable(containerChangeable && isControlChangeable());
        // don't invoke updateChangeable() as this would overwrite local controlChangeable!
      }
    }
    else {
      // use disabled property
      super.setContainerChangeable(containerChangeable);
    }
  }


  @Override
  public Integer getErrorOffset() {
    return errorOffset;
  }

  @Override
  public void setErrorOffset(Integer errorOffset) {
    this.errorOffset = errorOffset;
  }

  @Override
  public void showErrorPopup() {
    super.showErrorPopup();
    mapErrorOffsetToCaretPosition();
  }

  @Override
  public void triggerViewModified() {
    if (!viewModifiedTriggered) {
      // if not already propagated to parents
      super.triggerViewModified();
      // don't trigger again when field is now changed (this is the usual case).
      // But trigger on next keystroke, if field became unchanged due to this keystoke!
      viewModifiedTriggered = isViewModified();
    }
  }

  @Override
  public void updateView() {
    super.updateView();
    viewModifiedTriggered = false;
  }

  @Override
  public void updateModel() {
    viewModifiedTriggered = false;
    errorOffset = null;
    if (getComponent() instanceof TextInputControl) {
      if (((TextInputControl) getComponent()).isEditable()) {
        super.updateModel();
      }
    }
    else {
      super.updateModel();
    }
  }

  @Override
  public void saveView() {
    super.saveView();
    viewModifiedTriggered = false;
  }

  @Override
  public void setValidChars(String validChars) {
    this.validChars = validChars;
  }

  @Override
  public String getValidChars ()  {
    return validChars;
  }

  @Override
  public  void  setInvalidChars (String invalidChars)  {
    this.invalidChars = invalidChars;
  }

  @Override
  public String getInvalidChars ()  {
    return invalidChars;
  }

  @Override
  public void setTextConverter(Function<String, String> textConverter) {
    this.textConverter = textConverter;
  }

  @Override
  public Function<String, String> getTextConverter() {
    return textConverter;
  }


  @Override
  public TextFormatter.Change apply(TextFormatter.Change t) {
    String text = t.getText();    // never null
    if (!text.isEmpty()) {
      text = filter(text);
      if (maxColumns > 0) {    // max columns set
        String newText = t.getControlNewText();
        int tooMuch = newText.length() - maxColumns;
        if (tooMuch > 0) {
          // cut in length
          int length = text.length() - tooMuch;
          if (length < 0) {
            length = 0;
          }
          text = text.substring(0, length);
        }
      }
      // adjust cursor if length changed
      int diff = text.length() - t.getText().length();
      t.setText(text);
      if (diff != 0) {
        t.setCaretPosition(t.getCaretPosition() + diff);
        t.setAnchor(t.getAnchor() + diff);
      }
    }
    return t;
  }

  /**
   * Filters input characters.
   *
   * @param text the input text, never null
   * @return the filtered text, never null
   */
  public String filter(String text) {
    if (getValueTranslator() instanceof ValueStringTranslator) {
      String transValidChars = ((ValueStringTranslator) getValueTranslator()).getValidChars();
      if (transValidChars != null) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i < text.length(); i++) {
          char c = text.charAt(i);
          if (transValidChars.indexOf(c) >= 0) {
            buf.append(c);
          }
        }
        text = buf.toString();
      }
    }

    if (validChars != null) {
      StringBuilder buf = new StringBuilder();
      for (int i=0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (validChars.indexOf(c) >= 0) {
          buf.append(c);
        }
      }
      text = buf.toString();
    }

    if (invalidChars != null) {
      StringBuilder buf = new StringBuilder();
      for (int i=0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (invalidChars.indexOf(c) < 0) {
          buf.append(c);
        }
      }
      text = buf.toString();
    }

    if (caseConversion == CaseConversion.UPPER_CASE) {
      text = text.toUpperCase();
    }
    else if (caseConversion == CaseConversion.LOWER_CASE) {
      text = text.toLowerCase();
    }

    if (textConverter != null) {
      text = textConverter.apply(text);
    }
    return text;
  }


  @Override
  public void setTextAlignment(Pos textAlignment) {
    this.textAlignment = textAlignment;
  }

  @Override
  public Pos getTextAlignment() {
    return textAlignment;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);
    if (getValueTranslator() == null) {    // if not already set by application
      setValueTranslator(FxFactory.getInstance().createValueTranslator(type, String.class, getComponent()));
    }

    if (type.isPrimitive()) {
      type = ReflectionHelper.primitiveToWrapperClass(type);
    }
    if (Number.class.isAssignableFrom(type)) {
      Pos alignment = getTextComponent().getTextAlignment();
      if (alignment == null) {
        setTextAlignment(Pos.CENTER_RIGHT);
      }
    }
  }


  @Override
  public void setMaxColumns(int maxColumns) {
    this.maxColumns = maxColumns;
  }

  @Override
  public int getMaxColumns() {
    return maxColumns;
  }

  @Override
  public void setAutoSelect(boolean autoSelect) {
    this.autoSelect = autoSelect;
  }

  @Override
  public boolean isAutoSelect() {
    return autoSelect;
  }

  @Override
  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public String getPattern() {
    return pattern;
  }

  @Override
  public void setScale(int scale) {
    this.scale = scale;
  }

  @Override
  public int getScale() {
    return scale;
  }

  @Override
  public boolean isUnsigned() {
    return unsigned;
  }

  @Override
  public void setUnsigned(boolean unsigned) {
    this.unsigned = unsigned;
  }

  @Override
  public boolean isUTC() {
    return utc;
  }

  @Override
  public void setUTC(boolean utc) {
    this.utc = utc;
  }

  @Override
  public void setCaseConversion(CaseConversion caseConversion) {
    this.caseConversion = caseConversion;
  }

  @Override
  public CaseConversion getCaseConversion() {
    return caseConversion;
  }

  @Override
  public void setFiller(char filler) {
    if (Character.isISOControl(filler)) {
      throw new FxRuntimeException("invalid fill character: " + (int) filler);
    }
    this.filler = filler;
  }

  @Override
  public char getFiller() {
    return filler;
  }

  @Override
  public void autoSelect() {
    if (getComponent() instanceof TextInputControl) {
      if (isAutoSelect()) {
        ((TextInputControl) getComponent()).selectAll();
      }
      else {
        ((TextInputControl) getComponent()).deselect();
      }
    }
  }

}
