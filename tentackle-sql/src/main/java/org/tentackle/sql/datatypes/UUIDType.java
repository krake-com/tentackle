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
import org.tentackle.sql.SqlType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * Datatype for {@link UUID}.
 * <p>
 * For databases not supporting this type natively, it is mapped to VARCHAR(36).<br>
 * There are alternatives (storing in 2 longs or 16 bytes, etc...),
 * but manually written SQL queries may become rather tricky.
 * <p>
 * The type is not predefined, i.e. the persistence layer uses {@code setObject}/{@code getObject},
 * and it is not sortable by the database in a meaningful manner.
 * The MAPNULL option maps to the nil UUID.
 */
@Service(DataType.class)
public final class UUIDType extends AbstractDataType<UUID> {

  /** the NIL value. */
  public static final UUID NIL = new UUID(0, 0);

  /** the NIL string. */
  public static final String NIL_STR = NIL.toString();

  private static final int VARCHAR_LEN = 36;


  /**
   * Creates the datatype for {@link UUID}.
   */
  public UUIDType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "UUID";
  }

  @Override
  public int[] getSortableColumns() {
    return null;
  }

  @Override
  public boolean isMapNullSupported() {
    return true;
  }

  @Override
  public UUID getMappedNullValue(Backend backend, int index) {
    return NIL;
  }

  @Override
  public boolean isPredefined() {
    return false;   // NOT a predefined type -> via setObject/getObject!
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    if (backend.isUUIDSupported()) {
      return SqlType.UUID;
    }
    return SqlType.VARCHAR;
  }

  @Override
  public int getSize(Backend backend, int index, Integer size) {
    if (backend.isUUIDSupported()) {
      return super.getSize(backend, index, size);
    }
    return VARCHAR_LEN;
  }

  @Override
  public UUID valueOf(String str) {
    return UUID.fromString(str);
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if (!str.isEmpty() && str.charAt(0) != '"') {
      str = StringHelper.toDoubleQuotes(str);
    }
    return "UUID.fromString(" + str + ")";
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, UUID object, boolean mapNull, Integer size) throws SQLException {
    if (object == null && mapNull) {
      object = NIL;
    }
    if (backend.isUUIDSupported()) {
      if (object == null) {
        statement.setNull(pos, Types.JAVA_OBJECT);
      }
      else {
        statement.setObject(pos, object);
      }
    }
    else {
      if (object == null) {
        statement.setNull(pos, Types.VARCHAR);
      }
      else {
        statement.setString(pos, object.toString());
      }
    }
    return new Object[] { object };
  }

  @Override
  public UUID get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    UUID uuid;
    if (backend.isUUIDSupported()) {
      uuid = (UUID) resultSet.getObject(pos[0]);
      if (mapNull && NIL.equals(uuid)) {
        return null;
      }
    }
    else {
      String str = resultSet.getString(pos[0]);
      if (mapNull && NIL_STR.equals(str)) {
        return null;
      }
      uuid = str == null || str.isEmpty() ? null : UUID.fromString(str);
    }
    return uuid;
  }

}
