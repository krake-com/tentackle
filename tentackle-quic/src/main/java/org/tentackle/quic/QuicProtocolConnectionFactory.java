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

public class QuicProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {

  private final QuicTripTransport transport;

  public QuicProtocolConnectionFactory(QuicTripTransport transport) {
    this.transport = transport;
  }

  @Override
  public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
    return new QuicProtocolConnection(transport);
  }

  @Override
  public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
    return 0;  // Because unidirectional streams are not used
  }

  @Override
  public int maxConcurrentPeerInitiatedBidirectionalStreams() {
    return Integer.MAX_VALUE;
  }

}
