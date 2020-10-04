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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Datatype for {@link LocalDateTime}.
 * <p>
 * Since this is a predefined type, the methods {@link #set} and {@link #get} are not used by the framework
 * and only provided for completeness.
 */
@Service(DataType.class)
public class LocalDateTimeType extends AbstractDateTimeType<LocalDateTime> {

  @Override
  public String getJavaType() {
    return "LocalDateTime";
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
    return SqlType.TIMESTAMP;
  }

  @Override
  public LocalDateTime valueOf(String str) {
    return parseTimestamp(str).toLocalDateTime();
  }

  @Override
  public String toString(LocalDateTime object) {
    return StringHelper.toParsableString(printTimestamp(Timestamp.valueOf(object)));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, LocalDateTime object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = DateHelper.MIN_LOCALDATETIME;
      statement.setTimestamp(pos, DateHelper.MIN_TIMESTAMP);
    }
    else {
      if (object == null) {
        statement.setNull(pos, Types.TIMESTAMP);
      }
      else {
        statement.setTimestamp(pos, Timestamp.valueOf(object));
      }
    }
    return new Object[] { object };
  }

  @Override
  public LocalDateTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(pos[0]);
    if (timestamp == null || (mapNull && timestamp.equals(DateHelper.MIN_TIMESTAMP)))  {
      // mindate is translated back to null
      return null;
    }
    // upgrade to org.tentackle.common.Timestamp
    return timestamp.toLocalDateTime();
  }

}
