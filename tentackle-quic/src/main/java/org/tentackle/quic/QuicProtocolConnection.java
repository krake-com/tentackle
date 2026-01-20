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
import tech.kwik.core.server.ApplicationProtocolConnection;

import org.tentackle.log.Logger;

import java.io.IOException;

public class QuicProtocolConnection implements ApplicationProtocolConnection {

  private static final Logger LOGGER = Logger.get(QuicProtocolConnection.class);


  private final QuicTripTransport transport;

  public QuicProtocolConnection(QuicTripTransport transport) {
    this.transport = transport;
  }

  @Override
  public void acceptPeerInitiatedStream(QuicStream quicStream) {
    transport.getConnectionThreadPool().execute(() -> {
      try {
        String connectionName = quicStream.toString();
        transport.createConnectionHandler(connectionName, quicStream.getInputStream(), quicStream.getOutputStream()).run();
      }
      catch (Exception e) {
        LOGGER.warning("cannot handle QUIC stream: " + quicStream, e);
      }
      catch (Throwable t) {
        LOGGER.severe("unexpected error in connection handler thread: " + quicStream, t);
      }
      finally {
        // connection is closed, some server error, whatever
        try {
          quicStream.getOutputStream().close();
          quicStream.getInputStream().close();
        }
        catch (IOException e) {
          LOGGER.warning("cannot close QUIC stream: " + quicStream, e);
        }
      }
    });
  }

}
