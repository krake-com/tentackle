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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link QuicTripConnection}, the {@link org.tentackle.trip.Connection} wrapping a single
 * bidirectional QUIC stream. Uses a {@link FakeQuicStream} so no QUIC connection is involved.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicTripConnectionTest {

  private QuicTripTransport transport;
  private Dictionary dictionary;

  @BeforeMethod
  public void setUp() {
    transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    dictionary = TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
  }

  @AfterMethod
  public void tearDown() {
    transport.close();
  }


  /**
   * Creating a connection performs the TRIP handshake preamble: the magic number followed by
   * the client transport's UUID, which the server uses to pick the matching dictionary.
   */
  @Test
  public void sendsTheTripPreambleOnCreation() {
    Reporter.log("sendsTheTripPreambleOnCreation", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    QuicTripConnection connection = new QuicTripConnection(transport, dictionary, () -> true, quicStream);

    ByteBuffer expected = ByteBuffer.allocate(TripConstants.MAGIC.length + 16);
    expected.put(TripConstants.MAGIC);
    expected.putLong(transport.getId().getMostSignificantBits());
    expected.putLong(transport.getId().getLeastSignificantBits());
    assertEquals(quicStream.getWrittenBytes(), expected.array());

    assertSame(connection.getTransport(), transport);
    assertSame(connection.getStream().getDictionary(), dictionary);
    assertTrue(connection.isOpen());
  }

  @Test
  public void closesBothHalvesOfTheQuicStream() {
    Reporter.log("closesBothHalvesOfTheQuicStream", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    QuicTripConnection connection = new QuicTripConnection(transport, dictionary, () -> true, quicStream);

    connection.close();

    assertFalse(connection.isOpen());
    assertTrue(quicStream.isOutputClosed());
    assertTrue(quicStream.isInputClosed());
  }

  @Test
  public void reportsTheTransportAndTheQuicStreamInToString() {
    Reporter.log("reportsTheTransportAndTheQuicStreamInToString", true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    QuicTripConnection connection = new QuicTripConnection(transport, dictionary, () -> true, quicStream);

    assertEquals(connection.toString(), transport.toString() + quicStream);
  }

  @Test
  public void wrapsIoExceptionWhileClosing() {
    Reporter.log("wrapsIoExceptionWhileClosing", true);
    IOException failure = new IOException("stream is gone");
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0], failure);
    QuicTripConnection connection = new QuicTripConnection(transport, dictionary, () -> true, quicStream);

    TripRuntimeException exception = expectThrows(TripRuntimeException.class, connection::close);
    assertSame(exception.getCause(), failure);
    assertFalse(connection.isOpen());    // closed even though closing the stream failed
  }

  /**
   * The QUIC connection can be terminated without the stream being closed explicitly, for example
   * by the max idle timeout of either peer (RFC-9000, 10.1). The stream is unusable then, and the
   * connection must report that, so the pool drops it instead of handing it out.
   */
  @Test
  public void isNotOpenOnceTheQuicConnectionIsGone() {
    Reporter.log("isNotOpenOnceTheQuicConnectionIsGone", true);
    AtomicBoolean connected = new AtomicBoolean(true);
    FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
    QuicTripConnection connection = new QuicTripConnection(transport, dictionary, connected::get, quicStream);

    assertTrue(connection.isOpen());
    connected.set(false);
    assertFalse(connection.isOpen());
  }

}
