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

  /**
   * Derives the time from an OffsetTime value.
   *
   * @param value the OffsetTime value
   * @return the time
   */
  public static Time timeOf(OffsetTime value) {
    return Time.valueOf(value.toLocalTime());
  }

  /**
   * Derives the timezone offset from an OffsetTime value.
   *
   * @param value the OffsetTime value
   * @return the timezone offset in seconds
   */
  public static int offsetOf(OffsetTime value) {
    return value.getOffset().getTotalSeconds();
  }


  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss[ ]xxx");
  private static final DateTimeFormatter MS_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS[ ]xxx");


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
  public String getColumnGetter(int index, String varName) {
    return switch (index) {
      case 0 -> "OffsetTimeType.timeOf(" + varName + ")";
      case 1 -> "OffsetTimeType.offsetOf(" + varName + ")";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnAlias(int index) {
    return switch (index) {
      case 0 -> "time";
      case 1 -> "offset";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(int index, OffsetTime value) {
    if (value != null) {
      return switch (index) {
        case 0 -> timeOf(value);
        case 1 -> offsetOf(value);
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public int getSize(Backend backend, int index, Integer size) {
    return switch (index) {
      case 0 -> super.getSize(backend, index, size);
      case 1 -> 0;
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public int getScale(Backend backend, int index, Integer scale) {
    return 0;
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
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
  public String toLiteral(String str, Integer index) {
    if (index != null && index == 0 && !str.isEmpty() && str.charAt(0) != '\'') {
      str = "'" + str + "'";
    }
    return str;
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if (index != null) {
      return switch (index) {
        case 0 -> {
          if ("null".equals(str)) {
            yield str;
          }
          if (str.length() > 0 && Character.isDigit(str.charAt(0))) {
            str = "\"" + str + "\"";
          }
          yield "Time.valueOf(" + str + ")";
        }
        case 1 -> str;
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    if (str.length() > 0 && Character.isDigit(str.charAt(0))) {
      str = "\"" + str + "\"";
    }
    return "OffsetTime.parse(" + str + ")";
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, OffsetTime object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.TIME);
      statement.setNull(pos + 1, Types.INTEGER);
      return new Object[] { null, null };
    }
    Time time = timeOf(object);
    int offset = offsetOf(object);
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
          Time time = timeOf(object);
          statement.setTime(pos, time);
          return time;
        case 1:
          int offset = offsetOf(object);
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
    str = StringHelper.parseString(str);
    if (str.contains(".")) {
      try {
        return MS_TIME_FORMATTER.parse(str, OffsetTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing time with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return TIME_FORMATTER.parse(str, OffsetTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing time from '" + str + "' failed", e);
      }
    }
  }

  protected String format(OffsetTime time) {
    int nanos = time.getNano();
    if (nanos != 0) {
      return MS_TIME_FORMATTER.format(time);
    }
    return TIME_FORMATTER.format(time);
  }

}
