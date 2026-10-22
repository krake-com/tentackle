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

import org.tentackle.common.DMoney;
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
 * Datatype for {@link DMoney}.<br>
 * Stored as two columns:
 * <ol>
 *   <li>the value as a decimal without scale</li>
 *   <li>the scale</li>
 * </ol>
 * This type is not sortable by the database in a meaningful manner.
 */
@Service(DataType.class)
public class DMoneyType extends AbstractDataType<DMoney> {

  /**
   * Creates the datatype for {@link DMoney}.
   */
  public DMoneyType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "DMoney";
  }

  @Override
  public boolean isNumeric() {
    return true;
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
      case 1 -> Optional.of(" (scale)");
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    return switch (index) {
      case 0 -> SqlType.DECIMAL;
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
  public Object getColumnValue(Backend backend, int index, DMoney value) {
    if (value != null) {
      return switch (index) {
        case 0 -> value.bigDecimalValue();
        case 1 -> value.scale();
        default -> throw new IndexOutOfBoundsException(index);
      };
    }
    return null;
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    return switch (index) {
      case 0 -> varName + ".bigDecimalValue()";
      case 1 -> "(short) " + varName + ".scale()";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public String getColumnAlias(int index) {
    return switch (index) {
      case 0 -> "bigDecimalValue";
      case 1 -> "scale";
      default -> throw new IndexOutOfBoundsException(index);
    };
  }

  @Override
  public DMoney valueOf(String str) {
    return new DMoney(new BigDecimal(StringHelper.parseString(str)));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, DMoney object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.DECIMAL);
      statement.setNull(pos + 1, Types.SMALLINT);
      return new Object[] { null, null };
    }
    short s = (short) object.scale();
    BigDecimal d = object.bigDecimalValue();
    statement.setBigDecimal(pos, d);
    statement.setShort(pos + 1, s);
    return new Object[] { d, s };
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, DMoney object, int index, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      switch (index) {
        case 0:
          statement.setNull(pos, Types.DECIMAL);
          return null;
        case 1:
          statement.setNull(pos, Types.SMALLINT);
          return null;
      }
    }
    else {
      switch (index) {
        case 0:
          BigDecimal d = object.bigDecimalValue();
          statement.setBigDecimal(pos, d);
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
  public DMoney get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    BigDecimal value = resultSet.getBigDecimal(pos[0]);
    return resultSet.wasNull() ? null : new DMoney (value.movePointLeft(resultSet.getInt(pos[1])));
  }

}
