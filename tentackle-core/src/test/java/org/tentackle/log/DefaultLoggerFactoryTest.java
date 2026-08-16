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

import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link DefaultLoggerFactory}.
 * <p>
 * Each test uses a factory of its own, since the factory caches the resolved static methods and
 * constructors per logger class.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultLoggerFactoryTest {

  private DefaultLoggerFactory factory;
  private String name;
  private LogCapture capture;

  @BeforeMethod
  private void setUp() {
    factory = new DefaultLoggerFactory();
    name = LogCapture.uniqueLoggerName(DefaultLoggerFactoryTest.class);
    // the factory reports its failures through the plain java-util-logging logger of that name
    capture = LogCapture.attach(name, Level.WARNING);
  }

  @AfterMethod
  private void tearDown() {
    if (capture != null) {
      capture.close();
    }
  }


  @Test
  public void testDefaultLoggerClass() {
    Logger logger = factory.getLogger(name, null);
    assertTrue(logger instanceof DefaultLogger, "unexpected logger: " + logger.getClass());
    assertSame(logger, DefaultLogger.getLogger(name));
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testExplicitDefaultLoggerClass() {
    Logger logger = factory.getLogger(name, DefaultLogger.class);
    assertSame(logger, DefaultLogger.getLogger(name));
  }

  @Test
  public void testStaticFactoryMethodIsPreferred() {
    Logger logger = factory.getLogger(name, StaticFactoryLogger.class);
    assertTrue(logger instanceof StaticFactoryLogger, "unexpected logger: " + logger.getClass());
    assertEquals(((StaticFactoryLogger) logger).getName(), name);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testStaticFactoryMethodIsCached() {
    Logger first = factory.getLogger(name, StaticFactoryLogger.class);
    Logger second = factory.getLogger(name, StaticFactoryLogger.class);
    assertSame(second, first);    // the fixture caches by name, so the factory must have re-used the method
  }

  @Test
  public void testConstructorFallback() {
    Logger logger = factory.getLogger(name, ConstructorOnlyLogger.class);
    assertTrue(logger instanceof ConstructorOnlyLogger, "unexpected logger: " + logger.getClass());
    assertEquals(((ConstructorOnlyLogger) logger).getName(), name);
    assertTrue(capture.records().isEmpty(), capture.allMessages());
  }

  @Test
  public void testConstructorIsInvokedForEveryRequest() {
    Logger first = factory.getLogger(name, ConstructorOnlyLogger.class);
    Logger second = factory.getLogger(name, ConstructorOnlyLogger.class);
    assertNotSame(second, first);
    assertTrue(second instanceof ConstructorOnlyLogger, "unexpected logger: " + second.getClass());
  }

  @Test
  public void testInaccessibleFactoryMethodFallsBackToTheDefaultLogger() {
    Logger logger = factory.getLogger(name, PrivateStaticFactoryLogger.class);
    assertSame(logger, DefaultLogger.getLogger(name));
    assertWarned(PrivateStaticFactoryLogger.class);
  }

  @Test
  public void testInstanceFactoryMethodFallsBackToTheDefaultLogger() {
    // a non-static getLogger(String) is unusable and must not be invoked with a null receiver
    Logger logger = factory.getLogger(name, InstanceGetLoggerLogger.class);
    assertSame(logger, DefaultLogger.getLogger(name));
    assertWarned(InstanceGetLoggerLogger.class);
  }

  @Test
  public void testUnusableLoggerFallsBackToTheDefaultLogger() {
    Logger logger = factory.getLogger(name, UnusableLogger.class);
    assertSame(logger, DefaultLogger.getLogger(name));
    assertWarned(UnusableLogger.class);
  }

  @Test
  public void testFallbackIsStableAcrossRequests() {
    Logger first = factory.getLogger(name, UnusableLogger.class);
    Logger second = factory.getLogger(name, UnusableLogger.class);
    assertSame(second, first);
    assertEquals(capture.records().size(), 2);
  }

  @Test
  public void testDifferentLoggerClassesAreCachedSeparately() {
    Logger staticOne = factory.getLogger(name, StaticFactoryLogger.class);
    Logger constructed = factory.getLogger(name, ConstructorOnlyLogger.class);
    Logger staticAgain = factory.getLogger(name, StaticFactoryLogger.class);
    assertTrue(staticOne instanceof StaticFactoryLogger, "unexpected logger: " + staticOne.getClass());
    assertTrue(constructed instanceof ConstructorOnlyLogger, "unexpected logger: " + constructed.getClass());
    assertSame(staticAgain, staticOne);
  }

  @Test
  public void testDifferentNamesForTheSameLoggerClass() {
    String otherName = LogCapture.uniqueLoggerName(DefaultLoggerFactoryTest.class);
    Logger one = factory.getLogger(name, ConstructorOnlyLogger.class);
    Logger other = factory.getLogger(otherName, ConstructorOnlyLogger.class);
    assertEquals(((ConstructorOnlyLogger) one).getName(), name);
    assertEquals(((ConstructorOnlyLogger) other).getName(), otherName);
  }


  private void assertWarned(Class<?> loggerClass) {
    LogRecord record = capture.singleRecord();
    assertEquals(record.getLevel(), java.util.logging.Level.WARNING);
    assertTrue(record.getMessage().startsWith("Could not load logger "), record.getMessage());
    assertTrue(record.getMessage().contains(loggerClass.getName()), record.getMessage());
  }

}
