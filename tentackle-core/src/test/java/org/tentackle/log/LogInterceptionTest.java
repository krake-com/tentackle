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

package org.tentackle.log;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.log.Logger.Level;
import org.tentackle.reflect.InterceptableFactory;

import java.lang.reflect.Proxy;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link Log} interception through a real interceptable.
 * <p>
 * {@link LoggedCalculatorImpl} is bound to {@link LoggedCalculator} by {@code @InterceptionService},
 * whose mapping is generated at build time. A stale {@code target/test-classes} may therefore make
 * this test fail - rebuild the module in that case.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LogInterceptionTest {

  private LogCapture capture;
  private LoggedCalculator calculator;

  @BeforeMethod
  private void setUp() {
    capture = LogCapture.attach(LogInterceptor.class, Level.FINER);
    calculator = InterceptableFactory.getInstance().createInterceptable(LoggedCalculator.class);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }


  @Test
  public void testInterceptableIsAProxy() {
    assertTrue(Proxy.isProxyClass(calculator.getClass()));
  }

  @Test
  public void testInterceptedMethodReturnsTheResult() {
    assertEquals(calculator.add(1, 2), 3);
  }

  @Test
  public void testInterceptedMethodIsLogged() {
    calculator.add(1, 2);
    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    assertTrue(record.getMessage().contains(".add("), record.getMessage());
    assertTrue(record.getMessage().contains(" took "), record.getMessage());
    assertTrue(record.getMessage().endsWith("ms"), record.getMessage());
  }

  @Test
  public void testDefaultLevelIsInfo() {
    assertEquals(calculator.multiply(3, 4), 12);
    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.INFO);
    assertTrue(record.getMessage().contains(".multiply("), record.getMessage());
  }

  @Test
  public void testMethodWithoutTheAnnotationIsNotLogged() {
    assertEquals(calculator.subtract(5, 3), 2);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testNothingIsLoggedBelowTheLevel() {
    capture.setLevel(Level.SEVERE);
    assertEquals(calculator.add(1, 2), 3);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testExceptionOfTheInterceptedMethodIsPropagated() {
    IllegalStateException exception = expectThrows(IllegalStateException.class, calculator::boom);
    assertEquals(exception.getMessage(), LoggedCalculatorImpl.BOOM_MESSAGE);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testEveryInvocationIsLogged() {
    calculator.add(1, 2);
    calculator.add(3, 4);
    assertEquals(capture.records().size(), 2);
  }

}
