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
import org.tentackle.misc.SubString;

import javafx.geometry.Pos;
import java.util.List;
import java.util.function.Function;

/**
 * A component that is edited as text.
 *
 * @author harald
 */
public interface FxTextComponent extends FxComponent {

  /**
   * Returns whether the component is in text-editing mode.<br>
   * Some components, such as {@link org.tentackle.fx.component.FxComboBox}, are a <code>FxTextComponent</code>,
   * but can work in editable and non-editable mode.<br>
   * Not to be mixed up with {@link #isChangeable()}!
   *
   * @return true if the component is in text-editing mode
   */
  boolean isEditable();

  /**
   * Gets the error offset.
   *
   * @return the offset, null if no offset
   */
  Integer getErrorOffset();

  /**
   * Sets the error offset.
   *
   * @param errorOffset null to clear
   */
  void setErrorOffset(Integer errorOffset);

  /**
   * Sets the caret position according to the error offset.<br>
   * Does nothing if the error offset is null.
   */
  void mapErrorOffsetToCaretPosition();

  /**
   * Sets the text alignment.
   *
   * @param textAlignment the alignment, null if component's default
   */
  void setTextAlignment(Pos textAlignment);

  /**
   * Gets the text alignment.
   *
   * @return the alignment, null if component's default
   */
  Pos getTextAlignment();

  /**
   * Sets the number of columns in this component.<br>
   * The visible width is adjusted according to the current font.
   *
   * @param columns the number of columns &ge; 0
   */
  void setColumns(int columns);

  /**
   * Gets the columns.
   *
   * @return the columns
   */
  int getColumns();

  /**
   * Sets the maximum number of columns.<br>
   * Input exceeding the maximum is truncated and an {@link InputAlert} is raised.
   *
   * @param maxColumns the maximum number of columns, 0 if unlimited (default)
   */
  void setMaxColumns(int maxColumns);

  /**
   * Gets the maximum number of columns.
   *
   * @return the maximum number of columns, 0 if unlimited (default)
   */
  int getMaxColumns();

  /**
   * Sets the auto-select feature.<br>
   * A component with auto-select enabled will automatically
   * select all characters if the component gets the keyboard focus.
   * Useful for numerical fields, for example.
   *
   * @param autoSelect true if auto-select enabled, false if disabled (default)
   */
  void setAutoSelect(boolean autoSelect);

  /**
   * Returns whether the auto-select feature is enabled.
   *
   * @return true if auto-select enabled, false if disabled (default)
   */
  boolean isAutoSelect();

  /**
   * Sets the format pattern.<br>
   * The format pattern is used to format the view of the data.
   *
   * @param pattern the format pattern
   */
  void setPattern(String pattern);

  /**
   * Gets the format pattern.
   *
   * @return the format pattern
   */
  String getPattern();

  /**
   * Returns whether parsing to model should be lenient.
   *
   * @return true if lenient, false is default
   */
  boolean isLenient();

  /**
   * Sets whether parsing to model should be lenient.
   *
   * @param lenient true if lenient
   */
  void setLenient(boolean lenient);

  /**
   * Changes the format according to the given scale.
   *
   * @param scale the number of digits after the decimal point
   */
  void setScale(int scale);

  /**
   * Gets the current scale.
   *
   * @return the scale
   */
  int getScale();

  /**
   * Sets whether a numeric field is unsigned or signed.
   *
   * @param unsigned true if unsigned
   */
  void setUnsigned(boolean unsigned);

  /**
   * Returns whether a numeric field is unsigned or signed.
   *
   * @return true if unsigned
   */
  boolean isUnsigned();

  /**
   * Sets whether this is a UTC timestamp.
   *
   * @param utc true if UTC
   */
  void setUTC(boolean utc);

  /**
   * Returns whether this is a UTC timestamp.
   *
   * @return true if UTC
   */
  boolean isUTC();

  /**
   * Sets the case conversion.
   *
   * @param caseConversion the conversion, null if no conversion (default)
   */
  void setCaseConversion(CaseConversion caseConversion);

  /**
   * Gets the case conversion.
   *
   * @return the conversion, null if no conversion (default)
   */
  CaseConversion getCaseConversion();

  /**
   * Sets the filler character.<br>
   * Spaces in the input are replaced by the filler, and the filler is stripped
   * according to the {@link #getStripMode()}. The default is space.
   *
   * @param filler the filler
   * @throws FxRuntimeException if the filler is an ISO control character
   */
  void setFiller(char filler);

  /**
   * Gets the filler character.
   *
   * @return the filler
   */
  char getFiller();

  /**
   * Sets the input strip mode.
   *
   * @param stripMode the strip mode, null for the default {@link StripMode#BOTH}
   */
  void setStripMode(StripMode stripMode);

  /**
   * Gets the input strip mode.<br>
   * The default is {@link StripMode#BOTH}.
   *
   * @return the strip mode, never null
   */
  StripMode getStripMode();

  /**
   * Sets allowed characters.
   *
   * @param validChars the valid characters, null = all (default)
   */
  void setValidChars(String validChars);

  /**
   * Gets allowed characters.
   *
   * @return the valid characters, null = all (default)
   */
  String getValidChars();

  /**
   * Sets invalid characters.
   *
   * @param invalidChars the invalid characters, null = none (default)
   */
  void setInvalidChars(String invalidChars);

  /**
   * Gets invalid characters.
   *
   * @return the invalid characters, null = none (default)
   */
  String getInvalidChars();

  /**
   * Sets a text converter.<br>
   * Converts input text to the view's text.
   * Applied as the last step when filtering the input.
   *
   * @param textConverter the converter
   */
  void setTextConverter(Function<String,String> textConverter);

  /**
   * Gets the text converter.
   *
   * @return the converter
   */
  Function<String,String> getTextConverter();

  /**
   * Selects all text if auto-select is enabled, otherwise deselects it.
   *
   * @see #isAutoSelect()
   */
  void autoSelect();

  /**
   * Sets the auto-completion function.<br>
   * The function gets the input from the user and returns a list of
   * matching items. Each item is represented as a list of matching
   * text segments.
   *
   * @param autoCompletion the function, null to clear
   */
  void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion);

  /**
   * Gets the auto-completion function.
   *
   * @return the function, null if auto-completion disabled (default)
   */
  Function<String, List<List<SubString>>> getAutoCompletion();

}
