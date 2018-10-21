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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;


/**
 * A compressed socket wrapper on top of another socket, usually an SSL-socket.
 *
 * @author harald
 */
public class CompressedSocketWrapper extends SocketWrapper {


  private CompressedOutputStream out;
  private CompressedInputStream in;


  /**
   * Creates a compressed socket wrapper.
   *
   * @param socket the wrapped socket (for example a {@link javax.net.ssl.SSLSocket}).
   * @throws SocketException if creating the socket failed
   */
  public CompressedSocketWrapper(Socket socket) throws SocketException {
    super(socket);
  }


  @Override
  public InputStream getInputStream() throws IOException {
    if (in == null) {
      in = new CompressedInputStream(super.getInputStream());
    }
    return in;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    if (out == null) {
      out = new CompressedOutputStream(super.getOutputStream());
    }
    return out;
  }

  @Override
  public void close() throws IOException {
    if (out != null) {
      out.close();
      out = null;
    }
    if (in != null) {
      in.close();
      in = null;
    }
    super.close();
  }
}
