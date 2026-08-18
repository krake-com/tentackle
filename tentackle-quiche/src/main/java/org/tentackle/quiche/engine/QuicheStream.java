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

package org.tentackle.quiche.engine;

import org.tentackle.quiche.ffm.QuicheConn;
import org.tentackle.quiche.ffm.QuicheException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

/**
 * A bidirectional QUIC stream, presented as a pair of blocking {@link InputStream} / {@link OutputStream}.
 * <p>
 * TRIP serializes onto ordinary blocking streams, so this is where the gap between quiche's
 * "call me again when there is room" API and a blocking stream is closed: a call that cannot make
 * progress parks on the connection's condition until the event loop reports that something moved.
 * <p>
 * Nothing is buffered on this side. A read takes data straight out of quiche, which is what keeps the
 * QUIC flow control meaningful &mdash; see {@link QuicheConnection}. Every read is followed by a flush,
 * because consuming data is what produces the {@code MAX_STREAM_DATA} frame that lets the peer send more.
 * <p>
 * Like the blocking socket streams the TCP transports use, these streams are <em>interrupt-agnostic</em>:
 * an interrupt does not abort a read or a write, it is remembered and re-asserted when the call returns
 * &mdash; see {@link #reassertInterrupt(boolean)}.
 */
public class QuicheStream {

  /**
   * How long a blocked read or write waits before it looks at the connection's state again.
   * <p>
   * The event loop signals the condition whenever anything moves, so this is not how progress is
   * noticed &mdash; it is the upper bound on how long a thread can stay parked on a connection that
   * silently died without anybody signalling.
   */
  public static final long POLL_INTERVAL_MILLIS = 250;

  /**
   * The application error code sent when a half of the stream is closed in an orderly fashion.
   */
  private static final long STREAM_CLOSED = QuicheConnection.STREAM_CLOSED_ERROR_CODE;


  private final QuicheConnection connection;
  private final long streamId;
  private final Input inputStream;
  private final Output outputStream;
  private volatile boolean inputClosed;
  private volatile boolean outputClosed;

  /**
   * Creates a stream.
   *
   * @param connection the connection the stream belongs to
   * @param streamId the stream id
   */
  public QuicheStream(QuicheConnection connection, long streamId) {
    this.connection = connection;
    this.streamId = streamId;
    inputStream = new Input();
    outputStream = new Output();
  }


  /**
   * Gets the stream id.
   *
   * @return the stream id
   */
  public long getStreamId() {
    return streamId;
  }

  /**
   * Gets the connection the stream belongs to.
   *
   * @return the connection
   */
  public QuicheConnection getConnection() {
    return connection;
  }

  /**
   * Gets the stream to read the peer's data from.
   *
   * @return the input stream
   */
  public InputStream getInputStream() {
    return inputStream;
  }

  /**
   * Gets the stream to write to the peer with.
   *
   * @return the output stream
   */
  public OutputStream getOutputStream() {
    return outputStream;
  }

  /**
   * Tells whether the reading half has been closed.
   *
   * @return true if closed
   */
  public boolean isInputClosed() {
    return inputClosed;
  }

  /**
   * Tells whether the writing half has been closed.
   *
   * @return true if closed
   */
  public boolean isOutputClosed() {
    return outputClosed;
  }

  /**
   * Tells the peer to stop sending, discarding whatever has not been read yet.
   *
   * @param errorCode the application error code
   */
  public void abortReading(long errorCode) {
    inputClosed = true;
    shutdown(QuicheConn.SHUTDOWN_READ, errorCode);
  }

  /**
   * Abandons the writing half, discarding whatever has not been acknowledged yet.
   *
   * @param errorCode the application error code
   */
  public void resetStream(long errorCode) {
    outputClosed = true;
    shutdown(QuicheConn.SHUTDOWN_WRITE, errorCode);
  }

  @Override
  public String toString() {
    return "stream " + streamId + " of " + connection;
  }


  /**
   * Shuts one direction of the stream down and forgets the stream once both are gone.
   *
   * @param direction {@link QuicheConn#SHUTDOWN_READ} or {@link QuicheConn#SHUTDOWN_WRITE}
   * @param errorCode the application error code
   */
  private void shutdown(int direction, long errorCode) {
    connection.getLock().lock();
    try {
      if (!connection.getConn().isFreed()) {
        connection.getConn().streamShutdown(streamId, direction, errorCode);
      }
    }
    catch (QuicheException e) {
      // the stream may be gone already, which is exactly what was wanted
    }
    finally {
      connection.getLock().unlock();
    }
    if (inputClosed && outputClosed) {
      connection.removeStream(streamId);
    }
    connection.flush();
  }

