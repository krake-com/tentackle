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

import org.testng.Reporter;
import org.testng.annotations.Test;

import tech.kwik.core.server.ApplicationProtocolConnection;

import java.net.URI;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

/**
 * Tests for {@link QuicProtocolConnectionFactory}, the KWIK callback that creates one protocol
 * connection per incoming QUIC connection.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicProtocolConnectionFactoryTest {

  @Test(timeOut = 30000)
  public void createsOneProtocolConnectionPerQuicConnection() {
    Reporter.log("createsOneProtocolConnectionPerQuicConnection", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      QuicProtocolConnectionFactory factory = new QuicProtocolConnectionFactory(transport);

      // KWIK passes the negotiated protocol and the connection; both are irrelevant here since
      // the transport already knows everything the connection needs
      ApplicationProtocolConnection first = factory.createConnection(QuicTripTransport.PROTOCOL, null);
      ApplicationProtocolConnection second = factory.createConnection(QuicTripTransport.PROTOCOL, null);

      assertEquals(first.getClass(), QuicProtocolConnection.class);
      assertNotSame(second, first);
    }
    finally {
      transport.close();
    }
  }

  /**
   * TRIP is request/response over bidirectional streams, so unidirectional streams are refused
   * while the number of bidirectional streams is left to the server configuration
   * ({@code maxstreams}) rather than being capped here.
   */
  @Test(timeOut = 30000)
  public void declaresBidirectionalStreamsOnly() {
    Reporter.log("declaresBidirectionalStreamsOnly", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      QuicProtocolConnectionFactory factory = new QuicProtocolConnectionFactory(transport);
      assertEquals(factory.maxConcurrentPeerInitiatedUnidirectionalStreams(), 0);
      assertEquals(factory.maxConcurrentPeerInitiatedBidirectionalStreams(), Integer.MAX_VALUE);
    }
    finally {
      transport.close();
    }
  }

}
