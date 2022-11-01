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

import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Datatype for {@link LocalTime}.
 * <p>
 * Mapped to {@link Time}-related JDBC methods.
 */
@Service(DataType.class)
public final class LocalTimeType extends AbstractDateTimeType<LocalTime> {

  /**
   * Creates the datatype for {@link LocalTime}.
   */
  public LocalTimeType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "LocalTime";
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.TIME;
  }

  @Override
  public LocalTime valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(LocalTime object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, LocalTime object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.TIME);
    }
    else  {
      statement.setTime(pos, Time.valueOf(object));
    }
    return new Object[] { object };
  }

  @Override
  public LocalTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Time time = resultSet.getTime(pos[0]);
    return time == null ? null : time.toLocalTime();
  }


  protected LocalTime parse(String str) throws BackendException {
    try {
      return LocalTime.parse(StringHelper.parseString(str), DateTimeFormatter.ISO_LOCAL_TIME);
    }
    catch (DateTimeParseException e) {
      throw new BackendException("parsing time from '" + str + "' failed", e);
    }
  }

  protected String format(LocalTime time) {
    return DateTimeFormatter.ISO_LOCAL_TIME.format(time);
  }

}
