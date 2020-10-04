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

import org.tentackle.common.StringHelper;
import org.tentackle.sql.BackendException;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Base class for date- or time-related data types.
 *
 * @param <T> the java type
 */
public abstract class AbstractDateTimeType<T> extends AbstractDataType<T> {

  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);
  private static final String TIME_PATTERN = "HH:mm:ss";
  private static final DateFormat TIME_FORMAT = new SimpleDateFormat(TIME_PATTERN);
  private static final String TIMESTAMP_PATTERN = DATE_PATTERN + " " + TIME_PATTERN;
  private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(TIMESTAMP_PATTERN);
  private static final String MS_TIMESTAMP_PATTERN = TIMESTAMP_PATTERN + ".000";
  private static final DateFormat MS_TIMESTAMP_FORMAT = new SimpleDateFormat(MS_TIMESTAMP_PATTERN);


  @Override
  public boolean isDateOrTime() {
    return true;
  }


  protected Date parseDate(String str) throws BackendException {
    synchronized(DATE_FORMAT) {  // formatters are not thread-safe :(
      try {
        return new Date(DATE_FORMAT.parse(StringHelper.parseString(str)).getTime());
      }
      catch (ParseException e) {
        throw new BackendException("parsing date from '" + str + "' failed", e);
      }
    }
  }

  protected Time parseTime(String str) throws BackendException {
    synchronized(TIME_FORMAT) {
      try {
        return new Time(TIME_FORMAT.parse(StringHelper.parseString(str)).getTime());
      }
      catch (ParseException e) {
        throw new BackendException("parsing time from '" + str + "' failed", e);
      }
    }
  }

  protected Timestamp parseTimestamp(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.length() > 3 && str.charAt(str.length() - 4) == '.') {
      synchronized(MS_TIMESTAMP_FORMAT) {
        try {
          return new Timestamp(MS_TIMESTAMP_FORMAT.parse(str).getTime());
        }
        catch (ParseException e) {
          throw new BackendException("parsing timestamp with ms from '" + str + "' failed", e);
        }
      }
    }
    else  {
      synchronized(TIMESTAMP_FORMAT) {
        try {
          return new Timestamp(TIMESTAMP_FORMAT.parse(str).getTime());
        }
        catch (ParseException e) {
          throw new BackendException("parsing timestamp from '" + str + "' failed", e);
        }
      }
    }
  }

  protected String printDate(java.util.Date date) {
    synchronized(DATE_FORMAT) {  // formatters are not thread-safe :(
      return DATE_FORMAT.format(date);
    }
  }

  protected String printTime(java.util.Date time) {
    synchronized(TIME_FORMAT) {
      return TIME_FORMAT.format(time);
    }
  }

  protected String printTimestamp(java.util.Date timestamp) {
    if (timestamp.getTime() % 1000 != 0) {
      synchronized(MS_TIMESTAMP_FORMAT) {
        return MS_TIMESTAMP_FORMAT.format(timestamp);
      }
    }
    synchronized(TIMESTAMP_FORMAT) {
      return TIMESTAMP_FORMAT.format(timestamp);
    }
  }

}
