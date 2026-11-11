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

package org.tentackle.trip;

import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TransportKeyTest {

  /**
   * Transports are unique by scheme, host and port: neither the path nor the query parameters count.
   */
  @Test
  void identityIgnoresPathAndQuery() {
    TransportKey key = new TransportKey(URI.create("trip://localhost:4711/someObject?a=1&b=2"));
    TransportKey otherQuery = new TransportKey(URI.create("trip://localhost:4711/otherObject?a=3"));
    assertEquals(otherQuery, key);
    assertEquals(otherQuery.hashCode(), key.hashCode());

    assertNotEquals(new TransportKey(URI.create("trips://localhost:4711?a=1&b=2")), key);
    assertNotEquals(new TransportKey(URI.create("trip://otherhost:4711?a=1&b=2")), key);
    assertNotEquals(new TransportKey(URI.create("trip://localhost:4712?a=1&b=2")), key);
  }

  @Test
  void sameQuery() {
    TransportKey key = new TransportKey(URI.create("trip://localhost:4711?a=1&b=2"));
    assertTrue(key.isSameQuery(new TransportKey(URI.create("trip://localhost:4711/someObject?b=2&a=1"))));
    assertFalse(key.isSameQuery(new TransportKey(URI.create("trip://localhost:4711?a=1"))));
    assertFalse(key.isSameQuery(new TransportKey(URI.create("trip://localhost:4711?a=1&b=3"))));
    assertFalse(key.isSameQuery(new TransportKey(URI.create("trip://localhost:4711"))));

    TransportKey noQuery = new TransportKey(URI.create("trip://localhost:4711"));
    assertTrue(noQuery.isSameQuery(new TransportKey(URI.create("trip://localhost:4711?"))));
    assertTrue(noQuery.isSameQuery(new TransportKey("trip", "localhost", 4711, "")));
  }

  /**
   * The key is JVM-local and may hold passwords in its query parameters.
   */
  @Test
  void notSerializable() {
    assertThrows(TripRuntimeException.class, () -> TripFactory.getInstance().getType(TransportKey.class));
  }

}
