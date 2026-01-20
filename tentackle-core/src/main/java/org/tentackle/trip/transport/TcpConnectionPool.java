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

import org.tentackle.misc.PoolSlot;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

/**
 * The TCP client connection pool.
 */
public class TcpConnectionPool extends AbstractConnectionPool {

  private final TcpTransport transport;

  /**
   * Creates a TCP connection pool.
   *
   * @param transport     the transport associated with this pool
   * @param dictionary    the dictionary associated with this pool
   */
  public TcpConnectionPool(TcpTransport transport, Dictionary dictionary) {
    super(transport, dictionary);
    this.transport = transport;
  }

  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    return new ConnectionPoolSlot(this, slotNumber,
                                  new TcpConnection(transport, getDictionary(), createSocket()));
  }


  /**
   * Creates a new client socket.
   *
   * @return the connected socket
   */
  protected Socket createSocket() {
    URI uri = transport.getUri();
    try {
      return transport.getSocketFactory().createSocket(uri.getHost(), uri.getPort());
    }
    catch (IOException e) {
      throw new TripRuntimeException("connection to " + uri + " failed", e);
    }
  }

}
