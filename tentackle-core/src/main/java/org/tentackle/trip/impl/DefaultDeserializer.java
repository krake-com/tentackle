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

package org.tentackle.trip.impl;

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStreamClosedException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Default implementation of a low-level deserializer.
 */
public class DefaultDeserializer implements Deserializer {

  /** default deserialization buffer size. */
  private static final int DEFAULT_BUFFER_SIZE = 16384;


  /** input stream to fill the buffer from. */
  private InputStream in;

  /** the deserialization buffer. */
  private final byte[] buf;

  /** number of bytes loaded into the deserialization buffer. */
  private int len;

  /** current position to read from within the deserialization buffer. */
  private int pos;


  /**
   * Creates a deserializer.
   *
   * @param buf the deserialization buffer
   * @param in the input stream to read from when the buffer gets empty
   */
  public DefaultDeserializer(byte[] buf, InputStream in) {
    this.buf = Objects.requireNonNull(buf, "buf must not be null");
    setInputStream(in);
  }

  /**
   * Creates a deserializer with a default deserialization buffer.
   *
   * @param in the input stream to read from when the buffer gets empty, null for none
   */
  public DefaultDeserializer(InputStream in) {
    this(new byte[DEFAULT_BUFFER_SIZE], in);
  }


  @Override
  public InputStream getInputStream() {
    return in;
  }

  /**
   * Switches to another input stream.
   *
   * @param in the input stream
   */
  @Override
  public void setInputStream(InputStream in) {
    this.in = Objects.requireNonNull(in, "input stream must not be null");
    pos = 0;
    len = 0;
  }

  @Override
  public void deserialize(byte[] bytes, int offset, int length) {
    while (length > 0) {
      int segmentSize = Math.min(length, buf.length);
      want(segmentSize);
      System.arraycopy(buf, pos, bytes, offset, segmentSize);
      pos += segmentSize;
      offset += segmentSize;
      length -= segmentSize;
    }
  }

  @Override
  public OpCode deserializeOpCode() {
    return TripFactory.getInstance().getOpCode(deserializeByteAsUnsigned());
  }

  @Override
  public byte deserializeByte() {
    want(1);
    return buf[pos++];
  }

  @Override
  public int deserializeByteAsUnsigned() {
    want(1);
    return buf[pos++] & 0xFF;
  }

  @Override
  public boolean deserializeBoolean() {
    want(1);
    return buf[pos++] != 0;
  }

  @Override
  public char deserializeChar() {
    want(2);
    int p = pos;
    pos = p + 2;
    return (char) ((buf[p] & 0xFF) | ((buf[p + 1] & 0xFF)) << 8);
  }

  @Override
  public short deserializeShort() {
    want(2);
    int p = pos;
    pos = p + 2;
    return (short) ((buf[p] & 0xFF) | ((buf[p + 1] & 0xFF)) << 8);
  }

  @Override
  public int deserializeShortAsUnsigned() {
    want(2);
    int p = pos;
    pos = p + 2;
    return buf[p] & 0xFF | (buf[p + 1] & 0xFF) << 8;
  }

