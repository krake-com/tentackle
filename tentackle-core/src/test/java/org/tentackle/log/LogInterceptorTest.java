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
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.Interception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the {@link Log} annotation and its {@link LogInterceptor}.
 * <p>
 * The interceptor is driven directly here: {@code AbstractInterceptor.invoke} falls back to
 * {@code proceed} when there is no chained interceptor, so no proxy is needed. The interception
 * through a real dynamic proxy is covered by {@code LogInterceptionTest}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LogInterceptorTest {

  /**
   * The object the interceptor invokes its methods on.
   */
  public static class LogTarget implements Interceptable {

    /** the number of invocations. */
    public final AtomicInteger invocations = new AtomicInteger();

    /**
     * Increments the given value, logged with {@link Level#FINE}.
     *
     * @param value the value
     * @return the incremented value
     */
    @Log(Level.FINE)
    public int increment(int value) {
      invocations.incrementAndGet();
      return value + 1;
    }

    /**
     * Increments the given value, logged with the default level.
     *
     * @param value the value
     * @return the incremented value
     */
    @Log
    public int incrementWithDefaultLevel(int value) {
      invocations.incrementAndGet();
      return value + 1;
    }

    /**
     * Increments the given value, logged with {@link Level#WARNING}.
     *
     * @param value the value
     * @return the incremented value
     */
    @Log(Level.WARNING)
    public int incrementLoudly(int value) {
      invocations.incrementAndGet();
      return value + 1;
    }

    /**
     * Does nothing but count, logged with {@link Level#FINE}.
     */
    @Log(Level.FINE)
    public void nothing() {
      invocations.incrementAndGet();
    }

    /**
     * Always throws, logged with {@link Level#FINE}.
     */
    @Log(Level.FINE)
    public void boom() {
      invocations.incrementAndGet();
      throw new IllegalStateException("boom");
    }
  }


  private LogCapture capture;

  @BeforeMethod
  private void setUp() {
    capture = LogCapture.attach(LogInterceptor.class, Level.FINER);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }

  private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    return LogTarget.class.getMethod(name, parameterTypes);
  }

  private static LogInterceptor interceptorFor(Method method) {
    LogInterceptor interceptor = new LogInterceptor();
    interceptor.setAnnotation(method.getAnnotation(Log.class));
    return interceptor;
  }


  // ------------------------------------------------------------------ the annotation

  @Test
  public void testAnnotationIsAnInterception() {
    Interception interception = Log.class.getAnnotation(Interception.class);
    assertSame(interception.implementedBy(), LogInterceptor.class);
    assertSame(interception.type(), Interception.Type.ALL);
  }

  @Test
  public void testAnnotationRetentionAndTarget() {
    assertSame(Log.class.getAnnotation(Retention.class).value(), RetentionPolicy.RUNTIME);
    assertEquals(Log.class.getAnnotation(Target.class).value(), new ElementType[] { ElementType.METHOD });
  }

  @Test
  public void testAnnotationDefaultsToInfo() throws NoSuchMethodException {
    assertSame(Log.class.getMethod("value").getDefaultValue(), Level.INFO);
    assertSame(method("incrementWithDefaultLevel", int.class).getAnnotation(Log.class).value(), Level.INFO);
  }

  @Test
  public void testAnnotationValue() throws NoSuchMethodException {
    assertSame(method("increment", int.class).getAnnotation(Log.class).value(), Level.FINE);
    assertSame(method("incrementLoudly", int.class).getAnnotation(Log.class).value(), Level.WARNING);
  }


  // ------------------------------------------------------------------ the interceptor

  @Test
  public void testAnnotationRoundTrip() throws NoSuchMethodException {
    Method method = method("increment", int.class);
    LogInterceptor interceptor = interceptorFor(method);
    assertSame(interceptor.getAnnotation(), method.getAnnotation(Log.class));
  }

  @Test
  public void testMethodIsInvoked() throws Throwable {
    Method method = method("increment", int.class);
    LogTarget target = new LogTarget();
    Object result = interceptorFor(method).invoke(target, method, new Object[] { 41 });
    assertEquals(result, 42);
    assertEquals(target.invocations.get(), 1);
  }

  @Test
  public void testVoidMethodReturnsNull() throws Throwable {
    Method method = method("nothing");
    LogTarget target = new LogTarget();
    assertNull(interceptorFor(method).invoke(target, method, new Object[0]));
    assertEquals(target.invocations.get(), 1);
  }

  @Test
  public void testDurationIsLogged() throws Throwable {
    Method method = method("increment", int.class);
    interceptorFor(method).invoke(new LogTarget(), method, new Object[] { 41 });

    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    assertTrue(record.getMessage().startsWith(method.toGenericString()), record.getMessage());
    assertTrue(record.getMessage().contains(" took "), record.getMessage());
    assertTrue(record.getMessage().endsWith("ms"), record.getMessage());
  }

  @Test
  public void testLevelIsTakenFromTheAnnotation() throws Throwable {
    Method method = method("incrementLoudly", int.class);
    interceptorFor(method).invoke(new LogTarget(), method, new Object[] { 41 });
    assertEquals(capture.singleRecord().getLevel(), java.util.logging.Level.WARNING);
  }

  @Test
  public void testDefaultLevelIsInfo() throws Throwable {
    Method method = method("incrementWithDefaultLevel", int.class);
    interceptorFor(method).invoke(new LogTarget(), method, new Object[] { 41 });
    assertEquals(capture.singleRecord().getLevel(), java.util.logging.Level.INFO);
  }

  @Test
  public void testNothingIsLoggedBelowTheLevel() throws Throwable {
    capture.setLevel(Level.SEVERE);
    Method method = method("increment", int.class);
    LogTarget target = new LogTarget();
    assertEquals(interceptorFor(method).invoke(target, method, new Object[] { 41 }), 42);
    assertEquals(target.invocations.get(), 1);            // the method still runs
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testExceptionIsUnwrapped() throws NoSuchMethodException {
    // AbstractInterceptor.invoke must not leak the InvocationTargetException of the reflective call
    Method method = method("boom");
    LogTarget target = new LogTarget();
    LogInterceptor interceptor = interceptorFor(method);
    IllegalStateException exception =
        expectThrows(IllegalStateException.class, () -> interceptor.invoke(target, method, new Object[0]));
    assertEquals(exception.getMessage(), "boom");
    assertEquals(target.invocations.get(), 1);
  }

  @Test
  public void testFailedInvocationIsNotTimed() throws NoSuchMethodException {
    Method method = method("boom");
    LogInterceptor interceptor = interceptorFor(method);
    expectThrows(IllegalStateException.class, () -> interceptor.invoke(new LogTarget(), method, new Object[0]));
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testInterceptorIsReusable() throws Throwable {
    Method method = method("increment", int.class);
    LogInterceptor interceptor = interceptorFor(method);
    LogTarget target = new LogTarget();
    interceptor.invoke(target, method, new Object[] { 1 });
    interceptor.invoke(target, method, new Object[] { 2 });
    assertEquals(target.invocations.get(), 2);
    assertEquals(capture.records().size(), 2);
  }

}
