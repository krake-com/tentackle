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

package org.tentackle.misc;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.common.Date;
import org.tentackle.common.DateHelper;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests the FormatHelper.
 *
 * Performance tests show that the new DateTimeFormatter is about twice as fast as old DateFormat,
 * even including additional conversion between java.util- and java.time-types.
 */
@SuppressWarnings("missing-explicit-ctor")
public class FormatHelperTest {

  private static final int LOOPS = 1000000;

  @Test
  @SuppressWarnings("deprecation")
  public void testFormats() {
    try {
      Timestamp ts = new Timestamp();
      ts.setNanos(0);
      String sts = FormatHelper.formatTimestamp(ts);
      String stsf = FormatHelper.formatTimestampFast(ts);
      assertEquals(stsf, sts);
      Timestamp pts = FormatHelper.parseTimestamp(sts);
      assertEquals(pts, ts);
      pts = FormatHelper.parseTimestampFast(sts);
      assertEquals(pts, ts);
      LocalDateTime lts = ts.toLocalDateTime();
      String sdt = FormatHelper.formatLocalDateTime(lts);
      assertEquals(sdt, sts);

      ts.setSeconds(0);
      sts = FormatHelper.formatShortTimestamp(ts);
      stsf = FormatHelper.formatShortTimestampFast(ts);
      assertEquals(stsf, sts);
      pts = FormatHelper.parseShortTimestamp(sts);
      assertEquals(pts, ts);
      pts = FormatHelper.parseShortTimestampFast(sts);
      assertEquals(pts, ts);
      lts = ts.toLocalDateTime();
      sdt = FormatHelper.formatShortLocalDateTime(lts);
      assertEquals(sdt, sts);

      Calendar cal = DateHelper.today();
      DateHelper.setMidnight(cal);
      Date d = new Date(cal.getTimeInMillis());
      String sd = FormatHelper.formatDate(d);
      String sdf = FormatHelper.formatDateFast(d);
      assertEquals(sdf, sd);
      Date pd = FormatHelper.parseDate(sd);
      assertEquals(pd, d);
      pd = FormatHelper.parseDateFast(sd);
      assertEquals(pd, d);
      LocalDate ld = d.toLocalDate();
      String sld = FormatHelper.formatLocalDate(ld);
      assertEquals(sld, sd);

      sd = FormatHelper.formatShortDate(d);
      sdf = FormatHelper.formatShortDateFast(d);
      assertEquals(sdf, sd);
      pd = FormatHelper.parseShortDate(sd);
      assertEquals(pd, d);
      pd = FormatHelper.parseShortDateFast(sd);
      assertEquals(pd, d);
      ld = d.toLocalDate();
      sld = FormatHelper.formatShortLocalDate(ld);
      assertEquals(sld, sd);

      Time t = new Time();
      String st = FormatHelper.formatTime(t);
      String stf = FormatHelper.formatTimeFast(t);
      assertEquals(stf, st);
      Time pt = FormatHelper.parseTime(st);
      assertEquals(pt.toString(), t.toString());
      pt = FormatHelper.parseTimeFast(st);
      assertEquals(pt.toString(), t.toString());
      LocalTime lt = t.toLocalTime();
      String slt = FormatHelper.formatLocalTime(lt);
      assertEquals(slt, st);

      t.setSeconds(0);
      st = FormatHelper.formatShortTime(t);
      stf = FormatHelper.formatShortTimeFast(t);
      assertEquals(stf, st);
      pt = FormatHelper.parseShortTime(st);
      assertEquals(pt.toString(), t.toString());
      pt = FormatHelper.parseShortTimeFast(st);
      assertEquals(pt.toString(), t.toString());
      lt = t.toLocalTime();
      slt = FormatHelper.formatShortLocalTime(lt);
      assertEquals(slt, st);

    }
    catch (ParseException px) {
      fail("parsing failed", px);
    }
  }


