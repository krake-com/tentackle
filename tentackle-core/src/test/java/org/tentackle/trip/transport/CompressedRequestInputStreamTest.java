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

package org.tentackle.trip.transport;

import org.testng.annotations.Test;

import org.tentackle.io.CompressedInputStream;
import org.tentackle.trip.TripConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link CompressedRequestInputStream}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompressedRequestInputStreamTest {

  /** The connection request as the client sends it: magic, UUID and dictionary flag. */
  private static byte[] request() {
    byte[] request = new byte[TripConstants.MAGIC.length + 16 + 1];
    request[0] = TripConstants.MAGIC[0];
    request[1] = TripConstants.MAGIC[1];
    byte[] uuid = new byte[16];
    new Random(42).nextBytes(uuid);
    System.arraycopy(uuid, 0, request, TripConstants.MAGIC.length, uuid.length);
    request[request.length - 1] = 1;    // client dictionary empty
    return request;
  }

  /** Reads all bytes through the guard and a compressed input stream, as the server does. */
  private static byte[] serverRead(byte[] wire) throws IOException {
    try (InputStream in = new CompressedInputStream(new CompressedRequestInputStream(new ByteArrayInputStream(wire)))) {
      return in.readAllBytes();
    }
  }

  /** Writes the request and a message as the client does: the request is flushed as a packet of its own. */
  private static byte[] clientWrite(byte[] request, byte[] message, int minCompressSize) throws IOException {
    ByteArrayOutputStream wire = new ByteArrayOutputStream();
    try (OutputStream out = new FastCompressedOutputStream(wire, CompressedRequestInputStream.minCompressSize(minCompressSize))) {
      out.write(request);
      out.flush();
      out.write(message);
    }
    return wire.toByteArray();
  }

  /** A block of the given header followed by the given filler as the "request". */
  private static byte[] block(int b0, int b1, int filler) {
    byte[] block = new byte[CompressedRequestInputStream.BLOCK_SIZE];
    Arrays.fill(block, (byte) filler);
    block[0] = (byte) b0;
    block[1] = (byte) b1;
    return block;
  }


  @Test
  public void testRoundTrip() throws IOException {
    byte[] request = request();
    byte[] message = new byte[5000];    // compressible: compressed packets follow the request
    Arrays.fill(message, (byte) 'x');
    for (int minCompressSize : new int[] { 0, 1, 19, 64, 1024 }) {
      byte[] read = serverRead(clientWrite(request, message, minCompressSize));
      assertEquals(Arrays.copyOfRange(read, 0, request.length), request, "minCompressSize " + minCompressSize);
      assertEquals(Arrays.copyOfRange(read, request.length, read.length), message, "minCompressSize " + minCompressSize);
    }
  }

  @Test
  public void testRequestOfPreviousVersion() throws IOException {
    // a client of the previous version sends no dictionary flag: an 18-byte packet
    byte[] request = Arrays.copyOf(request(), CompressedRequestInputStream.REQUEST_SIZE);
    request[0] = TripConstants.MAGIC_1[0];
    request[1] = TripConstants.MAGIC_1[1];
    assertEquals(serverRead(clientWrite(request, new byte[0], 1024)), request);
  }

  @Test
  public void testMinCompressSize() {
    assertEquals(CompressedRequestInputStream.minCompressSize(0), CompressedRequestInputStream.BLOCK_SIZE);
    assertEquals(CompressedRequestInputStream.minCompressSize(1024), 1024);
  }

  @Test
  public void testInvalidFirstPacketRejected() {
    int[][] headers = {
        { 0xff, 0xff },   // compressed, 32K - 1
        { 0x01, 0x80 },   // compressed, 1 byte
        { 0x00, 0x00 },   // empty: would be skipped forever
        { 0x01, 0x00 },   // too short for the request
        { 0x11, 0x00 },   // 17 bytes
    };
    for (int[] header : headers) {
      byte[] wire = block(header[0], header[1], 0xff);
      expectThrows(IOException.class, () -> serverRead(wire));
    }
  }

  @Test
  public void testNotDecidedBeforeWholeBlock() throws IOException {
    // garbage deflate data after a compressed header: the inflater would fail at the third byte
    for (int length = 3; length < CompressedRequestInputStream.BLOCK_SIZE; length++) {
      byte[] wire = Arrays.copyOf(block(0xff, 0xff, 0xff), length);
      expectThrows(EOFException.class, () -> serverRead(wire));    // waits for the block, never "invalid"
    }
    // the whole block is consumed before the decision, but not more
    byte[] wire = new byte[CompressedRequestInputStream.BLOCK_SIZE + 10];
    Arrays.fill(wire, (byte) 0xff);
    ByteArrayInputStream raw = new ByteArrayInputStream(wire);
    try (InputStream in = new CompressedRequestInputStream(raw)) {
      IOException ex = expectThrows(IOException.class, in::read);
      assertEquals(ex.getClass(), IOException.class);
      assertEquals(raw.available(), 10);
    }
  }

  @Test
  public void testPassThrough() throws IOException {
    byte[] wire = new byte[100];
    new Random(1).nextBytes(wire);
    wire[0] = 50;   // uncompressed packet of 50 bytes
    wire[1] = 0;
    try (InputStream in = new CompressedRequestInputStream(new ByteArrayInputStream(wire))) {
      assertEquals(in.read(), 50);
      assertEquals(in.skip(9), 9);
      byte[] rest = in.readAllBytes();
      assertEquals(rest, Arrays.copyOfRange(wire, 10, wire.length));
      assertEquals(in.read(), -1);
    }
  }

}
