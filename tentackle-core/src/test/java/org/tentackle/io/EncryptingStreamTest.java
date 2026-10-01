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

import org.tentackle.common.Cryptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;

/**
 * Tests the {@link EncryptingOutputStream} / {@link DecryptingInputStream} round trip.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class EncryptingStreamTest {

  // a self-contained cryptor, independent of any application-provided @Service(Cryptor.class)
  private static final Cryptor CRYPTOR = new Cryptor("some salt", "some passphrase");

  /** Writes {@code data} through an encrypting stream and reads it back decrypted. */
  private static byte[] roundTrip(byte[] data, int bufSize) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, bufSize, CRYPTOR)) {
      out.write(data);
    }
    try (DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR)) {
      return in.readAllBytes();
    }
  }

  /**
   * A full buffer of the maximum size grows beyond the maximum packet size when encrypted.
   * This must fail instead of writing a corrupt packet header.
   */
  @Test
  public void testEncryptedPacketTooLarge() {
    byte[] data = new byte[65535];
    assertThrows(IOException.class, () -> roundTrip(data, 65535));
  }

  @Test
  public void testShortPayloadRoundTrip() throws IOException {
    byte[] data = "secret message".getBytes(StandardCharsets.US_ASCII);
    assertEquals(roundTrip(data, 128), data);
  }

  @Test
  public void testCipherTextDiffersFromPlainText() throws IOException {
    byte[] data = new byte[64];
    java.util.Arrays.fill(data, (byte) 'X');
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, 128, CRYPTOR)) {
      out.write(data);
    }
    // the on-the-wire bytes must not contain the clear text run
    byte[] wire = sink.toByteArray();
    assertFalse(new String(wire, StandardCharsets.US_ASCII).contains("XXXXXXXX"));
  }

  @Test
  public void testPayloadLargerThanBufferIsSplitIntoPackets() throws IOException {
    byte[] data = new byte[1000];
    new Random(7).nextBytes(data);
    assertEquals(roundTrip(data, 128), data);
  }

  @Test
  public void testSingleByteWriteAndRead() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, 128, CRYPTOR)) {
      for (int i = 0; i < 300; i++) {
        out.write(i & 0xFF);
      }
    }
    try (DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR)) {
      for (int i = 0; i < 300; i++) {
        assertEquals(in.read(), i & 0xFF);
      }
      assertEquals(in.read(), -1);
    }
  }

  @Test
  public void testEmptyStreamReadsMinusOne() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    new EncryptingOutputStream(sink, 128, CRYPTOR).close();
    try (DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR)) {
      assertEquals(in.read(), -1);
    }
  }

  @Test
  public void testRepeatedReadAfterEofReturnsMinusOne() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, 128, CRYPTOR)) {
      out.write("payload".getBytes(StandardCharsets.US_ASCII));
    }
    try (DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR)) {
      assertEquals(new String(in.readAllBytes(), StandardCharsets.US_ASCII), "payload");
      assertEquals(in.read(), -1);
      assertEquals(in.read(), -1);
      assertEquals(in.readAllBytes().length, 0);
    }
  }

  @Test
  public void testReadZeroBytesReturnsZero() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, 128, CRYPTOR)) {
      out.write("payload".getBytes(StandardCharsets.US_ASCII));
    }
    try (DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR)) {
      assertEquals(in.read(new byte[4], 0, 0), 0);
    }
  }

  @Test
  public void testReadWithBadBoundsThrows() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (EncryptingOutputStream out = new EncryptingOutputStream(sink, 128, CRYPTOR)) {
      out.write("payload".getBytes(StandardCharsets.US_ASCII));
    }
    DecryptingInputStream in = new DecryptingInputStream(new ByteArrayInputStream(sink.toByteArray()), CRYPTOR);
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> in.read(new byte[4], 2, 5));
  }

  @Test
  public void testIllegalBufferSizeTooSmall() {
    assertThrows(IllegalArgumentException.class,
                 () -> new EncryptingOutputStream(new ByteArrayOutputStream(), 64, CRYPTOR));
  }

  @Test
  public void testIllegalBufferSizeTooLarge() {
    assertThrows(IllegalArgumentException.class,
                 () -> new EncryptingOutputStream(new ByteArrayOutputStream(), 70000, CRYPTOR));
  }

  @Test
  public void testNullCryptorRejected() {
    assertThrows(NullPointerException.class,
                 () -> new EncryptingOutputStream(new ByteArrayOutputStream(), 128, null));
  }
}
