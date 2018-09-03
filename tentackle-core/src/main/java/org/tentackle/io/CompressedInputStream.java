/*
 * Tentackle - http://www.tentackle.org
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

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.tentackle.log.Logger;



/**
 * Stream to read compressed data from the underlying input stream.<br>
 *
 * Counterpart to CompressedOutputStream.
 */
public class CompressedInputStream extends FilterInputStream {

  private static final Logger LOGGER = Logger.get(CompressedInputStream.class);


  private final Inflater inflater;      // zip inflater
  private final byte[] byteBuf;         // single byte buffer for read()

  private byte[] infBuf;                // buffer holding compressed data for the inflater (size is dynamically adapted)
  private boolean compressed;           // true if current packet is compressed and inf... is valid
  private int readPending;              // number of bytes pending to read from underlying stream
  private boolean closed;               // true if closed

  // for statistic only (Level.FINE must be enabled)
  private long totalRead;               // total number of bytes read by application
  private long totalCompressed;         // number of compressed bytes read from stream
  private long totalUncompressed;       // number of uncompressed bytes read from stream



  /**
   * Creates a new compressed input stream.<br>
   *
   * The buffersize adapts dynamically to the packet size.
   *
   * @param in the underlying input stream
   */
  public CompressedInputStream(InputStream in) {
    super(in);
    inflater = new Inflater(true);
    byteBuf = new byte[1];
    LOGGER.finer("compressed input stream ceated for: {0}", in);
  }


  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      super.close();
      LOGGER.fine(() ->
          "compressed output stream stats: total=" + totalRead +
          ", compressed=" + totalCompressed +
          ", uncompressed=" + totalUncompressed +
          ", ratio=" + (int)((totalCompressed + totalUncompressed) * 100.0d / (totalRead == 0 ? 1 : totalRead)) + "%");
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
   * Reads the next uncompressed byte of data from the input stream.
   *
   * @return     the next byte of data, or <code>-1</code> if the end of the
   *             stream is reached.
   * @exception  IOException  if an I/O error occurs.
   */
  @Override
  public int read() throws IOException {
    int num = read(byteBuf, 0, 1);
    return num < 0 ? num : byteBuf[0];
  }


  /**
   * Reads up to <code>len</code> uncompressed bytes of data from this input stream
   * into an array of bytes.
   *
   * @param      b     the buffer into which the data is read.
   * @param      off   the start offset in the destination array <code>b</code>
   * @param      len   the maximum number of bytes read.
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   *
   * @exception  IOException  if an I/O error occurs.
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {

    // bounds check
    if (len <= 0 || off < 0 || off + len > b.length) {
      if (len == 0) {
        // reading 0 bytes is explicitly allowed and tolerates illegal values of b and off
        return 0;     // see SocketInputStream
      }
      throw new ArrayIndexOutOfBoundsException("b.length=" + b.length + ", off=" + off + ", len=" + len);
    }

    int count = 0;    // number of bytes read

    while (count == 0) {
      if (compressed) {
        // get decompressed data from inflater
        try {
          count = inflater.inflate(b, off, len);
        }
        catch (DataFormatException e) {
          throw new IOException("decompression failed", e);
        }

        if (count <= 0) {   // is <0 shouldn't be possible, but...
          // all data from infBuf has been decompressed
          if (readPending == 0) {
            // read header of next packet
            if (!readHeader()) {
              return -1;    // end of stream reached
            }
            if (!compressed) {
              // new packet is not compressed: start over
              continue;
            }
          }
          if (inflater.needsInput()) {
            // read as much as you can into the buffer.
            // At best: read the whole packet.
            int num = in.read(infBuf, 0, readPending);
            LOGGER.finer("read into infBuf: {0}", num);
            if (num < 0) {
              // unexpected EOF
              throw new EOFException();
            }
            readPending -= num;
            totalCompressed += num;
            // pass compressed data to the inflater
            inflater.setInput(infBuf, 0, num);
          }
          else {
            throw new IOException("nothing decompressed but inflator does not request more input");
          }
        }
      }
      else {
        // uncompressed
        if (readPending == 0) {
          // read header of next packet
          if (!readHeader()) {
            return -1;
          }
          if (compressed) {
            // next packet is compressed: start over
            continue;
          }
        }
        // read directly bypassing the buffer
        count = readPending;
        if (count > len)  {
          count = len;    // align to max. requested len
        }
        count = in.read(b, off, count);
        LOGGER.finer("read uncompressed: {0}", count);
        if (count < 0) {
          throw new EOFException();
        }
        readPending -= count;
        totalUncompressed += count;
      }
    }

    totalRead += count;
    return count;
  }



  /**
   * Reads the header of the next packet.
   *
   * @return true if next packet loaded, false if end of stream
   */
  private boolean readHeader() throws IOException {

    // read header first
    int ch1 = in.read();
    if (ch1 < 0) {
      return false;   // EOF
    }
    int ch2 = in.read();
    if (ch2 < 0) {
      throw new EOFException();
    }

    readPending = (ch1 << 8) + ch2;
    compressed = ((readPending & CompressedOutputStream.COMPRESSED) == CompressedOutputStream.COMPRESSED);
    readPending &= ~CompressedOutputStream.COMPRESSED;

    if (compressed) {
      // reset decompressor on each new compressed packet
      inflater.reset();
      // make sure buffer is large enough to hold the whole packet.
      if (infBuf == null || infBuf.length < readPending) {
        // allocate larger buffer
        infBuf = new byte[readPending];
        LOGGER.finer("infBuf enlarged to: {0}", readPending);
      }
    }

    return true;
  }

}
