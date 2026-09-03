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

import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStreamClosedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Default implementation of a low-level serializer.
 */
public class DefaultSerializer implements Serializer {

  /** default serialization buffer size. */
  private static final int DEFAULT_BUFFER_SIZE = 16384;


  /** output stream to flush to when the buffer gets full. */
  private OutputStream out;

  /** the serialization buffer. */
  private final byte[] buf;

  /** current position within the serialization buffer. */
  private int pos;


  /**
   * Creates a serializer.
   *
   * @param buf the serialization buffer
   * @param out the output stream to flush to when the buffer gets full
   */
  public DefaultSerializer(byte[] buf, OutputStream out) {
    this.buf = Objects.requireNonNull(buf, "buffer must not be null");
    setOutputStream(out);
  }

  /**
   * Creates a serializer with a default serialization buffer.
   *
   * @param out the output stream to flush to when the buffer gets full, null if none
   */
  public DefaultSerializer(OutputStream out) {
    this(new byte[DEFAULT_BUFFER_SIZE], out);
  }


  /**
   * Gets the serialization buffer.
   *
   * @return the buffer
   */
  public byte[] getBuffer() {
    return buf;
  }

  /**
   * Gets the position within the serialization buffer to write the next bytes to.
   *
   * @return the number of serialized bytes within the buffer
   */
  public int getPosition() {
    return pos;
  }

  @Override
  public OutputStream getOutputStream() {
    return out;
  }

  /**
   * Switches to another output stream.<br>
   * The buffer is cleared so that {@link #getPosition()} is 0.
   *
   * @param out the output stream
   */
  @Override
  public void setOutputStream(OutputStream out) {
    this.out = Objects.requireNonNull(out, "output stream must not be null");
    pos = 0;
  }

  /**
   * Flushes to the output stream.
   */
  @Override
  public void flush() {
    try {
      if (pos > 0) {
        out.write(buf, 0, pos);
        pos = 0;
      }
      out.flush();
    }
    catch (IOException e) {
      throw new TripStreamClosedException("flushing to output stream failed", e);
    }
  }

  @Override
  public void serialize(byte[] bytes, int offset, int length) {
    while (length > 0) {
      int count = Math.min(length, buf.length);
      want(count);
      System.arraycopy(bytes, offset, buf, pos, count);
      length -= count;
      offset += count;
      pos += count;
    }
  }

  @Override
  public void serialize(byte[] bytes) {
    serialize(bytes, 0, bytes.length);
  }

  @Override
  public void serializeOpCode(OpCode opCode) {
    serializeUnsignedAsByte(opCode.getCode());
  }

  @Override
  public void serializeByte(byte value) {
    want(1);
    buf[pos++] = value;
  }

  @Override
  public void serializeUnsignedAsByte(int value) {
    want(1);
    buf[pos++] = (byte) value;
  }

  @Override
  public void serializeBoolean(boolean value) {
    serializeUnsignedAsByte(value ? 1 : 0);
  }

  @Override
  public void serializeChar(char value) {
    want(2);
    int p = pos;
    buf[p] = (byte) value;
    buf[p + 1] = (byte) (value >>> 8);
    pos = p + 2;
  }

  @Override
  public void serializeUnsignedAsShort(int value) {
    want(2);
    int p = pos;
    buf[p] = (byte) value;
    buf[p + 1] = (byte) (value >>> 8);
    pos = p + 2;
  }

  @Override
  public void serializeShort(short value) {
    want(2);
    int p = pos;
    buf[p] = (byte) value;
    buf[p + 1] = (byte) (value >>> 8);
    pos = p + 2;
  }

  @Override
  public void serializeInt(int value) {
    want(4);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    b[p + 3] = (byte) (value >> 24);
    pos = p + 4;
  }

  @Override
  public void serializeInt3(int value) {
    want(3);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    pos = p + 3;
  }

  @Override
  public void serializeLong(long value) {
    want(8);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    b[p + 3] = (byte) (value >> 24);
    b[p + 4] = (byte) (value >> 32);
    b[p + 5] = (byte) (value >> 40);
    b[p + 6] = (byte) (value >> 48);
    b[p + 7] = (byte) (value >> 56);
    pos = pos + 8;
  }

  @Override
  public void serializeLong5(long value) {
    want(5);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    b[p + 3] = (byte) (value >> 24);
    b[p + 4] = (byte) (value >> 32);
    pos = pos + 5;
  }

  @Override
  public void serializeLong6(long value) {
    want(6);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    b[p + 3] = (byte) (value >> 24);
    b[p + 4] = (byte) (value >> 32);
    b[p + 5] = (byte) (value >> 40);
    pos = pos + 6;
  }

  @Override
  public void serializeLong7(long value) {
    want(7);
    byte[] b = buf;
    int p = pos;
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >> 8);
    b[p + 2] = (byte) (value >> 16);
    b[p + 3] = (byte) (value >> 24);
    b[p + 4] = (byte) (value >> 32);
    b[p + 5] = (byte) (value >> 40);
    b[p + 6] = (byte) (value >> 48);
    pos = pos + 7;
  }

  @Override
  public void serializeFloat(float value) {
    serializeInt(Float.floatToIntBits(value));
  }

  @Override
  public void serializeDouble(double value) {
    serializeLong(Double.doubleToLongBits(value));
  }


  /**
   * Specify the number of bytes required by the next serialization step.
   *
   * @param num number of bytes wanted
   */
  private void want(int num) {
    if (pos + num > buf.length) {
      flush();
      if (num > buf.length) {
        throw new TripRuntimeException("buffer too small: " + buf.length + " < " + num + " wanted");
      }
    }
  }

}
