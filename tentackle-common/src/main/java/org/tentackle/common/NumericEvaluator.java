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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A small arithmetic expression evaluator for numeric values.
 * <p>
 * The evaluator parses an infix expression made of numeric literals, named variables and the
 * binary operators {@code +}, {@code -}, {@code *}, {@code /} and {@code %}, with the usual
 * operator precedence and parentheses for grouping. Unary {@code +} and {@code -} are supported
 * as well. Whitespace is ignored.
 * <p>
 * The datatype {@code <T>} determines both the domain the arithmetic is carried out in and the
 * type of the result. Integral types ({@link Byte}, {@link Short}, {@link Integer}, {@link Long}
 * and {@link BigInteger}) are evaluated with integer semantics (i.e. {@code /} truncates towards
 * zero), floating-point types ({@link Float}, {@link Double} and {@link BigDecimal}) with
 * floating-point semantics. The type defaults to {@link Integer} if the shorthand constructor is
 * used. Variable and result types are checked at compile time via generics.
 * <p>
 * Examples:
 * <pre>
 *   Integer newPort = new NumericEvaluator&lt;&gt;("(port + 1000) / 2",
 *                             new NumericVariable&lt;&gt;("port", 20000)).evaluate();
 *
 *   Double average = new NumericEvaluator&lt;&gt;(Double.class, "(weight1 + weight2) / 2.0",
 *                             new NumericVariable&lt;&gt;("weight1", 12.7),
 *                             new NumericVariable&lt;&gt;("weight2", 28.2)).evaluate();
 * </pre>
 *
 * @param <T> the numeric datatype of the variables and the result
 * @author harald
 */
public class NumericEvaluator<T extends Number> implements Function<String, String> {

  private final Class<T> type;                    // the result datatype
  private final String expression;                // the source expression
  private final boolean integral;                 // true if evaluated with integer semantics
  private final Map<String, Number> variables;    // variable name -> value

  private int pos;                                // current parser position within expression

  /**
   * Creates an evaluator for the given datatype and evaluates the expression.
   *
   * @param type the result datatype
   * @param expression the expression to evaluate
   * @param arguments the variables referenced by the expression
   */
  @SafeVarargs
  public NumericEvaluator(Class<T> type, String expression, NumericVariable<T>... arguments) {
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.expression = Objects.requireNonNull(expression, "expression must not be null");
    this.integral = isIntegral(type);
    this.variables = new HashMap<>();
    for (NumericVariable<T> argument : arguments) {
      variables.put(argument.name(), argument.value());
    }
  }

  /**
   * Creates an evaluator for the {@link Integer} datatype and evaluates the expression.
   *
   * @param expression the expression to evaluate
   * @param arguments the variables referenced by the expression
   * @throws TentackleRuntimeException if the expression is malformed or cannot be evaluated
   */
  @SafeVarargs
  @SuppressWarnings("unchecked")
  public NumericEvaluator(String expression, NumericVariable<T>... arguments) {
    this((Class<T>) Integer.class, expression, arguments);
  }

  /**
   * Constructor to use it as a {@code Function<String, String>}.<br>
   * Nice for {@code PropertyDescriptor}s within the {@code tentackle-maven-plugin}.
   */
  public NumericEvaluator() {
    this("");
  }

  /**
   * Evaluates the expression.
   * <p>
   * @throws TentackleRuntimeException if the expression is malformed or cannot be evaluated
   *
   * @return the result, never null
   */
  public T evaluate() {
    return convert(parse());
  }

  /**
   * Gets the datatype of the result.
   *
   * @return the datatype
   */
  public Class<T> getType() {
    return type;
  }

  @Override
  public String toString() {
    return expression;
  }

  /**
   * Convenience method to evaluate a numeric expression without variables.
   * <p>
   * This allows the NumericEvaluator to be used as a {@code PropertyDescriptor}
   * within the {@code tentackle-maven-plugin}.
   *
   * @param expression the expression to evaluate
   * @return the result as string
   */
  @Override
  public String apply(String expression) {
    return Integer.toString(new NumericEvaluator<>(Integer.class, expression).evaluate());
  }



  // --- parser (recursive descent) ---

  private Number parse() {
    pos = 0;
    Number value = parseExpression();
    skipWhitespace();
    if (pos < expression.length()) {
      throw error("unexpected trailing input at position " + pos);
    }
    return value;
  }

