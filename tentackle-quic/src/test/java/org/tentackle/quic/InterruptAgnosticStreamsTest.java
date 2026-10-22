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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

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
   * KWIK fails a write that is interrupted while waiting for room in its send buffer, before it takes any
   * of the data. Such a write must be repeated instead of breaking the TRIP connection, without losing or
   * duplicating a single byte, and the interrupt must be handed back to the caller.
   */
  @Test(timeOut = 30000)
  public void repeatsAWriteInterruptedWithinKwik() throws Exception {
    Reporter.log("an interrupted write is repeated", true);
    byte[] data = new byte[InterruptAgnosticStreams.WRITE_CHUNK_SIZE * 3 + 4711];
    new Random(42).nextBytes(data);
    ScriptedOutput kwik = new ScriptedOutput();
    // the 1st and the 3rd write are interrupted, the latter twice in a row
    kwik.script(new InterruptedIOException("write failed because stream was aborted"), null,
                new InterruptedIOException(), new InterruptedIOException(), null);
    OutputStream out = InterruptAgnosticStreams.wrap(kwik);

    try {
      out.write(data, 0, data.length);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");
    }
    finally {
      Thread.interrupted();
    }

    assertEquals(kwik.written.toByteArray(), data, "data lost or duplicated");
    assertEquals(kwik.attempts, 4 + 3, "one write per chunk plus the repeated ones");
    assertTrue(kwik.largestWrite <= InterruptAgnosticStreams.WRITE_CHUNK_SIZE,
               "a write of " + kwik.largestWrite + " bytes is not all-or-nothing in KWIK");
    assertEquals(kwik.seen, List.of(false, false, false, false, false, false, false),
                 "the interrupt status reached KWIK");
  }

  @Test(timeOut = 30000)
  public void repeatsAnInterruptedSingleByteWrite() throws Exception {
    Reporter.log("an interrupted single byte write is repeated", true);
    ScriptedOutput kwik = new ScriptedOutput();
    kwik.script(new InterruptedIOException());
    OutputStream out = InterruptAgnosticStreams.wrap(kwik);

    try {
      out.write(42);
      assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");
    }
    finally {
      Thread.interrupted();
    }

    assertEquals(kwik.written.toByteArray(), new byte[] { 42 });
  }

  /**
   * KWIK interrupts a waiting writer by itself when the stream is reset or the connection is gone. The
   * repeated write then fails for that reason, which must end the write instead of repeating it forever.
   */
  @Test(timeOut = 30000)
  public void failsWhenKwikInterruptedTheWriterItself() throws Exception {
    Reporter.log("a write on a dead stream fails", true);
    IOException reset = new IOException("output stream is reset");
    ScriptedOutput kwik = new ScriptedOutput();
    kwik.script(new InterruptedIOException("write failed because stream was reset"), reset);
    OutputStream out = InterruptAgnosticStreams.wrap(kwik);

    assertSame(expectThrows(IOException.class, () -> out.write(new byte[] { 1, 2, 3 })), reset);
    assertEquals(kwik.written.size(), 0);
    Thread.interrupted();   // KWIK's own interrupt is handed back like any other
  }

  /**
   * A timeout is an {@code InterruptedIOException} as well, but has nothing to do with an interrupt.
   */
  @Test(timeOut = 30000)
  public void doesNotRepeatATimeout() throws Exception {
    Reporter.log("a timeout is not an interrupt", true);
    SocketTimeoutException timeout = new SocketTimeoutException("timed out");
    ScriptedOutput kwik = new ScriptedOutput();
    kwik.script(timeout, null);
    OutputStream out = InterruptAgnosticStreams.wrap(kwik);

    assertSame(expectThrows(IOException.class, () -> out.write(new byte[] { 1, 2, 3 })), timeout);
    assertEquals(kwik.attempts, 1);
    assertFalse(Thread.currentThread().isInterrupted(), "the wrapper interrupted the thread all by itself");
  }


  /**
   * An output stream failing its writes as scripted, the way KWIK does: a failing write takes nothing.
   */
  private static final class ScriptedOutput extends OutputStream {

    private final Deque<IOException> failures = new ArrayDeque<>();
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private final List<Boolean> seen = new ArrayList<>();
    private final IOException success = new IOException();
    private int attempts;
    private int largestWrite;

    /**
     * Sets the outcome of the next writes, the ones beyond succeed.
     *
     * @param outcomes the exception per write, null if the write succeeds
     */
    private void script(IOException... outcomes) {
      for (IOException outcome : outcomes) {
        failures.add(outcome == null ? success : outcome);
      }
    }

    @Override
    public void write(int b) throws IOException {
      write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      attempts++;
      largestWrite = Math.max(largestWrite, len);
      seen.add(Thread.currentThread().isInterrupted());
      IOException failure = failures.poll();
      if (failure != null && failure != success) {
        if (failure.getClass() == InterruptedIOException.class) {
          Thread.currentThread().interrupt();   // KWIK leaves it pending in some cases
        }
        throw failure;
      }
      written.write(b, off, len);
    }
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
