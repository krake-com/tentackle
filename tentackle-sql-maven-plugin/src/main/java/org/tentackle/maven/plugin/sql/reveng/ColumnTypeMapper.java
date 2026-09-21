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

package org.tentackle.maven.plugin.sql.reveng;

import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;

/**
 * Maps a database column's JDBC type to a Tentackle model attribute type declaration.
 * <p>
 * This is the reverse of what {@code DataType.getSqlType(Backend, int)} does for the forward
 * (model to database) direction; there is no such reverse lookup in {@code tentackle-sql}, so
 * this mapping is intentionally small and picks one reasonable default Java type per {@link SqlType}.
 * Where the SQL type has a matching Java primitive (int, long, boolean, ...), that primitive is chosen
 * for a {@code NOT NULL} column and the wrapper type for a nullable one, since the Tentackle model has
 * no other way to express column nullability. Types without a primitive equivalent (String, dates,
 * BigDecimal, ...) are always declared nullable; {@link ColumnType#nullabilityRepresentable()} tells the caller
 * whether the mapped type could reflect the column's actual {@code NOT NULL} constraint.
 *
 * @author harald
 */
public final class ColumnTypeMapper {

  private ColumnTypeMapper() {
    // no instances
  }

  /**
   * The result of mapping a column to a model attribute type.
   *
   * @param typeDeclaration the attribute-line type declaration, e.g. {@code String(60)} or {@code long}
   * @param nullabilityRepresentable whether the chosen type can express the column's {@code NOT NULL} constraint
   */
  public record ColumnType(String typeDeclaration, boolean nullabilityRepresentable) {
  }

  /**
   * Maps a column to a model attribute type.
   *
   * @param backend the backend the column was read from
   * @param column the column
   * @return the mapped type
   */
  public static ColumnType map(Backend backend, ColumnMetaData column) {
    SqlType[] possibleTypes = backend.jdbcTypeToSqlType(column.getType(), column.getTypeName(), column.getSize(), column.getScale());
    SqlType sqlType = possibleTypes.length > 0 ? possibleTypes[0] : SqlType.VARCHAR;
    boolean notNull = !column.isNullable();
    int size = column.getSize();
    int scale = column.getScale();

    return switch (sqlType) {
      case BIT -> new ColumnType(notNull ? "boolean" : "Boolean", true);
      case TINYINT -> new ColumnType(notNull ? "byte" : "Byte", true);
      case SMALLINT -> new ColumnType(notNull ? "short" : "Short", true);
      case INTEGER -> new ColumnType(notNull ? "int" : "Integer", true);
      case BIGINT -> new ColumnType(notNull ? "long" : "Long", true);
      case FLOAT -> new ColumnType(notNull ? "float" : "Float", true);
      case DOUBLE -> new ColumnType(notNull ? "double" : "Double", true);
      case CHAR -> size == 1 ? new ColumnType(notNull ? "char" : "Character", true)
                             : new ColumnType(sized("String", size, 0), false);
      case VARCHAR -> new ColumnType(sized("String", size, 0), false);
      case CLOB -> new ColumnType("String(0)", false);
      case DECIMAL -> new ColumnType(sized("BigDecimal", size, scale), false);
      case DATE -> new ColumnType("LocalDate", false);
      case TIME -> new ColumnType("LocalTime", false);
      case TIMESTAMP -> new ColumnType("LocalDateTime", false);
      case UUID -> new ColumnType("UUID", false);
      case BLOB -> new ColumnType("Binary<byte[]>", false);
      case XML -> new ColumnType("XML", false);
      case JSON -> new ColumnType("JSON", false);
      case JAVA_OBJECT -> new ColumnType(sized("String", size, 0), false);
    };
  }

  private static String sized(String javaType, int size, int scale) {
    if (size <= 0) {
      return javaType;
    }
    return scale > 0 ? javaType + "(" + size + "," + scale + ")" : javaType + "(" + size + ")";
  }

}
