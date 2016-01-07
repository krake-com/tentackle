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
import javax.swing.text.Document;
import org.tentackle.misc.FormatHelper;




/**
 * Abstract base class for all fraction number form fields.
 *
 * @author harald
 */
@SuppressWarnings("serial")
abstract public class AbstractFractionNumberFormField extends AbstractNumberFormField {

  private int scale;            // digits after comma


  /**
   * Creates a AbstractFractionNumberFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractFractionNumberFormField (Document doc, int columns) {
    super (doc, columns);
    setConvert(CONVERT_UC);   // in case of E (see below)
  }


  /**
   * Creates a AbstractFractionNumberFormField with the default document model and
   * given column width.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public AbstractFractionNumberFormField (int columns)  {
    super(columns);
  }

  /**
   * Creates an empty AbstractFractionNumberFormField with the default document model,
   * and minimum column width.<br>
   */
  public AbstractFractionNumberFormField () {
    super();
  }


  /**
   * Changes the decimalFormat according to the given scale.
   *
   * @param scale the number of digits after the comma
   */
  public void setScale(int scale) {
    if (this.scale != scale) {
      FormatHelper.setScale(decimalFormat, scale);
      this.scale = scale;
    }
  }

  /**
   * Gets the current scale.
   *
   * @return the scale
   */
  public int getScale() {
    return scale;
  }


  /**
   * Sets the decimal format.
   * <p>
   * Useful if format cannot be fully described by {@link #setFormat(java.lang.String)}.
   * @param decimalFormat the format
   */
  @Override
  public void setDecimalFormat(DecimalFormat decimalFormat) {
    super.setDecimalFormat(decimalFormat);
    scale = 0;
    String pattern = getFormat();
    int dotNdx = pattern.lastIndexOf('.');
    if (dotNdx >= 0)  {
      // count zeros after dot
      for (++dotNdx; dotNdx < pattern.length(); dotNdx++)  {
        if (pattern.charAt(dotNdx) != '0') {
          break;
        }
        scale++;
      }
    }

    // append/remove E(xponent) to valid chars depending on format
    if (pattern.indexOf('E') >= 0) {
      // format with exponent
      if (getValidChars().indexOf('E') < 0) {
        // add E
        setValidChars(getValidChars() + 'E');
      }
    }
    else  {
      // format without exponent
      if (getValidChars().indexOf('E') >= 0) {
        // remove E
        setValidChars(getValidChars().replace("E", ""));
      }
    }
  }

}
