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
import java.io.OutputStream;

/**
 * Wrappers making KWIK's stream operations as interrupt-agnostic as the blocking socket streams
 * the TCP transports use.
 * <p>
 * KWIK does not tolerate the interrupt status of the thread reading or writing one of its streams: a write
 * that has to wait for flow control credits fails with an {@code InterruptedIOException}, and a blocked
 * read consumes the interrupt without ever restoring it. Neither is acceptable here. Tentackle's task
 * dispatchers interrupt their own thread whenever work is queued, to cut a pending sleep short,
 * and such an interrupt regularly lands while that thread is inside a remote invocation
 * ({@code DbModificationTracker} polls from its dispatcher thread): failing the call would tear down a
 * perfectly healthy TRIP connection, and swallowing the interrupt would let the dispatcher sleep through
 * the work that was just queued for it.
 * <p>
 * The interrupt status is therefore withheld from KWIK for the duration of each operation and restored
 * afterward. An interrupt that arrives <em>while</em> KWIK is already blocked is still handled by KWIK,
 * which no wrapper can change.
 */
final class InterruptAgnosticStreams {

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
   * An output stream that does not let the interrupt status reach its delegate.
   */
  private static final class Output extends FilterOutputStream {

    private Output(OutputStream delegate) {
      super(delegate);
    }

    @Override
    public void write(int b) throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        out.write(b);
      }
      finally {
        reassertInterrupt(interrupted);
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      boolean interrupted = Thread.interrupted();
      try {
        // FilterOutputStream would write byte by byte, which is both slow and no longer atomic
        out.write(b, off, len);
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
  }

}
