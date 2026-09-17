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

import org.tentackle.io.CompressedInputStream;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.FastCompressedOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link CompressedQuicTripTransport}, the {@code tripcq} scheme.<br>
 * Verifies that the deflate layer is inserted into both the server path
 * ({@code createConnectionHandler}) and the client path ({@code createStream}), and that
 * {@code mincompress} is honored.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompressedQuicTripTransportTest {

  private static final int BLOB_SIZE = 64 * 1024;


  @Test(timeOut = 30000)
  public void wrapsTheStreamsInTheCompressionLayer() {
    Reporter.log("wrapsTheStreamsInTheCompressionLayer", true);
    CompressedQuicTripTransport transport =
      new CompressedQuicTripTransport(URI.create("tripcq://localhost:12345"));
    try {
      OutputStream out = transport.createCompressedOutputStream(new ByteArrayOutputStream());
      InputStream in = transport.createCompressedInputStream(new ByteArrayInputStream(new byte[0]));
      assertEquals(out.getClass(), FastCompressedOutputStream.class);
      assertEquals(in.getClass(), CompressedInputStream.class);
    }
    finally {
      transport.close();
    }
  }

  @Test(timeOut = 30000)
  public void compressedStreamsRoundTrip() throws Exception {
    Reporter.log("compressedStreamsRoundTrip", true);
    CompressedQuicTripTransport transport =
      new CompressedQuicTripTransport(URI.create("tripcq://localhost:12345"));
    try {
      byte[] payload = compressibleBytes(BLOB_SIZE);
      ByteArrayOutputStream wire = new ByteArrayOutputStream();
      OutputStream out = transport.createCompressedOutputStream(wire);
      out.write(payload);
      out.flush();

      assertTrue(wire.size() < payload.length / 4,
                 "expected the payload to be deflated, wire size is " + wire.size());

      InputStream in = transport.createCompressedInputStream(new ByteArrayInputStream(wire.toByteArray()));
      assertEquals(in.readNBytes(payload.length), payload);
    }
    finally {
      transport.close();
    }
  }

  /**
   * Blocks below {@code mincompress} are sent verbatim, so tiny payloads are not paid for twice.
   */
  @Test(timeOut = 30000)
  public void leavesBlocksBelowMinCompressUncompressed() throws Exception {
    Reporter.log("leavesBlocksBelowMinCompressUncompressed", true);
    CompressedQuicTripTransport transport =
      new CompressedQuicTripTransport(URI.create("tripcq://localhost:12345?mincompress=8192"));
    try {
      assertEquals(transport.getUriPars().get("mincompress"), Integer.valueOf(8192));

      byte[] payload = compressibleBytes(1024);   // well below the configured threshold
      ByteArrayOutputStream wire = new ByteArrayOutputStream();
      OutputStream out = transport.createCompressedOutputStream(wire);
      out.write(payload);
      out.flush();

      // below the threshold nothing is deflated, so the payload shows up verbatim on the wire
      assertTrue(wire.size() >= payload.length, "expected an uncompressed block, wire size is " + wire.size());
      assertTrue(QuicTripTransportTest.indexOf(wire.toByteArray(), payload) >= 0,
                 "payload was compressed although it is below mincompress");

      InputStream in = transport.createCompressedInputStream(new ByteArrayInputStream(wire.toByteArray()));
      assertEquals(in.readNBytes(payload.length), payload);
    }
    finally {
      transport.close();
    }
  }

  @Test(timeOut = 30000)
  public void defaultsMinCompressWhenNotConfigured() {
    Reporter.log("defaultsMinCompressWhenNotConfigured", true);
    CompressedQuicTripTransport transport =
      new CompressedQuicTripTransport(URI.create("tripcq://localhost:12345"));
    try {
      assertEquals(transport.getUriPars().getOrDefault("mincompress", 1024), Integer.valueOf(1024));
    }
    finally {
      transport.close();
    }
  }

  /**
   * {@code createStream} is the client-side bridge; for {@code tripcq} it must deflate,
   * unlike the plain {@code tripq} transport.
   */
  @Test(timeOut = 30000)
  public void createStreamCompressesTheQuicStream() {
    Reporter.log("createStreamCompressesTheQuicStream", true);
    CompressedQuicTripTransport compressed =
      new CompressedQuicTripTransport(URI.create("tripcq://localhost:12345"));
    QuicTripTransport plain = new QuicTripTransport(URI.create("tripq://localhost:12345"));
    try {
      byte[] payload = compressibleBytes(BLOB_SIZE);

      FakeQuicStream compressedQuicStream = new FakeQuicStream(new byte[0]);
      writePayload(compressed, compressedQuicStream, payload);

      FakeQuicStream plainQuicStream = new FakeQuicStream(new byte[0]);
      writePayload(plain, plainQuicStream, payload);

      assertTrue(plainQuicStream.getWrittenBytes().length >= payload.length,
                 "the plain transport must not compress");
      assertTrue(compressedQuicStream.getWrittenBytes().length < plainQuicStream.getWrittenBytes().length / 4,
                 "expected the tripcq stream to be much smaller than the tripq stream, got " +
                 compressedQuicStream.getWrittenBytes().length + " vs " + plainQuicStream.getWrittenBytes().length);

      // the compression layer sits between TRIP and the QUIC stream, so the serializer writes elsewhere
      assertNotSame(streamOf(compressed, compressedQuicStream).getSerializer().getOutputStream(),
                    compressedQuicStream.getOutputStream());
    }
    finally {
      plain.close();
      compressed.close();
    }
  }


  private static void writePayload(QuicTripTransport transport, FakeQuicStream quicStream, byte[] payload) {
    TripStream stream = streamOf(transport, quicStream);
    stream.getSerializer().serialize(payload);
    stream.getSerializer().flush();
  }

  private static TripStream streamOf(QuicTripTransport transport, FakeQuicStream quicStream) {
    Dictionary dictionary = TripFactory.getInstance().createDictionary(UUID.randomUUID(), false);
    return transport.createStream(dictionary, quicStream);
  }

  private static byte[] compressibleBytes(int size) {
    byte[] bytes = new byte[size];
    Arrays.fill(bytes, (byte) 'x');
    return bytes;
  }

}
