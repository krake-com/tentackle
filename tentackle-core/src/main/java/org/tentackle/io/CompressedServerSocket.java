/**
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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;



/**
 * Zip-compressed server socket.
 *
 * @author harald
 */
public class CompressedServerSocket extends ServerSocket {

  /**
   * Creates a compressed server socket.
   *
   * @param port the port number, or <code>0</code> to use any free port.
   * @throws IOException if creating the socket failed
   */
  public CompressedServerSocket(int port) throws IOException {
    super(port);
  }

  @Override
  public Socket accept() throws IOException {
    Socket socket = new CompressedSocket();
    implAccept(socket);
    return socket;
  }
}
