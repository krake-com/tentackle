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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that writes packets with a header holding the packet size.<br>
 * The header is written as 2 bytes, low byte first. Thus, the maximum packet size is 64K - 1.
 *
 * @see PacketSizedInputStream
 */
public abstract class PacketSizedOutputStream extends FilterOutputStream {

  /**
   * The number of bytes of the header preceding each packet.
   */
  protected static final int HEADER_SIZE = 2;

  private boolean closed;                     // true if closed

  /**
   * Creates a packet-sized output stream.
   *
   * @param out the underlying output stream (e.g., from a socket)
   */
  public PacketSizedOutputStream(OutputStream out) {
    super(out);
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
   * Writes a packet, i.e., the header followed by the packet data.
   * <p>
   * The data must start at {@link #HEADER_SIZE} within the buffer. The header is stored in front of
   * it, so that both reach the underlying stream in a single write operation.
   *
   * @param buf the buffer holding the data at offset {@link #HEADER_SIZE}
   * @param header the header value, i.e., the logical packet size plus optional flags
   * @param length the number of data bytes
   * @throws IOException if writing failed
   */
  protected void writePacket(byte[] buf, int header, int length) throws IOException {
    buf[0] = (byte) header;
    buf[1] = (byte) (header >>> 8);
    out.write(buf, 0, HEADER_SIZE + length);
  }

}
