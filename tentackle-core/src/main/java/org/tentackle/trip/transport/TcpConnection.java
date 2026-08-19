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

package org.tentackle.trip.transport;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;

import java.io.IOException;
import java.net.Socket;

/**
 * TCP connection.
 */
public class TcpConnection extends AbstractConnection {

  private final Socket socket;

  /**
   * Creates a new TCP connection.
   *
   * @param transport the transport associated with this connection
   * @param dictionary the dictionary associated with this connection
   * @param socket the open socket associated with this connection
   */
  public TcpConnection(TcpTransport transport, Dictionary dictionary, Socket socket) {
    super(transport, transport.createStream(dictionary, socket));
    this.socket = socket;
  }

  @Override
  public boolean isOpen() {
    return !socket.isClosed();
  }

  @Override
  public void close() {
    try {
      socket.close();
    }
    catch (IOException e) {
      throw new TripRuntimeException("closing " + socket + " failed", e);
    }
  }

  @Override
  public String toString() {
    return socket.toString();
  }

}
