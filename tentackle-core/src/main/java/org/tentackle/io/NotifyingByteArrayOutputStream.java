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
 * A byte array output stream notifying waiting threads when data was written or the stream was reset.
 *
 * @see BlockingByteArrayInputStream
 * @author harald
 */
public class NotifyingByteArrayOutputStream extends ByteArrayOutputStream {

  /**
   * Creates a new byte array output stream. The buffer capacity is initially 32
   * bytes, though its size increases if necessary.
   */
  public NotifyingByteArrayOutputStream() {
    super();
  }

  /**
   * Creates a new byte array output stream, with a buffer capacity of the
   * specified size, in bytes.
   *
   * @param size the initial size.
   * @throws IllegalArgumentException if size is negative.
   */
  public NotifyingByteArrayOutputStream(int size) {
    super(size);
  }

  @Override
  public synchronized void reset() {
    super.reset();
    notifyAll();   // there is only one listener at a time
  }

  @Override
  public synchronized void write(int b) {
    super.write(b);
    notifyAll();
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    super.write(b, off, len);
    notifyAll();
  }

}
