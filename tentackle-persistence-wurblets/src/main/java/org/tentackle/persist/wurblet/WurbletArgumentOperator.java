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

package org.tentackle.persist.wurblet;

/**
 * Logical operator for {@link WurbletArgumentExpression}.
 *
 * @author harald
 */
public enum WurbletArgumentOperator {

  /** logical AND. */
  AND("AND", "SQL_AND"),

  /** logical OR. */
  OR("OR", "SQL_OR"),

  /** logical NOT at the beginning of an expression. */
  NOT("NOT", "SQL_NOT"),

  /** AND NOT combined as a single pseudo-operator. */
  ANDNOT("AND NOT", "SQL_ANDNOT"),

  /** OR NOT combined as a single pseudo-operator. */
  ORNOT("OR NOT", "SQL_ORNOT");


  /** The text. */
  private final String text;

  /** the backend SQL_ code. */
  private final String sql;


  /**
   * Creates an operator.
   *
   * @param text the operator text
   */
  WurbletArgumentOperator(String text, String sql) {
    this.text = text;
    this.sql = sql;
  }


  /**
   * Gets the sql text.
   *
   * @return the text
   */
  public String getText() {
    return text;
  }

  /**
   * Gets the Backend.SQL_... code.
   *
   * @return the code to use in generated Java SQL code
   */
  public String getSql() {
    return sql;
  }


  @Override
  public String toString() {
    return getText();
  }


  /**
   * Determines the operator from a string.<br>
   * Case doesn't matter.
   *
   * @param str the string
   * @return the operator, null if not an operator
   */
  public static WurbletArgumentOperator toInternal(String str) {
    WurbletArgumentOperator operator = null;
    if (str != null) {
      for (WurbletArgumentOperator value : values()) {
        if (value.getText().equalsIgnoreCase(str)) {
          operator = value;
          break;
        }
      }
    }
    return operator;
  }

}
