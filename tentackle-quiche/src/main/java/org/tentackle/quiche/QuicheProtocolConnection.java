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
import org.tentackle.quiche.engine.QuicheConnection;
import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.quiche.engine.StreamListener;
import org.tentackle.trip.transport.MultiplexedConnectionGuard;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The server side of the QUIC connections.<br>
 * Dispatches each stream a client opens to a TRIP connection handler running on a virtual thread,
 * and subjects each QUIC connection to the transport's pre-authentication policy
 * (see {@link MultiplexedConnectionGuard}).
 */
class QuicheProtocolConnection implements StreamListener {

  private static final Logger LOGGER = Logger.get(QuicheProtocolConnection.class);


  private final QuicheTripTransport transport;
  private final Map<QuicheConnection, MultiplexedConnectionGuard> guards;

  /**
   * Creates a protocol connection.
   *
   * @param transport the transport the streams belong to
   */
  QuicheProtocolConnection(QuicheTripTransport transport) {
    this.transport = transport;
    guards = new ConcurrentHashMap<>();
  }

  @Override
  public boolean connectionAccepted(QuicheConnection connection) {
    // Not closed on the event loop: the closer may be invoked while evicting another connection.
    // No reason phrase: it would tell the peer what it talks to.
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(
      transport, connection.toString(),
      () -> Thread.ofVirtual().name("TRIP QUIC closer").start(() -> connection.closeGracefully(0, "")));
    if (guard == null) {
      return false;
    }
    guards.put(connection, guard);
    return true;
  }

  @Override
  public void connectionClosed(QuicheConnection connection) {
    MultiplexedConnectionGuard guard = guards.remove(connection);
    if (guard != null) {
      guard.closed();
    }
  }

  @Override
  public void streamOpened(QuicheStream quicStream) {
    try {
      ExecutorService connectionThreadPool = transport.getConnectionThreadPool();
      if (connectionThreadPool == null) {
        throw new IllegalStateException("transport " + transport + " is not accepting connections (anymore)");
      }
      MultiplexedConnectionGuard guard = guards.get(quicStream.getConnection());
      connectionThreadPool.execute(() -> {
        Runnable handler = null;
        try {
          String connectionName = quicStream.toString();
          handler = transport.createConnectionHandler(connectionName,
                                                      quicStream.getInputStream(), quicStream.getOutputStream());
          if (guard != null) {
            guard.streamOpened(handler);
          }
          handler.run();
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
          if (guard != null && handler != null) {
            guard.streamClosed(handler);
          }
        }
      });
    }
    catch (RuntimeException e) {
      /*
       * This runs on the endpoint's event loop, which an exception must not escape into: it would fail the
       * whole endpoint. Swallowing the failure - the transport being closed concurrently, the thread pool
       * rejecting the task - is not enough either, since the stream would stay open with nobody handling
       * it, and the client would wait for an answer forever.
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
