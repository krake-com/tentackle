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

import org.tentackle.common.InterruptedRuntimeException;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream waiting for data available in a {@link NotifyingByteArrayOutputStream}.<br>
 * The data written so far is moved from the output stream into this input stream, i.e. the output stream is reset.
 *
 * @author harald
 */
public class BlockingByteArrayInputStream extends InputStream {

  private final NotifyingByteArrayOutputStream out;     // output stream to wait for
  private byte[] buf;                                   // last chunk from out
  private int pos;                                      // next byte to read

  /**
   * Creates an input stream on an output stream.
   *
   * @param out the data source
   */
  public BlockingByteArrayInputStream(NotifyingByteArrayOutputStream out) {
    this.out = out;
  }


  /**
   * Gets the output stream.
   *
   * @return the stream
   */
  public NotifyingByteArrayOutputStream getOutputStream() {
    return out;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Blocks until data is available. Never returns -1.
   *
   * @throws org.tentackle.common.InterruptedRuntimeException if interrupted while waiting
   */
  @Override
  public int read() throws IOException {
    for(;;) {
      synchronized(out) {
        if (buf == null || pos >= buf.length) {
          if (out.size() == 0) {
            try {
              out.wait();   // wait until more data available
            }
            catch (InterruptedException ex) {
              throw new InterruptedRuntimeException("read interrupted", ex);
            }
          }
          buf = out.toByteArray();
          pos = 0;
          out.reset();
        }
        else  {
          return buf[pos++] & 0xff;
        }
      }
    }
  }

}
