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
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import tech.kwik.core.server.ServerConnection;

import org.tentackle.trip.transport.MultiplexedConnectionGuard;

import java.net.InetSocketAddress;

/**
 * The factory KWIK's server connector uses to create a {@link QuicProtocolConnection} for each incoming
 * QUIC connection negotiating the transport's ALPN id.
 */
public class QuicProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {

  private final QuicTripTransport transport;

  /**
   * Creates a factory.
   *
   * @param transport the transport the incoming streams are dispatched to
   */
  public QuicProtocolConnectionFactory(QuicTripTransport transport) {
    this.transport = transport;
  }

  @Override
  public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
    // the peer's address: the connection limits of the pre-authentication policy are per host
    InetSocketAddress peer = quicConnection instanceof ServerConnection serverConnection ?
                               serverConnection.getInitialRemoteAddress() : null;
    if (quicConnection == null) {
      return new QuicProtocolConnection(transport, peer);
    }
    // The QUIC connection itself is subject to the policy as well: without streams, it would not be
    // restricted at all, and the keep-alive keeps it open forever.
    // Not closed in this thread: it is KWIK's, and the closer may be invoked while evicting another connection.
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(
      transport, "QUIC connection to " + peer,
      () -> Thread.ofVirtual().name("TRIP QUIC closer").start(quicConnection::close));
    if (guard == null) {
      return new QuicProtocolConnection(transport, peer, null, true);
    }
    quicConnection.setConnectionListener(_ -> guard.closed());
    return new QuicProtocolConnection(transport, peer, guard, false);
  }

  /**
   * {@inheritDoc}
   * <p>
   * TRIP is request/response over bidirectional streams, so unidirectional streams are not used at all.
   */
  @Override
  public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
    return 0;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Unlimited here: the effective limit is the {@code maxstreams} parameter, which
   * {@link QuicTripTransport#accept()} configures in the server's connection config.
   */
  @Override
  public int maxConcurrentPeerInitiatedBidirectionalStreams() {
    return Integer.MAX_VALUE;
  }

}
