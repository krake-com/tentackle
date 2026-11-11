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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.EchoTransceiver;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.text.ParseException;
import java.time.format.DateTimeParseException;
import java.util.MissingResourceException;
import java.util.UnknownFormatConversionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.PatternSyntaxException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the JDK's exceptions survive the transfer via TRIP, although their fields are not accessible.
 * <p>
 * Runs here instead of tentackle-core, since the core's tests provide their own
 * {@code META-INF/mapped-services/org.tentackle.trip.TripInstanceCreator}, which hides the core's
 * creators when the tests are patched into the module.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JdkExceptionTripTest {

  @Test
  void testMessageAndCause() {
    IOException io = new IOException("io");
    assertTransferred(new UncheckedIOException("unchecked", io));
    assertTransferred(new UncheckedIOException(io));
    assertTransferred(new ExecutionException("execution", new IllegalStateException("state")));
    assertTransferred(new ExecutionException(new IllegalStateException("state")));
    assertTransferred(new CompletionException(new IllegalStateException("state")));
    assertTransferred(new AssertionError("assertion"));
    assertTransferred(new NullPointerException("npe"));
    assertTransferred(new IllegalArgumentException());
  }

  @Test
  void testPublicField() {
    InterruptedIOException ix = new InterruptedIOException("interrupted");
    ix.bytesTransferred = 42;
    assertEquals(assertTransferred(ix).bytesTransferred, 42);
  }

  @Test
  void testInvocationTargetException() {
    InvocationTargetException ix = assertTransferred(
      new InvocationTargetException(new IllegalStateException("target"), "invocation"));
    assertEquals(ix.getTargetException().getMessage(), "target");
  }

  @Test
  void testFileSystemExceptions() {
    NoSuchFileException nx = assertTransferred(new NoSuchFileException("/a", "/b", "gone"));
    assertEquals(nx.getFile(), "/a");
    assertEquals(nx.getOtherFile(), "/b");
    assertEquals(nx.getReason(), "gone");
    AccessDeniedException ax = assertTransferred(new AccessDeniedException("/a"));
    assertEquals(ax.getFile(), "/a");
    assertNull(ax.getOtherFile());
    DirectoryNotEmptyException dx = assertTransferred(new DirectoryNotEmptyException("/dir"));
    assertEquals(dx.getFile(), "/dir");
    FileSystemException fx = new FileSystemException("/a", null, "failed");
    fx.initCause(new IOException("io"));
    assertTransferred(fx);
  }

  @Test
  void testParseExceptions() {
    DateTimeParseException dx = assertTransferred(
      new DateTimeParseException("cannot parse", "2026-13-01", 5, new IllegalArgumentException("month")));
    assertEquals(dx.getParsedString(), "2026-13-01");
    assertEquals(dx.getErrorIndex(), 5);
    assertEquals(assertTransferred(new ParseException("parse", 3)).getErrorOffset(), 3);
  }

  @Test
  void testSyntaxExceptions() {
    URISyntaxException ux = assertTransferred(new URISyntaxException("a b", "space", 1));
    assertEquals(ux.getInput(), "a b");
    assertEquals(ux.getReason(), "space");
    assertEquals(ux.getIndex(), 1);
    PatternSyntaxException px = assertTransferred(new PatternSyntaxException("unclosed", "(a", 2));
    assertEquals(px.getDescription(), "unclosed");
    assertEquals(px.getPattern(), "(a");
    assertEquals(px.getIndex(), 2);
    InvalidPathException ix = assertTransferred(new InvalidPathException("a\0b", "nul", 1));
    assertEquals(ix.getInput(), "a\0b");
    assertEquals(ix.getReason(), "nul");
    assertEquals(ix.getIndex(), 1);
  }

  @Test
  void testMissingResourceException() {
    MissingResourceException mx = assertTransferred(new MissingResourceException("missing", "Bundle", "key"));
    assertEquals(mx.getClassName(), "Bundle");
    assertEquals(mx.getKey(), "key");
  }

  @Test
  void testFallback() {
    // no creator: the class survives, only the conversion is not transferred as such
    Throwable tx = tunnel(new UnknownFormatConversionException("q"));
    assertSame(tx.getClass(), UnknownFormatConversionException.class);
    assertTrue(tx.getMessage().contains("q"));
  }

  @Test
  void testAsCause() {
    TripRuntimeException tx = new TripRuntimeException("trip", new NoSuchFileException("/a"));
    TripRuntimeException tunneled = tunnel(tx);
    assertEquals(tunneled.getMessage(), "trip");
    assertSame(tunneled.getCause().getClass(), NoSuchFileException.class);
    assertEquals(((NoSuchFileException) tunneled.getCause()).getFile(), "/a");
  }


  /**
   * Transfers the throwable and asserts class, message, cause and stack trace.
   *
   * @param throwable the throwable
   * @return the transferred throwable
   * @param <T> the throwable type
   */
  private <T extends Throwable> T assertTransferred(T throwable) {
    T tunneled = tunnel(throwable);
    assertSame(tunneled.getClass(), throwable.getClass());
    assertEquals(tunneled.getMessage(), throwable.getMessage());
    if (throwable.getCause() == null) {
      assertNull(tunneled.getCause());
    }
    else {
      assertNotNull(tunneled.getCause());
      assertSame(tunneled.getCause().getClass(), throwable.getCause().getClass());
      assertEquals(tunneled.getCause().getMessage(), throwable.getCause().getMessage());
    }
    assertEquals(tunneled.getStackTrace(), throwable.getStackTrace());
    return tunneled;
  }

  @SuppressWarnings("unchecked")
  private <T> T tunnel(T value) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripType<T> tripType = TripFactory.getInstance().getType((Class<T>) value.getClass());
    tripType.write(value, transceiver.getStream());
    transceiver.echo();
    OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
    return tripType.read(transceiver.getStream(), opCode);
  }

}
