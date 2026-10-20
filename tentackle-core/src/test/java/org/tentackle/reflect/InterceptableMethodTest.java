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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import org.tentackle.reflect.interceptable.BenchInterceptable;
import org.tentackle.reflect.interceptable.BenchInterceptableImpl;
import org.tentackle.reflect.interceptable.Calculator;
import org.tentackle.reflect.interceptable.CalculatorImpl;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Tests the invocation of {@link InterceptableMethod}s, either via method handles or reflection.
 * <p>
 * Runs with the strategy selected by the system property {@code tentackle.reflect.methodHandles}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class InterceptableMethodTest {

  @Test
  public void testStrategy() throws Exception {
    Method method = BenchInterceptable.class.getMethod("sum", int.class, int.class);
    InterceptableMethod interceptableMethod = InterceptionUtilities.getInstance().createInterceptableMethod(
      BenchInterceptable.class, BenchInterceptableImpl.class, method);
    assertEquals(interceptableMethod.isMethodHandleBound(), InterceptableMethod.METHOD_HANDLES);
  }

  @Test
  public void testInterceptedNeverBound() throws Exception {
    Method method = Calculator.class.getMethod("add", int.class, int.class);
    InterceptableMethod interceptableMethod = InterceptionUtilities.getInstance().createInterceptableMethod(
      Calculator.class, CalculatorImpl.class, method);
    assertFalse(interceptableMethod.isMethodHandleBound());
  }

  @Test
  public void testPrimitives() {
    BenchInterceptable bench = InterceptableFactory.getInstance().createInterceptable(BenchInterceptable.class);
    assertEquals(bench.sum(3, 4), 7);
    assertEquals(bench.sum(Integer.MAX_VALUE, 1), Integer.MIN_VALUE);
  }

  @Test
  public void testNoArgs() {
    BenchInterceptable bench = InterceptableFactory.getInstance().createInterceptable(BenchInterceptable.class);
    assertEquals(bench.name(), "bench");
  }

  @Test
  public void testVoidAndNull() {
    BenchInterceptable bench = InterceptableFactory.getInstance().createInterceptable(BenchInterceptable.class);
    Object object = new Object();
    bench.touch(object);
    assertSame(bench.touched(), object);
    bench.touch(null);
    assertNull(bench.touched());
  }

  @Test
  public void testExceptionUnwrapped() {
    BenchInterceptable bench = InterceptableFactory.getInstance().createInterceptable(BenchInterceptable.class);
    assertThrows(BenchInterceptable.BenchException.class, bench::fail);
  }

  @Test
  public void testIntercepted() {
    Calculator calc = InterceptableFactory.getInstance().createInterceptable(Calculator.class);
    assertEquals(calc.add(1, 2), 103);   // (1 + 2) + 100 due to @Offset
  }

}
