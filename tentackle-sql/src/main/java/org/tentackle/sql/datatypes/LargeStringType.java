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
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Datatype for the large variant of {@link String}.
 * <p>
 * Notice that there are two types of strings. This one is for the SQL type <code>CLOB</code>.
 */
@Service(DataType.class)
public final class LargeStringType extends StringType {

  /**
   * Large string variant.<br>
   * For example, to distinguish standard VARCHAR Strings from CLOBs.
   */
  public static final String VARIANT = "LargeString";


  /**
   * Creates the datatype for the large variant of {@link String}.
   */
  public LargeStringType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getVariant() {
    return VARIANT;
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.CLOB;
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, String object, boolean mapNull, Integer size) throws SQLException {
    if (backend.isClobSupported()) {
      if (object == null && mapNull) {
        object = backend.getEmptyString();
      }
      if (object != null) {
        Clob clob = statement.getConnection().createClob();
        clob.setString(1, object);
        statement.setClob(pos, clob);
      }
      else {
        statement.setNull(pos, Types.CLOB);
      }
      return new Object[] { object };
    }
    return super.set(backend, statement, pos, object, mapNull, size);
  }

  @Override
  public String get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    if (backend.isClobSupported()) {
      Clob clob = resultSet.getClob(pos[0]);
      String str = clob == null ? null : clob.getSubString(1, (int) clob.length());
      if (mapNull && str != null && str.equals(backend.getEmptyString())) {
        return null;
      }
      return str;
    }
    return super.get(backend, resultSet, pos, mapNull, size);
  }

}
