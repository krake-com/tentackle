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


/**
 * Long Print Label.<br>
 * Used to print any integer values (from byte to long).
 *
 * @author harald
 */

@SuppressWarnings("serial")
public class LongPrintLabel extends NumberPrintLabel {


  /**
   * Creates a long print label.
   * 
   * @param columns the width in columns, 0 = minimum width
   */
  public LongPrintLabel (int columns)  {
    super(columns);
  }
  
  /**
   * Creates a long print label with minimum width.
   */
  public LongPrintLabel () {
    this (0);
  }

  
  // ------------------- implements NumberPrintLabel -----------------------

  @Override
  public String doFormat(Object number) {
    if (number instanceof Number) {
      long lval = ((Number)number).longValue();
      if (!isBlankZero() || lval != 0L)  {
        return format.format(lval);
      }
    }
    return null;
  }
  
}