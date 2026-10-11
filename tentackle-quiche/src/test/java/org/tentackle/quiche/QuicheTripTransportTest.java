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

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.trip.TripRuntimeException;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the life cycle of {@link QuicheTripTransport} that do not need a peer.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicheTripTransportTest {

  /**
   * {@code close()} shuts down the endpoint outside the transport's monitor and marks the transport
   * closed only afterward. An {@code accept()} getting in between must be refused, or it would start an
   * endpoint on a transport that is closed right after: nobody would ever release its UDP port.
   */
  @Test(timeOut = 60000)
  public void refusesToAcceptWhileClosing() throws Exception {
    Reporter.log("refusesToAcceptWhileClosing", true);
    Path keyStore = QuicheTestSupport.createKeyStore();
    try {
      int port = QuicheTestSupport.freeUdpPort();
      AtomicReference<Throwable> acceptFailure = new AtomicReference<>();
      QuicheTripTransport transport = new QuicheTripTransport(URI.create(
        "tripj://localhost:" + port + "?" + QuicheTestSupport.keyStoreParameters(keyStore))) {

        @Override
        protected synchronized Runnable stopAccepting() {
          Runnable closer = super.stopAccepting();
          // exactly the window another thread could get the monitor in
          try {
            accept();
          }
          catch (RuntimeException e) {
            acceptFailure.set(e);
          }
          return closer;
        }
      };

      transport.close();

      assertTrue(acceptFailure.get() instanceof TripRuntimeException,
                 "accept() was not refused: " + acceptFailure.get());
      assertTrue(acceptFailure.get().getMessage().contains("is closed"),
                 "unexpected message: " + acceptFailure.get().getMessage());
      assertTrue(transport.isClosed());
      assertFalse(transport.isAccepting());
      QuicheTestSupport.assertPortIsFree(port);
    }
    finally {
      Files.deleteIfExists(keyStore);
    }
  }

}
