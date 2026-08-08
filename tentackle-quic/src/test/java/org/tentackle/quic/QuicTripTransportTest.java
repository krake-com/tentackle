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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link QuicTripTransport} that do not need a peer:
 * the validation performed by {@code accept()} and the bridging of a {@code QuicStream} onto a
 * TRIP {@code TripStream}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicTripTransportTest {

  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  @Test
  public void refusesToAcceptWithoutKeyStore() {
    Reporter.log("refusesToAcceptWithoutKeyStore", true);
    // QUIC mandates TLS, hence a server certificate is not optional
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      TripRuntimeException exception = expectThrows(TripRuntimeException.class, transport::accept);
      assertTrue(exception.getMessage().contains("ksfile"), "unexpected message: " + exception.getMessage());
      assertFalse(transport.isAccepting());
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void refusesToAcceptWithoutPort() {
    Reporter.log("refusesToAcceptWithoutPort", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create(
      "tripq://localhost?" + QuicTestSupport.keyStoreParameters(keyStore)));
    try {
      TripRuntimeException exception = expectThrows(TripRuntimeException.class, transport::accept);
      assertTrue(exception.getMessage().contains("invalid port number -1"),
                 "unexpected message: " + exception.getMessage());
      assertFalse(transport.isAccepting());
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void refusesToAcceptWithOutOfRangePort() {
    Reporter.log("refusesToAcceptWithOutOfRangePort", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create(
      "tripq://localhost:70000?" + QuicTestSupport.keyStoreParameters(keyStore)));
    try {
      TripRuntimeException exception = expectThrows(TripRuntimeException.class, transport::accept);
      assertTrue(exception.getMessage().contains("invalid port number 70000"),
                 "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void acceptsAndRefusesToAcceptTwice() throws Exception {
    Reporter.log("acceptsAndRefusesToAcceptTwice", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create(
      "tripq://localhost:" + QuicTestSupport.freeUdpPort() + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    try {
      transport.accept();
      assertTrue(transport.isAccepting());
      assertNotNull(transport.getRegistry());
      assertNotNull(transport.getConnectionThreadPool());
      assertNotNull(transport.getCallThreadPool());

      expectThrows(TripRuntimeException.class, transport::accept);
    }
    finally {
      transport.close();
    }
  }

  /**
   * Closing an accepting transport must shut down the KWIK server connector, so that the UDP
   * port is free again. Re-binding the very same port is the proof.
   */
  @Test
  public void closeReleasesTheServerPort() throws Exception {
    Reporter.log("closeReleasesTheServerPort", true);
    int port = QuicTestSupport.freeUdpPort();
    String uri = "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore);

    QuicTripTransport first = new QuicTripTransport(URI.create(uri));
    first.accept();
    assertTrue(first.isAccepting());
    first.close();
    assertFalse(first.isAccepting());
    assertTrue(first.isClosed());

    // would throw "Address already in use" if the connector were still holding the socket
    QuicTripTransport second = new QuicTripTransport(URI.create(uri));
    try {
      second.accept();
      assertTrue(second.isAccepting());
    }
    finally {
      second.close();
    }
  }

  /**
   * The UDP socket is bound before KWIK validates the server certificate, so a keystore KWIK
   * cannot use must not leave the port bound behind.
   */
  @Test
  public void failedAcceptReleasesTheServerPort() throws Exception {
    Reporter.log("failedAcceptReleasesTheServerPort", true);
    // KWIK supports RSA and EC certificates only, so a DSA one makes ServerConnector.build() fail
    // after the socket has already been bound
    Path dsaKeyStore = QuicTestSupport.createKeyStore("DSA", 1024);
    try {
      int port = QuicTestSupport.freeUdpPort();
      QuicTripTransport transport = new QuicTripTransport(URI.create(
        "tripq://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(dsaKeyStore)));
      try {
        TripRuntimeException exception = expectThrows(TripRuntimeException.class, transport::accept);
        assertEquals(exception.getCause().getClass(), CertificateException.class);
        assertFalse(transport.isAccepting());
      }
      finally {
        transport.close();
      }
      QuicTestSupport.assertPortIsFree(port);
    }
    finally {
      Files.deleteIfExists(dsaKeyStore);
    }
  }

  /**
   * Closing a transport that never accepted, or closing twice, must not fail.
   */
  @Test
  public void closeIsIdempotent() throws Exception {
    Reporter.log("closeIsIdempotent", true);
    QuicTripTransport clientSide = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    clientSide.close();
    clientSide.close();

    QuicTripTransport serverSide = new QuicTripTransport(URI.create(
      "tripq://localhost:" + QuicTestSupport.freeUdpPort() + "?" + QuicTestSupport.keyStoreParameters(keyStore)));
    serverSide.accept();
    serverSide.close();
    serverSide.close();
    assertFalse(serverSide.isAccepting());
  }

  @Test
  public void readsMaxStreamsFromTheUriQuery() {
    Reporter.log("readsMaxStreamsFromTheUriQuery", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345?maxstreams=7"));
    try {
      assertEquals(transport.getUriPars().get("maxstreams"), Integer.valueOf(7));
      assertEquals(transport.getUriPars().getOrDefault("maxstreams", 128), Integer.valueOf(7));
    }
    finally {
      transport.close();
    }
  }

  @Test
  public void defaultsMaxStreamsWhenNotConfigured() {
    Reporter.log("defaultsMaxStreamsWhenNotConfigured", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      assertEquals(transport.getUriPars().getOrDefault("maxstreams", 128), Integer.valueOf(128));
    }
    finally {
      transport.close();
    }
  }

  /**
   * The only integration point with TRIP serialization: a {@code QuicStream}'s input/output streams
   * must end up as the deserializer/serializer of the returned {@code TripStream}.
   */
  @Test
  public void createsTripStreamOnTopOfTheQuicStream() {
    Reporter.log("createsTripStreamOnTopOfTheQuicStream", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      FakeQuicStream quicStream = new FakeQuicStream(new byte[0]);
      Dictionary dictionary = TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
      TripStream stream = transport.createStream(dictionary, quicStream);

      assertSame(stream.getDictionary(), dictionary);
      // the serializer writes through the stall guard, which passes everything on to the QUIC stream
      assertTrue(stream.getSerializer().getOutputStream() instanceof GuardedQuicOutputStream,
                 "expected a guarded output stream, got " + stream.getSerializer().getOutputStream());
      assertSame(stream.getDeserializer().getInputStream(), quicStream.getInputStream());

      // the plain transport must not touch the payload: it appears verbatim in the QUIC stream
      byte[] payload = "the quick brown fox".getBytes();
      stream.getSerializer().serialize(payload);
      stream.getSerializer().flush();
      assertTrue(indexOf(quicStream.getWrittenBytes(), payload) >= 0,
                 "payload was not written unmodified to the QUIC stream");
    }
    finally {
      transport.close();
    }
  }

  /**
   * Finds the first occurrence of {@code needle} within {@code haystack}.
   *
   * @param haystack the bytes to search in
   * @param needle the bytes to search for
   * @return the offset, -1 if not found
   */
  static int indexOf(byte[] haystack, byte[] needle) {
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
        return i;
      }
    }
    return -1;
  }

}
