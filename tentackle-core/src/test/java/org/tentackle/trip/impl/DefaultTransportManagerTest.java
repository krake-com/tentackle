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

package org.tentackle.trip.impl;

import org.testng.annotations.Test;

import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripRuntimeException;

import java.net.URI;

import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class DefaultTransportManagerTest {

  /**
   * The same endpoint with the same query parameters, in any order and for any path, shares one transport.
   */
  @Test
  void sameQuerySharesTransport() {
    TransportManager manager = new DefaultTransportManager();
    Transport first = manager.requestTransport(URI.create("loopback://localhost:4711/someObject?a=1&b=2"));
    Transport second = manager.requestTransport(URI.create("loopback://localhost:4711/otherObject?b=2&a=1"));
    try {
      assertSame(second, first);
    }
    finally {
      manager.releaseTransport(second);
      manager.releaseTransport(first);
    }
    assertTrue(first.isClosed());
  }

  /**
   * There cannot be two transports for the same endpoint, so a request with different
   * query parameters must fail instead of handing out a differently configured transport.
   */
  @Test
  void differentQueryIsRejected() {
    TransportManager manager = new DefaultTransportManager();
    Transport transport = manager.requestTransport(URI.create("loopback://localhost:4711?a=1"));
    try {
      assertThrows(TripRuntimeException.class,
                   () -> manager.requestTransport(URI.create("loopback://localhost:4711?a=2")));
      assertThrows(TripRuntimeException.class,
                   () -> manager.requestTransport(URI.create("loopback://localhost:4711")));
      // other endpoints are not affected
      Transport other = manager.requestTransport(URI.create("loopback://localhost:4712?a=2"));
      assertNotSame(other, transport);
      manager.releaseTransport(other);
    }
    finally {
      manager.releaseTransport(transport);
    }
    // once released, the endpoint may be requested with other query parameters
    Transport reconfigured = manager.requestTransport(URI.create("loopback://localhost:4711?a=2"));
    manager.releaseTransport(reconfigured);
  }

}
