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

import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Parses the wurblet arguments.<br>
 * The parser analyzes all regular wurblet arguments, i.e. without the options (starting with --).
 * The arguments consist of:
 * <ul>
 * <li>a logical expression describing the WHERE clause</li>
 * <li>optional extra arguments, e.g. for attributes to be updated</li>
 * <li>optional sorting criteria</li>
 * <li>optional load joins</li>
 * </ul>
 *
 * The expression ends implicitly at the first sorting criteria or load join or explicitly at the pipe-character |.
 * <p>
 * An expression consists of operands (which may be arguments or nested expressions) connected
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
public class WurbletArgumentParser {

  /** the entity the wurblet is applied to. */
  private final Entity entity;

  /** true if pipe character separates expression from extra arguments. */
  private final boolean argumentGroupingEnabled;

  /** the parsed expression. */
  private final WurbletArgumentExpression expression;

  /** the list of all arguments that are part of the expression. */
  private final List<WurbletArgument> expressionArguments;

  /** sorting arguments. */
  private final List<WurbletArgument> sortingArguments;

  /** extra arguments after group separator. */
  private final List<WurbletArgument> extraArguments;

  /** the load joins. */
  private final List<WurbletArgument> joinArguments;

  /** method arguments (expression + extra args). */
  private List<WurbletArgument> methodArguments;

  /** all arguments. */
  private List<WurbletArgument> allArguments;

  /** paths for join arguments. */
  private List<JoinPath> joinPaths;

  /** last wurblet argument index. */
  private int parIndex = 0;


  /**
   * Creates a parser.
   *
   * @param entity the entity the wurblet is applied to
   * @param argumentGroupingEnabled true if pipe character separates expression from extra arguments
   * @param arguments the wurblet arguments
   * @throws WurbelException if parsing failed
   */
  public WurbletArgumentParser(Entity entity, boolean argumentGroupingEnabled, List<String> arguments)
         throws WurbelException {

    this.entity = entity;
    this.argumentGroupingEnabled = argumentGroupingEnabled;

    expression = new WurbletArgumentExpression(null);
    expressionArguments = new ArrayList<>();
    sortingArguments = new ArrayList<>();
    joinArguments = new ArrayList<>();
    extraArguments = new ArrayList<>();

    parse(arguments);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(entity).append(": ");
    boolean needComma = false;
    for (WurbletArgument arg: getAllArguments()) {
      if (needComma) {
        buf.append(", ");
      }
      else {
        needComma = true;
      }
      buf.append(arg);
    }
    return buf.toString();
  }


  /**
   * Gets the entity the wurblet is applied to.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the where expression.
   *
   * @return the expression
   */
  public WurbletArgumentExpression getExpression() {
    return expression;
  }

  /**
   * Gets all arguments part of the expression.
   *
   * @return the expression arguments
   */
  public List<WurbletArgument> getExpressionArguments() {
    return expressionArguments;
  }

  /**
   * Gets the extra arguments.<br>
   * Those are not part of the expression and not sorting arguments.
   *
   * @return the extra arguments
   */
  public List<WurbletArgument> getExtraArguments() {
    return extraArguments;
  }

  /**
   * Gets the sorting arguments.
   *
   * @return the sorting arguments
   */
  public List<WurbletArgument> getSortingArguments() {
    return sortingArguments;
  }

  /**
   * Gets the join arguments.
   *
   * @return the joins
   */
  public List<WurbletArgument> getJoinArguments() {
    return joinArguments;
  }

  /**
   * Gets the consolidated relation paths for the join arguments.
   *
   * @return the join paths
   */
  public List<JoinPath> getJoinPaths() {
    if (joinPaths == null) {
      joinPaths = JoinPathFactory.getInstance().createPaths(joinArguments);
    }
    return joinPaths;
  }

  /**
   * Gets the method arguments.
   *
   * @return all arguments
   */
  public List<WurbletArgument> getMethodArguments() {
    if (methodArguments == null) {
      methodArguments = new ArrayList<>(extraArguments.size() + expressionArguments.size());
      methodArguments.addAll(extraArguments);
      methodArguments.addAll(expressionArguments);
    }
    return methodArguments;
  }

