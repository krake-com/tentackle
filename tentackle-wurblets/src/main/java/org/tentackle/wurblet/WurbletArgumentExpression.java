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

package org.tentackle.wurblet;

import org.wurbelizer.wurbel.WurbelException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An expression of wurblet arguments or other expressions.
 *
 * @author harald
 */
public class WurbletArgumentExpression implements WurbletArgumentOperand {

  /** the parent expression. */
  private final WurbletArgumentExpression parent;

  /** n operands. */
  private final List<WurbletArgumentOperand> operands;

  /** n-1 operators. */
  private final List<WurbletArgumentOperator> operators;

  /** merged relation paths. */
  private List<JoinPath> mergedPaths;


  /**
   * Creates a new expression.
   * @param parent optional parent expression, null if this is the top level expression
   */
  public WurbletArgumentExpression(WurbletArgumentExpression parent) {
    this.parent = parent;
    this.operands = new ArrayList<>();
    this.operators = new ArrayList<>();
  }

  /**
   * Gets the parent expression.
   *
   * @return the parent, null if top level
   */
  public WurbletArgumentExpression getParent() {
    return parent;
  }

  /**
   * Gets the n operands.
   *
   * @return the operands
   */
  public List<WurbletArgumentOperand> getOperands() {
    return operands;
  }

  /**
   * Gets the n-1 operators.
   *
   * @return the operators
   */
  public List<WurbletArgumentOperator> getOperators() {
    return operators;
  }

  /**
   * Gets the paths for wurblet arguments which can be expressed in a single SQL EXISTS clause.<br>
   * The returned list is empty, if there are no paths at all or each argument needs
   * its own EXISTS clause.
   *
   * @return the paths, empty if none, never null
   */
  public List<JoinPath> getMergedPaths() {
    if (mergedPaths == null) {
      List<WurbletArgument> pathArgs = new ArrayList<>();
      for (WurbletArgumentOperand oper: operands) {
        if (oper instanceof WurbletArgument arg) {
          if (arg.isPath()) {
            pathArgs.add(arg);
          }
        }
      }
      if (needParenthesesAfterAndOperator()) {
        // cannot merge (expression contains OR operators)
        mergedPaths = new ArrayList<>();
      }
      else {
        // merge as much as possible (only AND operators)
        mergedPaths = JoinPathFactory.getInstance().createPaths(pathArgs);
      }
    }
    return mergedPaths;
  }


  /**
   * Adds an operand.
   *
   * @param operator the operator, null defaults to ADD
   * @param operand the operand
   * @return the operator, null if start of expression
   * @throws WurbelException if failed
   */
  public WurbletArgumentOperator addOperand(WurbletArgumentOperator operator, WurbletArgumentOperand operand)
         throws WurbelException {
    if (operand == null) {
      throw new WurbelException("operand cannot be null");
    }
    if (operands.isEmpty()) {
      if (operator != null && operator != WurbletArgumentOperator.NOT) {
        throw new WurbelException("operator " + operator + " not allowed at start of expression");
      }
    }
    else  {
      if (operator == null) {
        operator = WurbletArgumentOperator.AND;
      }
      else {
        if (operator == WurbletArgumentOperator.NOT && !(operand instanceof WurbletArgumentExpression)) {
          throw new WurbelException("operator NOT must be followed by an expression");
        }
      }
    }
    if (operator != null) {
      operators.add(operator);
    }
    operands.add(operand);
    return operator;
  }


  /**
   * Returns whether expression must be enclosed in parentheses after an AND operator.
   *
   * @return true need parentheses
   */
  public boolean needParenthesesAfterAndOperator() {
    return operators.contains(WurbletArgumentOperator.OR) ||
           operators.contains(WurbletArgumentOperator.ORNOT);
  }


  /**
   * Generates the code.
   *
   * @param generator the code generator
   * @return the generated code
   * @throws WurbelException if code generation failed
   */
  public String toCode(CodeGenerator<Object> generator) throws WurbelException {
    StringBuilder buf = new StringBuilder();
    Iterator<WurbletArgumentOperand> operandIter = operands.iterator();
    Iterator<WurbletArgumentOperator> operatorIter = operators.iterator();
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
      return toCode(t -> {
        if (t instanceof WurbletArgumentOperator operator) {
          if (operator == WurbletArgumentOperator.NOT) {
            return t + " ";
          }
          return " " + t + " ";
        }
        if (t instanceof WurbletArgumentExpression) {
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
