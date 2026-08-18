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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for the {@link LoggerFactory} SPI.
 * <p>
 * No logging backend is configured for this module, hence the factory must be the
 * {@link DefaultLoggerFactory} handing out {@link DefaultLogger}s.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoggerFactoryTest {

  @Test
  public void testSingleton() {
    assertSame(LoggerFactory.getInstance(), LoggerFactory.getInstance());
  }

  @Test
  public void testDefaultImplementation() {
    assertTrue(LoggerFactory.getInstance() instanceof DefaultLoggerFactory,
               "unexpected factory: " + LoggerFactory.getInstance().getClass());
  }

  @Test
  public void testGetLoggerByName() {
    String name = LogCapture.uniqueLoggerName(LoggerFactoryTest.class);
    Logger logger = LoggerFactory.getLogger(name);
    assertTrue(logger instanceof DefaultLogger, "unexpected logger: " + logger.getClass());
    assertEquals(((java.util.logging.Logger) logger.getLoggerImpl()).getName(), name);
    assertSame(LoggerFactory.getLogger(name), logger);
  }

  @Test
  public void testGetLoggerByClass() {
    Logger logger = LoggerFactory.getLogger(LoggerFactoryTest.class);
    assertSame(logger, LoggerFactory.getLogger(LoggerFactoryTest.class.getName()));
    assertEquals(((java.util.logging.Logger) logger.getLoggerImpl()).getName(), LoggerFactoryTest.class.getName());
  }

  @Test
  public void testDifferentNamesYieldDifferentLoggers() {
    assertNotSame(LoggerFactory.getLogger(LogCapture.uniqueLoggerName(LoggerFactoryTest.class)),
                  LoggerFactory.getLogger(LogCapture.uniqueLoggerName(LoggerFactoryTest.class)));
  }

}
