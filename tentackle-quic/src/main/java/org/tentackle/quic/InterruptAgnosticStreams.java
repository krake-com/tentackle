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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Objects;

/**
 * Wrappers making KWIK's stream operations as interrupt-agnostic as the blocking socket streams
 * the TCP transports use.
 * <p>
 * KWIK does not tolerate the interrupt status of the thread reading or writing one of its streams: a write
 * that has to wait for room in the stream's send buffer fails with an {@code InterruptedIOException}, and a
 * read clears the interrupt status without ever restoring it. Neither is acceptable here. Tentackle's task
 * dispatchers interrupt their own thread whenever work is queued, to cut a pending sleep short,
 * and such an interrupt regularly lands while that thread is inside a remote invocation
 * ({@code DbModificationTracker} polls from its dispatcher thread): failing the call would tear down a
 * perfectly healthy TRIP connection, and swallowing the interrupt would let the dispatcher sleep through
 * the work that was just queued for it.
 * <p>
 * The interrupt status is therefore withheld from KWIK for the duration of each operation and restored
 * afterward.
 * <p>
 * This covers an interrupt that is <em>pending when the operation starts</em>. One that arrives while the
 * thread is inside KWIK is still handled by KWIK, since the operation runs on the calling thread (as of
 * KWIK 0.12):
 * <ul>
 *   <li>a write waiting for room in the send buffer fails with an {@code InterruptedIOException}. KWIK
 *   gives up before it takes any of the data, so the write is simply repeated, see {@link Output}.</li>
 *   <li>a read swallows it: the read goes on and returns normally, but the interrupt is lost. This is the
 *   one gap left, and the common case, because a remote invocation spends nearly all of its time waiting
 *   for the response. Nothing can be done about it here: once KWIK has cleared the interrupt status,
 *   nothing tells that there ever was an interrupt.</li>
 * </ul>
 */
final class InterruptAgnosticStreams {

  /**
   * The largest number of bytes handed to KWIK by a single write.
   * <p>
   * Must not exceed the send buffer of a KWIK stream, which is fixed at 50 kB. Well below that, since
   * KWIK makes a write wait until all of its data fits into the buffer.
   */
  static final int WRITE_CHUNK_SIZE = 16 * 1024;

  /**
   * Wraps an input stream.
   *
   * @param delegate the stream to read from
   * @return the wrapped stream
   */
  static InputStream wrap(InputStream delegate) {
    return new Input(delegate);
  }

  /**
   * Wraps an output stream.
   *
   * @param delegate the stream to write to
   * @return the wrapped stream
   */
  static OutputStream wrap(OutputStream delegate) {
    return new Output(delegate);
  }


  private InterruptAgnosticStreams() {
  }

  /**
   * Restores an interrupt withheld for the duration of an operation.
   *
   * @param interrupted true if the thread was interrupted before the operation started
   */
  private static void reassertInterrupt(boolean interrupted) {
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }


  /**
   * An input stream that does not let the interrupt status reach its delegate.
   */
  private static final class Input extends FilterInputStream {

    private Input(InputStream delegate) {
      super(delegate);
    }

    @Override
    public int read() throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        return in.read();
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        return in.read(b, off, len);
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }
  }


  /**
   * An output stream that does not let the interrupt status reach its delegate, and repeats a write the
   * delegate gave up on because of an interrupt.
   * <p>
   * Repeating is safe because KWIK takes the data of a write all or nothing, as long as it fits into the
   * stream's send buffer: an interrupted wait for room in that buffer ends before anything is queued.
   * A larger write is split by KWIK itself and could be interrupted halfway, hence the data is handed
   * over in chunks of at most {@link #WRITE_CHUNK_SIZE} bytes.
   * <p>
   * KWIK interrupts a waiting writer by itself, too, when the stream is reset or the connection is gone.
   * It marks the stream accordingly beforehand, so that the repeated write fails right away with the
   * {@code IOException} telling so.
   */
  private static final class Output extends FilterOutputStream {

    private Output(OutputStream delegate) {
      super(delegate);
    }

    @Override
    public void write(int b) throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        for (;;) {
          try {
            out.write(b);
            return;
          }
          catch (InterruptedIOException e) {
            assertRepeatable(e);
            interrupted = true;
          }
        }
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      Objects.checkFromIndexSize(off, len, b.length);
      boolean interrupted = Thread.interrupted();
      try {
        // FilterOutputStream would write byte by byte, which is slow
        int end = off + len;
        while (off < end) {
          int chunk = Math.min(end - off, WRITE_CHUNK_SIZE);
          try {
            out.write(b, off, chunk);
            off += chunk;
          }
          catch (InterruptedIOException e) {
            assertRepeatable(e);
            interrupted = true;   // same chunk again
          }
        }
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public void flush() throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        out.flush();
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    /**
     * Makes sure that a write the delegate gave up on can be repeated, and withholds the interrupt
     * that caused it from the next attempt.
     *
     * @param e the exception thrown by the delegate
     * @throws InterruptedIOException the given exception, if it is not about an interrupt or the delegate
     *         took some of the data already
     */
    private static void assertRepeatable(InterruptedIOException e) throws InterruptedIOException {
      if (e instanceof SocketTimeoutException || e.bytesTransferred != 0) {
        throw e;
      }
      Thread.interrupted();   // KWIK may or may not have cleared it
    }
  }

}
