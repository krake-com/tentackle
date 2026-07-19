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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ExceptionHelper}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "unchecked" })
public class ExceptionHelperTest {

  /** Builds a chain: IllegalStateException -&gt; IOException -&gt; IllegalArgumentException(root). */
  private Throwable buildChain() {
    Throwable root = new IllegalArgumentException("root");
    Throwable middle = new IOException("io", root);
    return new IllegalStateException("head", middle);
  }

  @Test
  public void testExtractExceptionByType() {
    Throwable head = buildChain();
    IOException io = ExceptionHelper.extractException(IOException.class, true, head);
    assertEquals(io.getMessage(), "io");

    // RuntimeException matches both the head and the root; first vs. last must differ
    RuntimeException first = ExceptionHelper.extractException(RuntimeException.class, true, head);
    assertEquals(first.getMessage(), "head");
    RuntimeException last = ExceptionHelper.extractException(RuntimeException.class, false, head);
    assertEquals(last.getMessage(), "root");

    // nothing matches
    assertNull(ExceptionHelper.extractException(NullPointerException.class, true, head));
    // null chain
    assertNull(ExceptionHelper.extractException(IOException.class, true, null));
  }

  @Test
  public void testExtractExceptionByTypes() {
    Throwable head = buildChain();
    // first matching any of the given types
    Throwable found = ExceptionHelper.extractException(true, head, IOException.class, NullPointerException.class);
    assertEquals(found.getMessage(), "io");
    // last matching any of the given types
    found = ExceptionHelper.extractException(false, head, RuntimeException.class);
    assertEquals(found.getMessage(), "root");
    // none match
    assertNull(ExceptionHelper.extractException(true, head, NullPointerException.class));
  }

  @Test
  public void testExtractTemporaryException() {
    TentackleRuntimeException temp1 = new TentackleRuntimeException("temp1");
    temp1.setTemporary(true);
    TentackleRuntimeException temp2 = new TentackleRuntimeException("temp2", temp1);
    temp2.setTemporary(true);
    Throwable head = new IllegalStateException("head", temp2);

    assertSame(ExceptionHelper.extractTemporaryException(true, head), temp2);
    assertSame(ExceptionHelper.extractTemporaryException(false, head), temp1);

    // a non-temporary TentackleRuntimeException is ignored
    Throwable nonTemp = new TentackleRuntimeException("plain");
    assertNull(ExceptionHelper.extractTemporaryException(true, nonTemp));
    assertNull(ExceptionHelper.extractTemporaryException(true, null));
  }

  @Test
  public void testGetMessage() {
    // skips exceptions with null or blank message
    Throwable root = new IllegalArgumentException("real message");
    Throwable blank = new IllegalStateException("   ", root);
    Throwable head = new RuntimeException(null, blank);
    assertEquals(ExceptionHelper.getMessage(head), "real message");
    assertNull(ExceptionHelper.getMessage(new RuntimeException((String) null)));
    assertNull(ExceptionHelper.getMessage(null));
  }

  @Test
  public void testConcatenateMessages() {
    Throwable root = new IllegalArgumentException("root");
    Throwable middle = new IOException("middle", root);
    Throwable head = new IllegalStateException("head", middle);
    assertEquals(ExceptionHelper.concatenateMessages(head), "head\nmiddle\nroot");

    // duplicate messages are not repeated
    Throwable dupRoot = new IllegalArgumentException("dup");
    Throwable dupHead = new IllegalStateException("dup", dupRoot);
    assertEquals(ExceptionHelper.concatenateMessages(dupHead), "dup");

    assertEquals(ExceptionHelper.concatenateMessages(null), "");
  }

  @Test
  public void testHandleException() {
    Throwable head = buildChain();
    AtomicReference<String> handled = new AtomicReference<>();

    boolean result = ExceptionHelper.handleException(true, head,
        new ExceptionHelper.Handler<>(IOException.class, e -> handled.set("io:" + e.getMessage())),
        new ExceptionHelper.Handler<>(NullPointerException.class, _ -> handled.set("npe")));
    assertTrue(result);
    assertEquals(handled.get(), "io:io");

    // no handler applies
    handled.set(null);
    result = ExceptionHelper.handleException(true, head,
        new ExceptionHelper.Handler<>(NullPointerException.class, _ -> handled.set("npe")));
    assertFalse(result);
    assertNull(handled.get());
  }

  @Test
  public void testHandlerAppliesTo() {
    ExceptionHelper.Handler<IOException> handler =
        new ExceptionHelper.Handler<>(IOException.class, _ -> { });
    assertTrue(handler.appliesTo(new java.io.FileNotFoundException()));
    assertFalse(handler.appliesTo(new IllegalStateException()));
  }

  @Test
  public void testGetStackTraceAsString() {
    Exception ex = new IllegalStateException("boom");
    String trace = ExceptionHelper.getStackTraceAsString(ex);
    assertTrue(trace.contains("IllegalStateException"));
    assertTrue(trace.contains("boom"));
    assertTrue(trace.contains("testGetStackTraceAsString"));
  }

  @Test
  public void testIsClassValuableForStackTrace() {
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("java.lang.String"));
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("sun.reflect.Foo"));
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("jdk.internal.Foo"));
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("com.sun.Foo"));
    assertTrue(ExceptionHelper.isClassValuableForStackTrace("org.example.MyClass"));
    assertTrue(ExceptionHelper.isClassValuableForStackTrace("org.tentackle.common.Foo"));
    // tentackle proxy/interceptor plumbing is considered noise
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("org.tentackle.reflect.MyInvocationHandler"));
    assertFalse(ExceptionHelper.isClassValuableForStackTrace("org.tentackle.reflect.InterceptableFoo"));
  }

  @Test
  public void testFilterStackTrace() {
    StackTraceElement noise1 = new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1);
    StackTraceElement valuable = new StackTraceElement("org.example.MyClass", "doIt", "MyClass.java", 2);
    StackTraceElement noise2 = new StackTraceElement("jdk.internal.Foo", "bar", "Foo.java", 3);
    StackTraceElement[] filtered = ExceptionHelper.filterStackTrace(
        new StackTraceElement[] { noise1, valuable, noise2 });
    // leading noise before the first valuable frame is kept (one frame of context),
    // trailing noise after the last valuable frame is dropped
    assertEquals(filtered.length, 2);
    assertEquals(filtered[0], noise1);
    assertEquals(filtered[1], valuable);

    assertEquals(ExceptionHelper.filterStackTrace(new StackTraceElement[0]).length, 0);
    assertEquals(ExceptionHelper.filterStackTrace(null).length, 0);
  }

}
