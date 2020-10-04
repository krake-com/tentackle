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

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.GregorianCalendar;
import org.tentackle.common.Date;
import org.tentackle.common.DateHelper;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the FormatHelper.
 *
 * @author harald
 */
public class FormatHelperTest {

  @Test
  @SuppressWarnings("deprecation")
  public void testFormats() {

    try {
      Timestamp ts = new Timestamp();
      ts.setNanos(0);
      String sts = FormatHelper.formatTimestamp(ts);
      Timestamp pts = FormatHelper.parseTimestamp(sts);
      assertEquals(pts, ts);
      LocalDateTime lts = ts.toLocalDateTime();
      String sdt = FormatHelper.formatLocalDateTime(lts);
      assertEquals(sdt, sts);

      ts.setSeconds(0);
      sts = FormatHelper.formatShortTimestamp(ts);
      pts = FormatHelper.parseShortTimestamp(sts);
      assertEquals(pts, ts);
      lts = ts.toLocalDateTime();
      sdt = FormatHelper.formatShortLocalDateTime(lts);
      assertEquals(sdt, sts);

      GregorianCalendar cal = new GregorianCalendar();
      DateHelper.setMidnight(cal);
      Date d = new Date(cal.getTime().getTime());
      String sd = FormatHelper.formatDate(d);
      Date pd = FormatHelper.parseDate(sd);
      assertEquals(pd, d);
      LocalDate ld = d.toLocalDate();
      String sld = FormatHelper.formatLocalDate(ld);
      assertEquals(sld, sd);

      sd = FormatHelper.formatShortDate(d);
      pd = FormatHelper.parseShortDate(sd);
      assertEquals(pd, d);
      ld = d.toLocalDate();
      sld = FormatHelper.formatShortLocalDate(ld);
      assertEquals(sld, sd);

      Time t = new Time();
      String st = FormatHelper.formatTime(t);
      Time pt = FormatHelper.parseTime(st);
      assertEquals(pt.toString(), t.toString());
      LocalTime lt = t.toLocalTime();
      String slt = FormatHelper.formatLocalTime(lt);
      assertEquals(slt, st);

      t.setSeconds(0);
      st = FormatHelper.formatShortTime(t);
      pt = FormatHelper.parseShortTime(st);
      assertEquals(pt.toString(), t.toString());
      lt = t.toLocalTime();
      slt = FormatHelper.formatShortLocalTime(lt);
      assertEquals(slt, st);

    }
    catch (ParseException px) {
      fail("parsing failed", px);
    }

  }

}
