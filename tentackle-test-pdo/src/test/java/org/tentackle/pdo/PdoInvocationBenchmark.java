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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import org.tentackle.pdo.pdofactory.SomeOperation;
import org.tentackle.pdo.pdofactory.SomeOperationPersistence;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.DefaultClassMapper;
import org.tentackle.reflect.InterceptableMethod;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.pdo.SecurityPersistence;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Throughput harness for the method invocation of PDOs and operations.
 * <p>
 * This is not a correctness test: it measures the overhead of the {@link PdoInvocationHandler} and the
 * {@link OperationInvocationHandler} compared to invoking the delegate directly. It is in the {@code benchmark}
 * TestNG group and therefore excluded from a normal build. Run it once per invocation strategy with:
 * <pre>
 *   mvn -pl :tentackle-test-pdo -Pbenchmark test -Dtentackle.reflect.methodHandles=true
 *   mvn -pl :tentackle-test-pdo -Pbenchmark test -Dtentackle.reflect.methodHandles=false
 * </pre>
 * Tunable via system properties: {@code pdo.bench.warmupMillis} (default 5000),
 * {@code pdo.bench.measureMillis} (default 2000) and {@code pdo.bench.runs} (default 7).
 * The reported figure is the median of the runs in nanoseconds per call; the minimum is printed as well.
 * Each workload runs its own loop of {@value #BATCH} calls, so the workloads do not share a call site.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoInvocationBenchmark {

  private static final long WARMUP_MILLIS = Long.getLong("pdo.bench.warmupMillis", 5000);
  private static final long MEASURE_MILLIS = Long.getLong("pdo.bench.measureMillis", 2000);
  private static final int RUNS = Integer.getInteger("pdo.bench.runs", 7);
  private static final int BATCH = 1000;

  /** consumes results so that the JIT cannot eliminate the work. */
  @SuppressWarnings("unused")
  private static volatile long sink;


  @Test(groups = "benchmark")
  void benchmarkPdo() {
    printStrategy();
    Security security = Pdo.create(Security.class);
    SecurityPersistence persistence = (SecurityPersistence) security.getPersistenceDelegate();

    measure("direct setAllowed(boolean)", () -> loopSetAllowed(persistence));
    measure("PDO setAllowed(boolean)", () -> loopSetAllowed(security));
    measure("direct isAllowed()", () -> loopIsAllowed(persistence));
    measure("PDO isAllowed()", () -> loopIsAllowed(security));
    measure("PDO getClassId()", () -> loopGetClassId(security));
  }

  @Test(groups = "benchmark")
  void benchmarkOperation() {
    printStrategy();
    Map<String, String> persistenceOperationMap = new TreeMap<>();
    persistenceOperationMap.put(SomeOperation.class.getName(), "org.tentackle.pdo.pdofactory.SomeOperationPersistenceImpl");
    ClassMapper persistenceMapper = new DefaultClassMapper(
      "persistence", getClass().getClassLoader(), persistenceOperationMap, new String[] { "org.tentackle.pdo.pdofactory" });
    ClassMapper domainMapper = new DefaultClassMapper(
      "domain", getClass().getClassLoader(), new TreeMap<>(), new String[] { "org.tentackle.pdo.pdofactory" });

    OperationFactory operationFactory = new AbstractOperationFactory() {
      @Override
      public ClassMapper getPersistenceMapper() {
        return persistenceMapper;
      }

      @Override
      public ClassMapper getDomainMapper() {
        return domainMapper;
      }
    };

    SomeOperation operation = operationFactory.create(SomeOperation.class);
    SomeOperationPersistence persistence = (SomeOperationPersistence) operation.getPersistenceDelegate();

    measure("direct echo(String)", () -> loopEcho(persistence));
    measure("operation echo(String)", () -> loopEcho(operation));
  }


  private static long loopSetAllowed(SecurityPersistence security) {
    for (int i = 0; i < BATCH; i++) {
      security.setAllowed((i & 1) == 0);
    }
    return BATCH;
  }

  private static long loopIsAllowed(SecurityPersistence security) {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      if (security.isAllowed()) {
        sum++;
      }
    }
    return sum;
  }

  private static long loopGetClassId(Security security) {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      sum += security.getClassId();
    }
    return sum;
  }

  private static long loopEcho(SomeOperationPersistence operation) {
    long sum = 0;
    for (int i = 0; i < BATCH; i++) {
      sum += operation.echo("hello").length();
    }
    return sum;
  }


  private static void printStrategy() {
    System.out.println("strategy: " + (InterceptableMethod.METHOD_HANDLES ? "method handles" : "reflection"));
  }

  /**
   * Runs a batch of calls repeatedly and prints the median and minimum nanoseconds per call.
   *
   * @param name the workload's name
   * @param batch the batch of {@link #BATCH} calls
   */
  private static void measure(String name, LongSupplier batch) {
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
