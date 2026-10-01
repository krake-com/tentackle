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

package org.tentackle.sql.metadata;

import java.sql.DatabaseMetaData;
import java.util.Locale;

/**
 * Describes the table name and some major properties for an existing database table.
 */
public class DatabaseMetaDataTableHeader {

  private final DatabaseMetaData metaData;
  private final String catalog;
  private final String schema;
  private final String name;
  private final String type;
  private final String comment;

  /**
   * Creates a table header.
   *
   * @param metaData the database metadata root object
   * @param catalog the catalog name
   * @param schema the schema name
   * @param name the table name
   * @param type the table type
   * @param comment the table remarks
   */
  public DatabaseMetaDataTableHeader(DatabaseMetaData metaData, String catalog, String schema, String name, String type, String comment) {
    this.metaData = metaData;
    this.catalog = catalog == null ? null : catalog.toLowerCase(Locale.ROOT);
    this.schema = schema == null ? null : schema.toLowerCase(Locale.ROOT);
    this.name = name.toLowerCase(Locale.ROOT);
    this.type = type == null ? null : type.toUpperCase(Locale.ROOT);
    this.comment = comment;
  }

  /**
   * Returns whether this is a user-defined table.
   *
   * @return true if standard table (type TABLE, BASE TABLE, null or empty)
   */
  public boolean isUserTable() {
    // some drivers (e.g. H2 >= 2.x) report the ANSI term "BASE TABLE" instead of "TABLE"
    return type == null || type.isEmpty() || "TABLE".equals(type) || "BASE TABLE".equals(type);
  }

  /**
   * Returns whether this is a reserved table.
   *
   * @return true if reserved table
   */
  public boolean isReserved() {
    return isUserTable() && MetaDataUtilities.getInstance().isReservedTable(name);
  }

  /**
   * Gets the metadata root object this table header was retrieved from.
   *
   * @return the metadata, never null
   */
  public DatabaseMetaData getMetaData() {
    return metaData;
  }

  /**
   * The optional catalog.
   *
   * @return the catalog, may be null
   */
  public String getCatalog() {
    return catalog;
  }

  /**
   * The optional schema.
   *
   * @return the schema, may be null
   */
  public String getSchema() {
    return schema;
  }

  /**
   * Gets the table's name.
   *
   * @return the table name, never null
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the table type.
   *
   * @return the type in uppercase, such as TABLE or VIEW, null if unknown
   */
  public String getType() {
    return type;
  }

  /**
   * The optional comment.
   *
   * @return the comment, may be null
   */
  public String getComment() {
    return comment;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (!isUserTable()) {
      buf.append(type).append(' ');
    }
    if (schema != null && !schema.isEmpty()) {
      buf.append(schema).append('.');
    }
    buf.append(name);
    if (comment != null && !comment.isEmpty()) {
      buf.append(" (").append(comment).append(")");
    }
    return buf.toString();
  }

}
