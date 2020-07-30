/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The whole metadata for the connected database.
 *
 * @author harald
 */
public class ModelMetaData {

  private final Backend backend;                      // the backend
  private final DatabaseMetaData[] metaData;          // the connection's meta data
  private final Map<String, TableMetaData> tableMap;  // the tables of the model <modelname : metadata>
  private final String[] schemas;                     // optional schemas
  private final Properties properties;                // migration properties

  private List<DatabaseMetaDataTableHeader> metaDataTableHeaderList;        // all table headers (names may be not unique!)
  private Map<String, DatabaseMetaDataTableHeader> metaDataTableHeaderMap;  // table header info (lazy)


  /**
   * Creates a database meta instance.
   *
   * @param backend the backend
   * @param metaData the connections meta data
   * @param schemas optional schemas, null if no schema check
   */
  public ModelMetaData(Backend backend, DatabaseMetaData[] metaData, String[] schemas) {
    this.backend = backend;
    this.metaData = metaData;
    this.schemas = schemas;

    tableMap = new TreeMap<>();
    properties = new Properties();
  }

  /**
   * Gets the migration properties.<br>
   * Used to remember settings of the migration process.
   *
   * @return the properties
   */
  public Properties getProperties() {
    return properties;
  }

  /**
   * Gets the header info of all tables in the database.
   *
   * @return the table headers
   * @throws SQLException if cannot retrieve meta data from database
   */
  public List<DatabaseMetaDataTableHeader> getAllTablesInDatabase() throws SQLException {
    getMetaDataTableHeaderMap();
    return new ArrayList<>(metaDataTableHeaderList);
  }

  /**
   * Gets the header info for given tablename.
   *
   * @param tableName the tablename
   * @return the header info, null if no such table in database
   * @throws SQLException if cannot retrieve meta data from database
   */
  public DatabaseMetaDataTableHeader getTableInDatabase(String tableName) throws SQLException {
    return getMetaDataTableHeaderMap().get(tableName.toLowerCase());
  }

  /**
   * Adds a table to this model.<br>
   * If schemas are set, only tables belonging to one of given schemas will be appended.
   *
   * @param tableMetaData the table
   * @return true if added, false if wrong schema
   */
  public boolean addTableMetaData(TableMetaData tableMetaData) {
    boolean add = false;
    if (schemas != null) {
      for (String schema: schemas) {
        if (tableMetaData.getSchemaName() != null && tableMetaData.getSchemaName().equalsIgnoreCase(schema)) {
          add = true;
          break;
        }
      }
    }
    else  {
      add = true;
    }

    if (add) {
      tableMap.put(tableMetaData.getModelTableName(), tableMetaData);
    }
    return add;
  }

  /**
   * Gets the tables of this model.
   *
   * @return the tables
   */
  public Collection<TableMetaData> getTables() {
    return tableMap.values();
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Gets the valid schemas.
   *
   * @return the optional schemas, null if no schema check
   */
  public String[] getSchemas() {
    return schemas;
  }

  /**
   * Gets the JDBC-connection's meta data.<br>
   * May be more than one if different schemas and schema-option was given in {@link BackendInfo}.
   *
   * @return the meta data
   */
  public DatabaseMetaData[] getMetaData() {
    return metaData;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (TableMetaData table: getTables()) {
      buf.append("\n");
      buf.append(table);
    }
    return buf.toString();
  }


  private Map<String, DatabaseMetaDataTableHeader> getMetaDataTableHeaderMap() throws SQLException {
    if (metaDataTableHeaderMap == null) {
      metaDataTableHeaderList = new ArrayList<>();
      metaDataTableHeaderMap = new TreeMap<>();   // sorted by tablename
      for (DatabaseMetaData md: metaData) {
        List<DatabaseMetaDataTableHeader> tableHeaders = backend.getTableHeaders(md);
        metaDataTableHeaderList.addAll(tableHeaders);
        for (DatabaseMetaDataTableHeader header : tableHeaders) {
          metaDataTableHeaderMap.put(header.getName(), header);
        }
      }
    }
    return metaDataTableHeaderMap;
  }

}
