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

package org.tentackle.wurblet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.wurbelizer.wurbel.WurbelException;

/**
 * An expression of {@link WurbletKeyParameter}s.
 *
 * @author harald
 */
public class WurbletParameterExpression implements WurbletParameterOperand {

  /** the parent expression. */
  private final WurbletParameterExpression parent;

  /** n operands. */
  private final List<WurbletParameterOperand> operands;

  /** n-1 operators. */
  private final List<WurbletParameterOperator> operators;

  /**
   * Creates a new expression.
   * @param parent optional parent expression, null if this is the top level expression
   */
  public WurbletParameterExpression(WurbletParameterExpression parent) {
    this.parent = parent;
    this.operands = new ArrayList<>();
    this.operators = new ArrayList<>();
  }

  /**
   * Gets the parent expression.
   *
   * @return the parent, null if top level
   */
  public WurbletParameterExpression getParent() {
    return parent;
  }

  /**
   * Gets the n operands.
   *
   * @return the operands
   */
  public List<WurbletParameterOperand> getOperands() {
    return operands;
  }

  /**
   * Gets the n-1 operators.
   *
   * @return the operators
   */
  public List<WurbletParameterOperator> getOperators() {
    return operators;
  }


  /**
   * Adds an operand.
   *
   * @param operator the operator, null defaults to ADD
   * @param operand the operand
   * @throws WurbelException if failed
   */
  public void addOperand(WurbletParameterOperator operator, WurbletParameterOperand operand) throws WurbelException {
    if (operand == null) {
      throw new WurbelException("operand cannot be null");
    }
    if (operands.isEmpty()) {
      if (operator != null && operator != WurbletParameterOperator.NOT) {
        throw new WurbelException("operator " + operator + " not allowed at start of expression");
      }
    }
    else  {
      if (operator == null) {
        operator = WurbletParameterOperator.AND;
      }
      else {
        if (operator == WurbletParameterOperator.NOT) {
          if (!(operand instanceof WurbletParameterExpression)) {
            throw new WurbelException("operator NOT must be followed by an expression");
          }
          if (!operands.isEmpty()) {
            throw new WurbelException("operator NOT cannot follow an operand");
          }
        }
      }
    }
    if (operator != null) {
      operators.add(operator);
    }
    operands.add(operand);
  }


  /**
   * Returns whether expression must be enclosed in parentheses after an AND operator.
   *
   * @return
   */
  public boolean needParenthesesAfterAndOperator() {
    return operators.contains(WurbletParameterOperator.OR) ||
           operators.contains(WurbletParameterOperator.ORNOT);
  }


  /**
   * Generates the code.
   *
   * @param generator the code generator
   * @return the generated code
   */
  public String toCode(CodeGenerator<Object> generator) throws WurbelException {
    StringBuilder buf = new StringBuilder();
    Iterator<WurbletParameterOperand> operandIter = operands.iterator();
    Iterator<WurbletParameterOperator> operatorIter = operators.iterator();
    if (!operators.isEmpty() && operands.size() == operators.size()) {
      buf.append(generator.generate(operatorIter.next()));
    }
    while (operandIter.hasNext()) {
      buf.append(generator.generate(operandIter.next()));
      if (operatorIter.hasNext()) {
        buf.append(generator.generate(operatorIter.next()));
      }
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    try {
      return toCode((t) -> {
        if (t instanceof WurbletParameterOperator) {
          WurbletParameterOperator operator = (WurbletParameterOperator) t;
          if (operator == WurbletParameterOperator.NOT) {
            return t + " ";
          }
          return " " + t + " ";
        }
        if (t instanceof WurbletParameterExpression) {
          return "(" + t + ")";
        }
        return t.toString();
      });
    }
    catch (WurbelException ex) {
      return ex.getMessage();
    }
  }

}