  @Override
  public int deserializeInt() {
    want(4);
    byte[] b = buf;
    int p = pos;
    pos = p + 4;
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b[p + 2] & 0xFF) << 16 |
           (b[p + 3] & 0xFF) << 24;
  }

  @Override
  public int deserializeInt3() {
    want(3);
    byte[] b = buf;
    int p = pos;
    pos = p + 3;
    byte b2 = b[p + 2];
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b2 & 0xFF) << 16 |
           ((b2 & 0x80) == 0x80 ? 0xFF << 24 : 0);
  }

  @Override
  public int deserializeInt3AsUnsigned() {
    want(3);
    int p = pos;
    pos = p + 3;
    return buf[p] & 0xFF | (buf[p + 1] & 0xFF) << 8 | (buf[p + 2] & 0xFF) << 16;
  }

  @Override
  public long deserializeLong() {
    want(8);
    byte[] b = buf;
    int p = pos;
    pos = p + 8;
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b[p + 2] & 0xFF) << 16 |
           (long) (b[p + 3] & 0xFF) << 24 |
           (long) (b[p + 4] & 0xFF) << 32 |
           (long) (b[p + 5] & 0xFF) << 40 |
           (long) (b[p + 6] & 0xFF) << 48 |
           (long) b[p + 7] << 56;
  }

  @Override
  public long deserializeLong5() {
    want(5);
    byte[] b = buf;
    int p = pos;
    pos = p + 5;
    byte b4 = b[p + 4];
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b[p + 2] & 0xFF) << 16 |
           (long) (b[p + 3] & 0xFF) << 24 |
           (long) (b4 & 0xFF) << 32 |
           ((b4 & 0x80) == 0x80 ? 0xFFFFFFL << 40 : 0L);
  }

  @Override
  public long deserializeLong6() {
    want(6);
    byte[] b = buf;
    int p = pos;
    pos = p + 6;
    byte b5 = b[p + 5];
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b[p + 2] & 0xFF) << 16 |
           (long) (b[p + 3] & 0xFF) << 24 |
           (long) (b[p + 4] & 0xFF) << 32 |
           (long) (b5 & 0xFF) << 40 |
           ((b5 & 0x80) == 0x80 ? 0xFFFFL << 48 : 0L);
  }

  @Override
  public long deserializeLong7() {
    want(7);
    byte[] b = buf;
    int p = pos;
    pos = p + 7;
    byte b6 = b[p + 6];
    return b[p] & 0xFF |
           (b[p + 1] & 0xFF) << 8 |
           (b[p + 2] & 0xFF) << 16 |
           (long) (b[p + 3] & 0xFF) << 24 |
           (long) (b[p + 4] & 0xFF) << 32 |
           (long) (b[p + 5] & 0xFF) << 40 |
           (long) (b6 & 0xFF) << 48 |
           ((b6 & 0x80) == 0x80 ? 0xFFL << 56 : 0L);
  }

  @Override
  public float deserializeFloat() {
    return Float.intBitsToFloat(deserializeInt());
  }

  @Override
  public double deserializeDouble() {
    return Double.longBitsToDouble(deserializeLong());
  }


  /**
   * Specify the number of bytes required by the next deserialization step.
   *
   * @param num number of bytes wanted
   */
  private void want(int num) {
    int missing = pos + num - len;
    if (missing > 0) {
      fill(missing);
    }
  }

  /**
   * Fills the buffer from the input stream as much as possible.
   *
   * @param wanted the minimum number of bytes to read from the input stream
   */
  private void fill(int wanted) {
    try {
      while (wanted > 0) {
        int unused = buf.length - len;
        if (unused > 0) {
          int read = in.read(buf, len, unused);
          if (read == -1) {
            throw new TripStreamClosedException("end of input stream reached while waiting for " + wanted + " bytes");
          }
          len += read;
          wanted -= read;
        }
        else {
          int unprocessed = len - pos;
          if (unprocessed > 0) {
            System.arraycopy(buf, pos, buf, 0, unprocessed);
          }
          else if (unprocessed < 0) {
            throw new TripRuntimeException("deserializer became corrupted");
          }
          pos = 0;
          len = unprocessed;
          if (wanted + unprocessed > buf.length) {
            throw new TripRuntimeException("buffer too small: required " + (wanted + unprocessed) + " > " + buf.length);
          }
        }
      }
    }
    catch (IOException e) {
      // A connection reset, a read timeout or an SSL failure leaves the stream unusable: there is
      // no framing to resynchronize on. Report it as closed, just like the end of the input stream
      // above and like DefaultSerializer#flush does for the write side, so that the callers can
      // tell a broken connection from a serialization problem.
      throw new TripStreamClosedException("filling the buffer from input stream failed", e);
    }
  }

}
