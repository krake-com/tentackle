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

import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.TripStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Loopback connection handler for testing purposes.
 */
public class LoopbackConnectionHandler extends ConnectionHandler {

  private final LoopbackTransport transport;
  private TripStream stream;
  private boolean firstCall = true;

  /**
   * Creates a new handler.
   *
   * @param transport    the transport associated with this handler
   * @param name         the name of the connection
   * @param inputStream  the input stream associated with this connection
   * @param outputStream the output stream associated with this connection
   */
  public LoopbackConnectionHandler(LoopbackTransport transport, String name, InputStream inputStream, OutputStream outputStream) {
    super(transport, name, inputStream, outputStream);
    this.transport = transport;
  }

  @Override
  protected TripStream createStream(UUID id, boolean clientDictionaryEmpty) {
    stream = super.createStream(id, clientDictionaryEmpty);
    return stream;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The loopback client runs in the same JVM: there is nobody to hide from.
   *
   * @return false
   */
  @Override
  protected boolean isRejectionDelayed() {
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The loopback client waits for an answer.
   *
   * @return false
   */
  @Override
  protected boolean isErrorSuppressed(PreAuthenticationPolicy policy, PreAuthenticationPolicy.Admission admission) {
    return false;
  }

  @Override
  protected boolean isConnectionAlive() {
    if (firstCall) {
      // request already received
      firstCall = false;
    }
    else {
      // wait for the next request
      byte[] request = transport.getConnectionPool().getRequest();
      stream.getDeserializer().setInputStream(new ByteArrayInputStream(request));
    }
    return super.isConnectionAlive();
  }
}
