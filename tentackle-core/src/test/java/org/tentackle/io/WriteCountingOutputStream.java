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

import java.io.ByteArrayOutputStream;

/**
 * A {@link ByteArrayOutputStream} counting the write calls it receives.<br>
 * Used to verify that a packet reaches the underlying stream (e.g., a socket) in one piece.
 */
class WriteCountingOutputStream extends ByteArrayOutputStream {

  private int writes;

  @Override
  public synchronized void write(int b) {
    writes++;
    super.write(b);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    writes++;
    super.write(b, off, len);
  }

  /**
   * Gets the number of write calls.
   *
   * @return the number of writes
   */
  int getWrites() {
    return writes;
  }

}
