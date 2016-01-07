/*
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

package org.tentackle.fx.translate;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.function.Function;
import org.tentackle.common.BMoney;
import org.tentackle.common.Constants;
import org.tentackle.common.DMoney;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.misc.FormatHelper;


/**
 * BMoney translator.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = BMoney.class, viewClass = String.class)
public class BMoneyStringTranslator extends FractionNumberStringTranslator<BMoney> {

  /**
   * Application wide setting for money input without comma.
   */
  public static boolean autoComma = false;


  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public BMoneyStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public Function<BMoney, String> toViewFunction() {
    return v -> v == null ? null : getFormat().format(v);
  }

  @Override
  public Function<String, BMoney> toModelFunction() {
    return s -> parse(s);
  }

  @Override
  public String getDefaultPattern() {
    return FormatHelper.getMoneyPattern();
  }

  @Override
  protected BMoney parse(String str) {
    if (str != null) {

      getComponent().setErrorOffset(null);
      getComponent().setError(null);

      str = str.replace(getComponent().getFiller(), ' ').trim();
      int slen = str.length();

      if (slen == 0) {
        return null;
      }

      DecimalFormat decimalFormat = getFormat();
      char commaChar = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator();
      boolean parseDMoney = DMoney.class.isAssignableFrom(getComponent().getType());
      // check for autocomma if no comma in input
      if (autoComma && getComponent().getScale() > 0 && str.indexOf(commaChar) < 0) {
        // insert comma
        int len = str.length();
        int prec = getComponent().getScale();
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
        value = value.setScale(getComponent().getScale(), BigDecimal.ROUND_HALF_UP);
        BMoney money = null;

        if (parseDMoney) {
          if (value.precision() > Constants.DMONEY_DIGITS) {
            getComponent().setError(MessageFormat.format(
                    FxFxBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.DMONEY_DIGITS));
          }
          else {
            money = new DMoney(value);
          }
        }
        else {
          if (value.precision() > Constants.BMONEY_DIGITS) {
            getComponent().setError(MessageFormat.format(
                    FxFxBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.BMONEY_DIGITS));
          }
          else {
            money = new BMoney(value);
          }
        }

        return money;
      }
      catch (ParseException e) {
        getComponent().setErrorOffset(e.getErrorOffset());
        // ParseExceptions are not localized, unfortunately
        getComponent().setError(MessageFormat.format(FxFxBundle.getString("INVALID MONEY VALUE: {0}"), str));
      }
    }
    return null;
  }

}
