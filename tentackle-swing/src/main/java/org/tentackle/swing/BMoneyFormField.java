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

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Currency;
import java.util.Locale;
import javax.swing.text.Document;
import org.tentackle.common.BMoney;
import org.tentackle.common.Constants;
import org.tentackle.common.DMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.misc.FormatHelper;
import org.tentackle.swing.bind.BMoneyFormFieldBinding;



/**
 * FormField to edit a BMoney object.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class BMoneyFormField extends AbstractFractionNumberFormField {

  static {
    // register the special binding due to DMoney/BMoney dual use
    FormUtilities.getInstance().getBindingFactory().setFormComponentBindingClass(
            BMoneyFormField.class, BMoneyFormFieldBinding.class);
  }

  /** application-wide default setting for the auto-comma feature **/
  public static boolean defaultAutoComma = false;

  private boolean autoComma;      // true if auto comma at scale
  private char commaChar;         // the comma character
  private boolean parseDMoney;    // true if return DMoney instead of BMoney
  private BMoney minValue;        // minimum value, null if none
  private BMoney maxValue;        // maximum value, null if none


  /**
   * Creates an empty BMoneyFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   * @param parseDMoney true if getFormValue() returns DMoney, false if BMoney
   */
  public BMoneyFormField (Document doc, int columns, boolean parseDMoney) {
    super (doc, columns);
    this.parseDMoney = parseDMoney;
    autoComma = defaultAutoComma;
    decimalFormat.applyPattern(FormatHelper.getMoneyPattern());    // generate default decimalFormat
    // set the commaChar and the scale from the current locale
    commaChar = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator();
    setScale(Currency.getInstance(LocaleProvider.getInstance().getLocale()).getDefaultFractionDigits());
  }


  /**
   * Creates an empty BMoneyFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   * @param parseDMoney true if getFormValue() returns DMoney, false if BMoney
   */
  public BMoneyFormField (int columns, boolean parseDMoney)  {
    this (null, columns, parseDMoney);
  }


  /**
   * Creates an empty BMoneyFormField with the default document model,
   * and minimum column width.<br>
   *
   * @param parseDMoney true if getFormValue() returns DMoney, false if BMoney
   */
  public BMoneyFormField (boolean parseDMoney) {
    this (0, parseDMoney);
  }


  /**
   * Creates an empty BMoneyFormField with the default document model,
   * a minimum column width for a BMoney.<br>
   */
  public BMoneyFormField () {
    this (false);
  }


  /**
   * Sets the minimum value.<br>
   *
   * @param minValue the minimum value, null if none
   */
  public void setMinValue(BMoney minValue) {
    this.minValue = minValue;
  }

  /**
   * Gets the minimum value.
   *
   * @return the minimum value, null if none
   */
  public BMoney getMinValue() {
    return minValue;
  }

  /**
   * Sets the maximum value.
   *
   * @param maxValue the maximum value, null if none
   */
  public void setMaxValue(BMoney maxValue) {
    this.maxValue = maxValue;
  }

  /**
   * Gets the maximum value.
   *
   * @return the maximum value
   */
  public BMoney getMaxValue() {
    return maxValue;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the scale from the money value
   */
  @Override
  public void setFormValue (Object object) {
    if (object instanceof BMoney) {
      setScale(((BMoney)object).scale());   // set scale!
    }
    super.setFormValue(object);
  }


  /**
   * Parses the money string.
   *
   * @param parseDMoney true if returned value is a DMoney, else BMoney
   * @return the parsed value
   */
  protected BMoney getFormValue(boolean parseDMoney) {
    String str = getText();
    if (str != null) {
     str = str.replace(getFiller(), ' ').trim();
      if (str.length() > 0) {
        // check for autocomma if no comma in input
        if (autoComma && getScale() > 0 && str.indexOf(commaChar) < 0)  {
          // insert comma
          int len = str.length();
          int prec = getScale();
          while (len <= prec) {
            str = "0" + str;
            len++;
          }
          str = str.substring(0, len - prec) + commaChar + str.substring(len - prec);
        }
        try {
          // convert
          decimalFormat.setParseBigDecimal(true);
          BigDecimal value = (BigDecimal) decimalFormat.parse(str);
          value = value.setScale(getScale(), BigDecimal.ROUND_HALF_UP);
          BMoney money = null;

          if (parseDMoney) {
            if (value.precision() > Constants.DMONEY_DIGITS) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.DMONEY_DIGITS);
            }
            else  {
              money = new DMoney(value);
            }
          }
          else  {
            if (value.precision() > Constants.BMONEY_DIGITS) {
              errorOffset = 0;
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.BMONEY_DIGITS);
            }
            else  {
              money = new BMoney(value);
            }
          }

          if (errorOffset < 0 && money != null) {
            // check min/max
            if (minValue != null && money.compareTo(minValue) < 0) {
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("MONEY VALUE MUST BE_>= {0}"), minValue);
            }
            else if (maxValue != null && money.compareTo(maxValue) > 0) {
              errorMessage = MessageFormat.format(SwingSwingBundle.getString("MONEY VALUE MUST BE <= {0}"), maxValue);
            }
            else  {
              return money;
            }
          }
          else  {
            return money;
          }
        }
        catch (ParseException e)  {
          errorOffset  = e.getErrorOffset();
          // ParseExceptions are not localized, unfortunately
          errorMessage = MessageFormat.format(SwingSwingBundle.getString("INVALID MONEY VALUE: {0}"), str);
        }
      }
    }
    return null;
  }

  @Override
  public BMoney getFormValue() {
    return getFormValue(parseDMoney);
  }

  /**
   * Gets the value as a DMoney.
   *
   * @return the money value or null if field is empty
   */
  public DMoney getDMoney() {
    return (DMoney) getFormValue(true);
  }


  /**
   * Sets whether getFormValue() returns BMoney or DMoney.
   *
   * @param parseDMoney true if parse to DMoney, else BMoney
   */
  public void setParseDMoney(boolean parseDMoney) {
    this.parseDMoney = parseDMoney;
  }

  /**
   * Gets the flag whether getFormValue() will return DMoney or BMoney.
   *
   * @return true if DMoney, else BMoney (default)
   */
  public boolean isParseDMoney() {
    return parseDMoney;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden cause of comma-char.
   */
  @Override
  public void setFormat(String pattern) {
    super.setFormat(pattern);
    commaChar = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator();
  }


  /**
   * Sets the auto-comma feature.<br>
   * Allows "business" keyboard input, i.e. comma is set according to the
   * property scale if no comma has been entered by the user.<br>
   * <pre>
   * Example: "1122" -&gt; "11.22" if scale was 2.
   * </pre>
   *
   * @param autoComma true if automatically set the comma, false if not (default)
   * @see #defaultAutoComma
   */
  public void setAutoComma(boolean autoComma)  {
    this.autoComma = autoComma;
  }

  /**
   * Returns the auto-comma setting.
   * @return true if automatically set the comma according to scale
   */
  public boolean isAutoComma() {
    return autoComma;
  }

}
