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

import org.tentackle.common.Binary;
import org.tentackle.common.Service;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.io.IOException;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Datatype for {@link Binary}.
 */
@Service(DataType.class)
public class BinaryType extends AbstractDataType<Binary<? extends Serializable>> {

  /**
   * Creates the datatype for {@link Binary}.
   */
  public BinaryType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "Binary";
  }

  @Override
  public boolean isJavaTypeGenerified() {
    return true;
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.BLOB;
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public Binary<? extends Serializable> valueOf(String str) {
    throw new BackendException("cannot convert a String to a Binary");
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, Binary<? extends Serializable> object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.LONGVARBINARY);
    }
    else {
      // notice: setBlob would create a large object on backends mapping BLOB to a plain binary
      // type such as BYTEA (postgres), BYTE (informix) or VARBINARY (mssql)
      statement.setBinaryStream(pos, object.getInputStream(), object.getLength());
    }
    return new Object[] { object };
  }

  @Override
  public Binary<? extends Serializable> get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    try {
      return Binary.create(resultSet.getBinaryStream(pos[0]), size == null ? 0 : size, true);
    }
    catch (IOException exception) {
      throw new SQLException("could not deserialize Binary", exception);
    }
  }
}
