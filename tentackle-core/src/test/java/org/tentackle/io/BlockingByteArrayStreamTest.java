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

import org.tentackle.common.InterruptedRuntimeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link NotifyingByteArrayOutputStream} together with the
 * {@link BlockingByteArrayInputStream} that consumes it.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BlockingByteArrayStreamTest {

  private static final long TIMEOUT_MS = 5000;

  @Test
  public void testGetOutputStream() {
    NotifyingByteArrayOutputStream out = new NotifyingByteArrayOutputStream();
    BlockingByteArrayInputStream in = new BlockingByteArrayInputStream(out);
    assertSame(in.getOutputStream(), out);
  }

  @Test
  public void testReadAlreadyAvailableData() throws IOException {
    // when the data is written before reading, no blocking occurs as long as
    // we read no more than what is available
    NotifyingByteArrayOutputStream out = new NotifyingByteArrayOutputStream();
    out.write('A');
    out.write('B');
    BlockingByteArrayInputStream in = new BlockingByteArrayInputStream(out);
    assertEquals(in.read(), 'A');
    assertEquals(in.read(), 'B');
  }

  @Test
  public void testHighBytesAreUnsigned() throws IOException {
    NotifyingByteArrayOutputStream out = new NotifyingByteArrayOutputStream();
    out.write(0xFF);
    BlockingByteArrayInputStream in = new BlockingByteArrayInputStream(out);
    assertEquals(in.read(), 0xFF);   // not sign-extended to -1
  }

  @Test
  public void testReaderBlocksUntilDataArrives() throws InterruptedException {
    NotifyingByteArrayOutputStream out = new NotifyingByteArrayOutputStream();
    BlockingByteArrayInputStream in = new BlockingByteArrayInputStream(out);

    byte[] payload = "streamed".getBytes(StandardCharsets.US_ASCII);
    AtomicReference<String> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread reader = new Thread(() -> {
      try {
        byte[] buf = new byte[payload.length];
        for (int i = 0; i < buf.length; i++) {
          buf[i] = (byte) in.read();
        }
        result.set(new String(buf, StandardCharsets.US_ASCII));
      }
      catch (Throwable t) {
        failure.set(t);
      }
    }, "blocking-reader");
    reader.start();

    // feed the data in two chunks with the reader already waiting
    synchronized (out) {
      out.write("stre".getBytes(StandardCharsets.US_ASCII), 0, 4);
    }
    synchronized (out) {
      out.write("amed".getBytes(StandardCharsets.US_ASCII), 0, 4);
    }

    reader.join(TIMEOUT_MS);
    assertFalse(reader.isAlive(), "reader did not finish in time");
    assertNull(failure.get());
    assertEquals(result.get(), "streamed");
  }

  @Test
  public void testInterruptingBlockedReaderThrows() throws InterruptedException {
    NotifyingByteArrayOutputStream out = new NotifyingByteArrayOutputStream();
    BlockingByteArrayInputStream in = new BlockingByteArrayInputStream(out);

    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread reader = new Thread(() -> {
      try {
        in.read();    // blocks: no data ever written
      }
      catch (Throwable t) {
        failure.set(t);
      }
    }, "interruptible-reader");
    reader.start();

    // give the reader a moment to reach the wait(), then interrupt it
    long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (reader.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
    reader.interrupt();
    reader.join(TIMEOUT_MS);

    assertFalse(reader.isAlive(), "reader did not finish in time");
    assertTrue(failure.get() instanceof InterruptedRuntimeException,
               "expected InterruptedRuntimeException but got " + failure.get());
  }
}
