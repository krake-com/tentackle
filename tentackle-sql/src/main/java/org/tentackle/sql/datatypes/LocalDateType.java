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
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/**
 * Datatype for {@link LocalDate}.
 */
@Service(DataType.class)
public class LocalDateType extends AbstractDateTimeType<LocalDate> {

  @Override
  public String getJavaType() {
    return "LocalDate";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public SqlType getSqlType(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.DATE;
  }

  @Override
  public LocalDate valueOf(String str) {
    return parseDate(str).toLocalDate();
  }

  @Override
  public String toString(LocalDate object) {
    return StringHelper.toParsableString(printDate(Date.valueOf(object)));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, LocalDate object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = DateHelper.MIN_LOCALDATE;
      statement.setDate(pos, DateHelper.MIN_DATE);
    }
    else {
      if (object == null) {
        statement.setNull(pos, Types.DATE);
      }
      else {
        statement.setDate(pos, Date.valueOf(object));
      }
    }
    return new Object[] { object };
  }

  @Override
  public LocalDate get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Date date = resultSet.getDate(pos[0]);
    if (date == null || (mapNull && date.equals(DateHelper.MIN_DATE)))  {
      // mindate is translated back to null
      return null;
    }
    return date.toLocalDate();
  }

}
