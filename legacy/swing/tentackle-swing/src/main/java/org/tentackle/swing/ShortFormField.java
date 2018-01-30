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

import java.text.MessageFormat;
import java.text.ParseException;
import javax.swing.text.Document;


/**
 * FormField to edit a short value.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class ShortFormField extends AbstractNumberFormField {

  private short minValue = Short.MIN_VALUE;
  private short maxValue = Short.MAX_VALUE;


  /**
   * Creates an empty ShortFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   */
  public ShortFormField (Document doc, int columns) {
    super (doc, columns);
  }


  /**
   * Creates an empty ShortFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public ShortFormField (int columns)  {
    this (null, columns);
  }


  /**
   * Creates an empty ShortFormField with the default document model,
   * a minimum column width.<br>
   */
  public ShortFormField () {
    this (0);
  }


  /**
   * Sets the minimum value.<br>
   *
   * @param minValue the minimum value
   */
  public void setMinValue(short minValue) {
    this.minValue = minValue;
  }

  /**
   * Gets the minimum value.<br>
   * The default is {@link Short#MIN_VALUE}.
   *
   * @return the minimum value
   */
  public short getMinValue() {
    return minValue;
  }

  /**
   * Sets the maximum value.
   *
   * @param maxValue the maximum value
   */
  public void setMaxValue(short maxValue) {
    this.maxValue = maxValue;
  }

  /**
   * Gets the maximum value.<br>
   * The defauls is {@link Short#MAX_VALUE}.
   *
   * @return the maximum value
   */
  public short getMaxValue() {
    return maxValue;
  }


  /**
   * @return the short value, null if empty
   */
  @Override
  public Short getFormValue ()  {
    String str = getText();
    if (str != null) {
      str = str.replace(getFiller(), ' ').trim();
      if (str.length() > 0) {
        try {
          Object value = decimalFormat.parse(str);
          if (value instanceof Long) {
            if ((Long) value > maxValue) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE <= {0}"), maxValue);
            }
            else if ((Long) value < minValue) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE >= {0}"), minValue);
            }
            else {
              return ((Long) value).shortValue();
            }
          }
          else  {
            errorOffset = 0;
            errorMessage = MessageFormat.format(SwingSwingBundle.getString("INVALID NUMBER: {0}"), str);
          }
        }
        catch (ParseException e)  {
          errorOffset  = e.getErrorOffset();
          // ParseExceptions are not localized, unfortunately
          errorMessage = MessageFormat.format(SwingSwingBundle.getString("INVALID NUMBER: {0}"), str);
        }
      }
    }
    return null;
  }


  /**
   * Gets the short value.
   *
   * @return the short value, 0.0 if empty.
   */
  public short getShortValue() {
    Short value = getFormValue();
    return value == null ? 0 : value;
  }

  /**
   * Sets the short value.
   *
   * @param value the short value
   */
  public void setShortValue(short value) {
    setFormValue(value);
  }

}