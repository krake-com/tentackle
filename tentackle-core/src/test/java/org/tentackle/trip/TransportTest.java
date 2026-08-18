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

import org.testng.SkipException;
import org.testng.annotations.Test;

import org.tentackle.trip.transport.LoopbackTransport;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

@SuppressWarnings("missing-explicit-ctor")
public class TransportTest {

  @Test
  void createTransports() {
    try {
      Transport transport = TripFactory.getInstance().createTransport(new URI("loopback://localhost:0"));
      assertEquals(transport.getClass(), LoopbackTransport.class);
    }
    catch (URISyntaxException e) {
      fail("invalid URI", e);
    }
  }

  /**
   * All connections of the same client transport must share one dictionary, and each
   * client transport must get its own.
   */
  @Test
  void serverDictionaryPerClientTransport() {
    Transport transport = new LoopbackTransport(URI.create("loopback://localhost:0"));
    transport.accept();
    try {
      UUID clientId = UUID.randomUUID();
      Dictionary dictionary = transport.getServerDictionary(clientId, true);
      assertNotNull(dictionary);

      // each connection reads its own UUID instance from the wire, and the later connections
      // of a client transport report a populated dictionary
      UUID sameClientId = new UUID(clientId.getMostSignificantBits(), clientId.getLeastSignificantBits());
      assertNotSame(sameClientId, clientId);
      assertSame(transport.getServerDictionary(sameClientId, false), dictionary);

      assertNotSame(transport.getServerDictionary(UUID.randomUUID(), true), dictionary);
    }
    finally {
      transport.close();
    }
  }

  /**
   * A client transport reporting a populated dictionary must be rejected whenever the server has
   * none for it, instead of being served with a new, empty one: the client would send ordinals the
   * server never learned. This covers a collected dictionary as well as a restarted server.
   */
  @Test
  void clientWithPopulatedDictionaryIsRejected() {
    Transport transport = new LoopbackTransport(URI.create("loopback://localhost:0"));
    transport.accept();
    try {
      // the server was restarted, or the client's dictionary was collected and its entry expunged
      assertNull(transport.getServerDictionary(UUID.randomUUID(), false),
                 "a client with a populated dictionary must be rejected");

      // and it stays rejected, no matter how often it retries
      UUID clientId = UUID.randomUUID();
      assertNull(transport.getServerDictionary(clientId, false));
      assertNull(transport.getServerDictionary(clientId, false));

      // a fresh client transport is served, though
      assertNotNull(transport.getServerDictionary(clientId, true));
    }
    finally {
      transport.close();
    }
  }

  /**
   * Once the dictionary of a client transport has been collected, a returning connection of that
   * transport must be rejected, since the client kept its own populated dictionary.
   */
  @Test
  void staleClientTransportIsRejected() throws InterruptedException {
    Transport transport = new LoopbackTransport(URI.create("loopback://localhost:0"));
    transport.accept();
    try {
      UUID clientId = UUID.randomUUID();

      // the connection handler reads its own UUID instance from the wire, so nothing outside
      // the transport keeps the instance the dictionary was created for alive
      UUID connectionId = new UUID(clientId.getMostSignificantBits(), clientId.getLeastSignificantBits());
      Dictionary dictionary = transport.getServerDictionary(connectionId, true);
      assertNotNull(dictionary);

      // the connection is closed -> the dictionary becomes collectable
      WeakReference<Dictionary> collectedDictionary = new WeakReference<>(dictionary);
      dictionary = null;
      connectionId = null;
      for (int i = 0; i < 100 && collectedDictionary.get() != null; i++) {
        System.gc();
        Thread.sleep(10);
      }
      if (collectedDictionary.get() != null) {
        throw new SkipException("the dictionary was not garbage collected");
      }

      // the client returns with the same transport UUID, but its own dictionary is still populated
      assertNull(transport.getServerDictionary(clientId, false), "a stale client transport must be rejected");

      // A client transport that never learned anything may start over. The new dictionary must
      // survive, i.e. its entry must not be dropped along with the collected UUID instance.
      Dictionary restarted = transport.getServerDictionary(clientId, true);
      assertNotNull(restarted);
      for (int i = 0; i < 10; i++) {
        System.gc();
        Thread.sleep(20);
      }
      assertSame(transport.getServerDictionary(clientId, false), restarted,
                 "the dictionary of a live client transport must not be dropped");
    }
    finally {
      transport.close();
    }
  }

}
