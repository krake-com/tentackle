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
 *   <li>the timestamp</li>
 *   <li>the zone id string</li>
 * </ol>
 * The MAPNULL option maps to {@code 1970-01-01 00:00:00 GMT}.<br>
 * This type is not sortable by the database in a meaningful manner.
 */
@Service(DataType.class)
public class ZonedDateTimeType extends AbstractDateTimeType<ZonedDateTime> {

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


  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV");
  private static final DateTimeFormatter MS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS VV");


  @Override
  public String getJavaType() {
    return "ZonedDateTime";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
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
      case 1 -> Optional.of(" (TZ)");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnGetter(int index) {
    return switch (index) {
      case 0 -> "toLocalDateTime";
      case 1 -> "getZone";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(int index, ZonedDateTime value) {
    if (value != null) {
      return switch (index) {
        case 0 -> value.toLocalDateTime();
        case 1 -> value.getZone();
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public int getSize(int index, Integer size) {
    return switch (index) {
      case 0 -> super.getSize(index, size);
      case 1 -> 32;
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
      timestamp = Timestamp.valueOf(object.toLocalDateTime());
      zoneId = object.getZone().getId();
    }
    statement.setTimestamp(pos, timestamp);
    statement.setString(pos + 1, zoneId);
    return new Object[] { timestamp, zoneId };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, ZonedDateTime object, int index, boolean mapNull, Integer size) throws SQLException {
    Timestamp timestamp;
    String zoneId;
    if (object == null) {
      if (mapNull) {
        timestamp = DateHelper.MIN_TIMESTAMP;
        zoneId = GMT;
      }
      else {
        timestamp = null;
        zoneId = null;
      }
    }
    else {
      timestamp = Timestamp.valueOf(object.toLocalDateTime());
      zoneId = object.getZone().getId();
    }

    if (timestamp == null) {
      switch (index) {
        case 0:
          statement.setNull(pos, Types.TIMESTAMP);
          return null;
        case 1:
          statement.setNull(pos, Types.VARCHAR);
          return null;
      }
    }
    else {
      switch (index) {
        case 0:
          statement.setTimestamp(pos, timestamp);
          return timestamp;
        case 1:
          statement.setString(pos, zoneId);
          return zoneId;
      }
    }
    throw new IndexOutOfBoundsException(index);
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


  protected ZonedDateTime parse(String str) throws BackendException {
    // TODO with zone offset
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

  protected String format(ZonedDateTime dateTime) {
    int nanos = dateTime.getNano();
    if (nanos != 0) {
      return MS_TIMESTAMP_FORMATTER.format(dateTime);
    }
    return TIMESTAMP_FORMATTER.format(dateTime);
  }

}
