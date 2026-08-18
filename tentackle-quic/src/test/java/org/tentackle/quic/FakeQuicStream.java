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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An in-memory {@link QuicStream} for tests that must not involve a real QUIC connection.<br>
 * Records what was written and whether both stream halves were closed.
 */
public class FakeQuicStream implements QuicStream {

  private final ByteArrayOutputStream written = new ByteArrayOutputStream();
  private final InputStream inputStream;
  private final OutputStream outputStream;

  private final IOException closeFailure;   // thrown on close, null if closing succeeds

  private boolean inputClosed;
  private boolean outputClosed;

  /**
   * Creates a fake stream reading from the given bytes.
   *
   * @param input the bytes the peer "sent"
   */
  public FakeQuicStream(byte[] input) {
    this(input, null);
  }

  /**
   * Creates a fake stream reading from the given bytes.
   *
   * @param input the bytes the peer "sent"
   * @param closeFailure the exception to throw when closing, null if closing succeeds
   */
  public FakeQuicStream(byte[] input, IOException closeFailure) {
    this.closeFailure = closeFailure;
    this.inputStream = new ByteArrayInputStream(input) {
      @Override
      public void close() throws IOException {
        inputClosed = true;
        failIfConfigured();
      }
    };
    this.outputStream = new OutputStream() {
      @Override
      public void write(int b) {
        written.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) {
        written.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
        outputClosed = true;
        failIfConfigured();
      }
    };
  }

  /**
   * Gets everything written to the output stream so far.
   *
   * @return the written bytes
   */
  public byte[] getWrittenBytes() {
    return written.toByteArray();
  }

  /**
   * Returns whether the input stream has been closed.
   *
   * @return true if closed
   */
  public boolean isInputClosed() {
    return inputClosed;
  }

  /**
   * Returns whether the output stream has been closed.
   *
   * @return true if closed
   */
  public boolean isOutputClosed() {
    return outputClosed;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public int getStreamId() {
    return 4;
  }

  @Override
  public boolean isUnidirectional() {
    return false;
  }

  @Override
  public boolean isClientInitiatedBidirectional() {
    return true;
  }

  @Override
  public boolean isServerInitiatedBidirectional() {
    return false;
  }

  @Override
  public void abortReading(long applicationProtocolErrorCode) {
  }

  @Override
  public void resetStream(long applicationProtocolErrorCode) {
  }

  @Override
  public String toString() {
    return "FakeQuicStream[" + getStreamId() + "]";
  }

  private void failIfConfigured() throws IOException {
    if (closeFailure != null) {
      throw closeFailure;
    }
  }

}
