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

package org.tentackle.quic;

import tech.kwik.core.QuicStream;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.AbstractConnection;

import java.io.IOException;

/**
 * QUIC connection.
 */
public class QuicTripConnection extends AbstractConnection {

  private final QuicStream quicStream;
  private boolean closed;

  /**
   * Creates a new QUIC connection.
   *
   * @param transport the transport associated with this connection
   * @param dictionary the dictionary associated with this connection
   * @param quicStream the QUIC stream associated with this connection
   */
  public QuicTripConnection(QuicTripTransport transport, Dictionary dictionary, QuicStream quicStream) {
    super(transport, transport.createStream(dictionary, quicStream));
    this.quicStream = quicStream;
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

  @Override
  public void close() {
    try {
      quicStream.getOutputStream().close();
      quicStream.getInputStream().close();
      closed = true;
    }
    catch (IOException e) {
      throw new TripRuntimeException("closing " + quicStream + " failed", e);
    }
  }

  @Override
  public String toString() {
    return getTransport().toString() + quicStream.toString();
  }

}
