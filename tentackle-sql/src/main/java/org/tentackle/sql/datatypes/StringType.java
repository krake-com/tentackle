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

/**
 * Datatype for {@link String}.
 * <p>
 * Notice that there are two types of strings. This one is for the SQL type <code>VARCHAR</code>.
 */
@Service(DataType.class)
public sealed class StringType extends AbstractDataType<String> permits LargeStringType, SQLXMLType {

  public StringType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "String";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    if (backend == null) {
      throw new BackendException("MAPNULL value is backend-specific for " + this);
    }
    return backend.getEmptyString();
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.VARCHAR;
  }

  @Override
  public String valueOf(String str) {
    return StringHelper.parseString(str);
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    return valueStringToCode(str);
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, String object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = backend.getEmptyString();
    }
    if (object == null) {
      statement.setNull(pos, Types.VARCHAR);
    }
    else {
      statement.setString(pos, object);
    }
    return new Object[] { object };
  }

  @Override
  public String get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    String str = resultSet.getString(pos[0]);
    if (mapNull && str != null && str.equals(backend.getEmptyString())) {
      return null;
    }
    return str;
  }

}
