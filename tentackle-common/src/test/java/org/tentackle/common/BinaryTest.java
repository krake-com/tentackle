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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertSame;

/**
 * Tests {@link Binary}, {@link TBinary} and the shared {@link AbstractBinary} logic.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BinaryTest {

  @Test
  public void testRoundtrip() throws IOException, ClassNotFoundException {
    String value = "hello world";
    Binary<String> binary = new Binary<>(value);
    assertTrue(binary.getLength() > 0);
    assertFalse(binary.isEmpty());
    assertEquals(binary.getObject(), value);

    // read back from the stored bytes as the database would
    Binary<String> restored = Binary.create(
        new ByteArrayInputStream(binary.getData(), 0, binary.getLength()), 0, false);
    assertFalse(restored.isEmpty());
    assertEquals(restored.getObject(), value);
  }

  @Test
  public void testNullObject() throws IOException, ClassNotFoundException {
    Binary<String> binary = new Binary<>(null);
    assertEquals(binary.getLength(), 0);
    assertTrue(binary.isEmpty());
    assertNull(binary.getObject());
    assertTrue(new Binary<>().isEmpty());
  }

  @Test
  public void testIsEmptyAfterClearing() throws IOException {
    Binary<String> binary = new Binary<>("value");
    assertFalse(binary.isEmpty());
    binary.setObject(null);
    assertTrue(binary.isEmpty());

    TBinary<String> tbinary = new TBinary<>("value");
    assertFalse(tbinary.isEmpty());
    tbinary.setObject(null);
    assertTrue(tbinary.isEmpty());
  }

  @Test
  public void testCreateFromNullOrEmptyStream() throws IOException, ClassNotFoundException {
    assertNull(Binary.create(null, 0, false));
    assertNull(TBinary.create(null, 0, false));
    // an empty stream is an empty binary, not null
    Binary<String> binary = Binary.create(new ByteArrayInputStream(new byte[0]), 0, false);
    assertNotNull(binary);
    assertTrue(binary.isEmpty());
    assertNull(binary.getObject());
    assertEquals(binary, new Binary<>());
    TBinary<String> tbinary = TBinary.create(new ByteArrayInputStream(new byte[0]), 0, false);
    assertNotNull(tbinary);
    assertTrue(tbinary.isEmpty());
    assertNull(tbinary.getObject());
    assertEquals(tbinary, new TBinary<>());
  }

  @Test
  public void testGrowingBuffer() throws IOException, ClassNotFoundException {
    // force the internal buffer to grow several times by using a tiny initial buffer
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 5000; i++) {
      sb.append((char) ('a' + (i % 26)));
    }
    String value = sb.toString();
    Binary<String> binary = new Binary<>(value);
    Binary<String> restored = Binary.create(
        new ByteArrayInputStream(binary.getData(), 0, binary.getLength()), 128, false);
    assertEquals(restored.getObject(), value);
  }

  @Test
  public void testEqualsAndHashCode() throws IOException {
    Binary<String> a = new Binary<>("same content");
    // a binary read back from a stream keeps its content but may use a larger backing array
    Binary<String> b = Binary.create(
        new ByteArrayInputStream(a.getData(), 0, a.getLength()), 0, false);
    assertEquals(a, b);
    // equal binaries must have equal hash codes, even with differently sized backing arrays
    assertEquals(a.hashCode(), b.hashCode());

    Binary<String> c = new Binary<>("other content");
    assertNotSame(a, c);
    assertNotEquals(c, a);
  }

  @Test
  public void testEqualsDetectsLastByteDifference() throws IOException {
    // two payloads differing only in the very last byte must not be considered equal
    Binary<String> a = new Binary<>("payloadX");
    Binary<String> b = new Binary<>("payloadY");
    assertNotEquals(b, a);
  }

  @Test
  public void testEmptyBinariesAreEqual() {
    Binary<String> a = new Binary<>();
    Binary<String> b = new Binary<>();
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  public void testFreeze() throws IOException {
    Binary<String> binary = new Binary<>("frozen");
    assertFalse(binary.isFrozen());
    binary.freeze();
    assertTrue(binary.isFrozen());
    // a frozen binary rejects modification
    assertThrows(TentackleRuntimeException.class, () -> binary.setObject("new value"));
  }

  @Test
  public void testCloneIsDeepAndUnfrozen() throws IOException {
    Binary<String> binary = new Binary<>("cloneable");
    binary.freeze();
    Binary<String> clone = binary.clone();
    assertEquals(clone, binary);
    assertNotSame(clone, binary);
    assertNotSame(clone.getData(), binary.getData());
    // clones are always unfrozen and hence modifiable
    assertFalse(clone.isFrozen());
    clone.setObject("modified");
  }

  @Test
  public void testTBinaryRoundtrip() throws IOException, ClassNotFoundException {
    // TBinary uses the pluggable ObjectSerializer instead of raw JOS
    String value = "tbinary value";
    TBinary<String> binary = new TBinary<>(value);
    assertTrue(binary.getLength() > 0);
    assertFalse(binary.isEmpty());
    assertEquals(binary.getObject(), value);

    TBinary<String> restored = TBinary.create(
        new ByteArrayInputStream(binary.getData(), 0, binary.getLength()), 0, false);
    assertEquals(restored.getObject(), value);
  }

  @Test
  public void testTBinaryNullAndFreeze() throws IOException, ClassNotFoundException {
    TBinary<String> binary = new TBinary<>(null);
    assertEquals(binary.getLength(), 0);
    assertTrue(binary.isEmpty());
    assertTrue(new TBinary<>().isEmpty());
    assertNull(binary.getObject());

    TBinary<String> frozen = new TBinary<>("x");
    frozen.freeze();
    assertThrows(TentackleRuntimeException.class, () -> frozen.setObject("y"));
  }

  @Test
  public void testTBinaryCachesObject() throws IOException, ClassNotFoundException {
    TBinary<String> binary = TBinary.create(
        new ByteArrayInputStream(new TBinary<>("cached").getData()), 0, false);
    String first = binary.getObject();
    assertEquals(first, "cached");
    // deserialized only once, like Binary
    assertSame(binary.getObject(), first);
  }

}
