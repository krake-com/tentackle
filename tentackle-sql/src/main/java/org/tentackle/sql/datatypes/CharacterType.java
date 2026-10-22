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
import org.tentackle.common.TentackleRuntimeException;
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
 * Datatype for {@link Character}.
 * <p>
 * The MAPNULL option maps to a blank.
 */
@Service(DataType.class)
public sealed class CharacterType extends AbstractDataType<Character> permits CharacterPrimitiveType {

  /**
   * Creates the datatype for {@link Character}.
   */
  public CharacterType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "Character";
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public Character getMappedNullValue(Backend backend, int index) {
    return ' ';
  }

  @Override
  public Optional<DataType<?>> toPrimitive() {
    return Optional.of(DataTypeFactory.getInstance().get("char"));
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.CHAR;
  }

  @Override
  public Character valueOf(String str) {
    String cstr = StringHelper.parseString(str);
    if (cstr.length() != 1) {
      throw new TentackleRuntimeException("character must be of length 1: " + cstr);
    }
    return cstr.charAt(0);
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if ("'".equals(str)) {
      return "'\\''";      // single quote
    }
    if ("\\".equals(str)) {
      return "'\\\\'";    // single backslash
    }
    if (str.isEmpty()) {
      return "0";         // numeric 0
    }
    if (str.length() == 1 || str.length() == 2 && str.charAt(0) == '\\') {
      return "'" + str + "'";   // standard character (not a single quote and not a backslash) or a quoted char such as "\n"
    }
    return str;
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Character object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = ' ';
    }
    if (object == null) {
      statement.setNull(pos, Types.CHAR);
    }
    else {
      statement.setString(pos, String.valueOf(object));
    }
    return new Object[] { object };
  }

  @Override
  public Character get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    String val = resultSet.getString(pos[0]);
    char value = val == null || val.isEmpty() ? 0 : val.charAt(0);
    return resultSet.wasNull() ? null : (mapNull && value == ' ' ? null : value);
  }

}