  /**
   * Gets all arguments.
   *
   * @return all arguments
   */
  public List<WurbletArgument> getAllArguments() {
    if (allArguments == null) {
      getMethodArguments();
      allArguments = new ArrayList<>(methodArguments.size() + sortingArguments.size() + joinArguments.size());
      allArguments.addAll(methodArguments);
      allArguments.addAll(sortingArguments);
      allArguments.addAll(joinArguments);
    }
    return allArguments;
  }


  /**
   * Creates a wurblet argument.<p>
   *
   * @param arg the argument string
   * @param expressionFinished true if expression is finished
   * @param argumentGroupingEnabled true if argument grouping is enabled
   * @return the created argument
   *
   * @throws WurbelException if parsing arg failed
   */
  public WurbletArgument createArgument(String arg, boolean expressionFinished, boolean argumentGroupingEnabled)
         throws WurbelException {
    return new WurbletArgument(entity, ++parIndex, arg, expressionFinished, argumentGroupingEnabled);
  }


  /**
   * Parses the source.
   *
   * @param args the arguments
   * @throws WurbelException if parsing failed
   */
  private void parse(List<String> args) throws WurbelException {

    int expressionLevel = 0;                        // no expression started yet
    LinkedList<WurbletArgumentOperator> operators = new LinkedList<>();   // stacked operators
    WurbletArgumentOperator lastOperator = null;    // last operator
    WurbletArgument lastExistsArg = null;           // last arg in EXISTS-clause
    WurbletArgumentExpression expr = expression;    // current expression
    boolean expressionFinished = false;             // true if expression evaluation finished
    Set<Relation> existsRelations = null;           // exists relations found so far, if not null
    Set<Entity> existsComponents = null;            // exists components found so far, if not null
    List<WurbletArgument> existsArguments = null;   // arguments sharing the current EXISTS-clause

    for (String arg : args) {
      if (arg != null) {
        if (expressionFinished) {
          WurbletArgument par = createArgument(arg, true, argumentGroupingEnabled);
          switch(par.getArgumentType()) {
            case SORT:
              sortingArguments.add(par);
              break;
            case JOIN:
              joinArguments.add(par);
              break;
            default:
              extraArguments.add(par);
          }
        }
        else {
          for (String subArg : splitArg(arg)) {
            boolean match = false;
            switch (subArg) {
              case "(":
                expr = new WurbletArgumentExpression(expr);
                operators.push(lastOperator);
                lastOperator = null;
                expressionLevel++;
                match = true;
                break;

              case ")":
                expressionLevel--;
                if (expressionLevel < 0) {
                  throw new WurbelException("unbalanced braces: more closed than opened");
                }
                WurbletArgumentExpression parent = expr != null ? expr.getParent() : null;
                lastOperator = operators.pop();
                if (parent != null) {
                  parent.addOperand(lastOperator, expr);
                  lastOperator = null;
                }
                expr = parent;
                match = true;
                break;

              case "|":
                if (expressionLevel != 0) {
                  throw new WurbelException("key group separator not allowed in nested expressions");
                }
                expressionFinished = true;
                match = true;
                break;
            }

            if (match) {
              existsRelations = null;
              existsComponents = null;
              existsArguments = null;
              if (lastExistsArg != null) {
                lastExistsArg.setEndOfExistsClause(true);
                lastExistsArg = null;
              }
              continue;
            }

            WurbletArgumentOperator operator = WurbletArgumentOperator.toInternal(subArg);
            if (operator != null) {
              if (lastOperator != null) {
                if (operator == WurbletArgumentOperator.NOT) {
                  if (lastOperator == WurbletArgumentOperator.AND) {
                    lastOperator = WurbletArgumentOperator.ANDNOT;
                    continue;
                  }
                  else if (lastOperator == WurbletArgumentOperator.OR) {
                    lastOperator = WurbletArgumentOperator.ORNOT;
                    continue;
                  }
                }
                throw new WurbelException(lastOperator + " cannot be followed by " + operator);
              }
              lastOperator = operator;
            }
            else {
              WurbletArgument operand = createArgument(subArg, expressionFinished, argumentGroupingEnabled);
              switch(operand.getArgumentType()) {
                case SORT:
                  if (expressionLevel == 0) {
                    sortingArguments.add(operand);
                    expressionFinished = true;
                  }
                  else {
                    throw new WurbelException("sorting key " + operand + " not allowed in nested expressions");
                  }
                  break;

                case JOIN:
                  if (expressionLevel == 0) {
                    joinArguments.add(operand);
                    expressionFinished = true;
                  }
                  else {
                    throw new WurbelException("join " + operand + " not allowed in nested expressions");
                  }
                  break;

                default:
                  // expression argument
                  WurbletArgumentOperator operandOperator = expr.addOperand(lastOperator, operand);
                  lastOperator = null;    // operator consumed
                  expressionArguments.add(operand);
                  if (operandOperator != WurbletArgumentOperator.AND) {
                    existsRelations = null;
                    existsComponents = null;
                    existsArguments = null;
                    if (lastExistsArg != null) {
                      lastExistsArg.setEndOfExistsClause(true);
                      lastExistsArg = null;
                    }
                  }

                  if (operand.getExpressionRelations() != null) {
                    if (existsRelations == null) {
                      // first operand found: will get all relations up the first non-AND operator
                      // first relation may be a component join
                      existsRelations = new LinkedHashSet<>(operand.getExpressionRelations());
                      existsComponents = new LinkedHashSet<>();
                      existsArguments = new ArrayList<>();
                      if (operand.getComponent() != null) {
                        existsComponents.add(operand.getComponent());
                      }
                      // this argument takes all relations up to the first non-ANDed argument
                      operand.setExistsRelations(existsRelations);
                      operand.setExistsComponents(existsComponents);
                    }
                    else {
                      // ANDed operator: add relations to exists relations
                      existsRelations.addAll(operand.getExpressionRelations());
                      if (operand.getComponent() != null) {
                        existsComponents.add(operand.getComponent());
                      }
                    }
                    // all arguments of the clause share the same list
                    existsArguments.add(operand);
                    operand.setExistsArguments(existsArguments);
                    lastExistsArg = operand;
                  }
                  break;
              }
            }
          }
        }
      }
    }

    if (lastExistsArg != null) {
      lastExistsArg.setEndOfExistsClause(true);
    }

    if (expressionLevel > 0) {
      throw new WurbelException("unbalanced braces: more opened than closed");
    }

    verifyOptionalArguments();
  }

  /**
   * Verifies that the boolean arguments of optional conditions don't collide with other method arguments.
   *
   * @throws WurbelException if a name is used twice
   */
  private void verifyOptionalArguments() throws WurbelException {
    Set<String> argNames = new LinkedHashSet<>();
    for (WurbletArgument arg: getMethodArguments()) {
      if (arg.isMethodArgument()) {
        argNames.add(arg.getMethodArgumentName());
      }
    }
    for (WurbletArgument arg: getMethodArguments()) {
      if (arg.isOptional() && argNames.contains(arg.getOptionalName())) {
        throw new WurbelException("'" + arg.getOptionalName() + "' in argument '" + arg +
                                  "' is already used as a method argument name");
      }
    }
  }

  /**
   * Returns whether at least one condition is optional.
   *
   * @return true if there are optional conditions
   * @see WurbletArgument#isOptional()
   */
  public boolean isWithOptionalArguments() {
    for (WurbletArgument arg: getMethodArguments()) {
      if (arg.isOptional()) {
        return true;
      }
    }
    return false;
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
            if (!StringHelper.isAllWhitespace(str)) {
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
    if (!StringHelper.isAllWhitespace(str)) {
      strs.add(str);
    }
    return strs;
  }

}
