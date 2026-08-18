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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the static API and the {@link Level} enum of {@link Logger}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoggerTest {

  /**
   * A nested class used to verify the caller detection of {@link Logger#get()}.
   */
  private static class Nested {

    static Logger ownLogger() {
      return Logger.get();
    }
  }


  @Test
  public void testGetForClass() {
    assertSame(Logger.get(LoggerTest.class), Logger.get(LoggerTest.class));
  }

  @Test
  public void testGetForClassEqualsFactory() {
    assertSame(Logger.get(LoggerTest.class), LoggerFactory.getLogger(LoggerTest.class));
    assertSame(Logger.get(LoggerTest.class), LoggerFactory.getLogger(LoggerTest.class.getName()));
    assertSame(Logger.get(LoggerTest.class), LoggerFactory.getInstance().getLogger(LoggerTest.class.getName(), null));
  }

  @Test
  public void testGetForDifferentClasses() {
    assertNotSame(Logger.get(LoggerTest.class), Logger.get(Nested.class));
  }

  @Test
  public void testGetUsesTheCallingClass() {
    assertSame(Logger.get(), Logger.get(LoggerTest.class));
  }

  @Test
  public void testGetUsesTheCallingNestedClass() {
    assertSame(Nested.ownLogger(), Logger.get(Nested.class));
    assertNotSame(Nested.ownLogger(), Logger.get(LoggerTest.class));
  }

  @Test
  public void testLoggerIsNamedAfterTheClass() {
    Object impl = Logger.get(LoggerTest.class).getLoggerImpl();
    assertTrue(impl instanceof java.util.logging.Logger);
    assertEquals(((java.util.logging.Logger) impl).getName(), LoggerTest.class.getName());
  }

  @Test
  public void testNestedLoggerIsNamedAfterTheNestedClass() {
    assertEquals(((java.util.logging.Logger) Nested.ownLogger().getLoggerImpl()).getName(),
                 "org.tentackle.log.LoggerTest$Nested");
  }

  @Test
  public void testLevelOrder() {
    assertEquals(Level.values(), new Level[] { Level.FINER, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE });
  }

  @Test
  public void testLevelValueOf() {
    for (Level level : Level.values()) {
      assertSame(Level.valueOf(level.name()), level);
    }
  }

  @Test
  public void testLevelIsOrderedByVerbosity() {
    // the ordinal order must match the java-util-logging order, several tests rely on it
    java.util.logging.Level previous = null;
    for (Level level : Level.values()) {
      java.util.logging.Level julLevel = LogCapture.translate(level);
      if (previous != null) {
        assertTrue(previous.intValue() < julLevel.intValue(), level + " is not above " + previous);
      }
      previous = julLevel;
    }
  }

}
