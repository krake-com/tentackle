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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Datatype for {@link LocalDate}.
 * <p>
 * Mapped to {@link Date}-related JDBC methods.
 */
@Service(DataType.class)
public final class LocalDateType extends AbstractDateTimeType<LocalDate> {

  /**
   * Creates the datatype for {@link LocalDate}.
   */
  public LocalDateType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "LocalDate";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.DATE;
  }

  @Override
  public LocalDate valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(LocalDate object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, LocalDate object, boolean mapNull, Integer size) throws SQLException {
    Date value;
    if (object == null) {
      if (mapNull) {
        value = DateHelper.MIN_DATE;
      }
      else {
        statement.setNull(pos, Types.DATE);
        return new Object[] { null };
      }
    }
    else {
      value = Date.valueOf(object);
    }
    statement.setDate(pos, value);
    return new Object[] { value };
  }

  @Override
  public LocalDate get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Date date = resultSet.getDate(pos[0]);
    if (date == null || (mapNull && date.toLocalDate().equals(DateHelper.MIN_DATE.toLocalDate())))  {
      // mindate is translated back to null.
      // Notice that a DATE column carries no time of day: the value read back is the local midnight
      // of the stored day, which equals MIN_DATE only in GMT. Comparing the local dates is symmetric
      // to what set() wrote in any timezone.
      return null;
    }
    return date.toLocalDate();
  }

  /**
   * Converts a string to a local date.
   *
   * @param str the string
   * @return the date
   * @throws BackendException if parsing failed
   */
  public LocalDate parse(String str) throws BackendException {
    try {
      return LocalDate.parse(StringHelper.parseString(str), DateTimeFormatter.ISO_LOCAL_DATE);
    }
    catch (DateTimeParseException e) {
      throw new BackendException("parsing date from '" + str + "' failed", e);
    }
  }

  /**
   * Converts a date object to a string.
   *
   * @param date the date
   * @return the formatted string
   */
  public String format(LocalDate date) {
    return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
  }

}
