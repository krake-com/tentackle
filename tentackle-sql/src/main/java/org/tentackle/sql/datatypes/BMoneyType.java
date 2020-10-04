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

/**
 * Datatype for {@link BMoney}.
 * <p>
 * Since this is a predefined type, the methods {@link #set} and {@link #get} are not used by the framework
 * and only provided for completeness.
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
  public SqlType getSqlType(int index) {
    switch (index) {
      case 0: return SqlType.DOUBLE;
      case 1: return SqlType.SMALLINT;
    }
    throw new IndexOutOfBoundsException();
  }

  @Override
  public boolean isScaleInSecondColumn() {
    return true;
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
  public BMoney get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    double value = resultSet.getDouble(pos[0]);
    return resultSet.wasNull() ? null : new BMoney (value, resultSet.getInt(pos[1]));
  }

}
