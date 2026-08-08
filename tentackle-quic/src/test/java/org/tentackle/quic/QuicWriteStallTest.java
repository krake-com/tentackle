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

import org.tentackle.trip.Connection;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that a remote call cannot block forever when the QUIC connection's flow control is wedged.
 * <p>
 * A QUIC sender has to stop once it has used up the credits the peer granted it, and it is the peer that
 * has to hand out more. KWIK emits a connection-level {@code MAX_DATA} only while it is reading, never
 * retransmits one whose datagram was lost, and ignores the {@code DATA_BLOCKED} frame the blocked sender
 * keeps sending. So if the last update before the sender runs out of credits goes missing &mdash; a single
 * dropped datagram, which is what an overloaded machine on a lossy loopback produces &mdash; nothing will
 * ever produce another one: the receiver has read everything there was to read. The QUIC connection stays
 * established all the while, since acknowledgements keep flowing and no idle timeout fires, and the calling
 * thread waits for credits that never come.
 * <p>
 * Reproducing the lost datagram itself is not practical, but the state it leaves behind is: as far as the
 * sender can tell, a peer that stops reading and a peer whose credit update was lost are the same thing.
 * That is what these tests set up, with a tiny {@code streamwindow} so the sender runs out of credits
 * quickly.
 *
 * @see GuardedQuicOutputStream
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicWriteStallTest {

  /** the write timeout of the client, kept short so the tests do not take forever. */
  private static final int WRITE_TIMEOUT_SECONDS = 3;

  /** larger than KWIK's 50kB stream send buffer plus the flow control window, so the write really blocks. */
  private static final int PAYLOAD_SIZE = 256 * 1024;

  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  /**
   * A call to a peer that stopped reading must fail once the write timeout elapsed, instead of parking
   * the calling thread for good.
   */
  @Test(timeOut = 120_000)
  public void failsTheCallWhenThePeerStopsReading() throws Exception {
    Reporter.log("failsTheCallWhenThePeerStopsReading", true);
    int port = QuicTestSupport.freeUdpPort();
    DeafTransport server = startServer(port);
    Transport client = createClient(port);
    try {
      EchoService remote = client.lookup(EchoService.class, "echo");
      assertEquals(remote.add(17, 25), 42);   // the connection works before the peer goes deaf

      server.goDeaf();
      long started = System.nanoTime();
      expectThrows(RuntimeException.class, () -> remote.echo(Payload.of("stalled", PAYLOAD_SIZE)));
      long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

      assertTrue(elapsedSeconds < 60,
                 "the call took " + elapsedSeconds + " seconds, far beyond the configured write timeout of " +
                 WRITE_TIMEOUT_SECONDS + " seconds");
    }
    finally {
      client.close();
      server.release();
      server.close();
    }
  }

  /**
   * The wedged QUIC connection must be discarded, not just the stream that ran into it: its flow control
   * credits cannot be recovered, so every other stream multiplexed over it would stall as well. Dropping
   * it makes the pool discard the connections belonging to it and establish a replacement.
   */
  @Test(timeOut = 120_000)
  public void discardsTheWedgedQuicConnection() throws Exception {
    Reporter.log("discardsTheWedgedQuicConnection", true);
    int port = QuicTestSupport.freeUdpPort();
    DeafTransport server = startServer(port);
    Transport client = createClient(port);
    try {
      EchoService remote = client.lookup(EchoService.class, "echo");
      assertEquals(remote.add(17, 25), 42);

      // a second pooled connection, i.e. a second stream on the very same QUIC connection
      Connection spectator = client.getConnection();
      client.putConnection(spectator);
      assertTrue(spectator.isOpen(), "the pooled connection should be usable before the stall");

      server.goDeaf();
      expectThrows(RuntimeException.class, () -> remote.echo(Payload.of("stalled", PAYLOAD_SIZE)));

      assertFalse(spectator.isOpen(),
                  "a connection multiplexed over the wedged QUIC connection is still handed out by the pool");
    }
    finally {
      client.close();
      server.release();
      server.close();
    }
  }


  /**
   * Starts a server that speaks TRIP normally until it is told to stop reading.
   *
   * @param port the UDP port to bind
   * @return the server transport
   */
  private DeafTransport startServer(int port) {
    DeafTransport server = new DeafTransport(URI.create(
      "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore) +
      "&streamwindow=" + QuicTripTransport.MIN_STREAM_WINDOW));
    server.accept();
    server.getRegistry().register(new EchoServiceImpl("echo"), "echo");
    return server;
  }

  /**
   * Creates a client for the given port.
   *
   * @param port the UDP port of the server
   * @return the client transport
   */
  private Transport createClient(int port) {
    return TripFactory.getInstance().createTransport(URI.create(
      "tripq://localhost:" + port + "?idle=1&streamwindow=" + QuicTripTransport.MIN_STREAM_WINDOW +
      "&writetimeout=" + WRITE_TIMEOUT_SECONDS));
  }


  /**
   * A server transport whose connection handlers can be made to stop reading from their QUIC streams.
   * <p>
   * The handler itself is the regular one, so the TRIP conversation is real: only the bytes stop being
   * consumed, which is exactly the state a lost credit update leaves the sender in.
   */
  private static class DeafTransport extends QuicTripTransport {

    private final CountDownLatch deaf = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    DeafTransport(URI uri) {
      super(uri);
    }

    /** Makes every connection handler stop reading, with immediate effect. */
    void goDeaf() {
      deaf.countDown();
    }

    /** Lets the handlers return, so that closing the transport does not have to wait for them. */
    void release() {
      released.countDown();
    }

    @Override
    public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
      return super.createConnectionHandler(connectionName, new DeafInputStream(inputStream), outputStream);
    }

    /**
     * Delivers bytes until the transport goes deaf, and blocks from then on.
     */
    private class DeafInputStream extends FilterInputStream {

      DeafInputStream(InputStream inputStream) {
        super(inputStream);
      }

      @Override
      public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        if (deaf.getCount() == 0) {
          try {
            released.await();
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IOException("deaf");
        }
        return in.read(bytes, offset, length);
      }

    }

  }

}
