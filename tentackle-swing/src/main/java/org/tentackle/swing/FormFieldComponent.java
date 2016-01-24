/**
 * Tentackle - http://www.tentackle.org
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
 */


package org.tentackle.swing;

import java.util.function.Function;


/**
 * Extended {@link FormComponent} for editable text fields.<p>
 * Adds features like max columns, autoselect, autotext, conversion, etc...
 *
 * @author harald
 */
public interface FormFieldComponent extends FormComponent {

  // text adjustments
  /** no adjustment at all **/
  static public final char ADJUST_NONE   = '=';
  /** remove leading fillers **/
  static public final char ADJUST_LEFT   = '<';
  /** remove trailing fillers **/
  static public final char ADJUST_RIGHT  = '>';
  /** trim to both sides (default) **/
  static public final char ADJUST_TRIM   = '|';

  // character conversion
  /** no conversion (default) **/
  static public final char CONVERT_NONE   = '=';
  /** to uppercase **/
  static public final char CONVERT_UC     = '^';
  /** to lowercase **/
  static public final char CONVERT_LC     = 'v';


  /**
   * Sets the format string.<br>
   * The format string is used to format the view of the data.
   *
   * @param pattern the format string
   */
  public void setFormat (String pattern);

  /**
   * Gets the format string.
   *
   * @return the format string
   */
  public String getFormat ();


  /**
   * Sets the auto-select feature.<br>
   * A component with autoselect enabled will automatically
   * select all characters if the component gets the keyboard focus.
   * Useful for numerical fields, for example.
   *
   * @param autoSelect true if autoselect enabled, false if disabled (default)
   */
  public void setAutoSelect (boolean autoSelect);

  /**
   * Returns whether the auto-select feature is enabled.
   *
   * @return true if autoselect enabled, false if disabled (default)
   */
  public boolean isAutoSelect();


  /**
   * Sets the auto-next feature.<br>
   * If autonext is enabled the next component will automatically
   * receive the focus if the maximum number of characters is reached.
   * Only meaningful if maxColumns &gt; 0
   *
   * @param autoNext true if autonext enabled, false if disabled (default)
   */
  public void setAutoNext (boolean autoNext);

  /**
   * Returns whether the auto-next feature is enabled.
   *
   * @return true if autonext enabled, false if disabled (default)
   */
  public boolean isAutoNext();



  /**
   * Sets character upper/lowercase conversion.<br>
   * Notice that changing the character conversion will immediately convert.
   *
   * @param convert one of {@code FormField.CONVERT_...}
   */
  public void setConvert (char convert);

  /**
   * Gets the current convesion
   *
   * @return the conversion, default is CONVERT_NONE
   */
  public char getConvert ();


  /**
   * Sets the string converter.<br>
   * Besides the lower/uppercase conversion an optional
   * converter may be set to translate characters.
   *
   * @param converter the converter, null if none (default)
   */
  public void setConverter(Function<String,String> converter);


  /**
   * Gets the current converter. Default is null.
   *
   * @return the converter.
   */
  public Function<String,String> getConverter();


  /**
   * Sets character adjustment.<br>
   * By default the text input data is trimmed, i.e. fill characters
   * (space by default)
   * to the left and right are removed.
   * This is not be mixed up with the horizontal orientation!
   *
   * @param adjust one of {@code FormField.ADJUST_...}
   * @see #setFiller(char)
   */
  public void setAdjust (char adjust);

  /**
   * Gets the current adjustment.
   *
   * @return the adjustment, default ADJUST_TRIM
   */
  public  char  getAdjust ();


  /**
   * Sets the maximum number of columns.<br>
   * The number of columns will be enforced by the model.
   * <p>
   * If {@link AbstractFormField#getColumns()} == 0 the component will resize as
   * if setColumns has been invoked with the given maxColumns. However,
   * getColumns() remains 0.
   *
   * @param maxColumns the maximum number of columns, 0 if unlimited (default)
   */
  public  void  setMaxColumns (int maxColumns);

  /**
   * Gets the maximum number of columns.
   *
   * @return the maximum number of columns, 0 if unlimited (default)
   */
  public int  getMaxColumns ();


  /**
   * Sets the default columns.<br>
   * The method is mainly provided for GUI-designers and will change
   * the displayed size if, and only if, setColumns() and setMaxColumns() has not been
   * invoked yet. The default columns will not affect the model in any way.
   * <p>
   * With defaultColumns it is possible to set the width of a field to a reasonable
   * size and let the application use setMaxColumn() to set the real value from
   * the model (which is not available at design time).
   *
   * @param defaultColumns the default columns
   */
  public void setDefaultColumns(int defaultColumns);

  /**
   * Gets the default columns.
   *
   * @return the default columns.
   */
  public int getDefaultColumns();

  /**
   * Sets the fill char.<br>
   * The fill char determines the adjustment of the text data.
   * @param filler the fill character
   * @see #setAdjust(char)
   */
  public  void  setFiller (char filler);

  /**
   * Gets the current fill char.
   * @return the filler, default is blank (space)
   */
  public char getFiller ();


  /**
   * Sets the insert/override mode.
   *
   * @param override true if override, false if insert (default)
   */
  public  void  setOverwrite (boolean override);

