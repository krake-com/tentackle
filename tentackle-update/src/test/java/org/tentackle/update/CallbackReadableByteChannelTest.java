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

package org.tentackle.update;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link CallbackReadableByteChannel}, the wrapper that turns a download into
 * progress-bar updates.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CallbackReadableByteChannelTest {

  @Test
  public void reportsTheProgressOfEveryRead() throws Exception {
    byte[] content = content(10);
    List<Double> progress = new ArrayList<>();
    try (CallbackReadableByteChannel channel = new CallbackReadableByteChannel(
             Channels.newChannel(new ByteArrayInputStream(content)), content.length, progress::add)) {

      ByteBuffer buffer = ByteBuffer.allocate(4);
      assertEquals(channel.read(buffer), 4);
      buffer.clear();
      assertEquals(channel.read(buffer), 4);
      buffer.clear();
      assertEquals(channel.read(buffer), 2);
      buffer.clear();
      assertEquals(channel.read(buffer), -1);     // end of stream

      assertEquals(progress, List.of(0.4, 0.8, 1.0));
      assertEquals(channel.getSize(), content.length);
    }
  }

  /**
   * The consumer is optional - the download itself must work without a progress bar.
   */
  @Test
  public void countsTheBytesWithoutAConsumer() throws Exception {
    byte[] content = content(10);
    try (CallbackReadableByteChannel channel = new CallbackReadableByteChannel(
             Channels.newChannel(new ByteArrayInputStream(content)), content.length, null)) {
      ByteBuffer buffer = ByteBuffer.allocate(64);
      assertEquals(channel.read(buffer), content.length);
      assertEquals(channel.read(buffer), -1);
      assertEquals(channel.getSize(), content.length);
    }
  }

  /**
   * The server answers {@code -1} when it could not determine the download size, which turns the
   * fraction negative - the signal for an indeterminate progress bar.
   */
  @Test
  public void reportsIndefiniteProgressForAnUnknownSize() throws Exception {
    List<Double> progress = new ArrayList<>();
    try (CallbackReadableByteChannel channel = new CallbackReadableByteChannel(
             Channels.newChannel(new ByteArrayInputStream(content(8))), -1, progress::add)) {
      assertEquals(channel.read(ByteBuffer.allocate(64)), 8);
      assertEquals(progress, List.of(-8.0));
      assertTrue(progress.getFirst() < 0.0);
    }
  }

  /**
   * A non-blocking source may hand out nothing at all; that must neither be counted nor reported.
   */
  @Test
  public void ignoresReadsThatDeliveredNothing() throws Exception {
    List<Double> progress = new ArrayList<>();
    CallbackReadableByteChannel channel = new CallbackReadableByteChannel(new StubChannel(0), 100, progress::add);
    assertEquals(channel.read(ByteBuffer.allocate(64)), 0);
    assertEquals(channel.getSize(), 0);
    assertTrue(progress.isEmpty());
  }

  @Test
  public void delegatesOpenStateAndClose() throws Exception {
    StubChannel stub = new StubChannel(-1);
    CallbackReadableByteChannel channel = new CallbackReadableByteChannel(stub, 100, null);
    assertTrue(channel.isOpen());
    channel.close();
    assertFalse(channel.isOpen());
    assertTrue(stub.closed);
  }


  private static byte[] content(int length) {
    byte[] content = new byte[length];
    for (int i = 0; i < length; i++) {
      content[i] = (byte) i;
    }
    return content;
  }


  /**
   * A channel that always answers with the same result, without ever touching the buffer.
   */
  private static class StubChannel implements ReadableByteChannel {

    private final int result;
    private boolean closed;

    StubChannel(int result) {
      this.result = result;
    }

    @Override
    public int read(ByteBuffer dst) {
      return result;
    }

    @Override
    public boolean isOpen() {
      return !closed;
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }
  }

}
