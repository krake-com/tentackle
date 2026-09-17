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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link InterruptAgnosticStreams}, the wrappers that keep the interrupt status of the calling
 * thread away from KWIK's stream operations without losing it.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class InterruptAgnosticStreamsTest {

  /**
   * What the wrapped stream saw of the interrupt status, one entry per operation.
   */
  private final List<Boolean> seen = new ArrayList<>();

  @BeforeMethod
  public void setUp() {
    seen.clear();   // TestNG reuses the instance for every method
  }


  @Test(timeOut = 30000)
  public void withholdsThePendingInterruptAndRestoresIt() throws Exception {
    Reporter.log("KWIK sees no interrupt, the caller gets it back", true);
    InputStream in = InterruptAgnosticStreams.wrap(recordingInput(7));
    OutputStream out = InterruptAgnosticStreams.wrap(recordingOutput(null));

    Thread.currentThread().interrupt();
    try {
      assertEquals(in.read(), 7);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed by the read");

      assertEquals(in.read(new byte[4], 0, 4), 1);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed by the block read");

      out.write(42);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed by the write");

      out.write(new byte[] { 1, 2, 3 });
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed by the block write");

      out.flush();
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed by the flush");
    }
    finally {
      Thread.interrupted();   // never leave the flag behind for whatever runs next
    }

    assertEquals(seen, List.of(false, false, false, false, false), "the interrupt status reached KWIK");
  }

  @Test(timeOut = 30000)
  public void restoresTheInterruptEvenWhenTheOperationFails() throws Exception {
    Reporter.log("a failing operation must not eat the interrupt either", true);
    IOException failure = new IOException("broken");
    OutputStream out = InterruptAgnosticStreams.wrap(recordingOutput(failure));

    Thread.currentThread().interrupt();
    try {
      assertSame(expectThrows(IOException.class, () -> out.write(new byte[] { 1 })), failure);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");
    }
    finally {
      Thread.interrupted();
    }
  }

  @Test(timeOut = 30000)
  public void leavesAnUninterruptedThreadAlone() throws Exception {
    Reporter.log("nothing is interrupted that was not interrupted before", true);
    InputStream in = InterruptAgnosticStreams.wrap(recordingInput(3));
    assertEquals(in.read(), 3);
    assertEquals(seen, List.of(false));
    assertFalse(Thread.currentThread().isInterrupted(), "the wrapper interrupted the thread all by itself");
  }


  /**
   * Creates an input stream recording the interrupt status it is called with.
   *
   * @param value the byte every read delivers
   * @return the stream
   */
  private InputStream recordingInput(int value) {
    return new InputStream() {
      @Override
      public int read() {
        seen.add(Thread.currentThread().isInterrupted());
        return value;
      }

      @Override
      public int read(byte[] b, int off, int len) {
        seen.add(Thread.currentThread().isInterrupted());
        b[off] = (byte) value;
        return 1;
      }
    };
  }

  /**
   * Creates an output stream recording the interrupt status it is called with.
   *
   * @param failure the exception every write throws, null if writing succeeds
   * @return the stream
   */
  private OutputStream recordingOutput(IOException failure) {
    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        record();
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        record();
      }

      @Override
      public void flush() throws IOException {
        record();
      }

      private void record() throws IOException {
        seen.add(Thread.currentThread().isInterrupted());
        if (failure != null) {
          throw failure;
        }
      }
    };
  }

}
