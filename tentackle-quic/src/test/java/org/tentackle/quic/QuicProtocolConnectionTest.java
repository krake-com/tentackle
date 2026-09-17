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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link QuicProtocolConnection}, the server-side dispatcher that turns a peer-initiated
 * QUIC stream into a TRIP connection handler running on a virtual thread.
 * <p>
 * The transport really binds a UDP port (a QUIC transport cannot enter server mode otherwise),
 * but the streams handed to it are {@link FakeQuicStream}s, so the dispatching itself is observed
 * in isolation.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicProtocolConnectionTest {

  private static final long TIMEOUT_SECONDS = 10;

  private Path keyStore;
  private RecordingTransport transport;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
    transport = new RecordingTransport(URI.create(
      "tripq://localhost:" + QuicTestSupport.freeUdpPort() + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    transport.accept();
    // wait for the server to start accepting connections, otherwise the client will fail to connect
    Thread.sleep(1000);
  }

  @AfterClass
  public void tearDown() throws Exception {
    transport.close();
    Files.deleteIfExists(keyStore);
  }


  @Test(timeOut = 60000)
  public void dispatchesThePeerInitiatedStreamToAConnectionHandler() throws Exception {
    Reporter.log("dispatchesThePeerInitiatedStreamToAConnectionHandler", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    transport.expectHandler();

    new QuicProtocolConnection(transport).acceptPeerInitiatedStream(quicStream);

    assertTrue(transport.awaitHandler(), "the connection handler was not invoked");
    assertSame(transport.inputStream.get(), quicStream.getInputStream());
    assertSame(transport.outputStream.get(), quicStream.getOutputStream());
    assertEquals(transport.connectionName.get(), quicStream.toString());

    // the handler ran on a virtual thread from the transport's connection thread pool
    assertTrue(transport.virtualThread.get(), "handler did not run on a virtual thread");

    awaitStreamClosed(quicStream);
  }

  /**
   * A handler blowing up must not leak the QUIC stream &mdash; it is closed in a {@code finally} block.
   */
  @Test(timeOut = 60000)
  public void closesTheStreamWhenTheHandlerFails() throws Exception {
    Reporter.log("closesTheStreamWhenTheHandlerFails", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    transport.expectHandler();
    transport.failWith(new IllegalStateException("handler exploded"));

    new QuicProtocolConnection(transport).acceptPeerInitiatedStream(quicStream);

    assertTrue(transport.awaitHandler(), "the connection handler was not invoked");
    awaitStreamClosed(quicStream);
  }

  /**
   * A stream that cannot be dispatched at all must be closed right away.
   * <p>
   * KWIK calls this from a shared callback thread and discards whatever it throws, so the failure
   * would go unnoticed, and the client would wait for an answer on that stream forever.
   */
  @Test(timeOut = 60000)
  public void closesTheStreamWhenItCannotBeDispatched() throws Exception {
    Reporter.log("closesTheStreamWhenItCannotBeDispatched", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    // a transport that never entered server mode has no connection thread pool to dispatch to
    QuicTripTransport closedTransport = new QuicTripTransport(URI.create("tripq://localhost:12345"));

    new QuicProtocolConnection(closedTransport).acceptPeerInitiatedStream(quicStream);

    assertTrue(quicStream.isOutputClosed(), "the QUIC output stream was not closed");
    assertTrue(quicStream.isInputClosed(), "the QUIC input stream was not closed");
  }

  /**
   * Waits until both halves of the stream are closed, which happens asynchronously on the
   * virtual thread after the handler returned.
   */
  private void awaitStreamClosed(FakeQuicStream quicStream) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      if (quicStream.isInputClosed() && quicStream.isOutputClosed()) {
        return;
      }
      Thread.sleep(10);
    }
    assertTrue(quicStream.isOutputClosed(), "the QUIC output stream was not closed");
    assertTrue(quicStream.isInputClosed(), "the QUIC input stream was not closed");
  }


  /**
   * A transport whose connection handler records what it was given instead of speaking TRIP.
   */
  private static class RecordingTransport extends QuicTripTransport {

    private final AtomicReference<String> connectionName = new AtomicReference<>();
    private final AtomicReference<InputStream> inputStream = new AtomicReference<>();
    private final AtomicReference<OutputStream> outputStream = new AtomicReference<>();
    private final AtomicReference<Boolean> virtualThread = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    RecordingTransport(URI uri) {
      super(uri);
    }

    void expectHandler() {
      latch.set(new CountDownLatch(1));
      failure.set(null);
    }

    void failWith(RuntimeException exception) {
      failure.set(exception);
    }

    boolean awaitHandler() throws InterruptedException {
      return latch.get().await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public Runnable createConnectionHandler(String name, InputStream in, OutputStream out) {
      return () -> {
        connectionName.set(name);
        inputStream.set(in);
        outputStream.set(out);
        virtualThread.set(Thread.currentThread().isVirtual());
        latch.get().countDown();
        RuntimeException exception = failure.get();
        if (exception != null) {
          throw exception;
        }
      };
    }

  }

}
