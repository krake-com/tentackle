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
import java.util.function.BooleanSupplier;

/**
 * QUIC connection.
 */
public class QuicTripConnection extends AbstractConnection {

  private final BooleanSupplier connectionAlive;
  private final QuicStream quicStream;
  private boolean closed;

  /**
   * Creates a new QUIC connection.
   *
   * @param transport the transport associated with this connection
   * @param dictionary the dictionary associated with this connection
   * @param connectionAlive tells whether the QUIC connection the stream belongs to is still established
   * @param quicStream the QUIC stream associated with this connection
   */
  public QuicTripConnection(QuicTripTransport transport, Dictionary dictionary,
                            BooleanSupplier connectionAlive, QuicStream quicStream) {
    super(transport, transport.createStream(dictionary, quicStream));
    this.connectionAlive = connectionAlive;
    this.quicStream = quicStream;
  }

  @Override
  public boolean isOpen() {
    // The QUIC connection may be terminated without the stream being closed explicitly, for example
    // by the max idle timeout of either peer. Such a stream is unusable, and the pool must not hand it out.
    return !closed && connectionAlive.getAsBoolean();
  }

  @Override
  public void close() {
    closed = true;    // even if closing the streams fails: the connection must not be used anymore
    try {
      quicStream.getOutputStream().close();
      quicStream.getInputStream().close();
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
