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

import org.tentackle.common.Cryptor;
import org.tentackle.log.Logger;
import org.tentackle.trip.TripConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Stream to write encrypted data to the underlying output stream.
 * <p>
 * Uses the given {@link org.tentackle.common.Cryptor} to encrypt the data.
 * For the packet size we use the first two bytes in the packet (unsigned short).
 * Because of this, the size of an <em>encrypted</em> packet is limited to 64K - 1.
 * Since encryption adds some overhead (padding, for example), the buffer size must leave room for it.
 * The default buffer size of 32K - 1 is safe for all common ciphers.
 */
public class EncryptingOutputStream extends PacketSizedOutputStream {

  private static final Logger LOGGER = Logger.get(EncryptingOutputStream.class);

  /**
   * The maximum encryption buffer size.
   */
  private static final int MAX_BUFFER_SIZE = TripConstants.UNSIGNED_SHORT_MAX;

  /**
   * Default buffer size: 1/2 of the maximum size.
   */
  private static final int DEFAULT_BUFFER_SIZE = MAX_BUFFER_SIZE >> 1;




  private final int bufSize;                  // buffer size
  private final byte[] byteBuf = new byte[1]; // single byte buffer for write(b)
  private final byte[] orgBuf;                // original unencrypted data buffer
  private int orgOff;                         // number of bytes / offset in orgBuf
  private final Cryptor cryptor;              // the cryptor

  // for statistic only (Level.FINE)
  private long totalWritten;                  // total number of bytes written by the application
  private long totalEncrypted;                // number of encrypted bytes written to stream

  /**
   * Creates an encrypted output stream.
   *
   * @param out the underlying output stream (e.g., from a socket)
   * @param bufSize the buffer size for encryption (larger packets are split), must leave room for the encryption overhead
   * @param cryptor the cryptor to use for encryption
   * @throws IllegalArgumentException if the buffer size is not in the range 65 to 64K - 1
   * @throws NullPointerException if cryptor is null
   */
  public EncryptingOutputStream(OutputStream out, int bufSize, Cryptor cryptor) {
    super(out);

    if (bufSize <= 64 || bufSize > MAX_BUFFER_SIZE) {
      throw new IllegalArgumentException("illegal buffer size [64 < ?" + bufSize + "? <= " + MAX_BUFFER_SIZE + "]");
    }
    this.bufSize = bufSize;
    this.cryptor = Objects.requireNonNull(cryptor);
    orgBuf = new byte[bufSize];
  }

  /**
   * Creates an encrypted output stream.
   *
   * @param out the underlying output stream (e.g., from a socket)
   * @param cryptor the cryptor to use for encryption
   */
  public EncryptingOutputStream(OutputStream out, Cryptor cryptor) {
    this(out, DEFAULT_BUFFER_SIZE, cryptor);
  }

  /**
   * Creates an encrypted output stream using the default {@link Cryptor}.
   *
   * @param out the underlying output stream (e.g., from a socket)
   * @throws NullPointerException if there is no default cryptor
   */
  public EncryptingOutputStream(OutputStream out) {
    this(out, DEFAULT_BUFFER_SIZE, Cryptor.getInstance());
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
      int num = Math.min(len, bufSize - orgOff);
      System.arraycopy(b, off, orgBuf, orgOff, num);
      off    += num;
      len    -= num;
      orgOff += num;
      if (orgOff >= bufSize) {
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
   * Overridden to log the statistics.
   */
  @Override
  public void close() throws IOException {
    if (!isClosed()) {
      super.close();
      LOGGER.fine(() ->
                    "encrypted output stream stats: total=" + totalWritten +
                    ", encrypted=" + totalEncrypted +
                    ", ratio=" + (int)(totalEncrypted * 100.0d / (totalWritten == 0 ? 1 : totalWritten)) + "%");
    }
  }


  /**
   * Encrypts the buffer and writes it as a packet.
   *
   * @throws IOException if writing failed or the encrypted packet is too large
   */
  private void flushBuffer() throws IOException {
    if (orgOff > 0) {
      byte[] encBuf = cryptor.encrypt(orgBuf, 0, orgOff);
      if (encBuf.length > MAX_BUFFER_SIZE) {
        // the header cannot hold the size: fail instead of corrupting the stream
        throw new IOException("encrypted packet too large (" + encBuf.length + " bytes): reduce the buffer size of " + bufSize);
      }
      writeHeader(encBuf.length);
      out.write(encBuf, 0, encBuf.length);
      LOGGER.fine("encrypted packet written: {0}/{1}", encBuf.length, orgOff);
      totalEncrypted += encBuf.length;
      orgOff = 0;
    }
  }

}
