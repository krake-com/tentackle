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

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Tests related to {@code java.util}-API.
 */
public class DateTimeTest {

  @Test
  public void testTime() {
    Time time = Time.valueOf(LocalTime.now());    // does the trick to remove the date
    TimeType type = new TimeType();
    String str = type.format(time);
    Reporter.log(str + "<br>");
    Time time1 = type.parse(str);
    Assert.assertEquals(time1, time);
  }

  @Test
  public void testDate() {
    Date date = Date.valueOf(LocalDate.now());    // does the trick to remove the millis
    DateType type = new DateType();
    String str = type.format(date);
    Reporter.log(str + "<br>");
    Date date1 = type.parse(str);
    Assert.assertEquals(date1, date);
  }

  @Test
  public void testTimestamp() {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    TimestampType type = new TimestampType();
    String str = type.format(timestamp);
    Reporter.log(str + "<br>");
    Timestamp timestamp1 = type.parse(str);
    Assert.assertEquals(timestamp1, timestamp);
    timestamp.setNanos(0);
    str = type.format(timestamp);
    Reporter.log(str + "<br>");
    timestamp1 = type.parse(str);
    Assert.assertEquals(timestamp1, timestamp);
  }

}
