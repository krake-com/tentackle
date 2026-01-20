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
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

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
    OutputStream outputStream = stream.getSerializer().getOutputStream();
    try {
      writeMagic(outputStream);
      writeUUID(outputStream, transport.getId());
      outputStream.flush();
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot write magic and UUID to stream", e);
    }
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

  private void writeMagic(OutputStream outputStream) throws IOException {
    outputStream.write(TripConstants.MAGIC);
  }

  private void writeUUID(OutputStream outputStream, UUID id) throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(16);
    bb.putLong(id.getMostSignificantBits());
    bb.putLong(id.getLeastSignificantBits());
    outputStream.write(bb.array());
  }

}
