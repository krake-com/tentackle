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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datatype for {@link ZonedDateTime}.
 * <p>
 * Stored as 2 columns:
 * <ol>
 *   <li>the local date and time within the zone (not normalized)</li>
 *   <li>the zone id as a {@code VARCHAR(32)}</li>
 * </ol>
 * The MAPNULL option maps to {@link org.tentackle.common.DateHelper#MIN_TIMESTAMP} in zone GMT.<br>
 * This type is not generally sortable by the database in a meaningful manner.
 * It is sortable only within the same timezone.
 */
@Service(DataType.class)
public final class ZonedDateTimeType extends AbstractDateTimeType<ZonedDateTime> {

  /** GMT timezone name. */
  public static final String GMT = "GMT";

  /**
   * Returns whether the given zoneId string corresponds to GMT.
   *
   * @param zoneId the zoneId string
   * @return true if GMT timezone
   */
  public static boolean isGMT(String zoneId) {
    return zoneId == null || zoneId.isBlank() || GMT.equals(zoneId) || "UTC".equals(zoneId);
  }

  /**
   * Derives the timestamp from a ZonedDateTime value.
   *
   * @param value the ZonedDateTime value
   * @return the timestamp
   */
  public static Timestamp timestampOf(ZonedDateTime value) {
    return Timestamp.valueOf(value.toLocalDateTime());
  }

  /**
   * Derives the timezone name from a ZonedDateTime value.
   *
   * @param value the ZonedDateTime value
   * @return the zone name
   */
  public static String zoneOf(ZonedDateTime value) {
    return value.getZone().getId();
  }


  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV");
  private static final DateTimeFormatter MS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS VV");


  /**
   * Creates the datatype for {@link ZonedDateTime}.
   */
  public ZonedDateTimeType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "ZonedDateTime";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    return switch (index) {
      case 0 -> DateHelper.MIN_TIMESTAMP;
      case 1 -> GMT;
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public int getColumnCount(Backend backend) {
    return 2;
  }

  @Override
  public int[] getSortableColumns() {
    return null;
  }

  @Override
  public Optional<String> getCommentSuffix(Backend backend, int index) {
    return switch (index) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(" (TZ)");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    return switch (index) {
      case 0 -> "ZonedDateTimeType.timestampOf(" + varName + ")";
      case 1 -> "ZonedDateTimeType.zoneOf(" + varName + ")";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnAlias(int index) {
    return switch (index) {
      case 0 -> "timestamp";
      case 1 -> "zone";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(Backend backend, int index, ZonedDateTime value) {
    if (value != null) {
      return switch (index) {
        case 0 -> timestampOf(value);
        case 1 -> zoneOf(value);
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public int getSize(Backend backend, int index, Integer size) {
    return switch (index) {
      case 0 -> super.getSize(backend, index, size);
      case 1 -> 32;
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
      case 1 -> SqlType.VARCHAR;
      default -> throw new IndexOutOfBoundsException();
    };
  }

  @Override
  public ZonedDateTime valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(ZonedDateTime object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public String toLiteral(String str, Integer index) {
    if (!str.isEmpty() && str.charAt(0) != '\'') {
      str = "'" + str + "'";
    }
    return str;
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if (index != null) {
      if ("null".equals(str)) {
        return str;
      }
      if (!str.isEmpty() && Character.isDigit(str.charAt(0))) {
        str = StringHelper.toDoubleQuotes(str);
      }
      return switch (index) {
        case 0 -> "Timestamp.valueOf(" + str + ")";
        case 1 -> str;
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    if (!str.isEmpty() && Character.isDigit(str.charAt(0))) {
      str = StringHelper.toDoubleQuotes(str);
    }
    return "ZonedDateTime.parse(" + str + ")";
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, ZonedDateTime object, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp;
    String zoneId;
    if (object == null) {
      if (mapNull) {
        timestamp = DateHelper.MIN_TIMESTAMP;
        zoneId = GMT;
      }
      else {
        statement.setNull(pos, Types.TIMESTAMP);
        statement.setNull(pos + 1, Types.VARCHAR);
        return new Object[]{null, null};
      }
    }
    else {
      timestamp = timestampOf(object);
      zoneId = zoneOf(object);
    }
    statement.setTimestamp(pos, timestamp);
    statement.setString(pos + 1, zoneId);
    return new Object[] { timestamp, zoneId };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, ZonedDateTime object, int index, boolean mapNull, Integer size) throws SQLException {
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
        String zoneId;
        if (object == null) {
          if (mapNull) {
            zoneId = GMT;
          }
          else {
            statement.setNull(pos, Types.VARCHAR);
            yield null;
          }
        }
        else {
          zoneId = zoneOf(object);
        }
        statement.setString(pos, zoneId);
        yield zoneId;
      }

      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public ZonedDateTime get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(pos[0]);
    String zoneId = resultSet.getString(pos[1]);
    if (resultSet.wasNull() ||
        mapNull && timestamp.getTime() == 0 && isGMT(zoneId)) {
      return null;
    }
    return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.of(zoneId));
  }

  /**
   * Converts a string to a zoned date time.
   *
   * @param str the string
   * @return the date time
   * @throws BackendException if parsing failed
   */
  public ZonedDateTime parse(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.contains(".")) {
      try {
        return MS_TIMESTAMP_FORMATTER.parse(str, ZonedDateTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return TIMESTAMP_FORMATTER.parse(str, ZonedDateTime::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing timestamp from '" + str + "' failed", e);
      }
    }
  }

  /**
   * Converts a zoned date time object to a string.
   *
   * @param dateTime the date time
   * @return the formatted string
   */
  public String format(ZonedDateTime dateTime) {
    int nanos = dateTime.getNano();
    if (nanos != 0) {
      return MS_TIMESTAMP_FORMATTER.format(dateTime);
    }
    return TIMESTAMP_FORMATTER.format(dateTime);
  }

}
