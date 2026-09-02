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

import org.tentackle.common.Constants;
import org.tentackle.sql.Backend;
import org.tentackle.sql.FakeJdbc;
import org.tentackle.sql.backends.Db2;
import org.tentackle.sql.backends.MySql;
import org.tentackle.sql.backends.Oracle;
import org.tentackle.sql.backends.Postgres;
import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the index and foreign key metadata and the backend-specific dialects of it.
 * <p>
 * The metadata is compared against the model to decide what the migration has to change, and it is
 * printed into the migration script for the developer to read. The backends report the very same
 * index or default value in their own dialect — postgres appends type casts, oracle keeps the
 * verbatim source text of a default — and normalizing that away is what keeps the migration from
 * rewriting an unchanged database on every run.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MetaDataStructureTest {

  private static final String TABLE = "invoice";


  /**
   * Creates a table of the given backend without touching a database.
   *
   * @param backend the backend
   * @return the table metadata
   */
  private TableMetaData table(Backend backend) {
    return table(backend, TABLE);
  }

  /**
   * Creates a table of the given backend without touching a database.
   *
   * @param backend the backend
   * @param tableName the table name
   * @return the table metadata
   */
  private TableMetaData table(Backend backend, String tableName) {
    // the table name is only known after the metadata has been read, hence the fake database
    DatabaseMetaData metaData = FakeJdbc.databaseMetaData((method, args) -> switch (method) {
      case "getTables" -> List.of(FakeJdbc.row("TABLE_SCHEM", null, "TABLE_NAME", tableName, "REMARKS", null));
      case "getColumns" -> List.of(FakeJdbc.row("TABLE_SCHEM", null,
                                                "TABLE_NAME", tableName,
                                                "COLUMN_NAME", Constants.CN_ID,
                                                "REMARKS", null,
                                                "COLUMN_SIZE", 0,
                                                "DECIMAL_DIGITS", (short) 0,
                                                "NULLABLE", DatabaseMetaData.columnNoNulls,
                                                "DATA_TYPE", Types.BIGINT,
                                                "TYPE_NAME", "BIGINT",
                                                "COLUMN_DEF", null));
      default -> List.of();
    });
    TableMetaData table = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[] { metaData }, null),
                                            tableName);
    try {
      table.setupTableFromMetaData(metaData, null, null, tableName);
    }
    catch (SQLException sqx) {
      throw new AssertionError("could not set up the table metadata", sqx);
    }
    return table;
  }

  /**
   * Creates an index over the given columns.
   *
   * @param table the table
   * @param indexName the index name
   * @param unique true if a unique index
   * @param columnNames the column names
   * @return the index
   */
  private IndexMetaData index(TableMetaData table, String indexName, boolean unique, String... columnNames)
      throws SQLException {
    IndexMetaData index = table.getModelMetaData().getBackend().createIndexMetaData(table);
    index.setIndexName(indexName);
    index.setUnique(unique);
    int position = 1;
    for (String columnName : columnNames) {
      var resultSet = FakeJdbc.resultSet(List.of(
          FakeJdbc.row("COLUMN_NAME", columnName,
                       "ASC_OR_DESC", position == 1 ? "A" : "D",
                       "ORDINAL_POSITION", (short) position)));
      resultSet.next();
      index.addIndexColumnFromMetaData(resultSet);
      position++;
    }
    return index;
  }


  // ---- the index -----------------------------------------------------------

  @Test
  public void testIndexToString() throws SQLException {
    TableMetaData table = table(new Db2());
    IndexMetaData index = index(table, "invoice_number", true, "number", "year");
    index.setComment("by number and year");
    assertEquals(index.getComment(), "by number and year");
    assertEquals(index.toString(), "UNIQUE INDEX invoice_number ON invoice (number, year DESC)");

    index.setUnique(false);
    index.setFilterCondition("deleted = 0");
    assertEquals(index.toString(), "INDEX invoice_number ON invoice (number, year DESC) WHERE deleted = 0");
  }

  @Test
  public void testIndexColumnOrder() throws SQLException {
    IndexMetaData index = index(table(new Db2()), "invoice_number", false, "number", "year");
    assertEquals(index.getColumns().stream().map(IndexColumnMetaData::getColumnName).toList(),
                 List.of("number", "year"));
    assertFalse(index.getColumns().get(0).isDescending());
    assertTrue(index.getColumns().get(1).isDescending());
    assertEquals(index.getColumns().get(1).getPosition(), 2);
    assertEquals(index.getColumns().get(1).toString(), "year DESC");
    assertNull(index.getColumns().get(0).getFunctionName(), "a plain column is no expression");

    // the columns are sorted by their ordinal position, whatever order the driver reports them in
    List<IndexColumnMetaData> shuffled = new ArrayList<>(index.getColumns());
    shuffled.sort(null);
    assertEquals(shuffled, index.getColumns());
    assertTrue(index.getColumns().get(0).compareTo(index.getColumns().get(1)) < 0);
  }

  /**
   * The primary key index is created by the table DDL itself and must not be emitted a second time
   * as a {@code CREATE UNIQUE INDEX}.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testPrimaryIdKey() throws SQLException {
    TableMetaData table = table(new Db2());
    assertTrue(index(table, "invoice_pkey", true, Constants.CN_ID).isPrimaryIdKey());
    assertFalse(index(table, "invoice_id", false, Constants.CN_ID).isPrimaryIdKey(), "not unique");
    assertFalse(index(table, "invoice_number", true, "number").isPrimaryIdKey(), "not the id column");
    assertFalse(index(table, "invoice_id_number", true, Constants.CN_ID, "number").isPrimaryIdKey(),
                "not a single column");
  }

  /**
   * The indexes of a table are held in a map keyed by the index, so the identity is the table
   * plus the name — never the columns, which are what a migration changes.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testIndexEquality() throws SQLException {
    TableMetaData table = table(new Db2());
    IndexMetaData index = index(table, "invoice_number", true, "number");
    IndexMetaData sameName = index(table, "invoice_number", false, "year");
    IndexMetaData otherName = index(table, "invoice_year", true, "number");

    assertEquals(index, sameName, "the columns are not part of the identity");
    assertEquals(index.hashCode(), sameName.hashCode());
    assertNotEquals(index, otherName);
    assertNotEquals(index, null);
    assertNotEquals(index, "not an index");

    // the same index name in another table is another index
    assertNotEquals(index, index(table(new Db2(), "credit_note"), "invoice_number", true, "number"));
  }


  // ---- the foreign key -----------------------------------------------------

  /**
   * Creates a foreign key from the rows a driver would report for it.
   *
   * @param table the referencing table
   * @param schema the schema of both tables, null if none
   * @param columnNames the referencing column names
   * @return the foreign key
   * @throws SQLException if the test failed
   */
  private ForeignKeyMetaData foreignKey(TableMetaData table, String schema, String... columnNames)
      throws SQLException {
    ForeignKeyMetaData foreignKey = new ForeignKeyMetaData(table);
    int position = 1;
    for (String columnName : columnNames) {
      var resultSet = FakeJdbc.resultSet(List.of(
          FakeJdbc.row("FK_NAME", "invoice_customer_fk",
                       "KEY_SEQ", (short) position,
                       "FKTABLE_SCHEM", schema,
                       "FKTABLE_NAME", TABLE,
                       "FKCOLUMN_NAME", columnName,
                       "PKTABLE_SCHEM", schema,
                       "PKTABLE_NAME", "customer",
                       "PKCOLUMN_NAME", columnName.replace("customer_", ""),
                       "UPDATE_RULE", (short) DatabaseMetaData.importedKeyNoAction,
                       "DELETE_RULE", (short) DatabaseMetaData.importedKeyCascade,
                       "DEFERRABILITY", (short) DatabaseMetaData.importedKeyNotDeferrable)));
      resultSet.next();
      if (position == 1) {
        foreignKey.setupForeignKeyFromMetaData(resultSet);
      }
      ForeignKeyColumnMetaData column = new ForeignKeyColumnMetaData(foreignKey);
      column.setupForeignKeyColumnFromMetaData(resultSet);
      foreignKey.addForeignKeyColumn(column);
      position++;
    }
    return foreignKey;
  }

  @Test
  public void testForeignKeyToString() throws SQLException {
    ForeignKeyMetaData foreignKey = foreignKey(table(new Db2()), null, "customer_id");
    assertEquals(foreignKey.toString(),
                 "FOREIGN KEY invoice_customer_fk (customer_id) REFERENCES customer (id) " +
                 "ON UPDATE NO ACTION ON DELETE CASCADE");
    assertEquals(foreignKey.getUpdateRule(), ForeignKeyAction.NO_ACTION);
    assertEquals(foreignKey.getDeleteRule(), ForeignKeyAction.CASCADE);
  }

  @Test
  public void testCompositeForeignKeyToString() throws SQLException {
    ForeignKeyMetaData foreignKey = foreignKey(table(new Db2()), null, "customer_id", "customer_serial");
    assertEquals(foreignKey.toString(),
                 "FOREIGN KEY invoice_customer_fk (customer_id, customer_serial) " +
                 "REFERENCES customer (id, serial) ON UPDATE NO ACTION ON DELETE CASCADE");
    assertEquals(foreignKey.getForeignKeyColumns().get(1).getPosition(), 2);
    assertEquals(foreignKey.getForeignKeyColumns().get(1).getPrimaryKeyColumn(), "serial");
    assertEquals(foreignKey.getForeignKeyColumns().get(1).toString(), "customer_serial");
  }

  @Test
  public void testForeignKeyTableNames() throws SQLException {
    ForeignKeyMetaData unqualified = foreignKey(table(new Db2()), null, "customer_id");
    assertEquals(unqualified.getFullForeignKeyTableName(), TABLE);
    assertEquals(unqualified.getFullPrimaryKeyTableName(), "customer");
    assertNull(unqualified.getForeignKeySchema());

    ForeignKeyMetaData qualified = foreignKey(table(new Db2()), "sales", "customer_id");
    assertEquals(qualified.getForeignKeySchema(), "sales");
    assertEquals(qualified.getFullForeignKeyTableName(), "sales." + TABLE);
    assertEquals(qualified.getFullPrimaryKeyTableName(), "sales.customer");

    // the migration renames the tables of a foreign key when it renames the tables themselves
    qualified.setForeignKeyTable("bill");
    qualified.setPrimaryKeyTable("client");
    assertEquals(qualified.getForeignKeyTable(), "bill");
    assertEquals(qualified.getPrimaryKeyTable(), "client");
    assertEquals(qualified.getFullPrimaryKeyTableName(), "sales.client");
  }

  @Test
  public void testForeignKeyEquality() throws SQLException {
    TableMetaData table = table(new Db2());
    ForeignKeyMetaData foreignKey = foreignKey(table, null, "customer_id");
    assertEquals(foreignKey, foreignKey(table, null, "customer_id", "customer_serial"),
                 "the identity is the table plus the name");
    assertEquals(foreignKey.hashCode(), foreignKey(table, null, "customer_id").hashCode());
    assertNotEquals(foreignKey, null);
    assertNotEquals(foreignKey, "not a foreign key");
    assertNotEquals(foreignKey, foreignKey(table(new Db2(), "credit_note"), null, "customer_id"), "another table");
  }


  // ---- what the backends have to normalize ---------------------------------

  /**
   * Postgres appends a type cast to everything it reports, which would make every default look
   * different from the model's.
   */
  @Test
  public void testPostgresStripsTypeCastsFromDefaults() {
    PostgresColumnMetaData column = new PostgresColumnMetaData(table(new Postgres()));
    column.setType(Types.VARCHAR);
    column.setDefaultValue("'open'::character varying");
    column.validate();
    assertEquals(column.getDefaultValue(), "'open'");

    column.setDefaultValue("0");
    column.validate();
    assertEquals(column.getDefaultValue(), "0", "a default without a cast is left alone");

    column.setDefaultValue(null);
    column.validate();
    assertNull(column.getDefaultValue());
  }

  @Test
  public void testPostgresStripsTypeCastsFromFilterConditions() throws SQLException {
    PostgresIndexMetaData index = (PostgresIndexMetaData) index(table(new Postgres()), "invoice_open", false, "number");
    index.setFilterCondition("(status = 'open'::text)");
    index.validate();
    assertEquals(index.getFilterCondition(), "(status = 'open')");

    // the type names consisting of more than one word have to be recognized as a whole
    index.setFilterCondition("(status = 'open'::character varying)");
    index.validate();
    assertEquals(index.getFilterCondition(), "(status = 'open')");

    // the multi-word temporal casts have to be recognized as a whole
    index.setFilterCondition("(due_at > '2020-01-01'::timestamp without time zone)");
    index.validate();
    assertEquals(index.getFilterCondition(), "(due_at > '2020-01-01')");

    index.setFilterCondition(null);
    index.validate();
    assertNull(index.getFilterCondition());
  }

  @Test
  public void testPostgresStripsTypeCastsFromIndexColumns() throws SQLException {
    IndexMetaData index = index(table(new Postgres()), "invoice_number", false,
                                "(number)::text", "((year)::text)", "plain");
    assertEquals(index.getColumns().stream().map(IndexColumnMetaData::getColumnName).toList(),
                 List.of("number", "year", "plain"));
  }

  /**
   * Oracle reports the default as the verbatim source text, including the whitespace up to the
   * following token, and has no way to express "no default" other than the literal NULL.
   */
  @Test
  public void testOracleNormalizesDefaults() {
    OracleColumnMetaData column = new OracleColumnMetaData(table(new Oracle()));
    column.setType(Types.INTEGER);

    column.setDefaultValue("1 ");
    column.validate();
    assertEquals(column.getDefaultValue(), "1", "the trailing whitespace is not part of the default");

    for (String noDefault : new String[] { "NULL", "null", "(NULL)", "(null)", "   " }) {
      column.setDefaultValue(noDefault);
      column.validate();
      assertNull(column.getDefaultValue(), noDefault + " means no default at all");
    }

    // the empty string default keeps its quotes and its single blank
    column.setDefaultValue("' '");
    column.validate();
    assertEquals(column.getDefaultValue(), "' '");
  }

  /**
   * MySQL reports a missing comment as an empty string, which would look like a comment change
   * on every migration run.
   */
  @Test
  public void testMySqlNormalizesEmptyComments() {
    MySqlColumnMetaData column = new MySqlColumnMetaData(table(new MySql()));
    column.setComment("");
    column.validate();
    assertNull(column.getComment());

    column.setComment("the number");
    column.validate();
    assertEquals(column.getComment(), "the number");

    // the default is deliberately left as is, MySql compares it itself
    column.setDefaultValue("open");
    column.validate();
    assertEquals(column.getDefaultValue(), "open");
  }

}
