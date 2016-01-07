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

import java.text.DecimalFormat;
import javax.swing.SwingConstants;
import javax.swing.text.Document;
import org.tentackle.common.BMoney;
import org.tentackle.misc.FormatHelper;


/**
 * Abstract base class for all numeric form fields.
 *
 * @author harald
 */
@SuppressWarnings("serial")
abstract public class AbstractNumberFormField extends AbstractFormField {

  private static final String defValidChars = ".,-0123456789";  // default valid chars

  private boolean unsigned;     // true if only positive integers allowed
  private boolean blankZero;    // true if zero should be an empty field

  /** the decimal decimalFormat */
  protected DecimalFormat decimalFormat;



  /**
   * Creates a AbstractNumberFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractNumberFormField (Document doc, int columns) {
    super (doc, null, columns);
    // default is only digits */
    setValidChars (defValidChars);
    setHorizontalAlignment(SwingConstants.TRAILING);
    decimalFormat = new DecimalFormat(FormatHelper.getIntegerPattern());
  }


  /**
   * Creates a AbstractNumberFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractNumberFormField (int columns)  {
    this (null, columns);
  }

  /**
   * Creates an empty AbstractNumberFormField with the default document model,
   * and minimum column width.<br>
   */
  public AbstractNumberFormField () {
    this (0);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The format string will be converted to a {@link DecimalFormat}.
   * @see #setDecimalFormat(java.text.DecimalFormat)
   */
  @Override
  public void setFormat (String pattern)  {
    setDecimalFormat(new DecimalFormat (pattern));
  }

  @Override
  public String getFormat ()  {
    return decimalFormat.toPattern();
  }



  /**
   * Sets the decimal format.
   * <p>
   * Useful if format cannot be fully described by {@link #setFormat(java.lang.String)}.
   * @param decimalFormat the format
   */
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    this.decimalFormat = decimalFormat;
  }

  /**
   * Gets the current decimal format.<br>
   * May be used to modify the format for special purposes.
   *
   * @return the decimal format
   */
  public DecimalFormat getDecimalFormat() {
    return decimalFormat;
  }


  @Override
  public void setFormValue (Object object)  {
    super.setText (doFormat(object));
  }


  /**
   * Applications such as a GUI-Builder should not invoke setText for numeric fields directly.
   * Overridden to swallow the text.
   * @param text ignored text
   */
  @Override
  public void setText (String text) {

  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden because {@link #setText(java.lang.String)} is overridden to swallow the text.
   */
  @Override
  protected void restoreSavedValue() {
    super.setText(savedValue);
  }

  @Override
  public void clearText() {
    super.setText("");
  }


  @Override
  public String doFormat (Object object)  {

    if (object != null) {

      if (blankZero)  {
        if (object instanceof Double) {
          if (((Double) object) == 0.0d) {
            return "";
          }
        }
        else if (object instanceof Float) {
          if (((Float) object) == 0.0f) {
            return "";
          }
        }
        else if (object instanceof BMoney) {
          if (((BMoney) object).isZero()) {
            return "";
          }
        }
        else if (object instanceof Number) {
          if (((Number) object).longValue() == 0l) {
            return "";
          }
        }
      }

      // else standard formatting */
      return decimalFormat.format(object);
    }

    return "";
  }



  /**
   * Sets field to unsigned.
   *
   * @param unsigned true if unsigned, false if signed
   */
  public void setUnsigned(boolean unsigned)  {

    this.unsigned = unsigned;

    String vchars;

    if (unsigned) {
      // remove minus sign from valid chars, if any
      vchars = getValidChars().replace('-', getFiller());
    }
    else  {
      // replace fillers if any
      vchars = getValidChars().replace(getFiller(), '-');
      // add minus sign if not already done
      if (!vchars.contains("-")) {
        vchars += "-";
      }
    }

    super.setValidChars (vchars);
  }

  /**
   * Returns whether decimalFormat is unsigned
   *
   * @return true if field is unsigned, else false
   */
  public boolean isUnsigned() {
    return unsigned;
  }




  @Override
  public void setValidChars(String validChars) {
    super.setValidChars(validChars);
    unsigned = validChars != null && validChars.indexOf('-') < 0;
  }




  /**
   * Sets whether zero fields are displayed as empty fields.
   *
   * @param blankZero true if zero values are displayed as empty field, default is false
   */
  public void setBlankZero (boolean blankZero)  {
    this.blankZero = blankZero;
  }

  /**
   * Returns the blankZero attribute.
   *
   * @return true if zero values are displayed as empty field
   */
  public boolean isBlankZero()  {
    return blankZero;
  }

}
