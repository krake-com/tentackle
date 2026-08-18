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

package org.tentackle.fx.table.filter;

import org.tentackle.common.I18NText;
import org.tentackle.fx.FxFxBundle;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The operator of a column filter's condition.
 *
 * @author harald
 * @see TableColumnFilter
 */
public enum TableFilterOperator {

  /** no condition at all. */
  NONE("NO CONDITION", 0),

  /** the displayed text equals the operand. */
  EQUAL("EQUAL", 1),

  /** the displayed text does not equal the operand. */
  NOT_EQUAL("NOT EQUAL", 1),

  /** the displayed text contains the operand. */
  CONTAINS("CONTAINS", 1),

  /** the displayed text does not contain the operand. */
  NOT_CONTAINS("NOT CONTAINS", 1),

  /** the displayed text starts with the operand. */
  STARTS_WITH("STARTS WITH", 1),

  /** the displayed text ends with the operand. */
  ENDS_WITH("ENDS WITH", 1),

  /** the value is less than the operand. */
  LESS("LESS", 1),

  /** the value is less than or equal to the operand. */
  LESS_OR_EQUAL("LESS OR EQUAL", 1),

  /** the value is greater than the operand. */
  GREATER("GREATER", 1),

  /** the value is greater than or equal to the operand. */
  GREATER_OR_EQUAL("GREATER OR EQUAL", 1),

  /** the value is within the range of both operands, boundaries included. */
  BETWEEN("BETWEEN", 2),

  /** the value is outside the range of both operands, boundaries included. */
  NOT_BETWEEN("NOT BETWEEN", 2),

  /** the value is null or its displayed text is blank. */
  EMPTY("IS EMPTY", 0),

  /** the value is not null and its displayed text is not blank. */
  NOT_EMPTY("IS NOT EMPTY", 0);


  private final String bundleKey;
  private final int operandCount;

  TableFilterOperator(String bundleKey, int operandCount) {
    this.bundleKey = bundleKey;
    this.operandCount = operandCount;
  }


  /**
   * Gets the number of operands this operator requires.
   *
   * @return the operand count, 0, 1 or 2
   */
  public int getOperandCount() {
    return operandCount;
  }

  /**
   * Returns whether this operator works on the displayed text.
   *
   * @return true if textual
   */
  public boolean isTextual() {
    return this == CONTAINS || this == NOT_CONTAINS || this == STARTS_WITH || this == ENDS_WITH;
  }

  /**
   * Returns whether this operator compares the values by their natural order.
   *
   * @return true if ordering
   */
  public boolean isOrdering() {
    return this == LESS || this == LESS_OR_EQUAL || this == GREATER || this == GREATER_OR_EQUAL ||
           this == BETWEEN || this == NOT_BETWEEN;
  }

  /**
   * Gets the localized text displayed for this operator.
   *
   * @return the displayed text, never null
   */
  public String getDisplayedText() {
    return FxFxBundle.getString(bundleKey);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Returns the displayed text so that the operators can be shown in a combobox without a converter.
   */
  @Override
  public String toString() {
    return getDisplayedText();
  }


  /**
   * Gets the operators applicable to a column type.<br>
   * Textual operators are omitted for numbers, booleans and date/time types, ordering operators
   * for types that are not comparable.
   *
   * @param type the column type, null if unknown
   * @return the applicable operators, {@link #NONE} always being the first
   */
  public static List<TableFilterOperator> applicableTo(Class<?> type) {
    boolean textual = type == null ||
                      !(Number.class.isAssignableFrom(type) ||
                        Boolean.class.isAssignableFrom(type) ||
                        Date.class.isAssignableFrom(type) ||
                        java.time.temporal.Temporal.class.isAssignableFrom(type));
    boolean ordering = type != null &&
                       !Boolean.class.isAssignableFrom(type) &&
                       (Comparable.class.isAssignableFrom(type) || I18NText.class.isAssignableFrom(type));
    List<TableFilterOperator> operators = new ArrayList<>();
    for (TableFilterOperator operator : values()) {
      if (operator.isTextual() && !textual || operator.isOrdering() && !ordering) {
        continue;
      }
      operators.add(operator);
    }
    return operators;
  }

}
