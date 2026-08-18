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

import org.testng.annotations.Test;

import org.tentackle.log.Logger.Level;

import java.io.Serial;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the default methods of {@link Loggable}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoggableTest {

  /**
   * An implementation relying on the defaults.
   */
  private static class DefaultLoggable implements Loggable {
  }

  /**
   * An implementation overriding both defaults.
   */
  private static class QuietLoggable implements Loggable {

    @Override
    public Level getLogLevel() {
      return Level.FINER;
    }

    @Override
    public boolean withStacktrace() {
      return false;
    }
  }

  /**
   * An implementation asking not to be logged at all.
   */
  private static class SilentLoggable implements Loggable {

    @Override
    public Level getLogLevel() {
      return null;
    }
  }

  /**
   * An exception carrying its own logging preferences.
   */
  private static class LoggableException extends RuntimeException implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    LoggableException(String message) {
      super(message);
    }

    @Override
    public Level getLogLevel() {
      return Level.WARNING;
    }

    @Override
    public boolean withStacktrace() {
      return false;
    }
  }


  @Test
  public void testDefaultLevelIsInfo() {
    assertSame(new DefaultLoggable().getLogLevel(), Level.INFO);
  }

  @Test
  public void testStacktraceIsLoggedByDefault() {
    assertTrue(new DefaultLoggable().withStacktrace());
  }

  @Test
  public void testOverriddenLevel() {
    assertSame(new QuietLoggable().getLogLevel(), Level.FINER);
  }

  @Test
  public void testOverriddenStacktraceFlag() {
    assertFalse(new QuietLoggable().withStacktrace());
  }

  @Test
  public void testNullLevelMeansNoLogging() {
    // the contract explicitly allows null for "no logging at all"
    assertNull(new SilentLoggable().getLogLevel());
    assertTrue(new SilentLoggable().withStacktrace());    // the other default is unaffected
  }

  @Test
  public void testAnonymousImplementationUsesTheDefaults() {
    Loggable loggable = new Loggable() { };
    assertSame(loggable.getLogLevel(), Level.INFO);
    assertTrue(loggable.withStacktrace());
  }

  @Test
  public void testLoggableException() {
    LoggableException exception = new LoggableException("boom");
    assertSame(exception.getLogLevel(), Level.WARNING);
    assertFalse(exception.withStacktrace());
    assertTrue(exception instanceof Loggable);
  }

}
