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
import org.tentackle.trip.transport.MultiplexedConnectionGuard;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

/**
 * The server side of a QUIC connection.<br>
 * Dispatches each stream a client opens to a TRIP connection handler running on a virtual thread.
 */
public class QuicProtocolConnection implements ApplicationProtocolConnection {

  private static final Logger LOGGER = Logger.get(QuicProtocolConnection.class);


  private final QuicTripTransport transport;
  private final InetSocketAddress peer;
  private final MultiplexedConnectionGuard guard;
  private final boolean refused;

  /**
   * Creates a protocol connection.
   * <p>
   * The peer's address names each stream's connection, which is what the
   * {@link org.tentackle.trip.PreAuthenticationPolicy} groups the connection limits by.
   * KWIK's stream names carry only the stream number.
   *
   * @param transport the transport the streams belong to
   * @param peer the client's address, null if unknown
   * @param guard the guard of the QUIC connection, null if none
   * @param refused true if the pre-authentication policy refused the QUIC connection: its streams are closed at once
   */
  public QuicProtocolConnection(QuicTripTransport transport, InetSocketAddress peer,
                                MultiplexedConnectionGuard guard, boolean refused) {
    this.transport = transport;
    this.peer = peer;
    this.guard = guard;
    this.refused = refused;
  }

  /**
   * Creates a protocol connection without a guard.
   *
   * @param transport the transport the streams belong to
   * @param peer the client's address, null if unknown
   */
  public QuicProtocolConnection(QuicTripTransport transport, InetSocketAddress peer) {
    this(transport, peer, null, false);
  }

  /**
   * Creates a protocol connection for an unknown peer.
   *
   * @param transport the transport the streams belong to
   */
  public QuicProtocolConnection(QuicTripTransport transport) {
    this(transport, null);
  }

  /**
   * Gets the name of a stream's TRIP connection.<br>
   * Same format as the one of quiche: {@code "stream <id> of QUIC connection to /<ip>:<port>"}, so that
   * {@link org.tentackle.trip.transport.TransportClientInfo#getHost(String)} finds the host.
   *
   * @param quicStream the stream
   * @return the connection name
   */
  protected String getConnectionName(QuicStream quicStream) {
    return peer == null ? quicStream.toString() : "stream " + quicStream.getStreamId() + " of QUIC connection to " + peer;
  }

  @Override
  public void acceptPeerInitiatedStream(QuicStream quicStream) {
    if (refused) {
      // the pre-authentication policy refused the QUIC connection, which is being closed
      closeStream(quicStream);
      return;
    }
    try {
      ExecutorService connectionThreadPool = transport.getConnectionThreadPool();
      if (connectionThreadPool == null) {
        throw new IllegalStateException("transport " + transport + " is not accepting connections (anymore)");
      }
      connectionThreadPool.execute(() -> {
        Runnable handler = null;
        try {
          String connectionName = getConnectionName(quicStream);
          handler = transport.createConnectionHandler(connectionName, quicStream.getInputStream(), quicStream.getOutputStream());
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
       * KWIK invokes this method from a shared callback thread and discards whatever it throws, so an
       * unhandled failure - the transport being closed concurrently, the thread pool rejecting the task -
       * would leave the stream open with nobody handling it: the client would wait for an answer forever.
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
  private void closeStream(QuicStream quicStream) {
    try {
      QuicTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      LOGGER.warning("cannot close QUIC stream: " + quicStream, e);
    }
  }

}
