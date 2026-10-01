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

import org.tentackle.log.Logger;
import org.tentackle.trip.TripStream;

/**
 * Loopback connection for testing purposes.
 */
public class LoopbackConnection extends AbstractConnection {

  private static final Logger LOGGER = Logger.get(LoopbackConnection.class);

  private final LoopbackTransport transport;

  /**
   * Creates a new loopback connection.
   *
   * @param transport the transport associated with this connection
   * @param stream the stream associated with this connection
   */
  public LoopbackConnection(LoopbackTransport transport, TripStream stream) {
    super(transport, stream);
    this.transport = transport;
  }

  @Override
  public void flush() {
    super.flush();
    transport.getConnectionPool().request();
    transport.getConnectionPool().getResponse();
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public void close() {
    LOGGER.info("closing connection " + this);
  }

}
