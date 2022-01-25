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
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * Datatype for {@link Float}.
 */
@Service(DataType.class)
public class FloatType extends AbstractNumberType<Float> {

  @Override
  public String getJavaType() {
    return "Float";
  }

  @Override
  public Optional<DataType<?>> toPrimitive() {
    return Optional.of(DataTypeFactory.getInstance().get("float"));
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.FLOAT;
  }

  @Override
  public Float valueOf(String str) {
    return Float.valueOf(StringHelper.parseString(str));
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Float object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.FLOAT);
    }
    else {
      statement.setFloat(pos, object);
    }
    return new Object[] { object };
  }

  @Override
  public Float get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    return resultSet.getFloat(pos[0]);
  }

}
