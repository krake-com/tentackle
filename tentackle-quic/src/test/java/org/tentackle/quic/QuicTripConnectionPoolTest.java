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

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the failure paths of {@link QuicTripConnectionPool}.<br>
 * The successful paths (opening the QUIC connection and one stream per pool slot) are covered
 * end-to-end by {@link QuicRemoteInvocationTest}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicTripConnectionPoolTest {

  @Test
  public void rejectsUriWithoutPort() {
    Reporter.log("rejectsUriWithoutPort", true);
    // unlike TCP, a QUIC client has no default port to fall back to
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost"));
    try {
      IllegalArgumentException exception =
        expectThrows(IllegalArgumentException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains("must be > 0"), "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  /**
   * A QUIC connection that cannot be established must surface as a {@link TripRuntimeException}
   * carrying the original {@link IOException}.
   */
  @Test(timeOut = 60000)
  public void wrapsConnectFailure() {
    Reporter.log("wrapsConnectFailure", true);
    // ".invalid" is reserved by RFC 6761 and never resolves, so this fails without waiting
    // for KWIK's connect timeout
    URI uri = URI.create("tripq://tentackle-quic-test.invalid:9999");
    QuicTripTransport transport = new QuicTripTransport(uri);
    try {
      TripRuntimeException exception =
        expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains(uri.toString()), "unexpected message: " + exception.getMessage());
      assertNotNull(exception.getCause());
      assertTrue(exception.getCause() instanceof IOException,
                 "expected the original IOException, got " + exception.getCause());
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void rejectsNonPositiveConnectTimeout() {
    Reporter.log("rejectsNonPositiveConnectTimeout", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345?connecttimeout=0"));
    try {
      IllegalArgumentException exception =
        expectThrows(IllegalArgumentException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      assertTrue(exception.getMessage().contains("connect timeout must be > 0"),
                 "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  /**
   * {@code connecttimeout} must actually bound how long connecting to an unresponsive server takes.
   * <p>
   * Nothing listens on the port, and a UDP datagram into the void produces no answer, so KWIK
   * waits for the whole handshake timeout &mdash; which is exactly what the parameter controls.
   * Without it, the default of {@value QuicTripConnectionPool#DEFAULT_CONNECT_TIMEOUT} seconds
   * would apply.
   */
  @Test(timeOut = 60000)
  public void honorsTheConfiguredConnectTimeout() throws Exception {
    Reporter.log("honorsTheConfiguredConnectTimeout", true);
    final int connectTimeout = 2;
    URI uri = URI.create("tripq://localhost:" + QuicTestSupport.freeUdpPort() +
                         "?connecttimeout=" + connectTimeout);
    QuicTripTransport transport = new QuicTripTransport(uri);
    try {
      long startTime = System.nanoTime();
      expectThrows(TripRuntimeException.class, () -> new QuicTripConnectionPool(transport, dictionary()));
      long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);

      assertTrue(elapsedSeconds >= connectTimeout,
                 "gave up after " + elapsedSeconds + "s, before the configured timeout of " + connectTimeout + "s");
      assertTrue(elapsedSeconds < QuicTripConnectionPool.DEFAULT_CONNECT_TIMEOUT,
                 "waited " + elapsedSeconds + "s, so the configured timeout was ignored " +
                 "in favor of the " + QuicTripConnectionPool.DEFAULT_CONNECT_TIMEOUT + "s default");
    }
    finally {
      transport.close();
    }
  }

  private static Dictionary dictionary() {
    return TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

}
