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
import org.tentackle.misc.FormatHelper;


/**
 * FormField to edit a double value.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class DoubleFormField extends AbstractFractionNumberFormField {

  private double minValue = -Double.MAX_VALUE;
  private double maxValue = Double.MAX_VALUE;


  /**
   * Creates an empty DoubleFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   */
  public DoubleFormField (Document doc, int columns) {
    super (doc, columns);
    decimalFormat.applyPattern(FormatHelper.getFloatingNumberPattern());  // generate default decimalFormat
  }


  /**
   * Creates an empty DoubleFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public DoubleFormField (int columns)  {
    this (null, columns);
  }


  /**
   * Creates an empty DoubleFormField with the default document model,
   * a minimum column width.<br>
   */
  public DoubleFormField () {
    this (0);
  }


  /**
   * Sets the minimum value.<br>
   *
   * @param minValue the minimum value
   */
  public void setMinValue(double minValue) {
    this.minValue = minValue;
  }

  /**
   * Gets the minimum value.<br>
   * The default is {@link Double#MIN_VALUE}.
   *
   * @return the minimum value
   */
  public double getMinValue() {
    return minValue;
  }

  /**
   * Sets the maximum value.
   *
   * @param maxValue the maximum value
   */
  public void setMaxValue(double maxValue) {
    this.maxValue = maxValue;
  }

  /**
   * Gets the maximum value.<br>
   * The defauls is {@link Double#MAX_VALUE}.
   *
   * @return the maximum value
   */
  public double getMaxValue() {
    return maxValue;
  }


  /**
   * @return the double value, null if empty
   */
  @Override
  public Double getFormValue ()  {
    String str = getText();
    if (str != null) {
      str = str.replace(getFiller(), ' ').trim();
      if (str.length() > 0) {
        try {
          Object value = decimalFormat.parse(str);
          if (value instanceof Long) {
            value = ((Long) value).doubleValue();
          }
          if (value instanceof Double) {
            if ((Double)value > maxValue) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE <= {0}"), maxValue);
            }
            else if ((Double)value < minValue) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE >= {0}"), minValue);
            }
            else {
              return (Double) value;
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
   * Gets the double value.
   *
   * @return the double value, 0.0 if empty.
   */
  public double getDoubleValue() {
    Double value = getFormValue();
    return value == null ? 0.0f : value;
  }

  /**
   * Sets the double value.
   *
   * @param value the double value
   */
  public void setDoubleValue (double value) {
    setFormValue(value);
  }

}