  private Number parseExpression() {
    Number value = parseTerm();
    while (true) {
      skipWhitespace();
      char c = peek();
      if (c == '+' || c == '-') {
        pos++;
        value = apply(c, value, parseTerm());
      }
      else {
        return value;
      }
    }
  }

  private Number parseTerm() {
    Number value = parseFactor();
    while (true) {
      skipWhitespace();
      char c = peek();
      if (c == '*' || c == '/' || c == '%') {
        pos++;
        value = apply(c, value, parseFactor());
      }
      else {
        return value;
      }
    }
  }

  private Number parseFactor() {
    skipWhitespace();
    char c = peek();
    if (c == '+') {
      pos++;
      return parseFactor();
    }
    if (c == '-') {
      pos++;
      return negate(parseFactor());
    }
    if (c == '(') {
      pos++;
      Number value = parseExpression();
      skipWhitespace();
      if (peek() != ')') {
        throw error("missing ')' at position " + pos);
      }
      pos++;
      return value;
    }
    if (isVariableStart(c)) {
      return parseVariable();
    }
    if (isDigit(c) || c == '.') {
      return parseNumber();
    }
    throw error("unexpected character '" + c + "' at position " + pos);
  }

  private Number parseVariable() {
    int start = pos;

    do {
      pos++;
    }
    while (pos < expression.length() && isVariablePart(expression.charAt(pos)));

    String name = expression.substring(start, pos);
    Number value = variables.get(name);
    if (value == null) {
      throw error("undefined variable '" + name + "'");
    }
    return value;
  }

  private Number parseNumber() {
    int start = pos;
    while (pos < expression.length() && (isDigit(expression.charAt(pos)) || expression.charAt(pos) == '.')) {
      pos++;
    }
    String literal = expression.substring(start, pos);
    try {
      return integral ? Long.valueOf(Long.parseLong(literal)) : Double.valueOf(Double.parseDouble(literal));
    }
    catch (NumberFormatException nx) {
      throw error("invalid " + (integral ? "integer" : "numeric") + " literal '" + literal + "'");
    }
  }

  private Number apply(char op, Number left, Number right) {
    try {
      if (integral) {
        long l = left.longValue();
        long r = right.longValue();
        return switch (op) {
          case '+' -> l + r;
          case '-' -> l - r;
          case '*' -> l * r;
          case '/' -> l / r;
          default  -> l % r;
        };
      }
      double l = left.doubleValue();
      double r = right.doubleValue();
      return switch (op) {
        case '+' -> l + r;
        case '-' -> l - r;
        case '*' -> l * r;
        case '/' -> l / r;
        default  -> l % r;
      };
    }
    catch (ArithmeticException ax) {
      throw error(ax.getMessage());
    }
  }

  private Number negate(Number value) {
    return integral ? Long.valueOf(-value.longValue()) : Double.valueOf(-value.doubleValue());
  }

  private T convert(Number value) {
    Number converted;
    if (type == Integer.class) {
      converted = value.intValue();
    }
    else if (type == Long.class) {
      converted = value.longValue();
    }
    else if (type == Double.class) {
      converted = value.doubleValue();
    }
    else if (type == Float.class) {
      converted = value.floatValue();
    }
    else if (type == Short.class) {
      converted = value.shortValue();
    }
    else if (type == Byte.class) {
      converted = value.byteValue();
    }
    else if (type == BigInteger.class) {
      converted = BigInteger.valueOf(value.longValue());
    }
    else if (type == BigDecimal.class) {
      converted = BigDecimal.valueOf(value.doubleValue());
    }
    else {
      throw error("unsupported numeric datatype " + type.getName());
    }
    return type.cast(converted);
  }

  private TentackleRuntimeException error(String message) {
    return new TentackleRuntimeException("invalid expression '" + expression + "': " + message);
  }

  private char peek() {
    return pos < expression.length() ? expression.charAt(pos) : '\0';
  }

  private void skipWhitespace() {
    while (pos < expression.length() && Character.isWhitespace(expression.charAt(pos))) {
      pos++;
    }
  }

  private static boolean isIntegral(Class<?> type) {
    return type == Byte.class || type == Short.class || type == Integer.class
        || type == Long.class || type == BigInteger.class;
  }

  private static boolean isVariableStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private static boolean isVariablePart(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

}
