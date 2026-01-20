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

package org.tentackle.io;

import java.io.ByteArrayInputStream;

/**
 * A mutable {@link ByteArrayInputStream}.
 * <p>
 * Provides methods to replace the buffer.
 */
public class MutableByteArrayInputStream extends ByteArrayInputStream {

  /**
   * Creates an empty {@code MutableByteArrayInputStream}.
   */
  public MutableByteArrayInputStream() {
    super(new byte[0]);
  }

  /**
   * Creates a {@code MutableByteArrayInputStream}.
   *
   * @param buf the input buffer
   */
  public MutableByteArrayInputStream(byte[] buf) {
    super(buf);
  }

  /**
   * Creates {@code MutableByteArrayInputStream}.
   *
   * @param buf    the input buffer
   * @param offset the offset in the buffer of the first byte to read
   * @param length the maximum number of bytes to read from the buffer
   */
  public MutableByteArrayInputStream(byte[] buf, int offset, int length) {
    super(buf, offset, length);
  }


  /**
   * Sets the buffer.
   *
   * @param buf    the input buffer
   * @param offset the offset in the buffer of the first byte to read
   * @param length the maximum number of bytes to read from the buffer
   */
  public void setBuffer(byte[] buf, int offset, int length) {
    this.buf = buf;
    this.pos = offset;
    this.count = Math.min(offset + length, buf.length);
    this.mark = offset;
  }

  /**
   * Sets the buffer with an offset of 0 and the length of the array.
   *
   * @param buf the input buffer
   */
  public void setBuffer(byte[] buf) {
    setBuffer(buf, 0, buf.length);
  }

}
