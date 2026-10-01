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

package org.tentackle.trip.transport;

import org.tentackle.io.CompressedOutputStream;

import java.io.OutputStream;
import java.util.zip.Deflater;

/**
 * A compressed output stream using the fastest compression level.<br>
 * Best suited for network traffic, where latency matters more than the compression ratio.
 */
public class FastCompressedOutputStream extends CompressedOutputStream {

  /**
   * Creates a compressed output stream.
   *
   * @param out             the underlying output stream (e.g., from a socket)
   * @param bufSize         the buffer size for compression (larger packets are split)
   * @param minCompressSize the minimum packet size to use compression (smaller packets will remain uncompressed)
   */
  public FastCompressedOutputStream(OutputStream out, int bufSize, int minCompressSize) {
    super(out, bufSize, minCompressSize);
  }

  /**
   * Creates a compressed output stream.
   *
   * @param out             the underlying output stream (e.g., from a socket)
   * @param minCompressSize the minimum packet size to use compression (smaller packets will remain uncompressed)
   */
  public FastCompressedOutputStream(OutputStream out, int minCompressSize) {
    super(out, minCompressSize);
  }

  /**
   * Creates a compressed output stream with the maximum allowed buffer size (32K-1) and
   * a default minCompressSize of 64.
   *
   * @param out the underlying output stream
   */
  public FastCompressedOutputStream(OutputStream out) {
    super(out);
  }


  @Override
  protected Deflater createDeflater() {
    // for network traffic, use the fastest compression level
    return new Deflater(Deflater.BEST_SPEED, true);
  }

}
