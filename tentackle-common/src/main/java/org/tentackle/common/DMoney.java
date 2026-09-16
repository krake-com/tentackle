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

package org.tentackle.common;

import java.io.Serial;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A DMoney is the same as a BMoney but will be treated
 * differently when stored in the database.<br>
 * A BMoney is stored as a DOUBLE for the value and an INTEGER for the scale.
 * A DMoney is stored as a DECIMAL for the value and an INTEGER for the scale.
 * The DECIMAL is stored with scale 0!
 *
 * @author harald
 */
public class DMoney extends BMoney {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a DMoney value.
   *
   * @param val double-representation of the number
   * @param scale digits after comma the value should be rounded to
   */
  public DMoney (double val, int scale)   {
    super(val, scale);
  }

  /**
   * Creates a DMoney value.
   *
   * @param  val     source string
   * @param  scale   digits after comma the value should be rounded to
   */
  public DMoney (String val, int scale)  {
    super(val, scale);
  }

  /**
   * Creates a zero DMoney value.
   *
   * @param  scale digits after comma
   */
  public DMoney (int scale)  {
    super(scale);
  }

  /**
   * Creates a DMoney value from a BigInteger.<br>
   *
   * The scale sets the comma within the given integer.
   *
   * @param intVal the big integer
   * @param scale  the digits after comma
   */
  public DMoney (BigInteger intVal, int scale) {
    super(intVal, scale);
  }

  /**
   * Creates a DMoney from a BMoney (useful to rescale)
   *
   * @param val the BMoney or DMoney value
   * @param scale digits after comma the value should be rounded to
   */
  public DMoney (BMoney val, int scale) {
    super(val, scale);
  }

  /**
   * Creates a DMoney from a BigDecimal.
   *
   * @param val the decimal value
   */
  public DMoney (BigDecimal val)  {
    super(val);
  }

  /**
   * Creates a zero DMoney with a scale according to
   * the currency of the current locale.
   */
  public DMoney ()  {
    super();
  }


  /**
   * Gets the decimal value.
   *
   * @return the decimal value
   */
  public BigDecimal bigDecimalValue() {
    return movePointRight(scale());
  }

  @Override
  public DMoney add(BMoney val)  {
    return new DMoney(unscaledValue().add(alignScale(val).unscaledValue()), scale());
  }

  @Override
  public DMoney subtract(BMoney val)  {
    return new DMoney(unscaledValue().subtract(alignScale(val).unscaledValue()), scale());
  }

  @Override
  public DMoney multiply(double val)  {
    return new DMoney(doubleValue() * val, scale());
  }

  @Override
  public DMoney divide(double val)  {
    return new DMoney(doubleValue() / val, scale());
  }

  @Override
  public DMoney invert() {
    return new DMoney(unscaledValue().negate(), scale());
  }

  @Override
  public DMoney absolute()  {
    return isNegative() ? invert() : this;
  }

  @Override
  public DMoney smallestPositive()  {
    return new DMoney(new BigDecimal(new BigInteger(new byte[] { 1 })).movePointLeft(scale()));
  }

}
