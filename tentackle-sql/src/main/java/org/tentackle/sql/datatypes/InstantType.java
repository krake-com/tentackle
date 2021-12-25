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
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datatype for {@link Instant}.
 * <p>
 * Stored as 2 columns:
 * <ol>
 *   <li>the epochal seconds since 1970-01-01 00:00:00 GMT</li>
 *   <li>the nanoseconds</li>
 * </ol>
 * The {@code [MAPNULL]} option maps null values to {@code 0s/0ns}.<br>
 * This type is sortable by the first column followed by the second.
 */
@Service(DataType.class)
public class InstantType extends AbstractDateTimeType<Instant> {

  private static final int[] SORTABLE_COLUMNS = { 0, 1 };

  // timestamp formats withZone necessary for Instant
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter MS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter NANO_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn").withZone(ZoneId.systemDefault());

  @Override
  public String getJavaType() {
    return "Instant";
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
    return SORTABLE_COLUMNS;
  }

  @Override
  public Optional<String> getCommentSuffix(int index) {
    return switch (index) {
      case 0 -> Optional.of(" [s]");
      case 1 -> Optional.of(" [ns]");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnGetter(int index) {
    return switch (index) {
      case 0 -> "getEpochSecond";
      case 1 -> "getNano";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public Object getColumnValue(int index, Instant value) {
    if (value != null) {
      return switch (index) {
        case 0 -> value.getEpochSecond();
        case 1 -> value.getNano();
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
      case 0 -> SqlType.BIGINT;
      case 1 -> SqlType.INTEGER;
      default -> throw new IndexOutOfBoundsException();
    };
  }

  @Override
  public Instant valueOf(String str) {
    return parse(str);
  }

  @Override
  public String toString(Instant object) {
    return StringHelper.toParsableString(format(object));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Instant object, boolean mapNull, Integer size) throws SQLException {
    Long seconds;
    int nanos;
    if (object == null) {
      seconds = mapNull ? 0L : null;
      nanos = 0;
    }
    else {
      seconds = object.getEpochSecond();
      nanos = object.getNano();
    }

    if (seconds == null) {
      statement.setNull(pos, Types.BIGINT);
      statement.setNull(pos + 1, Types.INTEGER);
      return new Object[] { null, null };
    }
    statement.setLong(pos, seconds);
    statement.setInt(pos + 1, nanos);
    return new Object[] { seconds, nanos };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, Instant object, int index, boolean mapNull, Integer size) throws SQLException {
    Long seconds;
    int nanos;
    if (object == null) {
      seconds = mapNull ? 0L : null;
      nanos = 0;
    }
    else {
      seconds = object.getEpochSecond();
      nanos = object.getNano();
    }

    if (seconds == null) {
      switch (index) {
        case 0:
          statement.setNull(pos, Types.BIGINT);
          return null;
        case 1:
          statement.setNull(pos, Types.INTEGER);
          return null;
      }
    }
    else {
      switch (index) {
        case 0:
          statement.setLong(pos, seconds);
          return seconds;
        case 1:
          statement.setInt(pos, nanos);
          return nanos;
      }
    }
    throw new IndexOutOfBoundsException(index);
  }

  @Override
  public Instant get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    long seconds = resultSet.getLong(pos[0]);
    int nanos = resultSet.getInt(pos[1]);
    if (resultSet.wasNull() ||
        mapNull && seconds == 0 && nanos == 0) {
      return null;
    }
    return Instant.ofEpochSecond(seconds, nanos);
  }


  protected Instant parse(String str) throws BackendException {
    str = StringHelper.parseString(str);
    if (str.length() > 9 && str.charAt(str.length() - 10) == '.') {
      try {
        return NANO_TIMESTAMP_FORMATTER.parse(str, Instant::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing instant with ns from '" + str + "' failed", e);
      }
    }
    else if (str.length() > 3 && str.charAt(str.length() - 4) == '.') {
      try {
        return MS_TIMESTAMP_FORMATTER.parse(str, Instant::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing instant with ms from '" + str + "' failed", e);
      }
    }
    else {
      try {
        return TIMESTAMP_FORMATTER.parse(str, Instant::from);
      }
      catch (DateTimeParseException e) {
        throw new BackendException("parsing instant from '" + str + "' failed", e);
      }
    }
  }

  protected String format(Instant instant) {
    int nanos = instant.getNano();
    if (nanos != 0) {
      if (nanos % 1000000 == 0) {
        return MS_TIMESTAMP_FORMATTER.format(instant);
      }
      return NANO_TIMESTAMP_FORMATTER.format(instant);
    }
    return TIMESTAMP_FORMATTER.format(instant);
  }

}
