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
import java.math.RoundingMode;

/**
 * Money value derived from BigDecimal.<br>
 *
 * A money value has a distinct and fixed scale that cannot change.
 *
 * @author harald
 */
public class BMoney extends BigDecimal implements Cloneable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Creates a BMoney value.
   *
   * @param val double-representation of the number
   * @param scale the digits after the comma (the value will be rounded to)
   */
  public BMoney (double val, int scale)   {
    super(BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).unscaledValue(), scale);
  }

  /**
   * Creates a BMoney value.
   *
   * @param  val     the string
   * @param  scale   digits after the comma (the value will be rounded to)
   */
  public BMoney (String val, int scale)  {
    super(new BigDecimal(val).setScale(scale, RoundingMode.HALF_UP).unscaledValue(), scale);
  }

  /**
   * Creates a zero BMoney value.
   *
   * @param  scale the digits after comma
   */
  public BMoney (int scale)  {
    super(new BigInteger(0, new byte[] {}), scale);
  }

  /**
   * Creates a BMoney value from a BigInteger.<br>
   *
   * The scale sets the comma within the given integer.
   *
   * @param intVal the big integer
   * @param scale  the digits after comma
   */
  public BMoney (BigInteger intVal, int scale) {
    super(intVal, scale);
  }


  /**
   * Creates a BMoney from a BMoney (useful to rescale)
   *
   * @param val the BMoney value
   * @param scale digits after comma the value should be rounded to
   */
  public BMoney (BMoney val, int scale) {
    super(val.setScale(scale, RoundingMode.HALF_UP).unscaledValue(), scale);
  }


  /**
   * Creates a BMoney from a BigDecimal.
   *
   * @param val the decimal value
   */
  public BMoney (BigDecimal val)  {
    super(val.unscaledValue(), val.scale());
  }


  /**
   * Creates a zero BMoney with a scale according to
   * the currency of the current locale.
   */
  public BMoney ()  {
    this(LocaleProvider.getInstance().getCurrency().getDefaultFractionDigits());
  }


  /**
   * Clones a BMoney value.
   */
  @Override
  public BMoney clone() {
    try {
      return (BMoney)super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
  }


  /**
   * Checks if this money value is negative.
   *
   * @return true if the number is negative
   */
  public boolean isNegative() {
    return signum() < 0;
  }

  /**
   * Checks if this money value is positive.
   *
   * @return true if the number is positive
   */
  public boolean isPositive() {
    return signum() > 0;
  }

  /**
   * Checks if this money value is zero.
   *
   * @return true if the number is zero
   */
  public boolean isZero() {
    return signum() == 0;
  }



  /**
   * Adds a BMoney to this value and returns a new object.
   * This object remains unchanged.
   *
   * @param val the money value to add
   * @return the sum of this and given value
   */
  public BMoney add(BMoney val)  {
    return new BMoney(unscaledValue().add(alignScale(val).unscaledValue()), scale());
  }


  /**
   * Subtracts a BMoney from this value and returns a new object.
   * This object remains unchanged.
   *
   * @param val the money value to subtract
   * @return this minus the given value
   */
  public BMoney subtract(BMoney val)  {
    return new BMoney(unscaledValue().subtract(alignScale(val).unscaledValue()), scale());
  }


  /**
   * Multiplies this BMoney by a double and returns a new object.
   * This object remains unchanged.
   *
   * @param val the double to multiply with
   * @return the product of this and given value
   */
  public BMoney multiply(double val)  {
    return new BMoney(doubleValue() * val, scale());
  }


  /**
   * Divides a BMoney by this value and returns a new object.
   * This object remains unchanged.
   *
   * @param val the money value to multiply
   * @return the quotient of this and given value
   */
  public double divide(BMoney val) {
    return doubleValue() / val.doubleValue();
  }


  /**
   * Divides a BMoney by double and returns a new object.
   * This object remains unchanged.
   *
   * @param val the value to divide this BMoney by
   * @return the quotient of this and given value
   */
  public BMoney divide(double val)  {
    return new BMoney(doubleValue() / val, scale());
  }


  /**
   * Inverts this money value.
   *
   * @return the negated money value
   */
  public BMoney invert() {
    return new BMoney(unscaledValue().negate(), scale());
  }


  /**
   * Gets the absolute value.
   *
   * @return the absolute (positive) value
   */
  public BMoney absolute()  {
    return isNegative() ? invert() : this;
  }


  /**
   * Returns the smallest positive value.<br>
   * Ex.: if the scale is 2 --&gt; 0.01 will be returned.
   *
   * @return the smallest positive value
   */
  public BMoney smallestPositive()  {
    return new BMoney(new BigDecimal(new BigInteger(new byte[] { 1 })).movePointLeft(scale()));
  }



  /**
   * Converts a money value to a string of digits as words.<br>
   * The digits after the comma are ignored.
   * Nice to print checks.
   * <pre>
   * Example: 234,77 will become "two three four"
   * </pre>
   *
   * @return the value as words
   */
  public String toWords() {
    String digits = setScale(0, RoundingMode.DOWN).toString();
    int len = digits.length();
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (int i=0; i < len; i++) {
      char c = digits.charAt(i);
      if (Character.isDigit(c)) {
        if (first) {
          first = false;
        }
        else {
          buf.append(' ');
        }
        buf.append(StringHelper.digitToString(c));
      }
    }
    return buf.toString();
  }



  /**
   * Check the scale of the given money value.
   * If it does not match the scale of this object,
   * scale it to the current scale of this object.
   *
   * @param  val the value to be checked
   * @return the re-scaled value or the value itself if no rescaling necessary
   */
  protected BMoney alignScale(BMoney val)  {
    return val.scale() == scale() ? val : new BMoney(val, scale());
  }

}
