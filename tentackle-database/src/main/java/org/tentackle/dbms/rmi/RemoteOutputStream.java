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

package org.tentackle.dbms.rmi;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author harald
 */
public class RemoteOutputStream extends OutputStream {


  private final OutputStreamRemoteDelegate delegate;
  private final byte[] singleBuf;


  /**
   * Creates a remote output stream.
   *
   * @param delegate the delegate
   */
  public RemoteOutputStream(OutputStreamRemoteDelegate delegate) {
    this.delegate = delegate;
    singleBuf = new byte[1];
  }

  @Override
  public void write(int b) throws IOException {
    singleBuf[0] = (byte) b;
    write(singleBuf, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    byte[] buf = b;
    if (off != 0 || len != b.length) {
      buf = new byte[len];
      System.arraycopy(b, off, buf, 0, len);
    }
    delegate.write(buf);
  }

}