  @Test
  public void testOffsetTime() {
    OffsetTime offsetTime = OffsetTime.now().truncatedTo(ChronoUnit.SECONDS);
    String str = FormatHelper.formatOffsetTime(offsetTime);
    Reporter.log(str + "<br>", true);
    OffsetTime ot = FormatHelper.parseOffsetTime(str);
    assertEquals(ot, offsetTime);
    offsetTime = offsetTime.truncatedTo(ChronoUnit.MINUTES);
    str = FormatHelper.formatShortOffsetTime(offsetTime);
    Reporter.log(str + "<br>", true);
    ot = FormatHelper.parseShortOffsetTime(str);
    assertEquals(ot, offsetTime);
  }

  @Test
  public void testOffsetDateTime() {
    OffsetDateTime offsetDateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    String str = FormatHelper.formatOffsetDateTime(offsetDateTime);
    Reporter.log(str + "<br>", true);
    OffsetDateTime odt = FormatHelper.parseOffsetDateTime(str);
    assertEquals(odt, offsetDateTime);
    offsetDateTime = offsetDateTime.truncatedTo(ChronoUnit.MINUTES);
    str = FormatHelper.formatShortOffsetDateTime(offsetDateTime);
    Reporter.log(str + "<br>", true);
    odt = FormatHelper.parseShortOffsetDateTime(str);
    assertEquals(odt, offsetDateTime);
  }

