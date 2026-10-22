/*
 * Tentackle - https://tentackle.org
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

import org.tentackle.common.BMoney;
import org.tentackle.common.Constants;
import org.tentackle.common.DMoney;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.misc.FormatHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.function.Function;


/**
 * BMoney translator.<br>
 * Applies to {@link DMoney} as well.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = BMoney.class, viewClass = String.class)
public class BMoneyStringTranslator extends FractionNumberStringTranslator<BMoney> {

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
    return this::parse;
  }

  @Override
  public String getDefaultPattern() {
    return FormatHelper.getMoneyPattern();
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the decimal separator is missing and {@link FxUtilities#isLenientMoneyInput(FxTextComponent)} is enabled,
   * it is inserted according to the component's scale.
   * Unlike the other number translators, parsing errors are only set in the component and null is returned.
   *
   * @return the money value, null if the string is null or parsing failed
   */
  @Override
  protected BMoney parse(String str) {
    if (str != null) {
      DecimalFormat decimalFormat = getFormat();
      char commaChar = decimalFormat.getDecimalFormatSymbols().getDecimalSeparator();
      boolean parseDMoney = DMoney.class.isAssignableFrom(getComponent().getType());
      // check if decimal sep
      if (getComponent().getScale() > 0 && str.indexOf(commaChar) < 0 &&        // decimal separator missing?
          FxUtilities.getInstance().isLenientMoneyInput(getComponent())) {      // insert automatically?
        // insert decimal separator according to scale
        int len = str.length();
        int scale = getComponent().getScale();
        int lead = scale - len + 1;
        if (lead > 0) {
          str = "0".repeat(lead) + str;
          len += lead;
        }
        str = str.substring(0, len - scale) + commaChar + str.substring(len - scale);
      }

      try {
        // convert
        decimalFormat.setParseBigDecimal(true);
        BigDecimal value = (BigDecimal) decimalFormat.parse(str);
        value = value.setScale(getComponent().getScale(), RoundingMode.HALF_UP);
        BMoney money = null;

        if (parseDMoney) {
          if (value.precision() > Constants.DMONEY_DIGITS) {
            getComponent().setError(MessageFormat.format(
                    FxFxBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.DMONEY_DIGITS));
            getComponent().setErrorTemporary(true);
          }
          else {
            money = new DMoney(value);
          }
        }
        else {
          if (value.precision() > Constants.BMONEY_DIGITS) {
            getComponent().setError(MessageFormat.format(
                    FxFxBundle.getString("MONEY VALUE MUST NOT EXCEED {0} DIGITS"), Constants.BMONEY_DIGITS));
            getComponent().setErrorTemporary(true);
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
        getComponent().setErrorTemporary(true);
      }
    }
    return null;
  }

}
