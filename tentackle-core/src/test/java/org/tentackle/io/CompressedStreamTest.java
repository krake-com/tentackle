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

package org.tentackle.io;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the {@link CompressedOutputStream} / {@link CompressedInputStream} round trip.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompressedStreamTest {

  /** Writes {@code data} through a compressed output stream and reads it back. */
  private static byte[] roundTrip(byte[] data, int bufSize, int minCompressSize) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink, bufSize, minCompressSize)) {
      out.write(data);
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      return in.readAllBytes();
    }
  }

  /**
   * The header and the data of a packet must reach the underlying stream in a single write.<br>
   * Written separately, a socket sends the header on its own and, with Nagle's algorithm, holds back
   * the data until the peer acknowledges it.
   */
  @Test
  public void testEachPacketIsWrittenAtOnce() throws IOException {
    WriteCountingOutputStream sink = new WriteCountingOutputStream();
    byte[] small = "hi".getBytes(StandardCharsets.US_ASCII);
    byte[] compressible = new byte[3000];
    java.util.Arrays.fill(compressible, (byte) 'A');
    byte[] incompressible = new byte[500];
    new Random(4711).nextBytes(incompressible);
    try (CompressedOutputStream out = new CompressedOutputStream(sink, 1024, 64)) {
      out.write(small);
      out.flush();
      assertEquals(sink.getWrites(), 1, "uncompressed packet");
      out.write(compressible);    // 1024 + 1024 + 952 bytes
      out.flush();
      assertEquals(sink.getWrites(), 4, "compressed packets");
      out.write(incompressible);
      out.flush();
      assertEquals(sink.getWrites(), 5, "incompressible packet");
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(in.readNBytes(small.length), small);
      assertEquals(in.readNBytes(compressible.length), compressible);
      assertEquals(in.readNBytes(incompressible.length), incompressible);
      assertEquals(in.read(), -1);
    }
  }

  @Test
  public void testSmallPayloadStaysUncompressed() throws IOException {
    // below minCompressSize -> uncompressed packet, but must still round trip
    byte[] data = "hi".getBytes(StandardCharsets.US_ASCII);
    assertEquals(roundTrip(data, 32767, 64), data);
  }

  @Test
  public void testHighlyCompressiblePayload() throws IOException {
    // long, repetitive data -> exercises the compressed packet path
    byte[] data = new byte[10_000];
    java.util.Arrays.fill(data, (byte) 'A');
    assertEquals(roundTrip(data, 32767, 64), data);
  }

  @Test
  public void testIncompressiblePayload() throws IOException {
    // random data does not shrink, so an uncompressed packet is emitted despite exceeding minCompressSize
    byte[] data = new byte[5000];
    new Random(42).nextBytes(data);
    assertEquals(roundTrip(data, 32767, 64), data);
  }

  @Test
  public void testPayloadLargerThanBufferIsSplitIntoPackets() throws IOException {
    // several buffers worth of compressible data forces multiple packets
    byte[] data = new byte[4000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i % 16);
    }
    assertEquals(roundTrip(data, 1024, 64), data);
  }

  @Test
  public void testSingleByteWriteAndRead() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink)) {
      for (int i = 0; i < 200; i++) {
        out.write(i & 0xFF);
      }
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      for (int i = 0; i < 200; i++) {
        assertEquals(in.read(), i & 0xFF);
      }
      assertEquals(in.read(), -1);
    }
  }

  @Test
  public void testEmptyStreamReadsMinusOne() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    new CompressedOutputStream(sink).close();
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(in.read(), -1);
    }
  }

  @Test
  public void testEmptyStreamReadAllBytesIsEmpty() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    new CompressedOutputStream(sink).close();
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(in.readAllBytes().length, 0);
    }
  }

  @Test
  public void testRepeatedReadAfterEofOnUncompressedStream() throws IOException {
    // regression: reading past EOF must keep returning -1, not throw (uncompressed last packet)
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink)) {
      out.write("hi".getBytes(StandardCharsets.US_ASCII));   // < minCompressSize -> uncompressed
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(new String(in.readAllBytes(), StandardCharsets.US_ASCII), "hi");
      assertEquals(in.read(), -1);
      assertEquals(in.read(), -1);
      assertEquals(in.readAllBytes().length, 0);
    }
  }

  @Test
  public void testRepeatedReadAfterEofOnCompressedStream() throws IOException {
    // regression: reading past EOF must keep returning -1, not throw (compressed last packet)
    byte[] data = new byte[500];
    java.util.Arrays.fill(data, (byte) 'A');   // compresses -> compressed last packet
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink)) {
      out.write(data);
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(in.readAllBytes(), data);
      assertEquals(in.read(), -1);
      assertEquals(in.read(), -1);
      assertEquals(in.readAllBytes().length, 0);
    }
  }

  @Test
  public void testReadZeroBytesReturnsZero() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink)) {
      out.write("payload".getBytes(StandardCharsets.US_ASCII));
    }
    try (CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(in.read(new byte[4], 0, 0), 0);
    }
  }

  @Test
  public void testReadWithBadBoundsThrows() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (CompressedOutputStream out = new CompressedOutputStream(sink)) {
      out.write("payload".getBytes(StandardCharsets.US_ASCII));
    }
    CompressedInputStream in = new CompressedInputStream(new ByteArrayInputStream(sink.toByteArray()));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> in.read(new byte[4], 2, 5));
  }

  @Test
  public void testIllegalBufferSizeTooLarge() {
    assertThrows(IllegalArgumentException.class,
                 () -> new CompressedOutputStream(new ByteArrayOutputStream(), 40000, 64));
  }

  @Test
  public void testIllegalBufferSizeNotAboveMinCompressSize() {
    assertThrows(IllegalArgumentException.class,
                 () -> new CompressedOutputStream(new ByteArrayOutputStream(), 64, 64));
  }
}
