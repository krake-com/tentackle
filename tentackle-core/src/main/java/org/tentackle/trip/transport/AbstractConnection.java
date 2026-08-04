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
import org.tentackle.trip.Serializer;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripStream;

import java.nio.ByteBuffer;

/**
 * Base class of a connection.
 */
public abstract class AbstractConnection implements Connection {

  private final Transport transport;
  private final TripStream stream;
  private Pool<Connection> pool;
  private int poolId;

  /**
   * Creates a new TCP connection.
   *
   * @param transport the transport associated with this connection
   * @param stream the stream associated with this connection
   */
  public AbstractConnection(Transport transport, TripStream stream) {
    this.transport = transport;
    this.stream = stream;

    Serializer serializer = stream.getSerializer();

    // write the 2-bytes magic number
    serializer.serialize(TripConstants.MAGIC);

    // write the transport ID
    ByteBuffer bb = ByteBuffer.allocate(16);
    bb.putLong(transport.getId().getMostSignificantBits());
    bb.putLong(transport.getId().getLeastSignificantBits());
    stream.getSerializer().serialize(bb.array());

    // Tell the server whether our dictionary is still empty. Only then it may create a new
    // dictionary for this transport. Otherwise, we would send ordinals it never learned.
    // If another connection of this transport is populating the dictionary right now, the server
    // still holds it and answers with that one, so the flag does not matter in that case.
    serializer.serializeBoolean(stream.getDictionary().isEmpty());

    serializer.flush();
  }

  @Override
  public Transport getTransport() {
    return transport;
  }

  @Override
  public TripStream getStream() {
    return stream;
  }

  @Override
  public void flush() {
    stream.getSerializer().flush();
    stream.clearModifiable();
  }

  @Override
  public void setPool(Pool<Connection> pool) {
    this.pool = pool;
  }

  @Override
  public Pool<Connection> getPool() {
    return pool;
  }

  @Override
  public void setPoolId(int poolId) {
    this.poolId = poolId;
  }

  @Override
  public int getPoolId() {
    return poolId;
  }

  @Override
  public String toString() {
    return getTransport().toString();
  }

}
