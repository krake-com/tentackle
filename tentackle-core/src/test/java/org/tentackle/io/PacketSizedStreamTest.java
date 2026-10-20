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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the packet-header handling and close semantics of
 * {@link PacketSizedInputStream} and {@link PacketSizedOutputStream}.
 *
 * @author harald
 */
@SuppressWarnings({"missing-explicit-ctor", "exports"})
public class PacketSizedStreamTest {

  /** Minimal concrete output stream exposing the protected header writer. */
  private static class TestOut extends PacketSizedOutputStream {
    TestOut(OutputStream out) {
      super(out);
    }
    void packet(byte[] buf, int header, int length) throws IOException {
      writePacket(buf, header, length);
    }
  }

  /** Minimal concrete input stream exposing the protected header reader. */
  private static class TestIn extends PacketSizedInputStream {
    TestIn(InputStream in) {
      super(in);
    }
    int header() throws IOException {
      return readHeader();
    }
  }

  @Test
  public void testPacketIsHeaderFollowedByData() throws IOException {
    WriteCountingOutputStream sink = new WriteCountingOutputStream();
    byte[] buf = new byte[] {0, 0, 'a', 'b', 'c', 'x'};    // data starts after the header, 'x' is not part of it
    new TestOut(sink).packet(buf, 0x8003, 3);
    assertEquals(sink.getWrites(), 1);
    assertEquals(sink.toByteArray(), new byte[] {0x03, (byte) 0x80, 'a', 'b', 'c'});
    TestIn in = new TestIn(new ByteArrayInputStream(sink.toByteArray()));
    assertEquals(in.header(), 0x8003);
  }

  @Test
  public void testReadHeaderReturnsMinusOneAtEndOfStream() throws IOException {
    TestIn in = new TestIn(new ByteArrayInputStream(new byte[0]));
    assertEquals(in.header(), -1);
  }

  @Test
  public void testReadHeaderReturnsMinusOneOnTruncatedHeader() throws IOException {
    // only one byte available -> not a full 2-byte header
    TestIn in = new TestIn(new ByteArrayInputStream(new byte[] {0x42}));
    assertEquals(in.header(), -1);
  }

  @Test
  public void testOutputCloseIsIdempotent() throws IOException {
    AtomicInteger closeCount = new AtomicInteger();
    OutputStream counting = new OutputStream() {
      @Override
      public void write(int b) {
      }
      @Override
      public void close() {
        closeCount.incrementAndGet();
      }
    };
    TestOut out = new TestOut(counting);
    assertFalse(out.isClosed());
    out.close();
    assertTrue(out.isClosed());
    out.close();    // second close must not propagate
    assertEquals(closeCount.get(), 1);
  }

  @Test
  public void testInputCloseIsIdempotent() throws IOException {
    AtomicInteger closeCount = new AtomicInteger();
    InputStream counting = new InputStream() {
      @Override
      public int read() {
        return -1;
      }
      @Override
      public void close() {
        closeCount.incrementAndGet();
      }
    };
    TestIn in = new TestIn(counting);
    assertFalse(in.isClosed());
    in.close();
    assertTrue(in.isClosed());
    in.close();     // second close must not propagate
    assertEquals(closeCount.get(), 1);
  }
}
