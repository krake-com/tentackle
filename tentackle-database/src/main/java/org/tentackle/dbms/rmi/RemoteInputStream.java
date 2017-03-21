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
import java.io.InputStream;

/**
 *
 * @author harald
 */
public class RemoteInputStream extends InputStream {


  private final InputStreamRemoteDelegate delegate;
  private byte[] buf;
  private int pos;


  /**
   * Creates a remote input stream.
   *
   * @param delegate the delegate
   */
  public RemoteInputStream(InputStreamRemoteDelegate delegate) {
    this.delegate = delegate;
  }



  @Override
  public synchronized int read() throws IOException {
    if (buf == null || pos >= buf.length) {
      // get more data from server (wait until data is available)
      buf = delegate.read();
    }
    // data is available
    return buf[pos++];
  }

}
