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
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link QuicLogger}, the bridge from KWIK's logging API onto Tentackle's {@code Logger}.
 * <p>
 * The bridge is observed through the {@code java.util.logging} logger that backs Tentackle's
 * default logger implementation. Because the QUIC server transports of the other tests log to the
 * very same logger, every assertion is made against records carrying a marker unique to the test.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicLoggerTest {

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();

  private Logger julLogger;
  private Handler handler;
  private Level savedLevel;
  private boolean savedUseParentHandlers;

  @BeforeClass
  public void setUp() {
    Object loggerImpl = org.tentackle.log.Logger.get(QuicLogger.class).getLoggerImpl();
    if (!(loggerImpl instanceof Logger)) {
      throw new SkipException("Tentackle is not backed by java.util.logging here, got " + loggerImpl);
    }
    julLogger = (Logger) loggerImpl;
    savedLevel = julLogger.getLevel();
    savedUseParentHandlers = julLogger.getUseParentHandlers();
    julLogger.setLevel(Level.FINE);
    julLogger.setUseParentHandlers(false);   // keep the test output clean
    handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    julLogger.addHandler(handler);
  }

  @AfterClass
  public void tearDown() {
    if (julLogger != null) {
      julLogger.removeHandler(handler);
      julLogger.setLevel(savedLevel);
      julLogger.setUseParentHandlers(savedUseParentHandlers);
    }
  }

  @BeforeMethod
  public void clearRecords() {
    records.clear();
  }


  @Test(timeOut = 30000)
  public void forwardsInfoAndWarningsToTheTentackleLogger() {
    Reporter.log("forwardsInfoAndWarningsToTheTentackleLogger", true);
    QuicLogger log = new QuicLogger();
    log.logInfo(true);
    log.logWarning(true);

    log.info("marker-info handshake done");
    log.warn("marker-warn retransmitting");

    List<LogRecord> info = recordsMatching("marker-info");
    List<LogRecord> warn = recordsMatching("marker-warn");
    assertEquals(info.size(), 1);
    assertEquals(warn.size(), 1);
    assertEquals(info.get(0).getLevel(), Level.INFO);
    assertEquals(warn.get(0).getLevel(), Level.INFO);   // KWIK warnings are informational for Tentackle
    assertTrue(info.get(0).getMessage().endsWith("marker-info handshake done"),
               "unexpected message: " + info.get(0).getMessage());
  }

  /**
   * KWIK's categories are switched off by default; nothing may reach the Tentackle logger
   * unless the category was enabled.
   */
  @Test(timeOut = 30000)
  public void suppressesCategoriesThatAreNotEnabled() {
    Reporter.log("suppressesCategoriesThatAreNotEnabled", true);
    QuicLogger log = new QuicLogger();

    log.info("marker-off info");
    log.warn("marker-off warn");
    log.debug("marker-off debug");
    log.stats("marker-off stats");
    log.raw("marker-off raw", new byte[] { 1, 2, 3 });

    assertEquals(recordsMatching("marker-off"), List.of());
  }

  @Test(timeOut = 30000)
  public void forwardsErrorsWithACauseAsSevere() {
    Reporter.log("forwardsErrorsWithACauseAsSevere", true);
    QuicLogger log = new QuicLogger();
    Exception cause = new IllegalStateException("marker-cause");

    log.error("marker-error connection lost", cause);

    List<LogRecord> matches = recordsMatching("marker-error");
    assertEquals(matches.size(), 1);
    assertEquals(matches.get(0).getLevel(), Level.SEVERE);
    assertSame(matches.get(0).getThrown(), cause);
  }

  /**
   * KWIK's raw-byte logging arrives as a hex block at FINE level, and stays out of the log
   * entirely when the Tentackle logger is not logging at FINE.
   * <p>
   * Note that this pins down the observable behavior only. {@code QuicLogger} additionally guards
   * the call with {@code isFineLoggable()} to avoid rendering the hex block when it would be
   * discarded anyway &mdash; that optimization cannot be observed through the logger.
   */
  @Test(timeOut = 30000)
  public void rendersHexDumpsAtFineLevel() {
    Reporter.log("rendersHexDumpsAtFineLevel", true);
    QuicLogger log = new QuicLogger();
    log.logRaw(true);
    byte[] data = { (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef };

    julLogger.setLevel(Level.INFO);
    try {
      log.raw("marker-hex suppressed", data);
      assertEquals(recordsMatching("marker-hex"), List.of());
    }
    finally {
      julLogger.setLevel(Level.FINE);
    }

    log.raw("marker-hex dumped", data);
    List<LogRecord> matches = recordsMatching("marker-hex");
    assertEquals(matches.size(), 1);
    assertEquals(matches.get(0).getLevel(), Level.FINE);
    assertTrue(matches.get(0).getMessage().contains("de ad be ef"),
               "unexpected hex dump: " + matches.get(0).getMessage());
  }


  private List<LogRecord> recordsMatching(String marker) {
    return records.stream()
                  .filter(record -> record.getMessage() != null && record.getMessage().contains(marker))
                  .toList();
  }

}
