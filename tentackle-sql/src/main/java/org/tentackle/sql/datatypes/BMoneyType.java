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

import org.tentackle.common.BMoney;
import org.tentackle.common.Service;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * Datatype for {@link BMoney}.<br>
 * Stored as two columns:
 * <ol>
 *   <li>the value in double precision</li>
 *   <li>the scale</li>
 * </ol>
 * This type is sortable by the first column.
 */
@Service(DataType.class)
public class BMoneyType extends AbstractDataType<BMoney> {

  @Override
  public String getJavaType() {
    return "BMoney";
  }

  @Override
  public boolean isNumeric() {
    return true;
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Optional<String> getCommentSuffix(int index) {
    return switch (index) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(" (scale)");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    return switch (index) {
      case 0 -> SqlType.DOUBLE;
      case 1 -> SqlType.SMALLINT;
      default -> throw new IndexOutOfBoundsException(index);
    };
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
  public Object getColumnValue(int index, BMoney value) {
    if (value != null) {
      return switch (index) {
        case 0 -> value.doubleValue();
        case 1 -> value.scale();
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    return switch (index) {
      case 0 -> varName + ".doubleValue()";
      case 1 -> "(short) " + varName + ".scale()";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnAlias(int index) {
    return switch (index) {
      case 0 -> "doubleValue";
      case 1 -> "scale";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public BMoney valueOf(String str) {
    return new BMoney(new BigDecimal(StringHelper.parseString(str)));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, BMoney object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.DOUBLE);
      statement.setNull(pos + 1, Types.SMALLINT);
      return new Object[] { null, null };
    }
    double d = object.doubleValue();
    short s = (short) object.scale();
    statement.setDouble(pos, d);
    statement.setShort(pos + 1, s);
    return new Object[] { d, s };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, BMoney object, int index, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      switch (index) {
        case 0:
          statement.setNull(pos, Types.DOUBLE);
          return null;
        case 1:
          statement.setNull(pos, Types.SMALLINT);
          return null;
      }
    }
    else {
      switch (index) {
        case 0:
          double d = object.doubleValue();
          statement.setDouble(pos, d);
          return d;
        case 1:
          short s = (short) object.scale();
          statement.setShort(pos, s);
          return s;
      }
    }
    throw new IndexOutOfBoundsException(index);
  }

  @Override
  public BMoney get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    double value = resultSet.getDouble(pos[0]);
    return resultSet.wasNull() ? null : new BMoney (value, resultSet.getInt(pos[1]));
  }

}
