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

import org.tentackle.log.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

/**
 * Stream to write compressed data to the underlying output stream.
 * <p>
 * Replacement for GZIPOutputStream and ZipOutputStream of java.util.zip
 * capable of communication link streams like TCP sockets.
 * The standard classes are only file-capable (relying on EOF-detection).
 * In communication links, however, there are several packets instead of a single file.
 * Hence, the stream must be blocked, i.e., the (varying) block size must
 * become part of the protocol to allow the corresponding input stream
 * to detect the end of the packets.
 * Furthermore, we optimize to compress only packets larger than a certain size.
 * All smaller packets, and those that don't get smaller by compression, will be transferred uncompressed.
 * For the packet size we use the first two bytes in the packet (short), while
 * the MSB signals whether the packet is compressed or not.
 * Because of this, the maximum buffer size is limited to 32K - 1.
 */
public class CompressedOutputStream extends PacketSizedOutputStream {

  private static final Logger LOGGER = Logger.get(CompressedOutputStream.class);

  /**
   * The maximum compression buffer size.
   * Larger buffers provide better compression.
   */
  private static final int MAX_BUFFER_SIZE = Short.MAX_VALUE;


  static final int COMPRESSED = MAX_BUFFER_SIZE + 1;    // 0x8000 MSBit: 1 = compressed, 0 = uncompressed

  private final int minCompressSize;          // minimum packet size for compression
  private final int bufSize;                  // buffer size

  private final byte[] byteBuf = new byte[1]; // single byte buffer for write(b)
  private final byte[] orgBuf;                // original uncompressed data buffer
  private int orgLen;                         // number of bytes in orgBuf
  private final Deflater deflater;            // the zip deflater
  private final byte[] defBuf;                // deflated/compressed output data buffer

  // for statistic only (Level.FINE)
  private long totalWritten;                  // total number of bytes written by the application
  private long totalCompressed;               // number of compressed bytes written to stream
  private long totalUncompressed;             // number of uncompressed bytes written to stream

  /**
   * Creates a compressed output stream.
   *
   * @param out the underlying output stream (e.g., from a socket)
   * @param bufSize the buffer size for compression (larger packets are split)
   * @param minCompressSize the minimum packet size to use compression (smaller packets will remain uncompressed)
   * @throws IllegalArgumentException if bufSize is not greater than minCompressSize or exceeds 32K - 1
   */
  public CompressedOutputStream(OutputStream out, int bufSize, int minCompressSize) {
    super(out);

    if (bufSize <= minCompressSize || bufSize > MAX_BUFFER_SIZE) {
      throw new IllegalArgumentException("illegal buffer size [" + minCompressSize + " < ?" + bufSize + "? <= " + MAX_BUFFER_SIZE + "]");
    }
    this.bufSize = bufSize;
    this.minCompressSize = minCompressSize;

    orgBuf = new byte[bufSize];
    deflater = createDeflater();
    defBuf = new byte[bufSize];
  }

  /**
   * Creates a compressed output stream.
   *
   * @param out the underlying output stream (e.g., from a socket)
   * @param minCompressSize the minimum packet size to use compression (smaller packets will remain uncompressed)
   */
  public CompressedOutputStream(OutputStream out, int minCompressSize) {
    this(out, MAX_BUFFER_SIZE, minCompressSize);
  }

  /**
   * Creates a compressed output stream with the maximum allowed buffer size (32K-1) and
   * a default minCompressSize of 64.
   *
   * @param out the underlying output stream
   */
  public CompressedOutputStream(OutputStream out) {
    this(out, MAX_BUFFER_SIZE, 64);
  }


  /**
   * Writes the specified <code>byte</code> to this output stream.
   *
   * @param b the <code>byte</code>
   * @exception IOException if an I/O error occurs
   */
  @Override
  public void write(int b) throws IOException {
    byteBuf[0] = (byte)b;
    write(byteBuf, 0, 1);
  }

  /**
   * Writes <code>len</code> bytes from the specified
   * <code>byte</code> array starting at offset <code>off</code> to
   * this output stream.
   * <p>
   * Packets larger than the buffer size will be split and written to
   * the underlying output stream as separate packets.
   *
   * @param      b     the data
   * @param      off   the start offset in the data
   * @param      len   the number of bytes to write
   * @exception  IOException  if an I/O error occurs
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {

    totalWritten += len;

    while (len > 0) {

      int num = len;
      if (num > bufSize - orgLen) {
        num = bufSize - orgLen;    // align to buffer size
      }

      System.arraycopy(b, off, orgBuf, orgLen, num);
      off    += num;
      len    -= num;
      orgLen += num;

      if (orgLen >= bufSize) {
        // buffer full: write/compress it!
        flushBuffer();
      }
    }
  }

  /**
   * Flushes this output stream and forces any buffered output bytes
   * to be written out to the stream.
   *
   * @exception IOException if an I/O error occurs
   */
  @Override
  public void flush() throws IOException {
    flushBuffer();    // write any pending data
    super.flush();
  }

  /**
   * Overridden to release the deflater and log the statistics.
   */
  @Override
  public void close() throws IOException {
    if (!isClosed()) {
      super.close();
      deflater.end();
      LOGGER.fine(() ->
          "compressed output stream stats: total=" + totalWritten +
          ", compressed=" + totalCompressed +
          ", uncompressed=" + totalUncompressed +
          ", ratio=" + (int)((totalCompressed + totalUncompressed) * 100.0d / (totalWritten == 0 ? 1 : totalWritten)) + "%");
    }
  }


  /**
   * Creates the deflater.
   *
   * @return the deflater
   */
  protected Deflater createDeflater() {
    // with noWrap: less metadata -> better compression
    return new Deflater(Deflater.DEFAULT_COMPRESSION, true);
  }


  /**
   * Writes the header.
   *
   * @param size the logical packet size
   * @param compressed is true if the packet is compressed, false if uncompressed
   * @throws IOException if writing failed
   */
  protected void writeHeader(int size, boolean compressed) throws IOException {
    if (compressed) {
      size |= COMPRESSED;
    }
    super.writeHeader(size);
  }

  /**
   * Flushes the buffer.<br>
   * If the packet size is large enough, the data will be compressed.
   * If the compressed data is smaller than the original data, a compressed
   * packet will be written. Otherwise, an uncompressed packet is written
   * to the underlying output stream.
   */
  private void flushBuffer() throws IOException {
    if (orgLen > 0) {
      if (orgLen >= minCompressSize) {
        // compress the data
        deflater.reset();   // sadly we must reset() cause of finish() :(
        deflater.setInput(orgBuf, 0, orgLen);
        deflater.finish();
        int defLen = 0;
        while (!deflater.finished()) {
          int num = deflater.deflate(defBuf, defLen, bufSize - defLen);
          if (num <= 0 &&
              // can this really happen? Yes: if minCompressSize is too small!
              deflater.needsInput()) {
            throw new IOException("Deflater needs more input! Bytes in buffer: " + orgLen);
          }
          defLen += num;
        }
        if (defLen < orgLen) {
          // deflate
          writeHeader(defLen, true);
          out.write(defBuf, 0, defLen);
          totalCompressed += defLen;
          orgLen = 0;
          LOGGER.fine("compressed packet written: {0}/{1}", defLen, orgLen);
          return;
        }
      }

      // uncompressed packet
      writeHeader(orgLen, false);
      out.write(orgBuf, 0, orgLen);
      totalUncompressed += orgLen;
      orgLen = 0;
      LOGGER.fine("uncompressed packet written: {0}", orgLen);
    }
  }

}
