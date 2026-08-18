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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the {@link NumericEvaluator}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class NumericEvaluatorTest {

  @Test
  public void testJavadocIntegerExample() {
    Integer newPort = new NumericEvaluator<>("(port + 1000) / 2",
                                             new NumericVariable<>("port", 20000)).evaluate();
    assertEquals(newPort, Integer.valueOf(10500));
  }

  @Test
  public void testJavadocDoubleExample() {
    Double average = new NumericEvaluator<>(Double.class, "(weight1 + weight2) / 2.0",
                              new NumericVariable<>("weight1", 12.7),
                              new NumericVariable<>("weight2", 28.2)).evaluate();
    assertEquals(average, 20.45, 1e-9);
  }

  @Test
  public void testDefaultTypeIsInteger() {
    NumericEvaluator<Integer> ev = new NumericEvaluator<>("40 + 2");
    assertEquals(ev.getType(), Integer.class);
    assertEquals(ev.evaluate(), Integer.valueOf(42));
  }

  @Test
  public void testOperatorPrecedence() {
    assertEquals(new NumericEvaluator<Integer>("2 + 3 * 4").evaluate(), Integer.valueOf(14));
    assertEquals(new NumericEvaluator<Integer>("(2 + 3) * 4").evaluate(), Integer.valueOf(20));
    assertEquals(new NumericEvaluator<Integer>("20 - 4 - 3").evaluate(), Integer.valueOf(13));
    assertEquals(new NumericEvaluator<Integer>("100 / 10 / 2").evaluate(), Integer.valueOf(5));
  }

  @Test
  public void testNestedParentheses() {
    assertEquals(new NumericEvaluator<Integer>("((1 + 2) * (3 + 4)) - 1").evaluate(), Integer.valueOf(20));
  }

  @Test
  public void testUnaryMinus() {
    assertEquals(new NumericEvaluator<Integer>("-5 + 3").evaluate(), Integer.valueOf(-2));
    assertEquals(new NumericEvaluator<Integer>("-(2 + 3) * 2").evaluate(), Integer.valueOf(-10));
    assertEquals(new NumericEvaluator<Integer>("- -4").evaluate(), Integer.valueOf(4));
  }

  @Test
  public void testIntegerDivisionTruncatesEachStep() {
    // 5 / 2 == 2 (integer), then 2 * 2 == 4 -- not floating 5.0
    assertEquals(new NumericEvaluator<Integer>("5 / 2 * 2").evaluate(), Integer.valueOf(4));
  }

  @Test
  public void testModulo() {
    assertEquals(new NumericEvaluator<Integer>("18000 % 7").evaluate(), Integer.valueOf(3));
  }

  @Test
  public void testLongType() {
    Long value = new NumericEvaluator<>(Long.class, "base * 1000",
                              new NumericVariable<>("base", 9_000_000L)).evaluate();
    assertEquals(value, Long.valueOf(9_000_000_000L));
  }

  @Test
  public void testMultipleVariables() {
    assertEquals(new NumericEvaluator<>("a * b + c",
                     new NumericVariable<>("a", 3),
                     new NumericVariable<>("b", 4),
                     new NumericVariable<>("c", 5)).evaluate(),
                 Integer.valueOf(17));
  }

  @Test
  public void testVariableNamesAreCaseSensitive() {
    assertThrows(TentackleRuntimeException.class,
                 () -> new NumericEvaluator<>("Port + 1", new NumericVariable<>("port", 1)).evaluate());
  }

  @Test
  public void testUndefinedVariableThrows() {
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("missing + 1").evaluate());
  }

  @Test
  public void testDivisionByZeroThrows() {
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("1 / 0").evaluate());
  }

  @Test
  public void testMalformedExpressionsThrow() {
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("2 +").evaluate());
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("(2 + 3").evaluate());
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("2 3").evaluate());
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("").evaluate());
  }

  @Test
  public void testDecimalLiteralRejectedForIntegralType() {
    assertThrows(TentackleRuntimeException.class, () -> new NumericEvaluator<Integer>("1.5 + 1").evaluate());
  }

  @Test
  public void testEvaluateConstantExpression() {
    assertEquals(new NumericEvaluator<>("1 + 1").evaluate(), 2);
    // same as a Function useful for the tentackle-maven-plugin PropertyDescriptor
    assertEquals(new NumericEvaluator<>().apply("1 + 1"), "2");
  }

}
