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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link AppendableWriter}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AppendableWriterTest {

  @Test
  public void testDefaultConstructorUsesStringBuffer() {
    AppendableWriter writer = new AppendableWriter();
    assertTrue(writer.getAppendable() instanceof StringBuffer);
    assertEquals(writer.toString(), "");
  }

  @Test
  public void testGetAppendable() {
    StringBuilder sb = new StringBuilder();
    AppendableWriter writer = new AppendableWriter(sb);
    assertSame(writer.getAppendable(), sb);
  }

  @Test
  public void testWriteString() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello");
    writer.write(" world");
    assertEquals(writer.toString(), "hello world");
  }

  @Test
  public void testWriteStringWithOffsetAndLength() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello world", 6, 5);
    assertEquals(writer.toString(), "world");
  }

  @Test
  public void testWriteCharArray() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello".toCharArray());
    assertEquals(writer.toString(), "hello");
  }

  @Test
  public void testWriteCharArrayPrefix() throws IOException {
    // writing a prefix starting at offset 0
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello".toCharArray(), 0, 3);
    assertEquals(writer.toString(), "hel");
  }

  @Test
  public void testWriteCharArrayWithOffset() throws IOException {
    // regression: write(cbuf, off, len) must honor the offset and write exactly len chars
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello".toCharArray(), 1, 3);
    assertEquals(writer.toString(), "ell");
  }

  @Test
  public void testWriteCharArraySuffix() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello".toCharArray(), 3, 2);
    assertEquals(writer.toString(), "lo");
  }

  @Test
  public void testWriteCharArrayZeroLengthIsNoOp() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("hello".toCharArray(), 2, 0);
    assertEquals(writer.toString(), "");
  }

  @Test
  public void testWriteCharArrayOutOfBoundsThrows() {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    char[] buf = "hello".toCharArray();
    assertThrows(IndexOutOfBoundsException.class, () -> writer.write(buf, -1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> writer.write(buf, 0, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> writer.write(buf, 4, 3));
  }

  @Test
  public void testAppendCharSequence() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    AppendableWriter returned = writer.append("abc");
    assertSame(returned, writer);
    assertEquals(writer.toString(), "abc");
  }

  @Test
  public void testAppendNullCharSequenceWritesNull() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.append((CharSequence) null);
    assertEquals(writer.toString(), "null");
  }

  @Test
  public void testAppendCharSequenceRange() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.append("hello world", 6, 11);
    assertEquals(writer.toString(), "world");
  }

  @Test
  public void testAppendNullCharSequenceRangeWritesFromNull() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    // a null sequence is treated as the literal "null"
    writer.append(null, 1, 3);
    assertEquals(writer.toString(), "ul");
  }

  @Test
  public void testAppendChar() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.append('x').append('y');
    assertEquals(writer.toString(), "xy");
  }

  @Test
  public void testFlushAndCloseAreNoOpAndWriterStaysUsable() throws IOException {
    AppendableWriter writer = new AppendableWriter(new StringBuilder());
    writer.write("a");
    writer.flush();
    writer.close();
    // still usable after close
    writer.write("b");
    assertEquals(writer.toString(), "ab");
  }
}
