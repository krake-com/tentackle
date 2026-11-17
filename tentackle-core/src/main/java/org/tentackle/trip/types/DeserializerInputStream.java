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

package org.tentackle.trip.types;

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.TripRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An input stream reading a given number of bytes from a deserializer.
 * <p>
 * Reads only as much as requested, so that whatever consumes the stream allocates its memory while the bytes
 * arrive instead of trusting the length announced ahead of them. A corrupted or forged length thus ends up as
 * a closed stream, not as an {@link OutOfMemoryError} or a huge array held while waiting for data that never comes.
 * <p>
 * Closing the stream does not close the deserializer.
 */
class DeserializerInputStream extends InputStream {

  private static final int SKIP_BUFFER_SIZE = 8192;

  private final Deserializer deserializer;
  private int remaining;

  /**
   * Creates the stream.
   *
   * @param deserializer the deserializer
   * @param length the number of bytes to read, not negative
   */
  DeserializerInputStream(Deserializer deserializer, int length) {
    this.deserializer = Objects.requireNonNull(deserializer);
    this.remaining = length;
  }

  /**
   * Reads a byte array of the given length from a deserializer.<br>
   * The array is allocated while the bytes arrive, not ahead of them.
   *
   * @param deserializer the deserializer
   * @param length the number of bytes, not negative
   * @return the bytes
   */
  static byte[] readBytes(Deserializer deserializer, int length) {
    try {
      return new DeserializerInputStream(deserializer, length).readAllBytes();
    }
    catch (IOException e) {
      // cannot happen: the deserializer throws only TripRuntimeExceptions
      throw new TripRuntimeException("reading " + length + " bytes failed", e);
    }
  }

  /**
   * Reads and discards the remaining bytes.<br>
   * Must be invoked when the consumer stopped early, to keep the deserializer in sync.
   */
  void drain() {
    if (remaining > 0) {
      byte[] buffer = new byte[Math.min(remaining, SKIP_BUFFER_SIZE)];
      while (remaining > 0) {
        int count = Math.min(remaining, buffer.length);
        deserializer.deserialize(buffer, 0, count);
        remaining -= count;
      }
    }
  }

  @Override
  public int read() {
    if (remaining <= 0) {
      return -1;
    }
    remaining--;
    return deserializer.deserializeByte() & 0xff;
  }

  @Override
  public int read(byte[] b, int off, int len) {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len == 0) {
      return 0;
    }
    if (remaining <= 0) {
      return -1;
    }
    int count = Math.min(len, remaining);
    deserializer.deserialize(b, off, count);
    remaining -= count;
    return count;
  }

  @Override
  public int available() {
    return 0;   // whatever the deserializer buffered is not known here
  }

  @Override
  public void close() {
    // the deserializer stays open
  }

}
