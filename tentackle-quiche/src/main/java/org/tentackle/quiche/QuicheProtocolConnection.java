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

import org.tentackle.log.Logger;
import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.quiche.engine.StreamListener;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * The server side of a QUIC connection.<br>
 * Dispatches each stream a client opens to a TRIP connection handler running on a virtual thread.
 */
class QuicheProtocolConnection implements StreamListener {

  private static final Logger LOGGER = Logger.get(QuicheProtocolConnection.class);


  private final QuicheTripTransport transport;

  /**
   * Creates a protocol connection.
   *
   * @param transport the transport the streams belong to
   */
  QuicheProtocolConnection(QuicheTripTransport transport) {
    this.transport = transport;
  }

  @Override
  public void streamOpened(QuicheStream quicStream) {
    try {
      ExecutorService connectionThreadPool = transport.getConnectionThreadPool();
      if (connectionThreadPool == null) {
        throw new IllegalStateException("transport " + transport + " is not accepting connections (anymore)");
      }
      connectionThreadPool.execute(() -> {
        try {
          String connectionName = quicStream.toString();
          transport.createConnectionHandler(connectionName,
                                            quicStream.getInputStream(), quicStream.getOutputStream()).run();
        }
        catch (Exception e) {
          LOGGER.warning("cannot handle QUIC stream: " + quicStream, e);
        }
        catch (Throwable t) {
          LOGGER.severe("unexpected error in connection handler thread: " + quicStream, t);
        }
        finally {
          // connection is closed, some server error, whatever
          closeStream(quicStream);
        }
      });
    }
    catch (RuntimeException e) {
      /*
       * This runs on the endpoint's event loop, which discards whatever it throws, so an unhandled
       * failure - the transport being closed concurrently, the thread pool rejecting the task - would
       * leave the stream open with nobody handling it: the client would wait for an answer forever.
       */
      LOGGER.warning("cannot dispatch QUIC stream: " + quicStream, e);
      closeStream(quicStream);
    }
  }

  /**
   * Closes both halves of the stream, logging instead of propagating a failure.
   *
   * @param quicStream the stream to close
   */
  private void closeStream(QuicheStream quicStream) {
    try {
      QuicheTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      LOGGER.warning("cannot close QUIC stream: " + quicStream, e);
    }
  }

}
