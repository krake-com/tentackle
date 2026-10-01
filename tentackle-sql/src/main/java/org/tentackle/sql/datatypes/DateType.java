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
 * Datatype for {@link Date}.
 */
@Service(DataType.class)
public class DateType extends AbstractDateTimeType<Date> {

  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);


  /**
   * Creates the datatype for {@link Date}.
   */
  public DateType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isTimezoneApplicable() {
    return true;
  }

  @Override
  public String getJavaType() {
    return "Date";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Date getMappedNullValue(Backend backend, int index) {
    return DateHelper.MIN_DATE;
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
    return SqlType.DATE;
  }

  @Override
  public Date valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(Date object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Date object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = DateHelper.MIN_DATE;
    }
    if (object == null) {
      statement.setNull(pos, Types.DATE);
    }
    else  {
      statement.setDate(pos, object);
    }
    return new Object[] { object };
  }

  @Override
  public Date get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Date date = resultSet.getDate(pos[0]);
    if (date == null || (mapNull && date.toLocalDate().equals(DateHelper.MIN_DATE.toLocalDate())))  {
      // minimum date is translated back to null.
      // Notice that a DATE column carries no time of day: the value read back is the local midnight
      // of the stored day, which equals MIN_DATE only in GMT. Comparing the local dates is symmetric
      // to what set() wrote in any timezone.
      return null;
    }
    return org.tentackle.common.Date.createFrozen(date.getTime());    // upgrade to org.tentackle.common.Date
  }

  /**
   * Parses a string in the format {@code yyyy-MM-dd}.
   *
   * @param str the string, optionally enclosed in double quotes
   * @return the date
   * @throws BackendException if parsing failed
   */
  protected Date parse(String str) throws BackendException {
    try {
      return Date.valueOf(DATE_FORMAT.parse(StringHelper.parseString(str), LocalDate::from));
    }
    catch (DateTimeParseException e) {
      throw new BackendException("parsing date from '" + str + "' failed", e);
    }
  }

  /**
   * Formats a date as {@code yyyy-MM-dd}.
   *
   * @param date the date
   * @return the formatted string
   */
  protected String format(Date date) {
    return DATE_FORMAT.format(date.toLocalDate());
  }

}
