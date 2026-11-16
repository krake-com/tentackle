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

import org.tentackle.io.CompressedInputStream;
import org.tentackle.trip.TripConstants;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Guards the connection request of a compressing transport on the server side.<br>
 * Sits between the raw stream of the connection and the {@link CompressedInputStream}.
 * <p>
 * The server must not decide about a connection before it has read the whole connection request, or the
 * point in time the connection is closed would tell a prober about the wire format
 * (see {@code trip.md#fingerprinting}). With a compressing transport, however, the request is preceded by the
 * two-byte header of its packet, and the {@link CompressedInputStream} would act on that header alone: a
 * compressed packet makes the inflater fail at the first invalid byte, and empty packets are skipped forever.
 * <p>
 * Therefore, this stream reads the header and the request as one block of {@link #BLOCK_SIZE} bytes
 * before it passes anything on. The first packet must be uncompressed and hold at least the request,
 * which is what every client sends (see {@link #minCompressSize(int)}). Otherwise, the stream fails,
 * and the connection is rejected like one with an invalid magic: after the same number of bytes, and after
 * the rejection delay. Once the block has been checked, the stream passes all bytes through unchanged.
 */
public class CompressedRequestInputStream extends FilterInputStream {

  /** The size of a packet header of the compressing streams. */
  public static final int HEADER_SIZE = 2;

  /** The size of the connection request the server decides upon: the magic and the UUID. */
  public static final int REQUEST_SIZE = TripConstants.MAGIC.length + 16;

  /** The number of bytes read before anything is decided. */
  public static final int BLOCK_SIZE = HEADER_SIZE + REQUEST_SIZE;

  /** The flag of a compressed packet in its header. */
  private static final int COMPRESSED = 0x8000;

  /**
   * Returns the minimum packet size for compression a compressing transport uses.<br>
   * The client sends its connection request (the magic, the UUID and the dictionary flag) as a packet of its own,
   * which must not be compressed, see {@link CompressedRequestInputStream}. Compressing so few bytes doesn't
   * pay off anyway.
   *
   * @param minCompressSize the configured minimum packet size for compression
   * @return the minimum packet size to use
   */
  public static int minCompressSize(int minCompressSize) {
    return Math.max(minCompressSize, BLOCK_SIZE);
  }


  private byte[] block;     // the header and the request, null if not read yet
  private int pos;          // the read position within block


  /**
   * Creates a guard for the connection request.
   *
   * @param in the raw input stream of the connection
   */
  public CompressedRequestInputStream(InputStream in) {
    super(Objects.requireNonNull(in, "in"));
  }

  @Override
  public int read() throws IOException {
    readBlock();
    if (pos < block.length) {
      return block[pos++] & 0xff;
    }
    return in.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len == 0) {
      return 0;
    }
    readBlock();
    if (pos < block.length) {
      int count = Math.min(len, block.length - pos);
      System.arraycopy(block, pos, b, off, count);
      pos += count;
      return count;
    }
    return in.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    // via read, so that the block is checked and nothing bypasses it
    long remaining = n;
    byte[] buf = new byte[(int) Math.min(remaining, 512)];
    while (remaining > 0) {
      int count = read(buf, 0, (int) Math.min(remaining, buf.length));
      if (count < 0) {
        break;
      }
      remaining -= count;
    }
    return n - remaining;
  }

  @Override
  public int available() throws IOException {
    return block == null ? 0 : block.length - pos + in.available();
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public synchronized void mark(int readlimit) {
    // not supported
  }

  @Override
  public synchronized void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }


  /**
   * Reads and checks the header and the request on the first read.
   *
   * @throws IOException if the stream ended before or the first packet is not an uncompressed packet
   *                     holding at least the request
   */
  private void readBlock() throws IOException {
    if (block == null) {
      byte[] buf = in.readNBytes(BLOCK_SIZE);
      if (buf.length != BLOCK_SIZE) {
        throw new EOFException("end of stream while reading the connection request");
      }
      int header = buf[0] & 0xff | (buf[1] & 0xff) << 8;    // little endian, see PacketSizedInputStream
      if ((header & COMPRESSED) != 0 || header < REQUEST_SIZE) {
        // don't tell what's wrong: anybody can send garbage
        throw new IOException("invalid connection request");
      }
      block = buf;
    }
  }

}