  /**
   * Gets the current override mode.
   *
   * @return true if override, false if insert (default)
   */
  public boolean isOverwrite();


  /**
   * Sets allowed characters.
   *
   * @param validChars the valid characters, null = all (default)
   */
  public  void  setValidChars (String validChars);

  /**
   * Gets allowed characters.
   *
   * @return the valid characters, null = all (default)
   */
  public String getValidChars();


  /**
   * Sets invalid characters.
   *
   * @param invalidChars the invalid characters, null = none (default)
   */
  public  void  setInvalidChars (String invalidChars);

  /**
   * Gets invalid characters.
   *
   * @return the invalid characters, null = none (default)
   */
  public String getInvalidChars ();


  /**
   * Returns whether carat is at leftmost position.
   *
   * @return true if caret is at leftmost position
   */
  public boolean isCaretLeft();

  /**
   * Returns whether caret is at rightmost position.
   *
   * @return true if caret is at rightmost position
   */
  public boolean isCaretRight();

  /**
   * Sets the caret to leftmost position.
   */
  public void setCaretLeft();

  /**
   * Sets the caret to rightmost position.
   */
  public void setCaretRight();


  /**
   * Move caret to the left side of field and if already there move to previous field.
   */
  public void upLeft();

  /**
   * Move the caret to the right side of field and if already there move to next field.
   */
  public void downRight();


  /**
   * Sets whether to erase the field after first setText().<br>
   *
   * @param eraseFirst true to clear after first setText
   */
  public void setEraseFirst(boolean eraseFirst);

  /**
   * Returns whether to erase the field after first setText().<br>
   *
   * @return true to clear after first setText
   */
  public boolean isEraseFirst();


  /**
   * Sets auto-select to be inhibited inhibited (once).
   *
   * @param inhibitAutoSelect true if inhibited
   */
  public void setInhibitAutoSelect(boolean inhibitAutoSelect);

  /**
   * Returns whether auto-select is inhibited (once).
   * @return true if inhibited
   */
  public boolean isInhibitAutoSelect();


  /**
   * Clears the contents of this component.
   */
  public void clearText();


  /**
   * Does the formatting of the given object.<br>
   * Renders the object and returns the string.
   *
   * @param object the object
   * @return the formatted object as a string
   */
  public String doFormat(Object object);


  /**
   * Determines whether this component is empty.<br>
   *
   * @return true if empty
   */
  public boolean isEmpty();


  /**
   * Sets whether to start edit leftmost in this component (once).
   *
   * @param startEditLeftmost true if start edit leftmost
   */
  public void setStartEditLeftmost(boolean startEditLeftmost);

  /**
   * Returns whether to start edit leftmost in this component (once).
   *
   * @return true if start edit leftmost
   */
  public boolean isStartEditLeftmost();


  /**
   * Gets the offset in this components text for the first parsing error.<br>
   *
   * @return the error offset, -1 if no error
   */
  public int getErrorOffset();

  /**
   * Sets the error offset.
   *
   * @param errorOffset the error offset in the text component, -1 to clear
   */
  public void setErrorOffset(int errorOffset);


  /**
   * Gets the parsing error message for the first error.
   *
   * @return the error message (may be null)
   */
  public String getErrorMessage();

  /**
   * Sets the error message.
   *
   * @param errorMessage the message, null to clear
   */
  public void setErrorMessage(String errorMessage);


  /**
   * Invokes the action performed handler.
   */
  public void postActionEvent();




  // ------------------- Swing standard methods ----------------------


  /**
   * Sets the given text in the view of this component.
   * @param str the text
   */
  public void setText(String str);

  /**
   * Gets the text from the view of this component.
   *
   * @return the text
   */
  public String getText();


  /**
   * Sets the number of columns in this component.<br>
   * The visible width is adjusted according to the current font.
   *
   * @param columns the number of columns &ge; 0
   */
  public void setColumns (int columns);

  /**
   * Gets the columns.
   *
   * @return the columns
   */
  public int getColumns ();


  /**
   * Sets the tooltip text for this component.
   *
   * @param text the tooltip text, null = none
   */
  public void setToolTipText(String text);

  /**
   * Gets the tooltip text for this component.
   *
   * @return the tooltip text, null = none
   */
  public String getToolTipText();


  /**
   * Sets the horizontal alignment.
   *
   * @param alignment the alignment
   * @see java.awt.Component
   */
  public void setHorizontalAlignment(int alignment);

  /**
   * Gets the horizontal alignment.
   *
   * @return the horizontal alignment
   * @see java.awt.Component
   */
  public int  getHorizontalAlignment();


  /**
   * Sets the vertical alignment.<br>
   * Can only be used in Tentackle-LookAndFeels!
   *
   * @param alignment the vertical alignment
   */
  public void setVerticalAlignment(int alignment);

  /**
   * Gets the vertical alignment.
   *
   * @return the vertical alignment
   */
  public int  getVerticalAlignment();


  /**
   * Sets the caret position.
   *
   * @param pos the caret position, 0 = start of field
   */
  public void setCaretPosition(int pos);

  /**
   * Gets the caret position.
   *
   * @return the caret position, 0 = start of field
   */
  public int getCaretPosition();

}