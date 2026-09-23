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

package org.tentackle.fx;

import org.tentackle.common.StripMode;
import org.tentackle.fx.translate.ValueStringTranslator;
import org.tentackle.misc.SubString;
import org.tentackle.reflect.ReflectionHelper;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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
   * whether parsing to model should be lenient.
   */
  private boolean lenient;

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
   * The input strip mode.
   */
  private StripMode stripMode = StripMode.BOTH;

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
   * Auto-completion function.
   */
  private Function<String, List<List<SubString>>> autoCompletion;


  /**
   * Creates a text component delegate.
   */
  public FxTextComponentDelegate() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

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
  public void setError(String errorMessage) {
    super.setError(errorMessage);
    if (errorMessage == null) {
      setErrorOffset(null);
    }
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
      // don't trigger again when the field is now changed (this is the usual case).
      // But trigger on the next keystroke if the field became unchanged due to this keystroke!
      viewModifiedTriggered = isViewModified();
    }
  }

  @Override
  public void updateView() {
    if (!isUpdatingView()) {
      super.updateView();
      viewModifiedTriggered = false;
    }
  }

  @Override
  public void updateModel() {
    if (!isUpdatingModel()) {
      viewModifiedTriggered = false;
      errorOffset = null;
      if (getComponent() instanceof TextInputControl textInputControl) {
        if (textInputControl.isEditable()) {
          if (stripMode != null && stripMode != StripMode.NONE) {
            String text = textInputControl.getText();
            if (text != null) {
              String strippedText = stripMode.strip(text, filler);
              if (!text.equals(strippedText)) {
                // don't trigger listeners if not really changed
                textInputControl.setText(strippedText);
              }
            }
          }
          super.updateModel();
        }
      }
      else {
        super.updateModel();
      }
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
          if (!isUpdatingView()) {
            InputAlert.getInstance().alert(getNode(), InputAlert.Reason.TOO_LONG);
          }
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
      if (diff != 0 && !text.isEmpty()) {   // empty field is pathological since pos must be > 0
        int pos = t.getCaretPosition() + diff;
        // align for sure
        if (pos <= 0) {
          pos = 1;
        }
        else if (pos > text.length()) {
          pos = text.length();
        }
        t.setCaretPosition(pos);
        t.setAnchor(pos);
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
  @SuppressWarnings("rawtypes")
  public String filter(String text) {
    if (getValueTranslator() instanceof ValueStringTranslator) {
      String transValidChars = ((ValueStringTranslator) getValueTranslator()).getValidChars();
      if (transValidChars != null) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
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
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == filler || validChars.indexOf(c) >= 0) {
          buf.append(c);
        }
      }
      text = buf.toString();
    }

    if (invalidChars != null) {
      StringBuilder buf = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (invalidChars.indexOf(c) < 0) {
          buf.append(c);
        }
      }
      text = buf.toString();
    }

    if (filler != ' ') {
      text = text.replace(' ', filler);
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
  public void setType(Class<?> type) {
    // important to set the type before creating the translator,
    // since the translator may need to know the type
    super.setType(type);
    if (getValueTranslator() == null) {    // if not already set by application
      setValueTranslator(createValueTranslator(type));
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

  protected ValueTranslator<?,?> createValueTranslator(Class<?> type) {
    return FxFactory.getInstance().createValueTranslator(type, String.class, getComponent());
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
  public boolean isLenient() {
    return lenient;
  }

  @Override
  public void setLenient(boolean lenient) {
    this.lenient = lenient;
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
  public StripMode getStripMode() {
    return stripMode;
  }

  @Override
  public void setStripMode(StripMode stripMode) {
    this.stripMode = stripMode == null ? StripMode.BOTH : stripMode;
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

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    this.autoCompletion = autoCompletion;
  }

  @Override
  public Function<String, List<List<SubString>>> getAutoCompletion() {
    return autoCompletion;
  }

  @Override
  public boolean isEditable() {
    return getTextComponent().isEditable();
  }

}
