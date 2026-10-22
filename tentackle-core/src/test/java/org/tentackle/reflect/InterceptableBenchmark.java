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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Throughput harness for the method invocation of interceptables.
 * <p>
 * This is not a correctness test: it compares invoking the delegate's method via reflection
 * ({@link Method#invoke}) against method handles ({@link MethodHandle#invokeExact}). It is in the
 * {@code benchmark} TestNG group and therefore excluded from a normal build. Run it with:
 * <pre>
 *   mvn -pl :tentackle-core -Pbenchmark test -Dtest='InterceptableBenchmark#benchmarkDispatch'
 *   mvn -pl :tentackle-core -Pbenchmark test -Dtest='InterceptableBenchmark#benchmarkProxy' -Dtentackle.reflect.methodHandles=false
 * </pre>
 * {@code benchmarkDispatch} compares the raw strategies within one JVM. {@code benchmarkProxy} measures
 * the whole path through the dynamic proxy and the {@link InterceptableInvocationHandler}, using the strategy
 * selected by the system property {@code tentackle.reflect.methodHandles} (default true). Run it once per strategy.
 * <p>
 * Tunable via system properties: {@code interceptable.bench.warmupMillis} (default 5000),
 * {@code interceptable.bench.measureMillis} (default 2000) and {@code interceptable.bench.runs} (default 7).
 * The reported figure is the median of the runs in nanoseconds per call; the minimum is printed as well.
 * Each variant runs its own loop of {@value #BATCH} calls, so the variants do not share a call site.
 */
@SuppressWarnings("missing-explicit-ctor")
public class InterceptableBenchmark {

  private static final long WARMUP_MILLIS = Long.getLong("interceptable.bench.warmupMillis", 5000);
  private static final long MEASURE_MILLIS = Long.getLong("interceptable.bench.measureMillis", 2000);
  private static final int RUNS = Integer.getInteger("interceptable.bench.runs", 7);
  private static final int BATCH = 1000;

  /** consumes results so that the JIT cannot eliminate the work. */
  @SuppressWarnings("unused")
  private static volatile long sink;

  // non-final instance fields: the cached method objects are not constants in the invocation handlers either
  private BenchInterceptable direct;
  private Method method;
  private MethodHandle handle;


  @Test(groups = "benchmark")
  void benchmarkDispatch() throws Exception {
    direct = new BenchInterceptableImpl(null);
    method = BenchInterceptableImpl.class.getMethod("sum", int.class, int.class);
    handle = MethodHandles.lookup().unreflect(method)
                          .asType(MethodType.genericMethodType(3))
                          .asSpreader(Object[].class, 2);

    measure("direct", this::loopDirect);
    measure("Method.invoke", this::loopReflection);
    measure("MethodHandle.invokeExact", this::loopMethodHandle);
  }

  @Test(groups = "benchmark")
  void benchmarkProxy() {
    System.out.println("strategy: " + (InterceptableMethod.METHOD_HANDLES ? "method handles" : "reflection"));
    direct = new BenchInterceptableImpl(null);
    BenchInterceptable proxy = InterceptableFactory.getInstance().createInterceptable(BenchInterceptable.class);
    Object object = new Object();

    measure("direct sum(int,int)", () -> loopSum(direct));
    measure("proxy sum(int,int)", () -> loopSum(proxy));
    measure("proxy name()", () -> loopName(proxy));
    measure("proxy touch(Object)", () -> loopTouch(proxy, object));
  }


  private long loopDirect() {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      sum += direct.sum(i, 1);
    }
    return sum;
  }

  private long loopReflection() {
    long sum = 0;
    try {
      for (int i = 0; i < BATCH; i++) {
        sum += (Integer) method.invoke(direct, new Object[] { i, 1 });
      }
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
    return sum;
  }

  private long loopMethodHandle() {
    long sum = 0;
    try {
      for (int i = 0; i < BATCH; i++) {
        sum += (Integer) (Object) handle.invokeExact((Object) direct, new Object[] { i, 1 });
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
    return sum;
  }

  private static long loopSum(BenchInterceptable bench) {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      sum += bench.sum(i, 1);
    }
    return sum;
  }

  private static long loopName(BenchInterceptable bench) {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      sum += bench.name().length();
    }
    return sum;
  }

  private static long loopTouch(BenchInterceptable bench, Object object) {
    for (int i = 0; i < BATCH; i++) {
      bench.touch(object);
    }
    return BATCH;
  }


  /**
   * Runs a batch of calls repeatedly and prints the median and minimum nanoseconds per call.
   *
   * @param name the variant's name
   * @param batch the batch of {@link #BATCH} calls
   */
  private void measure(String name, LongSupplier batch) {
    long warmupEnd = System.nanoTime() + WARMUP_MILLIS * 1_000_000L;
    long warmupBatches = 0;
    while (System.nanoTime() < warmupEnd) {
      sink = batch.getAsLong();
      warmupBatches++;
    }
    long batches = Math.max(1, warmupBatches * MEASURE_MILLIS / WARMUP_MILLIS);
    double[] results = new double[RUNS];
    for (int r = 0; r < RUNS; r++) {
      long start = System.nanoTime();
      for (long b = 0; b < batches; b++) {
        sink = batch.getAsLong();
      }
      results[r] = (System.nanoTime() - start) / (double) (batches * BATCH);
    }
    Arrays.sort(results);
    System.out.printf("%-28s median=%7.2f ns/call   min=%7.2f ns/call%n", name, results[RUNS / 2], results[0]);
  }

}
