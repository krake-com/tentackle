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

import org.tentackle.common.DateHelper;
import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Datatype for {@link Timestamp}.
 */
@Service(DataType.class)
public class TimestampType extends AbstractDateTimeType<Timestamp> {

  private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";
  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);
  private static final String MS_TIMESTAMP_PATTERN = TIMESTAMP_PATTERN + ".SSS";
  private static final DateTimeFormatter MS_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(MS_TIMESTAMP_PATTERN);


  /**
   * Creates the datatype for {@link Timestamp}.
   */
  public TimestampType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isTimezoneApplicable() {
    return true;
  }

  @Override
  public String getJavaType() {
    return "Timestamp";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Timestamp getMappedNullValue(Backend backend, int index) {
    return DateHelper.MIN_TIMESTAMP;
  }

  @Override
  public boolean isUTCSupported() {
    return true;
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.TIMESTAMP;
  }

  @Override
  public Timestamp valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(Timestamp object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Timestamp object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = DateHelper.MIN_TIMESTAMP;
    }
    if (object == null) {
      statement.setNull(pos, Types.TIMESTAMP);
    }
    else  {
      statement.setTimestamp(pos, object);
    }
    return new Object[] { object };
  }

  @Override
  public Timestamp get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(pos[0]);
    if (timestamp == null || (mapNull && timestamp.equals(DateHelper.MIN_TIMESTAMP)))  {
      // mindate is translated back to null
      return null;
    }
    // upgrade to org.tentackle.common.Timestamp
    return org.tentackle.common.Timestamp.createFrozen(timestamp.getTime(), timestamp.getNanos());
  }

  /**
   * Parses a string in the format {@code yyyy-MM-dd HH:mm:ss[.SSS]}.
   *
   * @param str the string, optionally enclosed in double quotes
   * @return the timestamp
   * @throws BackendException if parsing failed
   */
  protected Timestamp parse(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.contains(".")) {
      try {
        return Timestamp.valueOf(MS_TIMESTAMP_FORMAT.parse(str, LocalDateTime::from));
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return Timestamp.valueOf(TIMESTAMP_FORMAT.parse(str, LocalDateTime::from));
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp from '" + str + "' failed", e);
      }
    }
  }

  /**
   * Formats a timestamp as {@code yyyy-MM-dd HH:mm:ss}, with {@code .SSS} appended if it has milliseconds.
   *
   * @param timestamp the timestamp
   * @return the formatted string
   */
  protected String format(Timestamp timestamp) {
    if (timestamp.getTime() % 1000 != 0) {
      synchronized(MS_TIMESTAMP_FORMAT) {
        return MS_TIMESTAMP_FORMAT.format(timestamp.toLocalDateTime());
      }
    }
    synchronized(TIMESTAMP_FORMAT) {
      return TIMESTAMP_FORMAT.format(timestamp.toLocalDateTime());
    }
  }

}
