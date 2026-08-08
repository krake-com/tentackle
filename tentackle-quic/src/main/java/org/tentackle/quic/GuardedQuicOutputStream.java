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

import tech.kwik.core.QuicStream;

import org.tentackle.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The output stream of a QUIC stream, guarded against a wedged QUIC connection.
 * <p>
 * Writing to a QUIC stream blocks whenever the peer's flow control window is exhausted, and it blocks
 * <em>forever</em> if the credit update that would reopen the window never arrives. That is not a
 * theoretical case: KWIK advertises a connection-level {@code MAX_DATA} only while it is reading, it does
 * not retransmit the frame when the datagram carrying it is lost, and it ignores the {@code DATA_BLOCKED}
 * frame the blocked sender keeps sending (see the flow control section of {@code quic.md}). Losing the
 * <em>last</em> update of a burst therefore deadlocks both peers: the sender waits for credits, and the
 * receiver, having read everything there was to read, never produces another update. The QUIC connection
 * stays established all the while &mdash; acknowledgements keep flowing, so neither peer's idle timeout
 * ever fires &mdash; and without this guard the calling thread would hang for good.
 * <p>
 * The guard arms a watchdog for the duration of each write. If the write has not completed when the
 * watchdog fires, the QUIC stream is reset, which unblocks the writer with an {@link IOException} instead
 * of leaving it parked, and the stall action is run to discard the underlying QUIC connection: its
 * flow control credits cannot be recovered, so everything multiplexed over it is lost anyway and a
 * replacement connection has to be established.
 *
 * @see QuicTripTransport#getWriteTimeoutSeconds()
 */
public class GuardedQuicOutputStream extends OutputStream {

  /**
   * The application error code sent in the {@code RESET_STREAM} frame of a stalled stream.
   * <p>
   * QUIC does not define application error codes ({@code RFC-9000, 20.2}), they are up to the
   * application protocol. TRIP does not interpret them, so any non-zero value telling a reset caused
   * by this guard apart from an ordinary one will do.
   */
  public static final long STALLED_ERROR_CODE = 1;

  private static final Logger LOGGER = Logger.get(GuardedQuicOutputStream.class);


  private final QuicStream quicStream;
  private final OutputStream outputStream;
  private final long timeoutMillis;
  private final ScheduledExecutorService watchdog;
  private final Runnable stallAction;
  private volatile boolean stalled;   // volatile: set by the watchdog thread, read by the writing thread

  /**
   * Creates a guarded output stream.
   *
   * @param quicStream the QUIC stream to write to
   * @param timeoutSeconds the number of seconds a single write may block before the stream is reset, &gt; 0
   * @param watchdog the scheduler running the watchdog
   * @param stallAction invoked when a write stalled, to discard the wedged QUIC connection
   */
  public GuardedQuicOutputStream(QuicStream quicStream, int timeoutSeconds,
                                 ScheduledExecutorService watchdog, Runnable stallAction) {
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("write timeout must be > 0 seconds, got " + timeoutSeconds);
    }
    this.quicStream = Objects.requireNonNull(quicStream, "missing QUIC stream");
    this.outputStream = quicStream.getOutputStream();
    this.timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
    this.watchdog = Objects.requireNonNull(watchdog, "missing watchdog");
    this.stallAction = Objects.requireNonNull(stallAction, "missing stall action");
  }

  @Override
  public void write(int value) throws IOException {
    write(new byte[] { (byte) value }, 0, 1);
  }

  @Override
  public void write(byte[] bytes) throws IOException {
    write(bytes, 0, bytes.length);
  }

  @Override
  public void write(byte[] bytes, int offset, int length) throws IOException {
    ScheduledFuture<?> alarm = watchdog.schedule(this::stall, timeoutMillis, TimeUnit.MILLISECONDS);
    try {
      outputStream.write(bytes, offset, length);
    }
    catch (IOException e) {
      throw translate(e);
    }
    finally {
      alarm.cancel(false);
    }
  }

  @Override
  public void flush() throws IOException {
    // KWIK's flush is a no-op (it sends as soon as it may), so there is nothing to guard here.
    try {
      outputStream.flush();
    }
    catch (IOException e) {
      throw translate(e);
    }
  }

  @Override
  public void close() throws IOException {
    // Closing only appends the end-of-stream marker to the send buffer, it does not wait for it
    // to be transmitted, hence it cannot block either.
    outputStream.close();
  }

  @Override
  public String toString() {
    return "guarded " + quicStream;
  }


  /**
   * Aborts a write that has been blocking for longer than the timeout.
   * <p>
   * Runs on the watchdog thread. Resetting the stream comes first: it is what unparks the writer, and
   * the stall action may well have to wait for the writer to release a lock it is holding.
   */
  private void stall() {
    stalled = true;
    LOGGER.warning("no progress writing to {0} within {1} seconds -> resetting the stream and " +
                   "discarding the QUIC connection", quicStream, timeoutMillis / 1000);
    try {
      quicStream.resetStream(STALLED_ERROR_CODE);
    }
    catch (RuntimeException e) {
      LOGGER.warning("resetting the stalled " + quicStream + " failed", e);
    }
    try {
      stallAction.run();
    }
    catch (RuntimeException e) {
      LOGGER.warning("discarding the QUIC connection of the stalled " + quicStream + " failed", e);
    }
  }

  /**
   * Turns the failure a reset stream provokes into a message telling what really happened.
   * <p>
   * KWIK unblocks a parked writer by interrupting it, and depending on where exactly the interrupt lands
   * it may be left pending on the thread. Since it was this guard that caused it, it is cleared here
   * rather than being passed on to the caller, which never asked to be interrupted.
   *
   * @param e the failure reported by the underlying stream
   * @return the exception to throw
   */
  private IOException translate(IOException e) {
    if (stalled) {
      Thread.interrupted();
      return new IOException("write to " + quicStream + " stalled for more than " + timeoutMillis / 1000 +
                             " seconds: the QUIC connection is wedged and has been discarded", e);
    }
    return e;
  }

}
