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

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.server.ApplicationProtocolConnection;

import org.tentackle.log.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * The server side of a QUIC connection.<br>
 * Dispatches each stream a client opens to a TRIP connection handler running on a virtual thread.
 */
public class QuicProtocolConnection implements ApplicationProtocolConnection {

  private static final Logger LOGGER = Logger.get(QuicProtocolConnection.class);


  private final QuicTripTransport transport;
  private final QuicConnection quicConnection;

  /**
   * Creates a protocol connection.
   *
   * @param transport the transport the streams belong to
   * @param quicConnection the QUIC connection of the client, all streams are multiplexed over it
   */
  public QuicProtocolConnection(QuicTripTransport transport, QuicConnection quicConnection) {
    this.transport = transport;
    this.quicConnection = quicConnection;
  }

  @Override
  public void acceptPeerInitiatedStream(QuicStream quicStream) {
    try {
      ExecutorService connectionThreadPool = transport.getConnectionThreadPool();
      if (connectionThreadPool == null) {
        throw new IllegalStateException("transport " + transport + " is not accepting connections (anymore)");
      }
      connectionThreadPool.execute(() -> {
        try {
          String connectionName = quicStream.toString();
          // Writing the response is guarded just as the client guards writing the call: a client whose
          // flow control window never reopens would otherwise pin this handler - and the call thread it
          // waits for - forever. Discarding the QUIC connection is what the server can do about it;
          // everything multiplexed over a wedged connection is lost anyway.
          transport.createConnectionHandler(connectionName, quicStream.getInputStream(),
                                            transport.createOutputStream(quicStream, this::abortConnection)).run();
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
       * KWIK invokes this method from a shared callback thread and discards whatever it throws, so an
       * unhandled failure - the transport being closed concurrently, the thread pool rejecting the task -
       * would leave the stream open with nobody handling it: the client would wait for an answer forever.
       */
      LOGGER.warning("cannot dispatch QUIC stream: " + quicStream, e);
      closeStream(quicStream);
    }
  }

  /**
   * Discards the client's QUIC connection because writing to one of its streams stalled.
   * <p>
   * Runs on the watchdog thread. The client notices the connection going away and establishes
   * a replacement on its next request.
   */
  private void abortConnection() {
    LOGGER.warning("discarding the wedged QUIC connection {0}", quicConnection);
    quicConnection.close();
  }

  /**
   * Closes both halves of the stream, logging instead of propagating a failure.
   *
   * @param quicStream the stream to close
   */
  private void closeStream(QuicStream quicStream) {
    try {
      QuicTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      LOGGER.warning("cannot close QUIC stream: " + quicStream, e);
    }
  }

}
