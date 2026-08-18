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

/**
 * A named numeric variable passed to a {@link NumericEvaluator}.
 *
 * @param <T> the numeric datatype of the value
 * @author harald
 */
public record NumericVariable<T extends Number>(String name, T value) {

  /**
   * Creates a numeric argument.
   *
   * @param name  the variable name as referenced in the expression, never null
   * @param value the variable's value, never null
   */
  public NumericVariable {
    if (name == null) {
      throw new NullPointerException("variable name must not be null");
    }
    if (value == null) {
      throw new NullPointerException("value of variable '" + name + "' must not be null");
    }
  }

  @Override
  public String toString() {
    return name + "=" + value;
  }

}
