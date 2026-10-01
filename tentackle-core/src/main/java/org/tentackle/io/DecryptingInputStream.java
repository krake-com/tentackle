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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Stream to read encrypted data from the underlying input stream.
 * <p>
 * Counterpart to {@link EncryptingOutputStream}.
 */
public class DecryptingInputStream extends PacketSizedInputStream {

  private static final Logger LOGGER = Logger.get(DecryptingInputStream.class);


  private final Cryptor cryptor;        // the cryptor to decrypt the data
  private final byte[] byteBuf;         // single byte buffer for read()
  private byte[] encryptedBuffer;       // buffer holding encrypted data for the cryptor (size is dynamically adapted)
  private int readPending;              // number of bytes pending to read from the underlying stream
  private byte[] decBuf;                // decrypted data
  private int decOff;                   // number of decrypted bytes delivered so far / offset into decBuf
  private int decLen;                   // number of decrypted bytes in decBuf

  // for statistic only (Level.FINE must be enabled)
  private long totalRead;               // total number of bytes read by application
  private long totalDecrypted;          // number of encrypted bytes read from the stream


  /**
   * Creates a new decrypting input stream.<br>
   * The buffer size adapts dynamically to the packet size.
   *
   * @param in the underlying input stream
   * @param cryptor the cryptor to decrypt the data
   * @throws NullPointerException if cryptor is null
   */
  public DecryptingInputStream(InputStream in, Cryptor cryptor) {
    super(in);
    this.cryptor = Objects.requireNonNull(cryptor);
    byteBuf = new byte[1];
    LOGGER.finer("decrypting input stream created for: {0}", in);
  }

  /**
   * Creates a new decrypting input stream with the default {@link Cryptor}.
   *
   * @param in the underlying input stream
   */
  public DecryptingInputStream(InputStream in) {
    this(in, Cryptor.getInstance());
  }


  @Override
  public void close() throws IOException {
    if (!isClosed()) {
      super.close();
      LOGGER.fine(() ->
                    "decrypting input stream stats: total=" + totalRead +
                    ", decrypted=" + totalDecrypted +
                    ", ratio=" + (int)(totalDecrypted * 100.0d / (totalRead == 0 ? 1 : totalRead)) + "%");
    }
  }

  /**
   * Reads the next decrypted byte of data from the input stream.
   *
   * @return the next byte of data, or <code>-1</code> if the end of the stream is reached.
   * @exception IOException if an I/O error occurs
   */
  @Override
  public int read() throws IOException {
    int num = read(byteBuf, 0, 1);
    return num < 0 ? num : (byteBuf[0] & 0xff);
  }

  /**
   * Reads up to <code>len</code> decrypted bytes of data from this input stream
   * into an array of bytes.
   *
   * @param      b     the buffer into which the data is read.
   * @param      off   the start offset in the destination array <code>b</code>
   * @param      len   the maximum number of bytes read.
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   *
   * @exception  IOException  if an I/O error occurs
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

    if (decOff >= decLen) {
      // read header of next packet
      if (readHeader() < 0) {
        return -1;    // end of stream reached
      }
      int read = 0;
      while (readPending > 0) {
        int num = in.read(encryptedBuffer, read, readPending);
        if (num < 0) {
          throw new EOFException();
        }
        read += num;
        readPending -= num;
      }
      LOGGER.finer("decrypting {0} bytes", read);
      decBuf = cryptor.decrypt(encryptedBuffer, 0, read);
      decOff = 0;
      decLen = decBuf.length;
      totalDecrypted += decLen;
    }

    int num = Math.min(len, decLen - decOff);
    System.arraycopy(decBuf, decOff, b, off, num);
    decOff += num;
    totalRead += num;
    return num;
  }


  @Override
  protected int readHeader() throws IOException {
    readPending = super.readHeader();
    if (readPending >= 0) {
      if (encryptedBuffer == null || encryptedBuffer.length < readPending) {
        // allocate larger buffer
        encryptedBuffer = new byte[readPending];
        LOGGER.finer("decryption buffer enlarged to: {0}", readPending);
      }
    }
    return readPending;
  }

}