  /**
   * Waits for the connection to make progress.
   * <p>
   * Called with the connection's lock held, which {@link java.util.concurrent.locks.Condition#await}
   * releases while waiting.
   * <p>
   * An interrupt does <em>not</em> abort the wait, it is only reported to the caller, which re-asserts
   * it once the read or write is done &mdash; see {@link #reassertInterrupt(boolean)}.
   *
   * @return true if the waiting thread was interrupted
   */
  private boolean awaitProgress() {
    try {
      connection.getCondition().await(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
      return false;
    }
    catch (InterruptedException e) {
      return true;
    }
  }

  /**
   * Re-asserts an interrupt that arrived while a read or write was blocked.
   * <p>
   * A blocking socket stream ignores the interrupt status of the thread reading or writing it, and the
   * layers above rely on that: Tentackle's task dispatchers interrupt their own thread to wake it from a
   * sleep whenever work is queued, and such an interrupt may well land while the thread happens to be
   * inside a remote invocation. Failing the operation would tear the TRIP connection down for a reason
   * that has nothing to do with the connection, so the interrupt is withheld until the data has been
   * moved and handed to the caller here &mdash; where it belongs, since the next sleep is meant to
   * return immediately. A connection that really is gone releases its readers and writers through
   * {@link QuicheConnection#checkUsable()} instead, so nothing waits forever.
   *
   * @param interrupted true if an interrupt was caught while waiting
   */
  private void reassertInterrupt(boolean interrupted) {
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Turns a failure of the underlying connection into an {@link IOException}.
   *
   * @param what what was being attempted
   * @param cause the failure
   * @return the exception to throw
   */
  private IOException ioException(String what, QuicheException cause) {
    return new IOException(what + " on " + this + " failed", cause);
  }


  /**
   * The reading half of the stream.
   */
  private final class Input extends InputStream {

    @Override
    public int read() throws IOException {
      byte[] one = new byte[1];
      int read = read(one, 0, 1);
      return read < 0 ? -1 : Byte.toUnsignedInt(one[0]);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      java.util.Objects.checkFromIndexSize(off, len, b.length);
      if (len == 0) {
        return 0;
      }
      if (inputClosed) {
        throw new IOException("reading from a closed " + QuicheStream.this);
      }

      boolean[] fin = new boolean[1];
      boolean interrupted = false;
      try {
        while (true) {
          long read;
          connection.getLock().lock();
          try {
            connection.checkUsable();
            QuicheConn conn = connection.getConn();
            if (conn.streamFinished(streamId)) {
              return -1;
            }
            MemorySegment buffer = connection.getStreamBuffer();
            int chunk = Math.min(len, QuicheConnection.STREAM_BUFFER_SIZE);
            // ERR_DONE leaves the flag untouched, so it must not carry over from the last round
            fin[0] = false;
            read = conn.streamRecv(streamId, buffer, chunk, fin);
            if (read > 0) {
              MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, b, off, (int) read);
            }
          }
          catch (QuicheException e) {
            throw ioException("reading", e);
          }
          finally {
            connection.getLock().unlock();
          }

          if (read > 0) {
            // reading is what grants the peer further credits, so the frames doing so must go out
            connection.flush();
            return (int) read;
          }
          if (fin[0]) {
            connection.flush();
            return -1;
          }

          // nothing available: park until the event loop reports that something moved
          connection.getLock().lock();
          try {
            connection.checkUsable();
            interrupted |= awaitProgress();
          }
          catch (QuicheException e) {
            throw ioException("reading", e);
          }
          finally {
            connection.getLock().unlock();
          }

          if (inputClosed) {
            // another thread closed this half while this one was parked
            throw new IOException("reading from a closed " + QuicheStream.this);
          }
        }
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public int available() {
      connection.getLock().lock();
      try {
        QuicheConn conn = connection.getConn();
        // quiche does not report how much is buffered, only whether there is anything at all
        return !conn.isFreed() && conn.isReadable() ? 1 : 0;
      }
      catch (RuntimeException e) {
        return 0;
      }
      finally {
        connection.getLock().unlock();
      }
    }

    @Override
    public void close() {
      if (!inputClosed) {
        abortReading(STREAM_CLOSED);
      }
    }
  }


  /**
   * The writing half of the stream.
   */
  private final class Output extends OutputStream {

    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      java.util.Objects.checkFromIndexSize(off, len, b.length);
      if (outputClosed) {
        throw new IOException("writing to a closed " + QuicheStream.this);
      }

      int written = 0;
      boolean interrupted = false;
      try {
        while (written < len) {
          long accepted;
          connection.getLock().lock();
          try {
            connection.checkUsable();
            MemorySegment buffer = connection.getStreamBuffer();
            int chunk = Math.min(len - written, QuicheConnection.STREAM_BUFFER_SIZE);
            MemorySegment.copy(b, off + written, buffer, ValueLayout.JAVA_BYTE, 0, chunk);
            accepted = connection.getConn().streamSend(streamId, buffer, chunk, false);
          }
          catch (QuicheException e) {
            throw ioException("writing", e);
          }
          finally {
            connection.getLock().unlock();
          }

          // whatever quiche accepted has to go on the wire before the peer can grant further credits,
          // so this flush happens whether the write made progress or not
          connection.flush();

          if (accepted > 0) {
            written += (int) accepted;
            continue;
          }

          // the flow control window is exhausted: park until the peer grants more
          connection.getLock().lock();
          try {
            connection.checkUsable();
            interrupted |= awaitProgress();
          }
          catch (QuicheException e) {
            throw ioException("writing", e);
          }
          finally {
            connection.getLock().unlock();
          }

          if (outputClosed) {
            // another thread closed this half while this one was parked
            throw new IOException("writing to a closed " + QuicheStream.this);
          }
        }
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public void flush() {
      connection.flush();
    }

    @Override
    public void close() throws IOException {
      if (outputClosed) {
        return;
      }
      outputClosed = true;
      // an empty write with the fin flag is what tells the peer that the stream ended.
      // The connection's stream buffer is passed rather than a NULL pointer: quiche builds a Rust
      // slice from it, and slice::from_raw_parts requires a non-null pointer even for length 0.
      connection.getLock().lock();
      try {
        if (!connection.getConn().isFreed()) {
          connection.getConn().streamSend(streamId, connection.getStreamBuffer(), 0, true);
        }
      }
      catch (QuicheException e) {
        // the peer may have reset the stream meanwhile, in which case there is nothing left to end
      }
      finally {
        connection.getLock().unlock();
      }
      connection.flush();
      if (inputClosed) {
        connection.removeStream(streamId);
      }
    }
  }

}
