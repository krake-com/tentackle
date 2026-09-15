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

package org.tentackle.sql.backends;

import org.tentackle.common.Service;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Backend for CockroachDB.
 * <p>
 * CockroachDB speaks the PostgreSQL wire protocol and is accessed via the PostgreSQL JDBC driver.
 * Since the URL cannot tell both apart, this backend never matches a URL and must be selected by name
 * via the URL suffix {@code "|CockroachDB"}, for example:
 * <pre>
 *   jdbc:postgresql://localhost:26257/tentackle|CockroachDB
 * </pre>
 */
@Service(Backend.class)
public class CockroachDb extends Postgres {

  private static final String[] RESERVED_SCHEMAS = {
    "crdb_internal",
    "pg_extension"
  };


  /**
   * Creates the CockroachDB backend.
   */
  public CockroachDb() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return false;   // same URL as postgres -> select by name
  }

  @Override
  public String getName() {
    return "CockroachDB";
  }

  @Override
  public String getBackendId(Connection connection) {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SHOW session_id")) {
      if (rs.next()) {
        return "SID-" + rs.getString(1);
      }
      return null;
    }
    catch (SQLException ex) {
      throw new BackendException("cannot determine backend id", ex);
    }
  }

  @Override
  public boolean isReservedSchemaName(String name) {
    for (String reservedSchema: RESERVED_SCHEMAS) {
      if (reservedSchema.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return super.isReservedSchemaName(name);
  }

  @Override
  public boolean isSetConstraintsDeferredSupported() {
    return false;
  }

  @Override
  public String sqlSetConstraintsDeferred(boolean deferred) {
    throw new BackendException(this + " does not support deferred constraints");
  }

  @Override
  public void enrichIndexDeferrability(TableMetaData tableMetaData, Map<String, IndexMetaData> indexesByName,
                                       DatabaseMetaData metaData) {
    // no deferrable constraints at all
  }

  @Override
  public String sqlCreateSequenceComment(String name, String comment) {
    return "";    // sequences cannot be commented
  }

  @Override
  public String sqlCreateIndexComment(String tableName, String indexName, String comment) {
    StringBuilder buf = new StringBuilder("COMMENT ON INDEX ");
    buf.append(tableIndexName(tableName, indexName));
    buf.append(" IS ");
    if (comment == null) {
      buf.append("NULL");
    }
    else {
      buf.append(toQuotedString(comment));
    }
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  @Override
  public String sqlRenameIndex(String tableName, String oldIndexName, String newIndexName) {
    return  SQL_ALTER_INDEX + tableIndexName(tableName, oldIndexName) +
            " RENAME TO " +
            newIndexName +
            getStatementSeparator() + "\n";
  }

  @Override
  public String sqlDropIndex(String schemaName, String tableNameWithoutSchema, String indexName) {
    StringBuilder buf = new StringBuilder("DROP INDEX ");
    if (isDropIfExistsEnabled()) {
      buf.append(SQL_IF_EXISTS);
    }
    if (schemaName != null) {
      buf.append(schemaName);
      buf.append(".");
    }
    buf.append(tableIndexName(tableNameWithoutSchema, indexName));
    buf.append(getStatementSeparator()).append('\n');
    return buf.toString();
  }

  /**
   * Creates the qualified name of an index.<br>
   * Unlike postgres, the index names are unique per table only, not per schema.
   * Hence, an index is referred to as {@code table@index}.
   *
   * @param tableName the table name, optionally with a leading schema
   * @param indexName the index name, a leading schema is cut
   * @return the qualified index name
   */
  protected String tableIndexName(String tableName, String indexName) {
    int ndx = indexName.lastIndexOf('.');
    if (ndx >= 0) {
      indexName = indexName.substring(ndx + 1);
    }
    return tableName + "@" + indexName;
  }

}