  @Test
  public void testZonedDateTime() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    String str = FormatHelper.formatZonedDateTime(zonedDateTime);
    Reporter.log(str + "<br>", true);
    ZonedDateTime zdt = FormatHelper.parseZonedDateTime(str);
    assertEquals(zdt, zonedDateTime);
    zonedDateTime = zonedDateTime.truncatedTo(ChronoUnit.MINUTES);
    str = FormatHelper.formatShortZonedDateTime(zonedDateTime);
    Reporter.log(str + "<br>", true);
    zdt = FormatHelper.parseShortZonedDateTime(str);
    assertEquals(zdt, zonedDateTime);
  }

  /**
   * Determines the scan/format/conversion performance differences between old DateFormat and new DateTimeFormatter.
   */
  @Test
  public void testPerformance() {
    DateFormat dateFormat = new SimpleDateFormat(FormatHelper.getTimestampPattern(Locale.US));
    Timestamp timestamp = new Timestamp();
    TimeKeeper timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        synchronized (dateFormat) {   // dateFormat is not threadsafe
          String str = dateFormat.format(timestamp);
          Timestamp ts = new Timestamp(dateFormat.parse(str).getTime());
          assertEquals(dateFormat.format(ts), str);
          timestamp = new Timestamp(timestamp.getTime() + 1000);
        }
      }
    }
    catch (ParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/ sync: " + timeKeeper.millisToString() + " ms<br>", true);

    timestamp = new Timestamp();
    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        String str = dateFormat.format(timestamp);
        Timestamp ts = new Timestamp(dateFormat.parse(str).getTime());
        assertEquals(dateFormat.format(ts), str);
        timestamp = new Timestamp(timestamp.getTime() + 1000);
      }
    }
    catch (ParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/o sync: " + timeKeeper.millisToString() + " ms<br>", true);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FormatHelper.getTimestampPattern(Locale.US));
    LocalDateTime localDateTime = LocalDateTime.now();
    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        String str = formatter.format(localDateTime);
        LocalDateTime parsed = formatter.parse(str, LocalDateTime::from);
        assertEquals(formatter.format(parsed), str);
        localDateTime.plusSeconds(1);
      }
    }
    catch (DateTimeParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("new java.time package: " + timeKeeper.millisToString() + " ms<br>", true);

    timestamp = new Timestamp();
    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        String str = formatter.format(timestamp.toLocalDateTime());
        LocalDateTime parsed = formatter.parse(str, LocalDateTime::from);
        assertEquals(formatter.format(parsed), str);
        timestamp = new Timestamp(java.sql.Timestamp.valueOf(parsed).getTime() + 1000L);
      }
    }
    catch (DateTimeParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("new java.time package w/ conversion: " + timeKeeper.millisToString() + " ms<br>", true);
  }

  /**
   * Determines the scan-performance differences between old DateFormat and new DateTimeFormatter.
   */
  @Test
  public void testScanPerformance() {
    DateFormat dateFormat = new SimpleDateFormat(FormatHelper.getTimestampPattern(Locale.US));
    Timestamp timestamp = new Timestamp(System.currentTimeMillis() / 1000 * 1000);
    String str = dateFormat.format(timestamp);
    TimeKeeper timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        synchronized (dateFormat) {   // dateFormat is not threadsafe
          Timestamp ts = new Timestamp(dateFormat.parse(str).getTime());
          assertEquals(ts, timestamp);
        }
      }
    }
    catch (ParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/ sync: " + timeKeeper.millisToString() + " ms<br>", true);

    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        Timestamp ts = new Timestamp(dateFormat.parse(str).getTime());
        assertEquals(ts, timestamp);
      }
    }
    catch (ParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/o sync: " + timeKeeper.millisToString() + " ms<br>", true);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FormatHelper.getTimestampPattern(Locale.US));
    LocalDateTime localDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    str = formatter.format(localDateTime);
    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        LocalDateTime parsed = formatter.parse(str, LocalDateTime::from);
        assertEquals(parsed, localDateTime);
      }
    }
    catch (DateTimeParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("new java.time package: " + timeKeeper.millisToString() + " ms<br>", true);

    timestamp = new Timestamp(Timestamp.valueOf(localDateTime).getTime());
    timeKeeper = new TimeKeeper();
    try {
      for (int i = 0; i < LOOPS; i++) {
        Timestamp parsed = new Timestamp(Timestamp.valueOf(formatter.parse(str, LocalDateTime::from)).getTime());
        assertEquals(parsed, timestamp);
      }
    }
    catch (DateTimeParseException parseException) {
      fail("parsing failed", parseException);
    }
    timeKeeper.end();
    Reporter.log("new java.time package w/ conversion: " + timeKeeper.millisToString() + " ms<br>", true);
  }

  /**
   * Determines the format performance differences between old DateFormat and new DateTimeFormatter.
   */
  @Test
  public void testFormatPerformance() {
    DateFormat dateFormat = new SimpleDateFormat(FormatHelper.getTimestampPattern(Locale.US));
    Timestamp timestamp = new Timestamp();
    String str = dateFormat.format(timestamp);
    TimeKeeper timeKeeper = new TimeKeeper();
    for (int i = 0; i < LOOPS; i++) {
      synchronized (dateFormat) {   // dateFormat is not threadsafe
        assertEquals(dateFormat.format(timestamp), str);
      }
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/ sync: " + timeKeeper.millisToString() + " ms<br>", true);

    timeKeeper = new TimeKeeper();
    for (int i = 0; i < LOOPS; i++) {
      assertEquals(dateFormat.format(timestamp), str);
    }
    timeKeeper.end();
    Reporter.log("old java.util package w/ sync: " + timeKeeper.millisToString() + " ms<br>", true);

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FormatHelper.getTimestampPattern(Locale.US));
    LocalDateTime localDateTime = LocalDateTime.now();
    str = formatter.format(localDateTime);
    timeKeeper = new TimeKeeper();
    for (int i = 0; i < LOOPS; i++) {
      assertEquals(formatter.format(localDateTime), str);
    }
    timeKeeper.end();
    Reporter.log("new java.time package: " + timeKeeper.millisToString() + " ms<br>", true);

    timestamp = new Timestamp();
    localDateTime = timestamp.toLocalDateTime();
    str = formatter.format(localDateTime);
    timeKeeper = new TimeKeeper();
    for (int i = 0; i < LOOPS; i++) {
      assertEquals(formatter.format(timestamp.toLocalDateTime()), str);
    }
    timeKeeper.end();
    Reporter.log("new java.time package w/ conversion: " + timeKeeper.millisToString() + " ms<br>", true);
  }

}
