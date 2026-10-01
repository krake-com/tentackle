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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that reads packets with a header holding the packet size.<br>
 * The header is read as 2 bytes, low byte first. Thus, the maximum packet size is 64K - 1.
 *
 * @see PacketSizedOutputStream
 */
public abstract class PacketSizedInputStream extends FilterInputStream {

  private boolean closed;               // true if closed

  /**
   * Creates the input stream.
   *
   * @param in the underlying input stream
   */
  public PacketSizedInputStream(InputStream in) {
    super(in);
  }


  @Override
  public void close() throws IOException {
    if (!closed) {
      super.close();
      closed = true;
    }
  }

  /**
   * Returns the closed state.
   *
   * @return true if closed
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Reads the header of the next packet.
   *
   * @return the packet size, -1 if the end of the stream is reached
   * @throws IOException if reading failed
   */
  protected int readHeader() throws IOException {
    byte[] header = in.readNBytes(2);
    if (header.length != 2) {
      return -1;
    }
    return header[0] & 0xFF | (header[1] & 0xFF) << 8;
  }

}
