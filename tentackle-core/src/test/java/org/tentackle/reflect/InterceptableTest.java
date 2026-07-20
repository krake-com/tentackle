/*
 * Tentackle - https://tentackle.org.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.reflect.interceptable.Calculator;
import org.tentackle.reflect.interceptable.LogItInterceptor;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Interceptable test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class InterceptableTest {

  @Test
  public void testCreateInterceptable() {
    Calculator calc = InterceptableFactory.getInstance().createInterceptable(Calculator.class);
    assertEquals(calc.add(10, 30), 140);   // + 100 due to @Offset
  }

  @Test
  public void testCreatedInterceptableIsProxy() {
    Calculator calc = InterceptableFactory.getInstance().createInterceptable(Calculator.class);
    Assert.assertNotNull(calc);
    assertTrue(calc instanceof Calculator);
    assertTrue(Proxy.isProxyClass(calc.getClass()));
  }

  @Test
  public void testInterceptorChainIsApplied() {
    // The @LogIt interceptor runs first (outermost) and records the result
    // produced after the chained @Offset interceptor added its offset of 100.
    LogItInterceptor.lastResult = null;
    Calculator calc = InterceptableFactory.getInstance().createInterceptable(Calculator.class);
    int result = calc.add(1, 2);
    assertEquals(result, 103);                       // (1 + 2) + 100
    assertEquals(LogItInterceptor.lastResult, 103);  // recorded by the LogIt interceptor
  }

  @Test
  public void testFactorySingleton() {
    assertSame(InterceptableFactory.getInstance(), InterceptableFactory.getInstance());
  }
}
