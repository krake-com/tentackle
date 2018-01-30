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

import org.tentackle.misc.FormatHelper;


/**
 * Printing bean for float and double values.<br>
 * Used to print also floats.
 *
 * @author harald
 */

@SuppressWarnings("serial")
public class DoublePrintLabel extends NumberPrintLabel {

  /**
   * Creates a double print label.
   *
   * @param columns the width in columns, 0 = minimum width
   */
  public DoublePrintLabel (int columns) {
    super (columns);
    format.applyPattern(FormatHelper.getFloatingNumberPattern());
  }

  /**
   * Creates a double print label with minimum width.
   */
  public DoublePrintLabel () {
    this (0);
  }


  // ------------------- implements NumberPrintLabel -----------------------

  @Override
  public String doFormat(Object value) {
    if (value instanceof Number) {
      double dval = ((Number)value).doubleValue();
      if (!isBlankZero() || dval != 0.0) {
        return format.format(dval);
      }
    }
    return null;
  }

}