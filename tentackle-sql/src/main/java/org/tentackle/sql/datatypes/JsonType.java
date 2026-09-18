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

/**
 * Datatype for the JSON variant of {@link String}.
 * <p>
 * Binds a {@link String} attribute to a native JSON column where the backend supports it
 * ({@link Backend#isJsonSupported()}), falling back to plain VARCHAR/CLOB otherwise. The value
 * exposed to the application is always a plain {@link String}: tentackle neither parses nor
 * validates it.
 * <p>
 * Unlike the {@link XMLType}, there is no JDBC binding for JSON at all, so how a string reaches
 * the column differs per backend — Postgres takes it as an object of type {@code OTHER}, H2 only
 * accepts the UTF-8 bytes, and mysql is happy with a plain string. The backend knows, the datatype
 * does not, hence {@link Backend#setJson} and {@link Backend#getJson}.
 * <p>
 * Notice that a backend with a native type may <em>normalize</em> the document: postgres' {@code jsonb}
 * and H2's {@code JSON} both drop insignificant whitespace, and {@code jsonb} additionally reorders
 * and de-duplicates the keys. The string read back is therefore semantically, but not necessarily
 * textually, the one that was written.
 */
@Service(DataType.class)
public final class JsonType extends StringType {

  /**
   * JSON variant.<br>
   * Distinguishes a {@code String} bound to a native JSON column from a plain VARCHAR or
   * CLOB-backed one. Unlike {@link XMLType#VARIANT}, JDBC provides no JSON binding of its own,
   * so how the string reaches the column is up to the backend.
   */
  public static final String VARIANT = "Json";


  /**
   * Creates the datatype for the JSON variant of {@link String}.
   */
  public JsonType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getVariant() {
    return VARIANT;
  }

  @Override
  public String getModelType() {
    return "JSON";
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.JSON;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Unlike a plain string, a JSON value has no empty representation: the empty string is not a
   * JSON document and a backend with a native type rejects it.
   */
  @Override
  public boolean isMapNullSupported() {
    return false;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    throw new BackendException("MAPNULL is not supported for " + this +
                               ": the empty string is not a JSON document");
  }

  /**
   * {@inheritDoc}
   * <p>
   * Databases provide no comparator for JSON documents: comparing or ordering by a JSON
   * column is meaningless, and meaningful queries require the backend's native JSON query
   * language instead.
   */
  @Override
  public boolean isComparable() {
    return false;
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, String object, boolean mapNull, Integer size) throws SQLException {
    if (backend.isJsonSupported()) {
      backend.setJson(statement, pos, object);
      return new Object[] { object };
    }
    // mapNull is never true for this type (see isMapNullSupported) and passing it on would
    // silently write the empty string into the emulating column
    return super.set(backend, statement, pos, object, false, size);
  }

  @Override
  public String get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    if (backend.isJsonSupported()) {
      return backend.getJson(resultSet, pos[0]);
    }
    return super.get(backend, resultSet, pos, false, size);
  }

}
