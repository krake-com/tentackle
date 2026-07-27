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

import org.testng.annotations.Test;

import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import java.net.URI;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that the QUIC transports plug themselves into TRIP purely via the {@code @TransportService}
 * SPI, i.e. that the service index generated at build time really maps the {@code tripq} and
 * {@code tripcq} URI schemes onto the transport classes.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicTransportServiceTest {

  @Test
  public void resolvesPlainQuicTransportByScheme() {
    URI uri = URI.create(QuicTripTransport.PROTOCOL + "://localhost:12345");
    Transport transport = TripFactory.getInstance().createTransport(uri);
    try {
      assertEquals(transport.getClass(), QuicTripTransport.class);
      assertEquals(transport.getUri(), uri);
      assertFalse(transport.isAccepting());   // creating a transport neither connects nor binds
      assertFalse(transport.isClosed());
      assertNotNull(transport.getId());
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void resolvesCompressedQuicTransportByScheme() {
    URI uri = URI.create(CompressedQuicTripTransport.PROTOCOL + "://localhost:12345");
    Transport transport = TripFactory.getInstance().createTransport(uri);
    try {
      assertEquals(transport.getClass(), CompressedQuicTripTransport.class);
      assertTrue(transport instanceof QuicTripTransport, "the compressed transport must be a QUIC transport");
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void protocolConstantsMatchTheServiceAnnotations() {
    assertEquals(QuicTripTransport.PROTOCOL, "tripq");
    assertEquals(CompressedQuicTripTransport.PROTOCOL, "tripcq");
    assertEquals(QuicTripTransport.class.getAnnotation(TransportService.class).value(),
                 QuicTripTransport.PROTOCOL);
    assertEquals(CompressedQuicTripTransport.class.getAnnotation(TransportService.class).value(),
                 CompressedQuicTripTransport.PROTOCOL);
  }

  @Test
  public void rejectsUnknownScheme() {
    expectThrows(TripRuntimeException.class,
                 () -> TripFactory.getInstance().createTransport(URI.create("tripq2://localhost:12345")));
  }

  /**
   * Transports are keyed by their {@link org.tentackle.trip.TransportKey}, so the manager must hand
   * out the same instance for the same endpoint and distinct ones for different schemes.
   */
  @Test
  public void transportManagerSharesTransportsPerEndpoint() {
    TransportManager manager = TransportManager.getInstance();
    URI plain = URI.create(QuicTripTransport.PROTOCOL + "://localhost:12346/someObject");
    URI plainOtherPath = URI.create(QuicTripTransport.PROTOCOL + "://localhost:12346/otherObject");
    URI compressed = URI.create(CompressedQuicTripTransport.PROTOCOL + "://localhost:12346/someObject");

    Transport first = manager.requestTransport(plain);
    Transport second = manager.requestTransport(plainOtherPath);
    Transport third = manager.requestTransport(compressed);
    try {
      // the URI path denotes the remote object and is maintained by the registry, not by the transport
      assertSame(second, first);
      assertEquals(first.getClass(), QuicTripTransport.class);
      assertEquals(third.getClass(), CompressedQuicTripTransport.class);
    }
    finally {
      manager.releaseTransport(third);
      manager.releaseTransport(second);
      manager.releaseTransport(first);
    }
  }

}
