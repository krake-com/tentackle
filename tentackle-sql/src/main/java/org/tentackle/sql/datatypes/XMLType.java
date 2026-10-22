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
import java.sql.SQLXML;
import java.sql.Types;

/**
 * Datatype for the XML variant of {@link String}.
 * <p>
 * Bridges a {@link String} attribute to a native XML column via the JDBC {@link SQLXML}
 * interface where the backend supports it ({@link Backend#isSqlxmlSupported()}), falling back to
 * plain VARCHAR/CLOB otherwise. The value exposed to the application is always a plain, serializable
 * {@link String} — the {@link SQLXML} object itself is never handed out, since it is only valid for
 * the transaction that created it.
 */
@Service(DataType.class)
public final class XMLType extends StringType {

  /**
   * XML variant.<br>
   * Distinguishes a {@code String} bridged through the JDBC {@link java.sql.SQLXML} interface to a
   * native XML column from a plain VARCHAR or CLOB-backed one.
   */
  public static final String VARIANT = "Sqlxml";


  /**
   * Creates the datatype for the XML variant of {@link String}.
   */
  public XMLType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getVariant() {
    return VARIANT;
  }

  @Override
  public String getModelType() {
    return "XML";
  }

  @Override
  public SqlType getSqlType(Backend backend, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return SqlType.XML;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Unlike a plain string, an XML document has no empty representation: the empty string is not
   * a well-formed XML document and a backend with a native type rejects it.
   */
  @Override
  public boolean isMapNullSupported() {
    return false;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    throw new BackendException("MAPNULL is not supported for " + this +
                               ": the empty string is not an XML document");
  }

  /**
   * {@inheritDoc}
   * <p>
   * Databases provide no comparator for XML documents: comparing or ordering by an XML
   * column is meaningless, and meaningful queries require the backend's native XML query
   * language instead.
   */
  @Override
  public boolean isComparable() {
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Not sortable, since not comparable.
   *
   * @return null
   */
  @Override
  public int[] getSortableColumns() {
    return null;
  }

  @Override
  public Object[] set(Backend backend, PreparedStatement statement, int pos, String object, boolean mapNull, Integer size) throws SQLException {
    if (backend.isSqlxmlSupported()) {
      if (object != null) {
        SQLXML sqlxml = statement.getConnection().createSQLXML();
        sqlxml.setString(object);
        statement.setSQLXML(pos, sqlxml);
      }
      else {
        statement.setNull(pos, Types.SQLXML);
      }
      return new Object[] { object };
    }
    // mapNull is never true for this type (see isMapNullSupported) and passing it on would
    // silently write the empty string into the emulating column
    return super.set(backend, statement, pos, object, false, size);
  }

  @Override
  public String get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException {
    if (backend.isSqlxmlSupported()) {
      SQLXML sqlxml = resultSet.getSQLXML(pos[0]);
      String str = null;
      if (sqlxml != null) {
        str = sqlxml.getString();
        sqlxml.free();
      }
      return str;
    }
    return super.get(backend, resultSet, pos, false, size);
  }

}
