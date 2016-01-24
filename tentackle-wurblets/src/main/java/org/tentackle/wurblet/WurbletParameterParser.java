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
import java.util.LinkedList;
import java.util.List;
import org.tentackle.common.BasicStringHelper;
import org.wurbelizer.wurbel.WurbelException;

/**
 * Parses the wurblet parameters.
 * <p>
 * The list of args from the wurblet container consist of:
 * <ul>
 * <li>wurblet options (starting with --)</li>
 * <li>a logical expression of parameters used for the WHERE clause</li>
 * <li>optional extra parameters, e.g. for columns to be updated</li>
 * <li>optional sorting parameters</li>
 * <li>optional joins</li>
 * </ul>
 *
 * The expression evaluation is terminated either by the end of the argument list or a group
 * separator or the first sorting key.<br>
 * A sorting key is described by a + (for ascending) or - (for descending) immediately (i.e. without spaces) followd by an attribute.<br>
 * All other parameters outside the expression are considered as extra parameters. Expressions end implicitly at the
 * first sorting key or join or explicitly by a pipe-character |.<br>
 * Joins are relation names prepended by an asterisk.
 * <p>
 * An expression consists of operands (which may be parameters or nested expressions) connected
 * via logical operators. There are 3 kinds of operators:
 * <ol>
 * <li><code>AND</code></li>
 * <li><code>OR</code></li>
 * <li><code>NOT</code></li>
 * </ol>
 * The default operator, if missing, is <code>AND</code>.<br>
 * Some examples:
 * <pre>
 * fee fie
 * (foe or foo) and (voice or plugh)
 * ((fee or fie) and foe) or foo
 * </pre>
 * A complete wurblet example:
 * <pre>
 * &#64;wurblet selectUpTo PdoSelectList --remote processed:=:null or processed:&gt;= +id *address
 * </pre>
 *
 * @author harald
 */
public class WurbletParameterParser {

  /** list of wurblet args starting with -- (-- cut off). */
  private final List<String> optionArgs;

  /** the parsed expression. */
  private final WurbletParameterExpression expression;



  /** the list of all parameters in the expression. */
  private final List<WurbletParameter> expressionParameters;

  /** sorting parameters. */
  private final List<WurbletParameter> sortingParameters;

  /** extra parameters after group separator. */
  private final List<WurbletParameter> extraParameters;

  /** method parameters. */
  private List<WurbletParameter> methodParameters;

  /** all parameters. */
  private List<WurbletParameter> allParameters;

  /** the join names. */
  private final List<String> joinNames;


  /**
   * Creates a parser.
   *
   * @param args the wurblet arguments
   * @throws org.wurbelizer.wurbel.WurbelException if parsing failed
   */
  public WurbletParameterParser(List<String> args) throws WurbelException {
    optionArgs = new ArrayList<>();
    expression = new WurbletParameterExpression(null);
    expressionParameters = new ArrayList<>();
    sortingParameters = new ArrayList<>();
    joinNames = new ArrayList<>();
    extraParameters = new ArrayList<>();
    parse(args);
  }

  /**
   * Gets the options.
   *
   * @return the options
   */
  public List<String> getOptionArgs() {
    return optionArgs;
  }

  /**
   * Gets the where expression.
   *
   * @return the expression
   */
  public WurbletParameterExpression getExpression() {
    return expression;
  }

  /**
   * Gets all parameters part of the expression.
   *
   * @return the expression parameters
   */
  public List<WurbletParameter> getExpressionParameters() {
    return expressionParameters;
  }

  /**
   * Gets the sorting parameters.
   *
   * @return the sorting parameters
   */
  public List<WurbletParameter> getSortingParameters() {
    return sortingParameters;
  }

  /**
   * Gets the extra parameters.<br>
   * Those are not part of the expression and not sorting parameters.
   *
   * @return the extra parameters
   */
  public List<WurbletParameter> getExtraParameters() {
    return extraParameters;
  }

  /**
   * Gets all parameters.
   *
   * @return all parameters
   */
  public List<WurbletParameter> getAllParameters() {
    if (allParameters == null) {
      allParameters = new ArrayList<>(expressionParameters.size() + extraParameters.size() + sortingParameters.size());
      allParameters.addAll(expressionParameters);
      allParameters.addAll(extraParameters);
      allParameters.addAll(sortingParameters);
    }
    return allParameters;
  }

  /**
   * Gets the method parameters.
   *
   * @return all parameters
   */
  public List<WurbletParameter> getMethodParameters() {
    if (methodParameters == null) {
      methodParameters = new ArrayList<>(extraParameters.size() + expressionParameters.size());
      methodParameters.addAll(extraParameters);
      methodParameters.addAll(expressionParameters);
    }
    return methodParameters;
  }

