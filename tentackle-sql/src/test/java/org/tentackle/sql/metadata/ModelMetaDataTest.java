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

import org.tentackle.sql.Backend;
import org.tentackle.sql.FakeJdbc;
import org.tentackle.sql.backends.Db2;
import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ModelMetaData}, {@link DatabaseMetaDataTableHeader} and {@link MetaDataUtilities}.
 * <p>
 * The model metadata is the picture the migrator has of the existing database. The schema filter and
 * the case handling are what decides whether a table is recognized as the one the model describes:
 * the JDBC drivers disagree about the case they report names in, and a table missed here would be
 * created a second time.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ModelMetaDataTest {

  private final Backend backend = new Db2();    // no default schema, no index comments


  /**
   * Creates a row of {@code getTables}.
   *
   * @param schema the schema, null if none
   * @param name the table name
   * @param type the table type
   * @return the row
   */
  private static Map<String, Object> tableRow(String schema, String name, String type) {
    return FakeJdbc.row("TABLE_CAT", null,
                        "TABLE_SCHEM", schema,
                        "TABLE_NAME", name,
                        "TABLE_TYPE", type,
                        "REMARKS", "the " + name);
  }

  /**
   * Creates a row of {@code getColumns}.
   *
   * @param schema the schema, null if none
   * @param tableName the table name
   * @param columnName the column name
   * @return the row
   */
  private static Map<String, Object> columnRow(String schema, String tableName, String columnName) {
    return FakeJdbc.row("TABLE_SCHEM", schema,
                        "TABLE_NAME", tableName,
                        "COLUMN_NAME", columnName,
                        "REMARKS", null,
                        "COLUMN_SIZE", 32,
                        "DECIMAL_DIGITS", (short) 0,
                        "NULLABLE", DatabaseMetaData.columnNullable,
                        "DATA_TYPE", Types.VARCHAR,
                        "TYPE_NAME", "VARCHAR",
                        "COLUMN_DEF", null);
  }

  /**
   * Creates the database metadata of a database holding the given tables.
   *
   * @param tables the rows of {@code getTables}
   * @return the database metadata
   */
  private DatabaseMetaData metaData(List<Map<String, Object>> tables) {
    return FakeJdbc.databaseMetaData((method, args) -> switch (method) {
      case "getTables" -> tables;
      case "getColumns" -> List.of(columnRow((String) args[1], (String) args[2], "id"));
      default -> List.of();
    });
  }


  // ---- the table headers ---------------------------------------------------

  @Test
  public void testAllTablesInDatabase() throws SQLException {
    DatabaseMetaData metaData = metaData(List.of(tableRow("public", "foo", "TABLE"),
                                                 tableRow("public", "bar", "TABLE")));
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null);

    List<DatabaseMetaDataTableHeader> headers = model.getAllTablesInDatabase();
    assertEquals(headers.size(), 2);
    assertEquals(headers.stream().map(DatabaseMetaDataTableHeader::getName).toList(), List.of("foo", "bar"));
    assertSame(model.getBackend(), backend);
    assertEquals(model.getMetaData().length, 1);
  }

  /**
   * The same table name may exist in more than one schema, and the migrator has to see all of them
   * to decide which one it is looking at.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testTableInMoreThanOneSchema() throws SQLException {
    DatabaseMetaData metaData = metaData(List.of(tableRow("public", "foo", "TABLE"),
                                                 tableRow("other", "foo", "TABLE")));
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null);

    List<DatabaseMetaDataTableHeader> headers = model.getTableInDatabase("foo");
    assertNotNull(headers);
    assertEquals(headers.size(), 2);
    assertEquals(headers.stream().map(DatabaseMetaDataTableHeader::getSchema).toList(), List.of("public", "other"));
    assertNull(model.getTableInDatabase("nosuchtable"), "an unknown table must not be invented");
  }

  /**
   * The drivers disagree about the case they report table names in, so the lookup has to be
   * case-insensitive.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testTableLookupIsCaseInsensitive() throws SQLException {
    DatabaseMetaData metaData = metaData(List.of(tableRow(null, "FOO", "TABLE")));
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null);

    assertNotNull(model.getTableInDatabase("foo"));
    assertNotNull(model.getTableInDatabase("FOO"));
    assertEquals(model.getTableInDatabase("Foo").getFirst().getName(), "foo", "the name is normalized");
  }

  /**
   * More than one connection is inspected when the model spans several schemas.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testSeveralMetaDataSources() throws SQLException {
    DatabaseMetaData first = metaData(List.of(tableRow("one", "foo", "TABLE")));
    DatabaseMetaData second = metaData(List.of(tableRow("two", "bar", "TABLE")));
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[] { first, second }, null);

    assertEquals(model.getAllTablesInDatabase().size(), 2);
    assertNotNull(model.getTableInDatabase("bar"));
  }


  // ---- the schema filter ---------------------------------------------------

  /**
   * With the schemas option only the tables of those schemas belong to the model; everything else
   * in the database has to be left alone by the migration.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testSchemaFilter() throws SQLException {
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[0], new String[] { "public" });
    assertEquals(model.getSchemas(), new String[] { "public" });

    assertTrue(model.addTableMetaData(table("public", "foo")));
    assertTrue(model.addTableMetaData(table("PUBLIC", "bar")), "the schema is matched case-insensitively");
    assertFalse(model.addTableMetaData(table("other", "baz")));
    assertFalse(model.addTableMetaData(table(null, "qux")), "a table without a schema is not in one");

    assertEquals(model.getTables().size(), 2);
  }

  @Test
  public void testWithoutSchemaFilterEverythingBelongsToTheModel() throws SQLException {
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[0], null);
    assertTrue(model.addTableMetaData(table("public", "foo")));
    assertTrue(model.addTableMetaData(table(null, "bar")));
    assertEquals(model.getTables().size(), 2);
    assertTrue(model.toString().contains("foo"), model.toString());
  }


  // ---- the table header ----------------------------------------------------

  @Test
  public void testTableHeaderNormalizesNames() {
    DatabaseMetaDataTableHeader header =
        new DatabaseMetaDataTableHeader(null, "CAT", "SCHEMA", "TABLE_NAME", "table", "a remark");
    assertEquals(header.getCatalog(), "cat");
    assertEquals(header.getSchema(), "schema");
    assertEquals(header.getName(), "table_name");
    assertEquals(header.getType(), "TABLE", "the type is normalized to upper case");
    assertEquals(header.getComment(), "a remark");
    assertNull(header.getMetaData());
    assertEquals(header.toString(), "schema.table_name (a remark)");
  }

  /**
   * Views, system tables and the like are in the database but not in the model.
   */
  @Test
  public void testUserTable() {
    assertTrue(new DatabaseMetaDataTableHeader(null, null, null, "foo", "TABLE", null).isUserTable());
    assertTrue(new DatabaseMetaDataTableHeader(null, null, null, "foo", null, null).isUserTable(),
               "an unknown type is assumed to be a table");
    assertTrue(new DatabaseMetaDataTableHeader(null, null, null, "foo", "", null).isUserTable());
    assertFalse(new DatabaseMetaDataTableHeader(null, null, null, "foo", "VIEW", null).isUserTable());
    assertFalse(new DatabaseMetaDataTableHeader(null, null, null, "foo", "SYSTEM TABLE", null).isUserTable());
    assertEquals(new DatabaseMetaDataTableHeader(null, null, null, "foo", "VIEW", null).toString(), "VIEW foo");
  }

  /**
   * The tables tentackle creates for itself are not part of the application's model and must not
   * be reported as unknown by the migration.
   */
  @Test
  public void testReservedTable() {
    assertTrue(new DatabaseMetaDataTableHeader(null, null, null, "tokenlock", "TABLE", null).isReserved());
    assertTrue(new DatabaseMetaDataTableHeader(null, null, "public", "MODLOG", "TABLE", null).isReserved(),
               "the name is matched case-insensitively");
    assertFalse(new DatabaseMetaDataTableHeader(null, null, null, "tokenlock", "VIEW", null).isReserved(),
                "only a real table can be a reserved one");
    assertFalse(new DatabaseMetaDataTableHeader(null, null, null, "invoice", "TABLE", null).isReserved());
  }


  // ---- the utilities -------------------------------------------------------

  @Test
  public void testMetaDataUtilitiesIsASingleton() {
    assertSame(MetaDataUtilities.getInstance(), MetaDataUtilities.getInstance());
  }

  @Test
  public void testJdbcTypeToString() {
    MetaDataUtilities utilities = MetaDataUtilities.getInstance();
    assertEquals(utilities.jdbcTypeToString(Types.VARCHAR), "VARCHAR");
    assertEquals(utilities.jdbcTypeToString(Types.BIGINT), "BIGINT");
    assertEquals(utilities.jdbcTypeToString(Types.TIMESTAMP), "TIMESTAMP");
    assertEquals(utilities.jdbcTypeToString(-999999), "UNKNOWN", "an unknown type must not be reported as null");
  }

  @Test
  public void testIsReservedTable() {
    MetaDataUtilities utilities = MetaDataUtilities.getInstance();
    for (String reserved : new String[] { "bundle", "bundlekey", "modification", "modlog", "numpool",
                                          "numrange", "prefkey", "prefnode", "secrules", "tokenlock" }) {
      assertTrue(utilities.isReservedTable(reserved), reserved + " is a tentackle table");
      assertTrue(utilities.isReservedTable(reserved.toUpperCase(java.util.Locale.ROOT)));
      assertTrue(utilities.isReservedTable("public." + reserved), "the schema must be stripped first");
    }
    assertFalse(utilities.isReservedTable("invoice"));
    assertFalse(utilities.isReservedTable(null));
    assertFalse(utilities.isReservedTable("mytokenlock"), "only the exact name is reserved");
  }


  /**
   * Creates a table belonging to a schema.
   *
   * @param schemaName the schema, null if none
   * @param tableName the table name
   * @return the table metadata
   * @throws SQLException if the fake metadata could not be read
   */
  private TableMetaData table(String schemaName, String tableName) throws SQLException {
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) -> switch (method) {
      case "getTables" -> List.of(tableRow(schemaName, tableName, "TABLE"));
      case "getColumns" -> List.of(columnRow(schemaName, tableName, "id"));
      default -> List.of();
    });
    ModelMetaData model = new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null);
    TableMetaData table = new TableMetaData(model, tableName);
    table.setupTableFromMetaData(metaData, null, null, tableName);
    return table;
  }

}
