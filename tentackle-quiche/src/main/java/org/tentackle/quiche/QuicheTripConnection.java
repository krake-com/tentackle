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

package org.tentackle.quiche;

import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.AbstractConnection;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * A pooled TRIP connection on a single bidirectional QUIC stream.
 */
public class QuicheTripConnection extends AbstractConnection {

  private final BooleanSupplier connectionAlive;
  private final QuicheStream quicStream;
  private volatile boolean closed;    // volatile: closed by the pool's closing thread, read by the pool

  /**
   * Creates a TRIP connection on a QUIC stream.
   * <p>
   * Notice: the constructor sends the TRIP preamble, so it may well fail. The stream is only owned by the
   * connection once the constructor returned; until then, closing it is up to the caller.
   *
   * @param transport the transport associated with this connection
   * @param dictionary the dictionary associated with this connection
   * @param connectionAlive tells whether the QUIC connection the stream belongs to is still established
   * @param quicStream the QUIC stream associated with this connection
   */
  QuicheTripConnection(QuicheTripTransport transport, Dictionary dictionary,
                       BooleanSupplier connectionAlive, QuicheStream quicStream) {
    super(transport, transport.createStream(dictionary, quicStream));
    this.connectionAlive = connectionAlive;
    this.quicStream = quicStream;
  }

  @Override
  public boolean isOpen() {
    // The QUIC connection may be terminated without the stream being closed explicitly, for example,
    // by the max idle timeout of either peer. Such a stream is unusable, and the pool must not hand it out.
    return !closed && connectionAlive.getAsBoolean();
  }

  @Override
  public void close() {
    closed = true;    // even if closing the streams fails: the connection must not be used anymore
    try {
      QuicheTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      throw new TripRuntimeException("closing " + quicStream + " failed", e);
    }
  }

  @Override
  public String toString() {
    return quicStream.toString();
  }

}
