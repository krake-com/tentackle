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

import org.tentackle.common.I18NText;
import org.tentackle.common.ParameterString;
import org.tentackle.common.Service;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.util.Optional;

/**
 * Datatype for {@link I18NText}.<br>
 * Stored as one or two columns, depending on the backend.
 * <ul>
 *   <li>If the database supports very long strings as an extenstion to <code>VARCHAR</code>, like postgres, a single column is used.</li>
 *   <li>For all other databases, two columns are used:
 *    <ol>
 *      <li><code>VARCHAR(max): a string with maximum supported length</code></li>
 *      <li><code>CLOB: if the string is too large and would not fit into the first column</code></li>
 *    </ol>
 *   </li>
 * </ul>
 * This type is sortable by the first column.
 */
@Service(DataType.class)
public final class I18NTextType extends AbstractDataType<I18NText> {

  /**
   * Creates the datatype for {@link I18NText}.
   */
  public I18NTextType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "I18NText";
  }

  @Override
  public boolean isPredefined() {
    return false;
  }

  @Override
  public boolean isColumnCountBackendSpecific() {
    return true;
  }

  @Override
  public boolean isMapNullSupported() {
    return false;
  }

  @Override
  public boolean isLiteralSupported(Integer index) {
    return false;
  }

  @Override
  public int getColumnCount(Backend backend) {
    return backend.getMaxSize(SqlType.VARCHAR) <= 0 ? 1 : 2;
  }

  @Override
  public Optional<String> getCommentSuffix(Backend backend, int index) {
    int maxSize = backend.getMaxSize(SqlType.VARCHAR);
    if (maxSize > 0) {
      return switch (index) {
        case 0 -> Optional.empty();
        case 1 -> Optional.of(" (L > " + maxSize + ")");
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return Optional.empty();
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (getColumnCount(backend) == 2) {
      if (!backend.isClobSupported()) {
        throw new BackendException(backend + " does not support the SQL type CLOB");
      }
      return switch (index) {
        case 0 -> SqlType.VARCHAR;
        case 1 -> SqlType.CLOB;
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return SqlType.VARCHAR;
  }

  @Override
  public int getSize(Backend backend, int index, Integer size) {
    if (getColumnCount(backend) == 2) {
      return switch (index) {
        case 0 -> super.getSize(backend, index, size);
        case 1 -> 0;    // CLOB has no size
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return super.getSize(backend, index, size);
  }

  @Override
  public int getScale(Backend backend, int index, Integer scale) {
    return 0;
  }

  @Override
  public Object getColumnValue(Backend backend, int index, I18NText value) {
    if (value != null) {
      String s = value.toParameterString().toString();
      int maxSize = backend.getMaxSize(SqlType.VARCHAR);
      if (maxSize > 0) {
        return switch (index) {
          case 0 -> s.length() > maxSize ? null : s;
          case 1 -> s.length() > maxSize ? s : null;
          default -> throw new IndexOutOfBoundsException(index);
        };
      }
      return s;
    }
    return null;
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    throw new BackendException("getColumnGetter not applicable for backend-specific datatype " + this);
  }

  @Override
  public String getColumnAlias(int index) {
    throw new BackendException("getColumnAlias not applicable for backend-specific datatype " + this);
  }

  @Override
  public I18NText valueOf(String str) {
    return I18NText.valueOf(str);
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, I18NText object, boolean mapNull, Integer size) throws SQLException {
    int maxSize = backend.getMaxSize(SqlType.VARCHAR);
    if (object == null) {
      statement.setNull(pos, Types.VARCHAR);
      if (maxSize > 0) {
        statement.setNull(pos + 1, Types.CLOB);
        return new Object[]{null, null};
      }
      return new Object[]{null};
    }

    String s = object.toParameterString().toString();
    if (maxSize > 0) {
      if (s.length() > maxSize) {
        statement.setNull(pos, Types.VARCHAR);
        Clob clob = statement.getConnection().createClob();
        clob.setString(1, s);
        statement.setClob(pos + 1, clob);
        return new Object[]{null, clob};
      }
      statement.setString(pos, s);
      statement.setNull(pos + 1, Types.CLOB);
      return new Object[]{s, null};
    }
    statement.setString(pos, s);
    return new Object[]{s};
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, I18NText object, int index, boolean mapNull, Integer size) throws SQLException {
    throw new BackendException("method not applicable for backend-specific datatype " + this);
  }

  @Override
  public I18NText get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    String s = resultSet.getString(pos[0]);
    if (s == null) {
      if (getColumnCount(backend) == 2) {
        Clob clob = resultSet.getClob(pos[1]);
        s = resultSet.wasNull() ? null : clob.getSubString(1, (int) clob.length());
      }
    }
    if (s != null) {
      try {
        return new I18NText(new ParameterString(s));
      }
      catch (ParseException px) {
        throw new BackendException("malformed I18NText contents", px);
      }
    }
    return null;
  }

}
