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
   * Writes the header.
   *
   * @param size the logical packet size
   * @throws IOException if writing failed
   */
  protected void writeHeader(int size) throws IOException {
    byte[] header = new byte[2];
    header[0] = (byte) size;
    header[1] = (byte) (size >>> 8);
    out.write(header);
  }

}
