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

import org.tentackle.trip.Transport;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.remote.RemoteCall;

import java.io.ByteArrayOutputStream;

/**
 * Loopback call handler for testing purposes.
 */
public class LoopbackCallHandler extends CallHandler {

  private final LoopbackConnectionPool connectionPool;

  /**
   * Creates a new handler.
   *
   * @param transport  the transport associated with this handler
   * @param stream     the stream associated with this handler
   * @param clientInfo the client connection information
   * @param remoteCall the remote call
   */
  public LoopbackCallHandler(Transport transport, TripStream stream, TransportClientInfo clientInfo, RemoteCall remoteCall) {
    super(transport, stream, clientInfo, remoteCall);
    connectionPool = ((LoopbackTransport) getTransport()).getConnectionPool();
  }

  @Override
  public void finalizeResponse() {
    super.finalizeResponse();
    ByteArrayOutputStream bos = (ByteArrayOutputStream) getStream().getSerializer().getOutputStream();
    connectionPool.respond(bos.toByteArray());
    bos.reset();
  }

}
