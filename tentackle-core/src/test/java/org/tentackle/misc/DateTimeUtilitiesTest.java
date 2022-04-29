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

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DateTimeUtilitiesTest {

  @Test
  public void nowShortCut() {
    OffsetDateTime offsetDateTimeNow = OffsetDateTime.now();
    OffsetDateTime offsetDateTime = DateTimeUtilities.parseOffsetDateTime(".");
    long epochNow = offsetDateTimeNow.toInstant().toEpochMilli();
    long epoch = offsetDateTime.toInstant().toEpochMilli();
    long diff = epoch - epochNow;
    assertTrue(diff < 1000);   // 1s should really be enough...
  }

  @Test
  public void addShortcut() {
    skipFlip();
    LocalDateTime localDateTimeNow = LocalDateTime.now();
    LocalDateTime localDateTime = DateTimeUtilities.parseLocalDateTime("-10d");
    assertEquals(localDateTime.plusDays(10).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    localDateTime = DateTimeUtilities.parseLocalDateTime("-10");    // d can be omitted
    assertEquals(localDateTime.plusDays(10).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    localDateTime = DateTimeUtilities.parseLocalDateTime("-2M");
    assertEquals(localDateTime.plusMonths(2).getMonthValue(), localDateTimeNow.getMonthValue());
    localDateTime = DateTimeUtilities.parseLocalDateTime("+3y");
    assertEquals(localDateTime.minusYears(3).getYear(), localDateTimeNow.getYear());
    localDateTime = DateTimeUtilities.parseLocalDateTime("+8h");
    assertEquals(localDateTime.minusHours(8).getHour(), localDateTimeNow.getHour());
    localDateTime = DateTimeUtilities.parseLocalDateTime("+30m");
    assertEquals(localDateTime.minusMinutes(30).getMinute(), localDateTimeNow.getMinute());
    localDateTime = DateTimeUtilities.parseLocalDateTime("+222s");
    assertTrue(localDateTime.minusSeconds(222).getSecond() - localDateTimeNow.getSecond() <= 2);
  }

  @Test
  public void numberShortcut() {
    LocalDate localDateNow = LocalDate.now();
    LocalDate localDate = DateTimeUtilities.parseLocalDate("20y");
    assertEquals(localDate.getYear(), 2020);
    assertEquals(localDate.getMonthValue(), 1);
    assertEquals(localDate.getDayOfMonth(), 1);
    localDate = DateTimeUtilities.parseLocalDate("10m");
    assertEquals(localDate.getYear(), localDateNow.getYear());
    assertEquals(localDate.getMonthValue(), 10);
    assertEquals(localDate.getDayOfMonth(), 1);
    localDate = DateTimeUtilities.parseLocalDate("10d");
    assertEquals(localDate.getYear(), localDateNow.getYear());
    assertEquals(localDate.getMonthValue(), localDateNow.getMonthValue());
    assertEquals(localDate.getDayOfMonth(), 10);
    localDate = DateTimeUtilities.parseLocalDate("10");
    assertEquals(localDate.getYear(), localDateNow.getYear());
    assertEquals(localDate.getMonthValue(), localDateNow.getMonthValue());
    assertEquals(localDate.getDayOfMonth(), 10);
    localDate = DateTimeUtilities.parseLocalDate("0310");
    assertEquals(localDate.getYear(), localDateNow.getYear());
    assertEquals(localDate.getMonthValue(), 3);
    assertEquals(localDate.getDayOfMonth(), 10);
    localDate = DateTimeUtilities.parseLocalDate("031021");
    assertEquals(localDate.getYear(), 2021);
    assertEquals(localDate.getMonthValue(), 3);
    assertEquals(localDate.getDayOfMonth(), 10);

    LocalDateTime localDateTimeNow = LocalDateTime.now();
    LocalDateTime localDateTime = DateTimeUtilities.parseLocalDateTime("20y");
    assertEquals(localDateTime.getYear(), 2020);
    assertEquals(localDateTime.getMonthValue(), 1);
    assertEquals(localDateTime.getDayOfMonth(), 1);
    localDateTime = DateTimeUtilities.parseLocalDateTime("10M");    // m would mean "minute"
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), 10);
    assertEquals(localDateTime.getDayOfMonth(), 1);
    localDateTime = DateTimeUtilities.parseLocalDateTime("10d");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), 10);
    localDateTime = DateTimeUtilities.parseLocalDateTime("10");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), 10);
    localDateTime = DateTimeUtilities.parseLocalDateTime("0310");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), 3);
    assertEquals(localDateTime.getDayOfMonth(), 10);
    localDateTime = DateTimeUtilities.parseLocalDateTime("031021");
    assertEquals(localDateTime.getYear(), 2021);
    assertEquals(localDateTime.getMonthValue(), 3);
    assertEquals(localDateTime.getDayOfMonth(), 10);
    assertEquals(localDateTime.getHour(), 0);
    assertEquals(localDateTime.getMinute(), 0);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("031021s");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), 3);
    assertEquals(localDateTime.getMinute(), 10);
    assertEquals(localDateTime.getSecond(), 21);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("0310h");    // would also work with m or s
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), 3);
    assertEquals(localDateTime.getMinute(), 10);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("3h");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), 3);
    assertEquals(localDateTime.getMinute(), 0);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("20m");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.getMonthValue(), localDateTimeNow.getMonthValue());
    assertEquals(localDateTime.getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), localDateTimeNow.getHour());
    assertEquals(localDateTime.getMinute(), 20);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);

    OffsetTime offsetTimeNow = OffsetTime.now();
    OffsetTime offsetTime = DateTimeUtilities.parseOffsetTime("142259");
    assertEquals(offsetTime.getHour(), 14);
    assertEquals(offsetTime.getMinute(), 22);
    assertEquals(offsetTime.getSecond(), 59);
    assertEquals(offsetTime.getNano(), 0);
    offsetTime = DateTimeUtilities.parseOffsetTime("1422");
    assertEquals(offsetTime.getHour(), 14);
    assertEquals(offsetTime.getMinute(), 22);
    assertEquals(offsetTime.getSecond(), 0);
    assertEquals(offsetTime.getNano(), 0);
    offsetTime = DateTimeUtilities.parseOffsetTime("14");
    assertEquals(offsetTime.getHour(), 14);
    assertEquals(offsetTime.getMinute(), 0);
    assertEquals(offsetTime.getSecond(), 0);
    assertEquals(offsetTime.getNano(), 0);
    offsetTime = DateTimeUtilities.parseOffsetTime("22m");
    assertEquals(offsetTime.getHour(), offsetTimeNow.getHour());
    assertEquals(offsetTime.getMinute(), 22);
    assertEquals(offsetTime.getSecond(), 0);
    assertEquals(offsetTime.getNano(), 0);
    offsetTime = DateTimeUtilities.parseOffsetTime("22s");
    assertEquals(offsetTime.getHour(), offsetTimeNow.getHour());
    assertEquals(offsetTime.getMinute(), offsetTimeNow.getMinute());
    assertEquals(offsetTime.getSecond(), 22);
    assertEquals(offsetTime.getNano(), 0);
  }

  @Test
  public void combined() {
    LocalDate localDate = DateTimeUtilities.parseLocalDate("05/30/2020");   // full scan
    assertEquals(localDate.getDayOfMonth(), 30);
    assertEquals(localDate.getMonthValue(), 5);
    assertEquals(localDate.getYear(), 2020);

    LocalDateTime localDateTime = DateTimeUtilities.parseLocalDateTime("05/30/2020 15:30:00");
    assertEquals(localDateTime.getDayOfMonth(), 30);
    assertEquals(localDateTime.getMonthValue(), 5);
    assertEquals(localDateTime.getYear(), 2020);
    assertEquals(localDateTime.getHour(), 15);
    assertEquals(localDateTime.getMinute(), 30);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);

    skipFlip();
    localDateTime = DateTimeUtilities.parseLocalDateTime("+2d0h");
    LocalDateTime localDateTimeNow = LocalDateTime.now();
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.minusDays(2).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), 0);
    assertEquals(localDateTime.getMinute(), 0);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("+2d0");   // h can be omitted
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.minusDays(2).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), 0);
    assertEquals(localDateTime.getMinute(), 0);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("+2d0m");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.minusDays(2).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), localDateTimeNow.getHour());
    assertEquals(localDateTime.getMinute(), 0);
    assertEquals(localDateTime.getSecond(), 0);
    assertEquals(localDateTime.getNano(), 0);
    localDateTime = DateTimeUtilities.parseLocalDateTime("+2d30s");
    assertEquals(localDateTime.getYear(), localDateTimeNow.getYear());
    assertEquals(localDateTime.minusDays(2).getDayOfMonth(), localDateTimeNow.getDayOfMonth());
    assertEquals(localDateTime.getHour(), localDateTimeNow.getHour());
    assertEquals(localDateTime.getMinute(), localDateTimeNow.getMinute());
    assertEquals(localDateTime.getSecond(), 30);
    assertEquals(localDateTime.getNano(), 0);
  }

  @Test
  public void offset() {
    OffsetTime offsetTime = DateTimeUtilities.parseOffsetTime("20:50:11 +2:30");
    assertEquals(offsetTime.getHour(), 20);
    assertEquals(offsetTime.getMinute(), 50);
    assertEquals(offsetTime.getSecond(), 11);
    assertEquals(offsetTime.getOffset().getTotalSeconds(), 9000);
    offsetTime = DateTimeUtilities.parseOffsetTime("20:50:11 +02");
    assertEquals(offsetTime.getHour(), 20);
    assertEquals(offsetTime.getMinute(), 50);
    assertEquals(offsetTime.getSecond(), 11);
    assertEquals(offsetTime.getOffset().getTotalSeconds(), 7200);
    offsetTime = DateTimeUtilities.parseOffsetTime("20:50:11 +2");
    assertEquals(offsetTime.getHour(), 20);
    assertEquals(offsetTime.getMinute(), 50);
    assertEquals(offsetTime.getSecond(), 11);
    assertEquals(offsetTime.getOffset().getTotalSeconds(), 7200);
    skipFlip();
    offsetTime = DateTimeUtilities.parseOffsetTime("20:50:11");
    assertEquals(offsetTime.getHour(), 20);
    assertEquals(offsetTime.getMinute(), 50);
    assertEquals(offsetTime.getSecond(), 11);
    assertEquals(offsetTime.getOffset(), OffsetTime.now().getOffset());
    offsetTime = DateTimeUtilities.parseOffsetTime("=+2:30");
    assertEquals(offsetTime.getOffset().getTotalSeconds(), 9000);
  }

  @Test
  public void timezone() {
    ZonedDateTime zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 15:30:00 Europe/Berlin");
    assertEquals(zonedDateTime.getDayOfMonth(), 30);
    assertEquals(zonedDateTime.getMonthValue(), 5);
    assertEquals(zonedDateTime.getYear(), 2020);
    assertEquals(zonedDateTime.getHour(), 15);
    assertEquals(zonedDateTime.getMinute(), 30);
    assertEquals(zonedDateTime.getSecond(), 0);
    assertEquals(zonedDateTime.getZone().getId(), "Europe/Berlin");
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("053020 paris");
    assertEquals(zonedDateTime.getDayOfMonth(), 30);
    assertEquals(zonedDateTime.getMonthValue(), 5);
    assertEquals(zonedDateTime.getYear(), 2020);
    assertEquals(zonedDateTime.getHour(), 0);
    assertEquals(zonedDateTime.getMinute(), 0);
    assertEquals(zonedDateTime.getSecond(), 0);
    assertEquals(zonedDateTime.getNano(), 0);
    assertEquals(zonedDateTime.getZone().getId(), "Europe/Paris");
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("053020");
    assertEquals(zonedDateTime.getDayOfMonth(), 30);
    assertEquals(zonedDateTime.getMonthValue(), 5);
    assertEquals(zonedDateTime.getYear(), 2020);
    assertEquals(zonedDateTime.getHour(), 0);
    assertEquals(zonedDateTime.getMinute(), 0);
    assertEquals(zonedDateTime.getSecond(), 0);
    assertEquals(zonedDateTime.getNano(), 0);
    assertEquals(zonedDateTime.getZone().getId(), ZonedDateTime.now().getZone().getId());
    skipFlip();
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("+10h berlin");
    assertEquals(zonedDateTime.getHour(), ZonedDateTime.now().plusHours(10).getHour());
    assertEquals(zonedDateTime.getZone().getId(), "Europe/Berlin");

    zonedDateTime = DateTimeUtilities.parseZonedDateTime("+1d16h =york");    // tomorrow 16:00 local time converted to New York time
    assertEquals(zonedDateTime.getZone().getId(), "America/New_York");

    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 15:30:00 york");
    assertEquals(zonedDateTime.getZone().getId(), "America/New_York");
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("=paris");
    assertEquals(zonedDateTime.getZone().getId(), "Europe/Paris");
  }

  @Test
  public void handlers() {
    ZonedDateTime zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 15:30:00");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 915");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());
    assertEquals(zonedDateTime.getHour(), 9);
    assertEquals(zonedDateTime.getMinute(), 15);
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 9:15:30");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());
    assertEquals(zonedDateTime.getHour(), 9);
    assertEquals(zonedDateTime.getMinute(), 15);
    assertEquals(zonedDateTime.getSecond(), 30);
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 91530");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());
    assertEquals(zonedDateTime.getHour(), 9);
    assertEquals(zonedDateTime.getMinute(), 15);
    assertEquals(zonedDateTime.getSecond(), 30);
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020 9");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());
    assertEquals(zonedDateTime.getHour(), 9);
    zonedDateTime = DateTimeUtilities.parseZonedDateTime("05/30/2020");
    assertEquals(zonedDateTime.getZone(), ZonedDateTime.now().getZone());

    OffsetDateTime offsetDateTime = DateTimeUtilities.parseShortOffsetDateTime("05/30/20 1530");
    assertEquals(offsetDateTime.getOffset(), OffsetDateTime.now().getOffset());
    assertEquals(offsetDateTime.getHour(), 15);
    assertEquals(offsetDateTime.getMinute(), 30);
    assertEquals(offsetDateTime.getSecond(), 0);

    offsetDateTime = DateTimeUtilities.parseShortOffsetDateTime("05/30/20 1530 +2");
    assertEquals(offsetDateTime.getOffset().getTotalSeconds(), 7200);
    assertEquals(offsetDateTime.getHour(), 15);
    assertEquals(offsetDateTime.getMinute(), 30);
    assertEquals(offsetDateTime.getSecond(), 0);
  }

  @Test
  public void filter() {
    LocalDateTime localDateTime = DateTimeUtilities.parseLocalDateTime("05/30/21 15:45");
    assertEquals(localDateTime.getYear(), 2021);
    assertEquals(localDateTime.getMonthValue(), 5);
    assertEquals(localDateTime.getDayOfMonth(), 30);
    // notice that it is not possible to enter more than one shortcut in a row
    // thus, the above will be interpreted as 05/30/2021 00:00:00 and the user must change the time in a second step
    assertEquals(localDateTime.getHour(), 0);

    localDateTime = DateTimeUtilities.parseLocalDateTime("5/30/");
    assertEquals(localDateTime.getMonthValue(), 5);
    assertEquals(localDateTime.getDayOfMonth(), 30);

    localDateTime = DateTimeUtilities.parseLocalDateTime("5/30");
    assertEquals(localDateTime.getMonthValue(), 5);
    assertEquals(localDateTime.getDayOfMonth(), 30);

    localDateTime = DateTimeUtilities.parseLocalDateTime("1/8");
    assertEquals(localDateTime.getMonthValue(), 1);
    assertEquals(localDateTime.getDayOfMonth(), 8);
  }


  private void skipFlip() {
    LocalTime localTimeNow = LocalTime.now();
    if (localTimeNow.getSecond() >= 58) {    // wait for flip...
      try {
        Reporter.log("waiting for 00s...<br>", true);
        Thread.sleep(3000);
      }
      catch (InterruptedException e) {
        Assert.fail("interrupted??", e);
      }
    }
  }

}
