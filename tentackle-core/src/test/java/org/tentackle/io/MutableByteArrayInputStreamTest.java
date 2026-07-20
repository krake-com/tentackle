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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;

/**
 * Tests the {@link MutableByteArrayInputStream}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MutableByteArrayInputStreamTest {

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  private static String readAll(MutableByteArrayInputStream in) throws IOException {
    return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
  }

  @Test
  public void testEmptyStream() {
    MutableByteArrayInputStream in = new MutableByteArrayInputStream();
    assertEquals(in.available(), 0);
    assertEquals(in.read(), -1);
  }

  @Test
  public void testReadFromInitialBuffer() throws IOException {
    MutableByteArrayInputStream in = new MutableByteArrayInputStream(bytes("hello"));
    assertEquals(in.available(), 5);
    assertEquals(readAll(in), "hello");
    assertEquals(in.read(), -1);
  }

  @Test
  public void testReadWithOffsetAndLength() throws IOException {
    // read "ell" out of "hello"
    MutableByteArrayInputStream in = new MutableByteArrayInputStream(bytes("hello"), 1, 3);
    assertEquals(in.available(), 3);
    assertEquals(readAll(in), "ell");
  }

  @Test
  public void testSetBufferReplacesContent() throws IOException {
    MutableByteArrayInputStream in = new MutableByteArrayInputStream(bytes("first"));
    assertEquals(readAll(in), "first");
    // now reuse the very same stream instance with fresh content
    in.setBuffer(bytes("second"));
    assertEquals(in.available(), 6);
    assertEquals(readAll(in), "second");
  }

  @Test
  public void testSetBufferWithOffsetAndLength() throws IOException {
    MutableByteArrayInputStream in = new MutableByteArrayInputStream();
    in.setBuffer(bytes("hello world"), 6, 5);
    assertEquals(in.available(), 5);
    assertEquals(readAll(in), "world");
  }

  @Test
  public void testSetBufferLengthClampedToArrayEnd() throws IOException {
    MutableByteArrayInputStream in = new MutableByteArrayInputStream();
    // request more than is available past the offset: count is clamped to buf.length
    in.setBuffer(bytes("hello"), 3, 100);
    assertEquals(in.available(), 2);
    assertEquals(readAll(in), "lo");
  }

  @Test
  public void testResetReturnsToBufferStart() throws IOException {
    // setBuffer sets the mark to the offset, so reset() rewinds there
    MutableByteArrayInputStream in = new MutableByteArrayInputStream();
    in.setBuffer(bytes("abcdef"), 2, 4);
    assertEquals(in.read(), 'c');
    assertEquals(in.read(), 'd');
    in.reset();
    assertEquals(readAll(in), "cdef");
  }
}
