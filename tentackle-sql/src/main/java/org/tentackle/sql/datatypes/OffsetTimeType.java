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
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datatype for {@link OffsetTime}.
 * <p>
 * Stored as 2 columns:
 * <ol>
 *   <li>the time</li>
 *   <li>the zone offset in seconds</li>
 * </ol>
 * This type is not sortable by the database in a meaningful manner.
 */
@Service(DataType.class)
public class OffsetTimeType extends AbstractDateTimeType<OffsetTime> {

  @Override
  public String getJavaType() {
    return "OffsetTime";
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public int[] getSortableColumns() {
    return null;
  }

  @Override
  public Optional<String> getCommentSuffix(int index) {
    return switch (index) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(" [+-s]");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnGetter(int index) {
    return switch (index) {
      case 0 -> "toLocalTime";
      case 1 -> "getOffset";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(int index, OffsetTime value) {
    if (value != null) {
      return switch (index) {
        case 0 -> value.toLocalTime();
        case 1 -> value.getOffset();
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public int getSize(int index, Integer size) {
    return switch (index) {
      case 0 -> super.getSize(index, size);
      case 1 -> 0;
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public int getScale(int index, Integer scale) {
    return 0;
  }

  @Override
  public SqlType getSqlType(int index) {
    return switch (index) {
      case 0 -> SqlType.TIME;
      case 1 -> SqlType.INTEGER;
      default -> throw new IndexOutOfBoundsException();
    };
  }

  @Override
  public OffsetTime valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(OffsetTime object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, OffsetTime object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.TIME);
      statement.setNull(pos + 1, Types.INTEGER);
      return new Object[] { null, null };
    }
    Time time = Time.valueOf(object.toLocalTime());
    int offset = object.getOffset().getTotalSeconds();
    statement.setTime(pos, time);
    statement.setInt(pos + 1, offset);
    return new Object[] { time, offset };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, OffsetTime object, int index, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      switch (index) {
        case 0:
          statement.setNull(pos, Types.TIME);
          return null;
        case 1:
          statement.setNull(pos, Types.INTEGER);
          return null;
      }
    }
    else {
      switch (index) {
        case 0:
          Time time = Time.valueOf(object.toLocalTime());
          statement.setTime(pos, time);
          return time;
        case 1:
          int offset = object.getOffset().getTotalSeconds();
          statement.setInt(pos, offset);
          return offset;
      }
    }
    throw new IndexOutOfBoundsException(index);
  }

  @Override
  public OffsetTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Time time = resultSet.getTime(pos[0]);
    return resultSet.wasNull() ? null : OffsetTime.of(time.toLocalTime(), ZoneOffset.ofTotalSeconds(resultSet.getInt(pos[1])));
  }


  protected OffsetTime parse(String str) throws BackendException {
    try {
      return OffsetTime.parse(StringHelper.parseString(str), DateTimeFormatter.ISO_OFFSET_TIME);
    }
    catch (DateTimeParseException e) {
      throw new BackendException("parsing time from '" + str + "' failed", e);
    }
  }

  protected String format(OffsetTime time) {
    return DateTimeFormatter.ISO_OFFSET_TIME.format(time);
  }

}
