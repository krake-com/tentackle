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

import org.testng.annotations.Test;

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.FakeJdbc;
import org.tentackle.sql.backends.Db2;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Testing the extraction of the table metadata from the database metadata.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TableMetaDataTest {

  private static final String TABLE_NAME = "blah";

  /** the standard metadata of a table with 2 columns, 1 index and 1 single-column foreign key. */
  private static final FakeJdbc.RowSource STANDARD = (method, args) -> switch (method) {
    case "getTables" -> List.of(FakeJdbc.row("TABLE_SCHEM", null,
                                             "TABLE_NAME", TABLE_NAME,
                                             "REMARKS", "the blah table"));
    case "getColumns" -> List.of(column(TABLE_NAME, "id"), column(TABLE_NAME, "name"));
    case "getIndexInfo" -> List.of(indexColumn("blah_name", "name", 1));
    case "getImportedKeys" -> List.of(foreignKeyColumn("blah_owner_fk", 1, "owner_id", "id"));
    default -> List.of();
  };


  @Test
  public void testSetup() throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData(STANDARD);
    TableMetaData table = createTableMetaData(metaData);

    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertEquals(table.getTableName(), TABLE_NAME);
    assertEquals(table.getComment(), "the blah table");
    assertEquals(table.getColumns().size(), 2);
    assertNotNull(table.getColumnByName("name"));
    assertEquals(table.getIndexes().size(), 1);
    assertEquals(table.getForeignKeys().size(), 1);
  }

  /**
   * The caller retries with different name patterns and metadata sources until the table
   * is found, hence the setup must not accumulate anything from a previous invocation.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testSetupIsIdempotent() throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData(STANDARD);
    TableMetaData table = createTableMetaData(metaData);

    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);
    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertEquals(table.getColumns().size(), 2, "columns must not accumulate");
    assertEquals(table.getIndexes().size(), 1, "indexes must not accumulate");
    assertEquals(table.getForeignKeys().size(), 1, "foreign keys must not accumulate");
  }

  /**
   * A setup failing halfway through must not leave its columns behind.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testFailedSetupLeavesNoResidue() throws SQLException {
    // the second column claims to belong to another table -> the setup fails after the first one was added
    boolean[] consistent = { false };
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) ->
        "getColumns".equals(method) && !consistent[0] ?
          List.of(column(TABLE_NAME, "id"), column("other", "name")) :
          STANDARD.getRows(method, args));
    TableMetaData table = createTableMetaData(metaData);

    expectThrows(BackendException.class, () -> table.setupTableFromMetaData(metaData, null, null, TABLE_NAME));

    consistent[0] = true;
    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertEquals(table.getColumns().size(), 2, "the column of the failed attempt must be gone");
  }

  /**
   * The same, but through the retry logic of the backend.
   */
  @Test
  public void testRetryWithUppercasePatternLeavesNoResidue() {
    // the backend knows the table by its uppercase name only, but reports it in lowercase
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) -> {
      String pattern = (String) args[2];
      if ("getColumns".equals(method)) {
        return TABLE_NAME.equals(pattern) ?
            List.of(column(TABLE_NAME, "id"), column("other", "name")) :   // inconsistent
            List.of(column(pattern, "id"), column(pattern, "name"));
      }
      if ("getTables".equals(method)) {
        return List.of(FakeJdbc.row("TABLE_SCHEM", null, "TABLE_NAME", pattern, "REMARKS", null));
      }
      return STANDARD.getRows(method, args);
    });
    Backend backend = new Db2();
    ModelMetaData modelMetaData = new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null);

    TableMetaData table = backend.getTableMetaData(modelMetaData, TABLE_NAME);

    assertNotNull(table, "the table is known by its uppercase name");
    assertEquals(table.getTableName(), TABLE_NAME.toUpperCase(java.util.Locale.ROOT));
    assertEquals(table.getColumns().size(), 2, "the column of the failed attempt must be gone");
  }

  /**
   * A foreign key spanning more than one column is reported as one row per column.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testCompositeForeignKey() throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) ->
        "getImportedKeys".equals(method) ?
          List.of(foreignKeyColumn("blah_owner_fk", 1, "owner_id", "id"),
                  foreignKeyColumn("blah_owner_fk", 2, "owner_serial", "serial")) :
          STANDARD.getRows(method, args));
    TableMetaData table = createTableMetaData(metaData);

    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertEquals(table.getForeignKeys().size(), 1);
    ForeignKeyMetaData foreignKey = table.getForeignKeys().iterator().next();
    assertEquals(foreignKey.getForeignKeyName(), "blah_owner_fk");
    assertEquals(columnNames(foreignKey), List.of("owner_id", "owner_serial"));
  }

  /**
   * The rows of getImportedKeys are ordered by the referenced table and the key sequence,
   * but not by the name of the foreign key. Two composite keys referencing the same table
   * may therefore be interleaved.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testInterleavedCompositeForeignKeys() throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) ->
        "getImportedKeys".equals(method) ?
          List.of(foreignKeyColumn("blah_owner_fk", 1, "owner_id", "id"),
                  foreignKeyColumn("blah_deputy_fk", 1, "deputy_id", "id"),
                  foreignKeyColumn("blah_owner_fk", 2, "owner_serial", "serial"),
                  foreignKeyColumn("blah_deputy_fk", 2, "deputy_serial", "serial")) :
          STANDARD.getRows(method, args));
    TableMetaData table = createTableMetaData(metaData);

    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertEquals(table.getForeignKeys().size(), 2);
    assertEquals(columnNames(foreignKeyByName(table, "blah_owner_fk")), List.of("owner_id", "owner_serial"));
    assertEquals(columnNames(foreignKeyByName(table, "blah_deputy_fk")), List.of("deputy_id", "deputy_serial"));
  }

  /**
   * Temporary foreign keys are not part of the model.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testTemporaryForeignKeyIsIgnored() throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) ->
        "getImportedKeys".equals(method) ?
          List.of(foreignKeyColumn("_scratch_fk", 1, "owner_id", "id"),
                  foreignKeyColumn("_scratch_fk", 2, "owner_serial", "serial")) :
          STANDARD.getRows(method, args));
    TableMetaData table = createTableMetaData(metaData);

    table.setupTableFromMetaData(metaData, null, null, TABLE_NAME);

    assertTrue(table.getForeignKeys().isEmpty(), "temporary foreign keys must be ignored");
  }


  /**
   * Creates the table metadata for a table of the fake database.
   *
   * @param metaData the database metadata
   * @return the table metadata, not set up yet
   */
  private TableMetaData createTableMetaData(DatabaseMetaData metaData) {
    Backend backend = new Db2();    // no default schema, no comment column for indexes
    return new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null), TABLE_NAME);
  }

  /**
   * Gets the foreign key columns in their ordinal order.
   *
   * @param foreignKey the foreign key
   * @return the referencing column names
   */
  private List<String> columnNames(ForeignKeyMetaData foreignKey) {
    return foreignKey.getForeignKeyColumns().stream().map(ForeignKeyColumnMetaData::getForeignKeyColumn).toList();
  }

  /**
   * Gets a foreign key by its name.
   *
   * @param table the table
   * @param foreignKeyName the name of the foreign key
   * @return the foreign key
   */
  private ForeignKeyMetaData foreignKeyByName(TableMetaData table, String foreignKeyName) {
    return table.getForeignKeys().stream()
        .filter(foreignKey -> foreignKeyName.equals(foreignKey.getForeignKeyName()))
        .findAny().orElseThrow(() -> new AssertionError("no such foreign key: " + foreignKeyName));
  }

  /**
   * Creates a row of getColumns.
   *
   * @param tableName the name of the table the column belongs to
   * @param columnName the column name
   * @return the row
   */
  private static Map<String, Object> column(String tableName, String columnName) {
    return FakeJdbc.row("TABLE_SCHEM", null,
                        "TABLE_NAME", tableName,
                        "COLUMN_NAME", columnName,
                        "REMARKS", "the " + columnName,
                        "COLUMN_SIZE", 128,
                        "DECIMAL_DIGITS", (short) 0,
                        "NULLABLE", DatabaseMetaData.columnNoNulls,
                        "DATA_TYPE", Types.VARCHAR,
                        "TYPE_NAME", "VARCHAR",
                        "COLUMN_DEF", null);
  }

  /**
   * Creates a row of getIndexInfo.
   *
   * @param indexName the index name
   * @param columnName the column name
   * @param position the ordinal position within the index
   * @return the row
   */
  private static Map<String, Object> indexColumn(String indexName, String columnName, int position) {
    return FakeJdbc.row("INDEX_NAME", indexName,
                        "NON_UNIQUE", true,
                        "FILTER_CONDITION", null,
                        "COLUMN_NAME", columnName,
                        "ASC_OR_DESC", "A",
                        "ORDINAL_POSITION", (short) position);
  }

  /**
   * Creates a row of getImportedKeys.
   *
   * @param foreignKeyName the name of the foreign key
   * @param keySeq the ordinal position within the foreign key, starting at 1
   * @param foreignKeyColumn the referencing column
   * @param primaryKeyColumn the referenced column
   * @return the row
   */
  private static Map<String, Object> foreignKeyColumn(String foreignKeyName, int keySeq,
                                                      String foreignKeyColumn, String primaryKeyColumn) {
    return FakeJdbc.row("FK_NAME", foreignKeyName,
                        "KEY_SEQ", (short) keySeq,
                        "FKTABLE_SCHEM", null,
                        "FKTABLE_NAME", TABLE_NAME,
                        "FKCOLUMN_NAME", foreignKeyColumn,
                        "PKTABLE_SCHEM", null,
                        "PKTABLE_NAME", "owner",
                        "PKCOLUMN_NAME", primaryKeyColumn,
                        "UPDATE_RULE", (short) DatabaseMetaData.importedKeyNoAction,
                        "DELETE_RULE", (short) DatabaseMetaData.importedKeyCascade);
  }

}
