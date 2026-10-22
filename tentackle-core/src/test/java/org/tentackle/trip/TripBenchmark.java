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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.io.MutableByteArrayInputStream;
import org.tentackle.trip.impl.BenchmarkEntity;
import org.tentackle.trip.impl.CanonicalDto;
import org.tentackle.trip.impl.DemoLogin;
import org.tentackle.trip.impl.DemoLoginImpl;
import org.tentackle.trip.impl.DemoSession;
import org.tentackle.trip.impl.ValidatedBenchmarkEntity;
import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Throughput harness for the TRIP serialization core.
 * <p>
 * This is not a correctness test: it is the measuring device for CPU optimizations of the
 * {@code org.tentackle.trip} package. It is in the {@code benchmark} TestNG group and therefore
 * excluded from a normal build. Run it with:
 * <pre>
 *   mvn -pl :tentackle-core -Pbenchmark test
 * </pre>
 * Tunable via system properties: {@code trip.bench.warmupMillis} (default 5000),
 * {@code trip.bench.measureMillis} (default 2000) and {@code trip.bench.runs} (default 7).
 * <p>
 * Each workload is measured three ways &mdash; serialize only, deserialize only, and round trip
 * &mdash; and the encoded size is reported alongside, so that a change trading bytes for cycles
 * cannot hide. The reported figure is the median of the runs; the minimum is printed as well,
 * since it is the least noisy estimator on a busy machine.
 * <p>
 * The serialized form of every workload is captured once at the start of a run and asserted to be
 * byte-identical on re-encoding. Comparing that size across builds is the cheapest evidence that an
 * optimization did not shift the wire format.
 * <p>
 * <strong>Compare one workload at a time.</strong> All workloads share the generic measuring loop
 * and the generic {@code Rig}, so running them in one JVM makes those call sites megamorphic and
 * every workload pays for the others. The absolute numbers of a full run are therefore inflated,
 * and the inflation is not stable between runs &mdash; it was good for a phantom 15% swing while
 * these measurements were being taken. Run the single workload you are comparing:
 * <pre>
 *   mvn -pl :tentackle-core -Pbenchmark test -Dtest='TripBenchmark#benchmarkStrings'
 * </pre>
 * Isolated like that, the run-to-run spread is under 3%.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TripBenchmark {

  private static final long WARMUP_MILLIS = Long.getLong("trip.bench.warmupMillis", 5000);
  private static final long MEASURE_MILLIS = Long.getLong("trip.bench.measureMillis", 2000);
  private static final int RUNS = Integer.getInteger("trip.bench.runs", 7);

  private static final UUID NIL_UUID = new UUID(0, 0);

  /** consumes results so that the JIT cannot eliminate the work. */
  @SuppressWarnings("unused")
  private static volatile Object sink;

  @Test(groups = "benchmark")
  void benchmarkEntity() {
    measure("entity", new BenchmarkEntity(1, new BenchmarkEntity(2, null)), BenchmarkEntity.class);
  }

  @Test(groups = "benchmark")
  void benchmarkEntityList() {
    BenchmarkEntity parent = new BenchmarkEntity(0, null);
    List<BenchmarkEntity> list = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      list.add(new BenchmarkEntity(i, parent));
    }
    measure("entity-list-1000", list, ArrayList.class);
  }

  @Test(groups = "benchmark")
  void benchmarkClientEntityList() {
    // same as entity-list-1000, but written by a client: its entries stay client-only for as long as
    // the server never sends the class back, which is the case for everything a client sends
    BenchmarkEntity parent = new BenchmarkEntity(0, null);
    List<BenchmarkEntity> list = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      list.add(new BenchmarkEntity(i, parent));
    }
    measure("client-entity-list-1000", list, ArrayList.class, true);
  }

  @Test(groups = "benchmark")
  void benchmarkValidatedEntityList() {
    // @TripSerializable hooks invoked per object, like AbstractDbObject's validate method
    BenchmarkEntity parent = new BenchmarkEntity(0, null);
    List<BenchmarkEntity> list = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      list.add(new ValidatedBenchmarkEntity(i, parent));
    }
    measure("validated-entity-list-1000", list, ArrayList.class);
  }

  @Test(groups = "benchmark")
  void benchmarkRpcRoundTrip() throws Exception {
    // a real TCP connection on the loopback interface: the latency of a remote call is dominated
    // by how the transport hands its bytes to the socket, not by the codec
    measureRpc("trip");
    measureRpc("tripc");
  }

  @Test(groups = "benchmark")
  void benchmarkCanonicalDto() {
    // immutable objects created by their @CanonicalConstructor: a different read path than the
    // no-args construction of BenchmarkEntity
    List<CanonicalDto> list = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      list.add(new CanonicalDto("canonical-dto-name-" + i, i));
    }
    measure("canonical-dto-1000", list, ArrayList.class);
  }

  @Test(groups = "benchmark")
  void benchmarkRemoteCall() {
    IdentifiedRemoteCall call = new IdentifiedRemoteCall(
      UUID.randomUUID(), 7, new Object[] { 4711L, "a parameter", Boolean.TRUE, new BenchmarkEntity(3, null) });
    measure("rpc-call", call, IdentifiedRemoteCall.class);

    IdentifiedRemoteResponse response = new IdentifiedRemoteResponse(new BenchmarkEntity(4, null), null);
    measure("rpc-response", response, IdentifiedRemoteResponse.class);
  }

  @Test(groups = "benchmark")
  void benchmarkByteArray() {
    byte[] dense = new byte[1 << 20];
    new Random(42).nextBytes(dense);
    for (int i = 0; i < dense.length; i++) {
      if (dense[i] == 0) {
        dense[i] = 1;   // no zeros at all, so the skip encoding never triggers
      }
    }
    measure("byte-array-1M-dense (100%)", dense, byte[].class);

    dense = new byte[1 << 20];
    Random random = new Random(42);
    for (int i = 0; i < dense.length; i++) {
      dense[i] = (byte) random.nextInt(256);
    }
    measure("byte-array-1M-dense (0-255)", dense, byte[].class);

    byte[] sparse = new byte[1 << 20];
    random = new Random(43);
    byte nextVal = 0;
    int blkLen = 1;
    for (int i = 0; i < sparse.length - blkLen; i += blkLen) {
      Arrays.fill(sparse, i, i + blkLen, nextVal);
      blkLen = random.nextInt(500);
      nextVal = (byte) (nextVal == 0 ? 1 : 0);
    }
    measure("byte-array-1M-sparse (50%)", sparse, byte[].class);

    sparse = new byte[1 << 20];
    Random randomZero = new Random(43);
    Random randomOne = new Random(63);
    nextVal = 0;
    blkLen = 1;
    for (int i = 0; i < sparse.length - blkLen; i += blkLen) {
      Arrays.fill(sparse, i, i + blkLen, nextVal);
      if (nextVal == 0) {
        nextVal = 1;
        blkLen = randomZero.nextInt(100);
      }
      else {
        nextVal = 0;
        blkLen = randomOne.nextInt(1000);
      }
    }
    measure("byte-array-1M-sparse (10%)", sparse, byte[].class);

    sparse = new byte[1 << 20];
    randomZero = new Random(43);
    randomOne = new Random(63);
    nextVal = 0;
    blkLen = 1;
    for (int i = 0; i < sparse.length - blkLen; i += blkLen) {
      Arrays.fill(sparse, i, i + blkLen, nextVal);
      if (nextVal == 0) {
        nextVal = 1;
        blkLen = randomZero.nextInt(1000);
      }
      else {
        nextVal = 0;
        blkLen = randomOne.nextInt(100);
      }
    }
    measure("byte-array-1M-sparse (90%)", sparse, byte[].class);
  }

  @Test(groups = "benchmark")
  void benchmarkStrings() {
    // half unique, half repeated, so that both the plain and the de-duplicated path are hit
    List<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 2000; i++) {
      strings.add(i % 2 == 0 ? "unique-string-value-" + i : "repeated-string-value-" + (i % 20));
    }
    measure("strings-2000", strings, ArrayList.class);
  }


  /**
   * Measures a workload and prints the result.
   *
   * @param name the name of the workload
   * @param value the value to serialize
   * @param clazz the class to resolve the trip type from
   * @param <T> the type of the value
   */
  private <T> void measure(String name, T value, Class<?> clazz) {
    measure(name, value, clazz, false);
  }

  /**
   * Measures a workload and prints the result.
   *
   * @param name the name of the workload
   * @param value the value to serialize
   * @param clazz the class to resolve the trip type from
   * @param clientWrite true if written by a client and read by a server, false for the other way round
   * @param <T> the type of the value
   */
  private <T> void measure(String name, T value, Class<?> clazz, boolean clientWrite) {
    Rig rig = new Rig(clientWrite);
    @SuppressWarnings("unchecked")
    TripType<T> type = (TripType<T>) TripFactory.getInstance().getType(clazz);

    byte[] encoded = rig.prepare(value, type);
    assertNotNull(encoded);

    double writeNs = run(() -> rig.write(value, type));
    double readNs = run(() -> sink = rig.read(encoded, type));
    double roundTripNs = run(() -> {
      rig.write(value, type);
      sink = rig.read(type);
    });

    // the wire format must not have moved: re-encoding in the steady state is byte-identical
    assertEquals(rig.encodeSteady(value, type), encoded, "encoded form changed for " + name);

    report(name, encoded.length, writeNs, readNs, roundTripNs);
  }

  /**
   * Runs a task repeatedly and returns the median nanoseconds per operation.
   *
   * @param task the task to run
   * @return the median duration of a single operation in nanoseconds
   */
  private double run(Runnable task) {
    long warmupEnd = System.nanoTime() + WARMUP_MILLIS * 1_000_000L;
    long warmupOps = 0;
    while (System.nanoTime() < warmupEnd) {
      task.run();
      warmupOps++;
    }

    // size a batch so that a timed run takes about MEASURE_MILLIS
    int batch = (int) Math.max(1, Math.min(Integer.MAX_VALUE,
                                           warmupOps * MEASURE_MILLIS / Math.max(1, WARMUP_MILLIS)));
    double[] results = new double[RUNS];
    for (int r = 0; r < RUNS; r++) {
      long start = System.nanoTime();
      for (int i = 0; i < batch; i++) {
        task.run();
      }
      results[r] = (System.nanoTime() - start) / (double) batch;
    }
    Arrays.sort(results);
    return results[results.length / 2];
  }

  /**
   * Measures remote calls over a TCP transport on the loopback interface and prints the result.
   * <p>
   * Each call is timed on its own, so that the occasional stall of a single call shows up in the
   * upper percentiles instead of being averaged away.
   *
   * @param scheme the transport scheme, e.g. "trip" or "tripc"
   * @throws Exception if setting up the transports failed
   */
  private void measureRpc(String scheme) throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    URI uri = new URI(scheme + "://127.0.0.1:" + port);
    Transport server = TripFactory.getInstance().createTransport(uri);
    server.accept();
    Transport client = TripFactory.getInstance().createTransport(uri);
    try {
      server.getRegistry().register(new DemoLoginImpl("1.0.0"), "login");
      UUID remoteId = client.getRemoteLookup().lookup("login");
      DemoLogin login = TripFactory.getInstance().createRemoteProxy(remoteId, client, DemoLogin.class);
      DemoSession session = login.login("harr", "0815");

      BenchmarkEntity parent = new BenchmarkEntity(0, null);
      List<BenchmarkEntity> small = new ArrayList<>(10);
      for (int i = 0; i < 10; i++) {
        small.add(new BenchmarkEntity(i, parent));
      }
      List<BenchmarkEntity> large = new ArrayList<>(1000);
      for (int i = 0; i < 1000; i++) {
        large.add(new BenchmarkEntity(i, parent));
      }

      runRpc(scheme + "-call", session::getUsername);
      runRpc(scheme + "-echo-10", () -> session.echo(small));
      runRpc(scheme + "-echo-1000", () -> session.echo(large));
    }
    finally {
      client.close();
      server.close();
    }
  }

  /**
   * Runs a remote call repeatedly and prints the latency distribution.
   *
   * @param name the name of the workload
   * @param call the remote call
   */
  private void runRpc(String name, Supplier<?> call) {
    long warmupEnd = System.nanoTime() + WARMUP_MILLIS * 1_000_000L;
    while (System.nanoTime() < warmupEnd) {
      sink = call.get();
    }
    long[] samples = new long[1024];
    int count = 0;
    long measureEnd = System.nanoTime() + MEASURE_MILLIS * RUNS * 1_000_000L;
    long start;
    while ((start = System.nanoTime()) < measureEnd) {
      sink = call.get();
      if (count == samples.length) {
        samples = Arrays.copyOf(samples, count << 1);
      }
      samples[count++] = System.nanoTime() - start;
    }
    Arrays.sort(samples, 0, count);
    System.out.printf("%-24s calls=%8d   median=%10.1f us   p99=%10.1f us   max=%10.1f us%n",
                      name, count,
                      samples[count / 2] / 1e3,
                      samples[Math.min(count - 1, (int) (count * 0.99))] / 1e3,
                      samples[count - 1] / 1e3);
  }

  private void report(String name, int size, double writeNs, double readNs, double roundTripNs) {
    System.out.printf("%-24s size=%9d B   write=%12.1f ns (%10.0f ops/s)   " +
                      "read=%12.1f ns (%10.0f ops/s)   roundtrip=%12.1f ns (%10.0f ops/s)%n",
                      name, size,
                      writeNs, 1e9 / writeNs,
                      readNs, 1e9 / readNs,
                      roundTripNs, 1e9 / roundTripNs);
  }


  /**
   * A serialize/deserialize rig.
   * <p>
   * This is {@link org.tentackle.trip.impl.EchoTransceiver} without its buffer copy in
   * {@code echo()}, so that the serialize and deserialize phases can be timed on their own. By
   * default, the sending dictionary is a server dictionary, which avoids temporary (client-only)
   * entries. A client rig sends with a client dictionary instead.
   */
  private static final class Rig {

    private final ExposedByteArrayOutputStream bos = new ExposedByteArrayOutputStream(1 << 21);
    private final MutableByteArrayInputStream bis = new MutableByteArrayInputStream();
    private final Serializer serializer;
    private final Deserializer deserializer;
    private final TripStream outStream;
    private final TripStream inStream;

    private Rig(boolean clientWrite) {
      TripFactory factory = TripFactory.getInstance();
      serializer = factory.createSerializer(bos);
      deserializer = factory.createDeserializer(bis);
      outStream = factory.createStream(factory.createDictionary(NIL_UUID, !clientWrite), serializer, deserializer);
      inStream = factory.createStream(factory.createDictionary(NIL_UUID, clientWrite), serializer, deserializer);
    }

    /**
     * Brings both dictionaries into their steady state and returns the bytes to replay.
     * <p>
     * The very first message carries the dictionary entries of every class involved. A long-lived
     * connection sends those once and then only their ordinals, which is the state worth measuring
     * &mdash; and the only state in which the same bytes can be read back repeatedly, since
     * re-adding a dictionary entry is rejected as a duplicate ordinal.
     *
     * @param value the value to serialize
     * @param type the trip type of the value
     * @return the steady-state encoding of the value
     * @param <T> the type of the value
     */
    private <T> byte[] prepare(T value, TripType<T> type) {
      read(encodeSteady(value, type), type);    // first message: carries the dictionary entries
      byte[] steady = encodeSteady(value, type);
      read(steady, type);                       // verify the ordinal-only form reads back
      return steady;
    }

    /**
     * Serializes a value and returns a copy of the bytes.
     *
     * @param value the value to serialize
     * @param type the trip type of the value
     * @return the serialized bytes
     * @param <T> the type of the value
     */
    private <T> byte[] encodeSteady(T value, TripType<T> type) {
      write(value, type);
      return bos.toByteArray();
    }

    /**
     * Serializes a value into the output buffer, without copying it out.
     *
     * @param value the value to serialize
     * @param type the trip type of the value
     * @param <T> the type of the value
     */
    private <T> void write(T value, TripType<T> type) {
      bos.reset();
      outStream.clear();
      type.write(value, outStream);
      serializer.flush();
    }

    /**
     * Deserializes the value of the last {@link #write}, straight from the output buffer.
     *
     * @param type the trip type of the value
     * @return the deserialized value
     * @param <T> the type of the value
     */
    private <T> T read(TripType<T> type) {
      bis.setBuffer(bos.buffer(), 0, bos.size());
      return readCurrent(type);
    }

    /**
     * Deserializes a value.
     *
     * @param data the serialized bytes
     * @param type the trip type of the value
     * @return the deserialized value
     * @param <T> the type of the value
     */
    private <T> T read(byte[] data, TripType<T> type) {
      bis.setBuffer(data);
      return readCurrent(type);
    }

    @SuppressWarnings("unchecked")
    private <T> T readCurrent(TripType<T> type) {
      deserializer.setInputStream(bis);   // discards whatever is left in the read buffer
      inStream.clear();
      OpCode opCode = deserializer.deserializeOpCode();
      if (type instanceof PredefinedArray<?> arrayType) {
        return (T) arrayType.readWithType(inStream, opCode);
      }
      return type.read(inStream, opCode);
    }

  }

  /**
   * A {@link ByteArrayOutputStream} handing out its internal buffer.
   * <p>
   * The round-trip measurement reads back what it just wrote; going through
   * {@link ByteArrayOutputStream#toByteArray()} would charge a megabyte copy to the codec.
   */
  private static final class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

    private ExposedByteArrayOutputStream(int size) {
      super(size);
    }

    private byte[] buffer() {
      return buf;
    }

  }

}
