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

import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.remote.RemoteCall;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Loopback transport for testing purposes.
 * <p>
 * Differs from regular transports in that it accepts only one connection.<br>
 * Emulates the server side in separate threads in the same way a regular transport would do.
 */
@TransportService(LoopbackTransport.PROTOCOL)
public class LoopbackTransport extends AbstractTransport {

  /**
   * The protocol name.
   */
  public static final String PROTOCOL = "loopback";

  private LoopbackConnectionPool connectionPool;

  /**
   * Creates a new transport.
   *
   * @param uri the URI of the transport
   */
  public LoopbackTransport(URI uri) {
    super(uri);
  }

  @Override
  public boolean isAccepting() {
    return true;
  }

  /**
   * Gets the loopback connection pool.
   *
   * @return the pool
   */
  public LoopbackConnectionPool getConnectionPool() {
    return connectionPool;
  }

  /**
   * Share the loopback connection pool with the server transport.
   *
   * @param connectionPool the client transport's connection pool
   */
  public void setConnectionPool(LoopbackConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    return new LoopbackConnectionHandler(this, connectionName, inputStream, outputStream);
  }

  @Override
  public Runnable createCallHandler(TripStream stream, TransportClientInfo clientInfo, RemoteCall remoteCall) {
    return new LoopbackCallHandler(this, stream, clientInfo, remoteCall);
  }

  @Override
  protected Pool<Connection> createClientConnectionPool() {
    connectionPool = new LoopbackConnectionPool(this, getClientDictionary());
    return connectionPool;
  }

}
