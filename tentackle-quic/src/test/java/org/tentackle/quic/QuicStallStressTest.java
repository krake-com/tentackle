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

package org.tentackle.quic;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Stress test detecting remote calls that stall forever on a healthy QUIC connection.
 * <p>
 * Several threads run remote calls of mixed sizes concurrently, each on its own pooled QUIC stream.
 * A call that does not return within {@link #STALL_SECONDS} has stalled: on the loopback interface
 * a call takes well under a millisecond.
 * <p>
 * As of KWIK 0.12 this test <em>fails</em> within seconds, which is why it is not part of the regular
 * build (group {@code stress}, run with {@code mvn -Pstress test}). KWIK's
 * {@code StreamOutputStreamImpl.scheduleSendStreamFrameRequest()} queues the request to send stream data
 * before it counts it. If the sender thread serves the request in between and the data does not fit into
 * a single frame, the follow-up request is not queued, and the remaining data stays in the stream's send
 * buffer until the application writes to the stream again &mdash; which a TRIP call never does before it
 * got its response. The reader at the other end, be it the server waiting for the rest of the call or the
 * client waiting for the rest of the response, waits forever, while the QUIC connection itself stays alive.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicStallStressTest {

  /** The seconds after which a pending call is considered stalled. */
  private static final int STALL_SECONDS = 10;

  /** The seconds to run, tunable with {@code -Dquic.stress.seconds}. */
  private static final int RUN_SECONDS = Integer.getInteger("quic.stress.seconds", 120);

  /** The number of concurrently calling threads, tunable with {@code -Dquic.stress.threads}. */
  private static final int THREADS = Integer.getInteger("quic.stress.threads", 4);

  /** The largest payload in bytes. Anything beyond a QUIC packet has to be split into several frames. */
  private static final int MAX_PAYLOAD = 50_000;

  private Path keyStore;

  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  @Test(groups = "stress")
  public void concurrentCallsNeverStall() throws Exception {
    Reporter.log("concurrentCallsNeverStall", true);
    int port = QuicTestSupport.freeUdpPort();
    Transport server = TripFactory.getInstance().createTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    Transport client = null;
    try {
      server.accept();
      Thread.sleep(1000);   // wait for the server to start accepting connections
      assertTrue(server.isAccepting());
      server.getRegistry().register(new EchoServiceImpl("echo"), "echo");

      client = TripFactory.getInstance().createTransport(URI.create(
        QuicTripTransport.PROTOCOL + "://localhost:" + port + "?certcheck=false"));
      EchoService remote = client.lookup(EchoService.class, "echo");

      AtomicLong[] pendingSince = new AtomicLong[THREADS];   // nanoTime the pending call started at, 0 if none
      AtomicLong calls = new AtomicLong();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      for (int i = 0; i < THREADS; i++) {
        AtomicLong since = pendingSince[i] = new AtomicLong();
        Random random = new Random(i);
        Thread caller = new Thread(() -> {
          try {
            for (;;) {
              // mostly small calls, like an application's, mixed with ones spanning many QUIC packets
              int size = random.nextInt(4) == 0 ? random.nextInt(MAX_PAYLOAD) : random.nextInt(3000);
              since.set(System.nanoTime());
              Payload result = remote.duplicate(Payload.of("stress", size));
              since.set(0);
              if (result.blob().length != size) {
                throw new IllegalStateException("expected " + size + " bytes, got " + result.blob().length);
              }
              calls.incrementAndGet();
            }
          }
          catch (Throwable t) {
            failure.compareAndSet(null, t);
          }
        }, "QUIC stress caller " + i);
        caller.setDaemon(true);   // a stalled caller never returns, it must not keep the JVM alive
        caller.start();
      }

      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(RUN_SECONDS);
      while (System.nanoTime() < end) {
        Thread.sleep(500);
        assertNull(failure.get(), "a caller failed");
        long now = System.nanoTime();
        for (int i = 0; i < THREADS; i++) {
          long since = pendingSince[i].get();
          assertTrue(since == 0 || now - since < TimeUnit.SECONDS.toNanos(STALL_SECONDS),
                     "caller " + i + " stalled for more than " + STALL_SECONDS + " seconds after " +
                     calls.get() + " calls");
        }
      }
      Reporter.log(calls.get() + " calls within " + RUN_SECONDS + " seconds without a stall", true);
    }
    finally {
      if (client != null) {
        client.close();
      }
      server.close();
    }
  }

}
