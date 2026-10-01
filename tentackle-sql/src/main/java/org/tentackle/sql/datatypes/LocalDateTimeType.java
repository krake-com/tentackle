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
 * Datatype for {@link LocalDateTime}.
 * <p>
 * Mapped to {@link Timestamp}-related JDBC methods.
 */
@Service(DataType.class)
public final class LocalDateTimeType extends AbstractDateTimeType<LocalDateTime> {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter MS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");


  /**
   * Creates the datatype for {@link LocalDateTime}.
   */
  public LocalDateTimeType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "LocalDateTime";
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
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.TIMESTAMP;
  }

  @Override
  public LocalDateTime valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(LocalDateTime object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, LocalDateTime object, boolean mapNull, Integer size) throws SQLException {
    Timestamp value;
    if (object == null) {
      if (mapNull) {
        value = DateHelper.MIN_TIMESTAMP;
      }
      else {
        statement.setNull(pos, Types.TIMESTAMP);
        return new Object[] { null };
      }
    }
    else {
      value = Timestamp.valueOf(object);
    }
    statement.setTimestamp(pos, value);
    return new Object[] { value };
  }

  @Override
  public LocalDateTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(pos[0]);
    if (timestamp == null || (mapNull && timestamp.equals(DateHelper.MIN_TIMESTAMP)))  {
      // mindate is translated back to null
      return null;
    }
    return timestamp.toLocalDateTime();
  }

  /**
   * Converts a string to a local date time.
   *
   * @param str the string
   * @return the date time
   * @throws BackendException if parsing failed
   */
  public LocalDateTime parse(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.contains(".")) {
      try {
        return LocalDateTime.parse(str, MS_TIMESTAMP_FORMATTER);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return LocalDateTime.parse(str, TIMESTAMP_FORMATTER);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp from '" + str + "' failed", e);
      }
    }
  }

  /**
   * Converts a date time object to a string.
   *
   * @param dateTime the date time
   * @return the formatted string
   */
  public String format(LocalDateTime dateTime) {
    int nanos = dateTime.getNano();
    if (nanos != 0) {
      return MS_TIMESTAMP_FORMATTER.format(dateTime);
    }
    return TIMESTAMP_FORMATTER.format(dateTime);
  }

}
