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
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Datatype for {@code byte[]}.
 * <p>
 * Stores the raw bytes "as is", without the serialization overhead of {@link BinaryType}
 * or {@link TBinaryType}. Unlike those, an empty array is not treated as null.
 * <p>
 * Notice that arrays cannot be frozen. Hence, the type is not {@link #isMutable()} in the
 * sense of {@link org.tentackle.common.Freezable}, although the array contents may change.
 */
@Service(DataType.class)
public class ByteArrayType extends AbstractDataType<byte[]> {

  /**
   * The java type.
   */
  public static final String JAVA_TYPE = "byte[]";


  /**
   * Creates the datatype for {@code byte[]}.
   */
  public ByteArrayType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return JAVA_TYPE;
  }

  /**
   * {@inheritDoc}
   * <p>
   * {@code byte[]} is not a valid java identifier, hence the name of the JDBC methods
   * {@code getBytes}/{@code setBytes} is used, which also yields the constant {@code DT_BYTES}.
   */
  @Override
  public String getMethodTypeName() {
    return "Bytes";
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.BLOB;
  }

  @Override
  public byte[] valueOf(String str) {
    throw new BackendException("cannot convert a String to a byte[]");
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, byte[] object, boolean mapNull, Integer size) throws SQLException {
    if (object == null) {
      statement.setNull(pos, Types.LONGVARBINARY);
    }
    else {
      statement.setBytes(pos, object);
    }
    return new Object[] { object };
  }

  @Override
  public byte[] get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    return resultSet.getBytes(pos[0]);
  }
}
