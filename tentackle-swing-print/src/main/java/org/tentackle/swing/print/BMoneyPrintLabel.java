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


package org.tentackle.swing.print;

import java.util.Currency;
import org.tentackle.common.BMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.misc.FormatHelper;


/**
 * Printing bean for a money object.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class BMoneyPrintLabel extends NumberPrintLabel {

  /**
   * Creates a money print label.
   *
   * @param columns the width in columns, 0 = minimum width
   */
  public BMoneyPrintLabel (int columns)  {
    super (columns);
    // generate default format
    format.setGroupingUsed(true);
    format.setGroupingSize(3);
    format.applyPattern(FormatHelper.getMoneyPattern());
    setScale(Currency.getInstance(LocaleProvider.getInstance().getLocale()).getDefaultFractionDigits());
    setPrintValue(new BMoney());
  }

  /**
   * Creates a money print label with minimum width.
   */
  public BMoneyPrintLabel () {
    this (0);
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the scale from the money value
   */
  @Override
  public void setPrintValue (Object value) {
    if (value instanceof BMoney) {
      setScale(((BMoney)value).scale());   // set scale!
    }
    super.setPrintValue(value);
  }


  // ------------------- implements NumberPrintLabel -----------------------

  @Override
  public String doFormat(Object value) {
    if (value instanceof BMoney &&
        (!isBlankZero() || !((BMoney)value).isZero()))  {
      return format.format(((BMoney)value).doubleValue());
    }
    return null;
  }

}