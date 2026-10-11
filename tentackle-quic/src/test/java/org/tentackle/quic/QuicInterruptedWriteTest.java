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

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests against a real KWIK stream that a write blocked on flow control survives interrupts.
 * <p>
 * {@link InterruptAgnosticStreamsTest} covers the logic of repeating a write. This test is about the
 * assumption that logic rests on: that KWIK fails an interrupted write without having taken any of its
 * data, so that repeating it neither loses nor duplicates a byte.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicInterruptedWriteTest {

  /** far more than the stream's flow control window plus KWIK's send buffer. */
  private static final int PAYLOAD_SIZE = 1_000_000;

  /** the number of interrupts sent to the blocked writer. */
  private static final int INTERRUPTS = 5;

  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  @Test(timeOut = 120000)
  public void blockedWriteSurvivesInterrupts() throws Exception {
    Reporter.log("blockedWriteSurvivesInterrupts", true);
    byte[] payload = new byte[PAYLOAD_SIZE];
    new Random(4711).nextBytes(payload);

    int port = QuicTestSupport.freeUdpPort();
    CountDownLatch startReading = new CountDownLatch(1);
    CompletableFuture<byte[]> received = new CompletableFuture<>();
    // a server that does not read until told to, so that the client runs out of flow control credits
    QuicTripTransport server = new QuicTripTransport(URI.create(
      "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore))) {

      @Override
      public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
        return () -> {
          try {
            startReading.await();
            received.complete(inputStream.readAllBytes());
          }
          catch (Throwable t) {
            received.completeExceptionally(t);
          }
        };
      }
    };
    QuicClientConnection client = null;
    try {
      server.accept();
      Thread.sleep(1000);   // let the server start accepting connections

      client = QuicClientConnection.newBuilder()
                                   .uri(URI.create("tripq://localhost:" + port))
                                   .applicationProtocol(QuicTripTransport.PROTOCOL)
                                   .noServerCertificateCheck()
                                   .build();
      client.connect();
      QuicStream stream = client.createStream(true);
      OutputStream out = InterruptAgnosticStreams.wrap(stream.getOutputStream());

      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicBoolean interruptKept = new AtomicBoolean();
      Thread writer = new Thread(() -> {
        try {
          out.write(payload);
          interruptKept.set(Thread.interrupted());
          out.close();
        }
        catch (Throwable t) {
          failure.set(t);
        }
      }, "interrupted writer");
      writer.start();

      for (int i = 0; i < INTERRUPTS; i++) {
        awaitBlocked(writer);
        writer.interrupt();
        Thread.sleep(100);    // let it fail and wait again
      }
      awaitBlocked(writer);   // still writing: the interrupts did not make it give up

      startReading.countDown();
      writer.join(60000);

      assertNull(failure.get(), "the write failed: " + failure.get());
      assertTrue(interruptKept.get(), "the interrupt was swallowed");
      assertEquals(received.get(60, TimeUnit.SECONDS), payload, "data lost or duplicated");
    }
    finally {
      startReading.countDown();
      if (client != null) {
        client.close();
      }
      server.close();
    }
  }

  /**
   * Waits until the given thread is blocked, which the writer is only while waiting for room in
   * KWIK's send buffer.
   *
   * @param thread the thread
   * @throws InterruptedException if interrupted while waiting
   */
  private static void awaitBlocked(Thread thread) throws InterruptedException {
    while (thread.getState() != Thread.State.WAITING) {
      assertTrue(thread.isAlive(), "the writer terminated instead of waiting for flow control credits");
      Thread.sleep(10);
    }
  }

}
