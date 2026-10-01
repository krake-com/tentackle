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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datatype for {@link OffsetDateTime}.
 * <p>
 * Stored as 2 columns:
 * <ol>
 *   <li>the timestamp normalized to GMT (hence sortable)</li>
 *   <li>the zone offset in seconds</li>
 * </ol>
 * The MAPNULL option maps to {@link org.tentackle.common.DateHelper#MIN_TIMESTAMP} with offset 0.<br>
 * This type is sortable by the first column.
 */
@Service(DataType.class)
public final class OffsetDateTimeType extends AbstractDateTimeType<OffsetDateTime> {

  /**
   * Derives the timestamp from an OffsetDateTime value.
   *
   * @param value the OffsetDateTime value
   * @return the timestamp
   */
  public static Timestamp timestampOf(OffsetDateTime value) {
    return Timestamp.valueOf(value.toLocalDateTime().minusSeconds(offsetOf(value)));
  }

  /**
   * Derives the timezone offset from an OffsetDateTime value.
   *
   * @param value the OffsetDateTime value
   * @return the timezone offset in seconds
   */
  public static int offsetOf(OffsetDateTime value) {
    return value.getOffset().getTotalSeconds();
  }


  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[ ]xxx");
  private static final DateTimeFormatter MS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS[ ]xxx");


  /**
   * Creates the datatype for {@link OffsetDateTime}.
   */
  public OffsetDateTimeType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "OffsetDateTime";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    return switch (index) {
      case 0 -> DateHelper.MIN_TIMESTAMP;
      case 1 -> 0;
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public int getColumnCount(Backend backend) {
    return 2;
  }

  @Override
  public Optional<String> getCommentSuffix(Backend backend, int index) {
    return switch (index) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(" [+-s]");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    return switch (index) {
      case 0 -> "OffsetDateTimeType.timestampOf(" + varName + ")";
      case 1 -> "OffsetDateTimeType.offsetOf(" + varName + ")";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnAlias(int index) {
    return switch (index) {
      case 0 -> "timestamp";
      case 1 -> "offset";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(Backend backend, int index, OffsetDateTime value) {
    if (value != null) {
      int offset = offsetOf(value);
      return switch (index) {
        case 0 -> Timestamp.valueOf(value.toLocalDateTime().minusSeconds(offset));
        case 1 -> offset;
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
      case 0 -> SqlType.TIMESTAMP;
      case 1 -> SqlType.INTEGER;
      default -> throw new IndexOutOfBoundsException();
    };
  }

  @Override
  public OffsetDateTime valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(OffsetDateTime object) {
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
          if (!str.isEmpty() && Character.isDigit(str.charAt(0))) {
            str = StringHelper.toDoubleQuotes(str);
          }
          yield "Timestamp.valueOf(" + str + ")";
        }
        case 1 -> str;
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    if (!str.isEmpty() && Character.isDigit(str.charAt(0))) {
      str = StringHelper.toDoubleQuotes(str);
    }
    return "OffsetDateTime.parse(" + str + ")";
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, OffsetDateTime object, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp;
    int offset;
    if (object == null) {
      if (mapNull) {
        timestamp = DateHelper.MIN_TIMESTAMP;
        offset = 0;
      }
      else {
        statement.setNull(pos, Types.TIMESTAMP);
        statement.setNull(pos + 1, Types.INTEGER);
        return new Object[]{null, null};
      }
    }
    else {
      offset = offsetOf(object);
      timestamp = Timestamp.valueOf(object.toLocalDateTime().minusSeconds(offset));
    }
    statement.setTimestamp(pos, timestamp);
    statement.setInt(pos + 1, offset);
    return new Object[] { timestamp, offset };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, OffsetDateTime object, int index, boolean mapNull, Integer size) throws SQLException {
    return switch (index) {

      case 0 -> {
        Timestamp timestamp;
        if (object == null) {
          if (mapNull) {
            timestamp = DateHelper.MIN_TIMESTAMP;
          }
          else {
            statement.setNull(pos, Types.TIMESTAMP);
            yield null;
          }
        }
        else {
          timestamp = timestampOf(object);
        }
        statement.setTimestamp(pos, timestamp);
        yield timestamp;
      }

      case 1 -> {
        int offset;
        if (object == null) {
          if (mapNull) {
            offset = 0;
          }
          else {
            statement.setNull(pos, Types.INTEGER);
            yield null;
          }
        }
        else {
          offset = offsetOf(object);
        }
        statement.setInt(pos, offset);
        yield offset;
      }

      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public OffsetDateTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(pos[0]);
    int offset = resultSet.getInt(pos[1]);
    if (resultSet.wasNull() ||
        mapNull && timestamp.getTime() == 0 && offset == 0) {
      return null;
    }
    return OffsetDateTime.of(timestamp.toLocalDateTime().plusSeconds(offset), ZoneOffset.ofTotalSeconds(offset));
  }

  /**
   * Converts a string to an offset date time.
   *
   * @param str the string
   * @return the date time
   * @throws BackendException if parsing failed
   */
  public OffsetDateTime parse(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.contains(".")) {
      try {
        return MS_TIMESTAMP_FORMATTER.parse(str, OffsetDateTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return TIMESTAMP_FORMATTER.parse(str, OffsetDateTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp from '" + str + "' failed", e);
      }
    }
  }

  /**
   * Converts an offset date time object to a string.
   *
   * @param dateTime the date time
   * @return the formatted string
   */
  public String format(OffsetDateTime dateTime) {
    int nanos = dateTime.getNano();
    if (nanos != 0) {
      return MS_TIMESTAMP_FORMATTER.format(dateTime);
    }
    return TIMESTAMP_FORMATTER.format(dateTime);
  }

}
