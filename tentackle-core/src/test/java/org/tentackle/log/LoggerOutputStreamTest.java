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

package org.tentackle.log;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.log.Logger.Level;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link LoggerOutputStream}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoggerOutputStreamTest {

  private DefaultLogger logger;
  private LogCapture capture;

  @BeforeMethod
  private void setUp() {
    logger = new DefaultLogger(LogCapture.uniqueLoggerName(LoggerOutputStreamTest.class));
    capture = LogCapture.attach(logger, Level.FINER);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }

  private static void write(LoggerOutputStream stream, String text) throws IOException {
    stream.write(text.getBytes(StandardCharsets.UTF_8));
  }


  @Test
  public void testNothingIsLoggedBeforeFlush() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "hello");
    assertTrue(capture.records().isEmpty());
    assertEquals(stream.size(), 5);
  }

  @Test
  public void testFlushLogsAndResets() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "hello");
    stream.flush();

    LogRecord record = capture.singleRecord();
    assertEquals(record.getMessage(), "hello");
    assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    assertEquals(stream.size(), 0);
  }

  @Test
  public void testFlushOfAnEmptyBufferLogsNothing() {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    stream.flush();
    stream.close();
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testFlushingTwiceLogsOnlyOnce() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "hello");
    stream.flush();
    stream.flush();
    assertEquals(capture.messages(), java.util.List.of("hello"));
  }

  @Test
  public void testPrintStreamCloseLogsOnlyOnce() {
    // closing a PrintStream flushes the underlying stream more than once
    try (PrintStream printStream = new PrintStream(new LoggerOutputStream(logger, Level.FINE))) {
      printStream.print("hello");
    }
    assertEquals(capture.messages(), java.util.List.of("hello"));
  }

  @Test
  public void testSuccessiveFlushes() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "first");
    stream.flush();
    write(stream, "second");
    stream.flush();
    assertEquals(capture.messages(), java.util.List.of("first", "second"));
  }

  @Test
  public void testCloseFlushes() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "hello");
    stream.close();
    assertEquals(capture.singleRecord().getMessage(), "hello");
  }

  @Test
  public void testDefaultLevelIsSevere() throws IOException {
    LoggerOutputStream stream = new LoggerOutputStream(logger);
    write(stream, "hello");
    stream.flush();
    assertEquals(capture.singleRecord().getLevel(), java.util.logging.Level.SEVERE);
  }

  @Test
  public void testExplicitBufferSize() throws IOException {
    // the buffer size is a mere optimization, the stream grows as needed
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE, 2);
    write(stream, "far more than two bytes");
    stream.flush();
    assertEquals(capture.singleRecord().getMessage(), "far more than two bytes");
  }

  @Test
  public void testNothingIsLoggedWhenNotLoggable() throws IOException {
    capture.setLevel(Level.SEVERE);
    LoggerOutputStream stream = new LoggerOutputStream(logger, Level.FINE);
    write(stream, "hello");
    stream.flush();
    assertTrue(capture.records().isEmpty(), capture.allMessages());
    assertEquals(stream.size(), 0);    // the buffer is reset nonetheless
  }

  @Test
  public void testLogException() {
    Exception exception = new IllegalStateException("boom");
    LoggerOutputStream.logException(exception, logger, Level.FINE, 64);

    assertFalse(capture.records().isEmpty());
    for (LogRecord record : capture.records()) {
      assertEquals(record.getLevel(), java.util.logging.Level.FINE);
    }
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains(LoggerOutputStreamTest.class.getName() + ".testLogException"), logged);
  }

  @Test
  public void testLogExceptionDefaultsToSevere() {
    LoggerOutputStream.logException(new IllegalStateException("boom"), logger);
    assertFalse(capture.records().isEmpty());
    for (LogRecord record : capture.records()) {
      assertEquals(record.getLevel(), java.util.logging.Level.SEVERE);
    }
    assertTrue(capture.allMessages().contains("java.lang.IllegalStateException: boom"), capture.allMessages());
  }

  @Test
  public void testLogExceptionWithoutLoggerUsesTheOwnLogger() {
    try (LogCapture ownCapture = LogCapture.attach(LoggerOutputStream.class, Level.FINER)) {
      LoggerOutputStream.logException(new IllegalStateException("boom"));
      assertFalse(ownCapture.records().isEmpty());
      assertTrue(ownCapture.allMessages().contains("java.lang.IllegalStateException: boom"), ownCapture.allMessages());
    }
  }

  @Test
  public void testLoggedExceptionCarriesItsCause() {
    Exception cause = new IllegalArgumentException("the root cause");
    LoggerOutputStream.logException(new IllegalStateException("boom", cause), logger, Level.FINE, 512);
    String logged = capture.allMessages();
    assertTrue(logged.contains("java.lang.IllegalStateException: boom"), logged);
    assertTrue(logged.contains("java.lang.IllegalArgumentException: the root cause"), logged);
  }

}
