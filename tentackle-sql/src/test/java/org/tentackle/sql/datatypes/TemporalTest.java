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

package org.tentackle.sql.datatypes;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Tests related to {@code java.time}-API.
 */
public class TemporalTest {

  @Test
  public void testLocalTime() {
    LocalTime time = LocalTime.now().truncatedTo(ChronoUnit.MILLIS);
    LocalTimeType type = new LocalTimeType();
    String str = type.format(time);
    Reporter.log(str + "<br>");
    LocalTime time1 = type.parse(str);
    Assert.assertEquals(time1, time);
    time = time.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(time);
    Reporter.log(str + "<br>");
    time1 = type.parse(str);
    Assert.assertEquals(time1, time);
  }

  @Test
  public void testOffsetTime() {
    OffsetTime time = OffsetTime.now().truncatedTo(ChronoUnit.MILLIS);
    OffsetTimeType type = new OffsetTimeType();
    String str = type.format(time);
    Reporter.log(str + "<br>");
    OffsetTime time1 = type.parse(str);
    Assert.assertEquals(time1, time);
    time = time.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(time);
    Reporter.log(str + "<br>");
    time1 = type.parse(str);
    Assert.assertEquals(time1, time);
  }

  @Test
  public void testLocalDate() {
    LocalDate date = LocalDate.now();
    LocalDateType type = new LocalDateType();
    String str = type.format(date);
    Reporter.log(str + "<br>");
    LocalDate date1 = type.parse(str);
    Assert.assertEquals(date1, date);
  }

  @Test
  public void testLocalDateTime() {
    LocalDateTime dateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    LocalDateTimeType type = new LocalDateTimeType();
    String str = type.format(dateTime);
    Reporter.log(str + "<br>");
    LocalDateTime dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
    dateTime = dateTime.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(dateTime);
    Reporter.log(str + "<br>");
    dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
  }

  @Test
  public void testOffsetDateTime() {
    OffsetDateTime dateTime = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    OffsetDateTimeType type = new OffsetDateTimeType();
    String str = type.format(dateTime);
    Reporter.log(str + "<br>");
    OffsetDateTime dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
    dateTime = dateTime.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(dateTime);
    Reporter.log(str + "<br>");
    dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
  }

  @Test
  public void testZonedDateTime() {
    ZonedDateTime dateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    ZonedDateTimeType type = new ZonedDateTimeType();
    String str = type.format(dateTime);
    Reporter.log(str + "<br>");
    ZonedDateTime dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
    dateTime = dateTime.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(dateTime);
    Reporter.log(str + "<br>");
    dateTime1 = type.parse(str);
    Assert.assertEquals(dateTime1, dateTime);
  }

  @Test
  public void testInstant() {
    Instant instant = Instant.now();
    InstantType type = new InstantType();
    String str = type.format(instant);
    Reporter.log(str + "<br>");
    Instant instant1 = type.parse(str);
    Assert.assertEquals(instant1, instant);
    instant = instant.truncatedTo(ChronoUnit.MILLIS);
    str = type.format(instant);
    Reporter.log(str + "<br>");
    instant1 = type.parse(str);
    Assert.assertEquals(instant1, instant);
    instant = instant.truncatedTo(ChronoUnit.SECONDS);
    str = type.format(instant);
    Reporter.log(str + "<br>");
    instant1 = type.parse(str);
    Assert.assertEquals(instant1, instant);
  }

}