  /**
   * Gets the relation names of the joins.
   *
   * @return the join names
   */
  public List<String> getJoinNames() {
    return joinNames;
  }



  /**
   * Parses the source.
   *
   * @param args the argumenys
   * @throws WurbelException if parsing failed
   */
  private void parse(List<String> args) throws WurbelException {

    int expressionLevel = 0;                        // no expression started yet
    LinkedList<WurbletParameterOperator> operators = new LinkedList<>();   // stacked operators
    WurbletParameterOperator operator = null;       // last operator
    WurbletParameterExpression expr = expression;   // current expression
    boolean expressionFinished = false;             // true if expression evaluation finished

    for (String arg : args) {
      if (arg != null) {
        if (arg.startsWith("--")) {
          optionArgs.add(arg.substring(2));
        }
        else if (arg.length() > 1 && arg.startsWith("*")) {
          joinNames.add(arg.substring(1));
          expressionFinished = true;
        }
        else {
          if (expressionFinished) {
            WurbletParameter par = new WurbletParameter(arg);
            if (par.isSortKey()) {
              sortingParameters.add(par);
            }
            else {
              extraParameters.add(par);
            }
          }
          else {
            for (String subArg : splitArg(arg)) {
              switch (subArg) {
                case "(":
                  expr = new WurbletParameterExpression(expr);
                  operators.push(operator);
                  operator = null;
                  expressionLevel++;
                  continue;

                case ")":
                  expressionLevel--;
                  if (expressionLevel < 0) {
                    throw new WurbelException("unbalanced braces: more closed than opened");
                  }
                  WurbletParameterExpression parent = expr.getParent();
                  operator = operators.pop();
                  if (parent != null) {
                    parent.addOperand(operator, expr);
                    operator = null;
                  }
                  expr = parent;
                  continue;

                case "|":
                  if (expressionLevel == 0) {
                    expressionFinished = true;
                    continue;
                  }
                  throw new WurbelException("key group separator not allowed in nested expressions");
              }

              WurbletParameterOperator oper = WurbletParameterOperator.toInternal(subArg);
              if (oper != null) {
                if (operator != null) {
                  if (oper == WurbletParameterOperator.NOT) {
                    if (operator == WurbletParameterOperator.AND) {
                      operator = WurbletParameterOperator.ANDNOT;
                      continue;
                    }
                    else if (operator == WurbletParameterOperator.OR) {
                      operator = WurbletParameterOperator.ORNOT;
                      continue;
                    }
                  }
                  throw new WurbelException(operator + " cannot be followed by " + oper);
                }
                operator = oper;
              }
              else {
                WurbletParameter operand = new WurbletParameter(subArg);
                if (operand.isSortKey()) {
                  if (expressionLevel == 0) {
                    sortingParameters.add(operand);
                    expressionFinished = true;
                  }
                  else {
                    throw new WurbelException("sorting key " + operand + " not allowed in nested expressions");
                  }
                }
                else {
                  expr.addOperand(operator, operand);
                  operator = null;
                  expressionParameters.add(operand);
                }
              }
            }
          }
        }
      }
    }

    if (expressionLevel > 0) {
      throw new WurbelException("unbalanced braces: more opened than closed");
    }
  }

  /**
   * Separates the arg according to the braces or group separator it contains.
   * <p>
   * Braces may be quoted with a backslash.
   * The backslash itself is a double backslash.
   *
   * @param arg the arg
   * @return the braces
   */
  private List<String> splitArg(String arg) {
    List<String> strs = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    boolean quoted = false;
    char c = 0;
    char cl;
    for (int i=0; i < arg.length(); i++) {
      cl = c;
      c = arg.charAt(i);
      if (quoted) {
        buf.append(c);
        quoted = false;
      }
      else  {
        if (c == '\\') {
          quoted = true;
        }
        else  {
          if ((c == '(' && (i >= arg.length() - 1 || arg.charAt(i+1) != ')')) ||
              (c == ')' && cl != '(') ||
              c == '|') {
            String str = buf.toString().trim();
            if (!BasicStringHelper.isAllWhitespace(str)) {
              strs.add(str);
            }
            buf.setLength(0);
            buf.append(c);
            strs.add(buf.toString());
            buf.setLength(0);
          }
          else  {
            buf.append(c);
          }
        }
      }
    }
    String str = buf.toString().trim();
    if (!BasicStringHelper.isAllWhitespace(str)) {
      strs.add(str);
    }
    return strs;
  }

}
