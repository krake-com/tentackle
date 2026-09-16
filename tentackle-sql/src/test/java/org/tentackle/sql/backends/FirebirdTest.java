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

import org.testng.annotations.Test;

import org.tentackle.sql.BackendException;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.FirebirdColumnMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the Firebird backend.
 * <p>
 * Firebird cannot be reached from the build, so the generated SQL is asserted verbatim. It deviates in
 * places where a wrong statement still parses on other databases: the explicit row lock, the sort order
 * per index and the missing COLUMN keyword.
 */
@SuppressWarnings("missing-explicit-ctor")
public class FirebirdTest {

  private final Firebird backend = new Firebird();


  @Test
  public void testValidateVersion() {
    expectThrows(BackendException.class, () -> backend.validateVersion(3, 0));
    backend.validateVersion(4, 0);
    backend.validateVersion(5, 0);
  }

  /**
   * FOR UPDATE alone locks nothing in Firebird, and the clauses follow the SQL standard order.
   */
  @Test
  public void testBuildSelectSql() {
    assertEquals(backend.buildSelectSql("* FROM foo", false, 0, 0), "SELECT * FROM foo");
    assertEquals(backend.buildSelectSql("* FROM foo", true, 0, 0), "SELECT * FROM foo FOR UPDATE WITH LOCK");
    assertEquals(backend.buildSelectSql("* FROM foo", false, 10, 0), "SELECT * FROM foo FETCH FIRST ? ROWS ONLY");
    assertEquals(backend.buildSelectSql("* FROM foo", true, 10, 20),
                 "SELECT * FROM foo OFFSET ? ROWS FETCH FIRST ? ROWS ONLY FOR UPDATE WITH LOCK");
  }

  @Test
  public void testTrailingSelectParameterOrder() {
    List<Integer> values = new ArrayList<>();
    int index = backend.setLeadingSelectParameters(HanaTest.recorder(values), 10, 20);
    index = backend.setTrailingSelectParameters(HanaTest.recorder(values), index, 10, 20);
    assertEquals(values, List.of(20, 10), "offset must be bound before limit");
    assertEquals(index, 3);
  }

  @Test
  public void testTypeMapping() {
    assertEquals(backend.columnTypeToString(SqlType.BIT, 0, 0), "BOOLEAN");
    assertEquals(backend.columnTypeToString(SqlType.TINYINT, 0, 0), "SMALLINT");
    assertEquals(backend.columnTypeToString(SqlType.DOUBLE, 0, 0), "DOUBLE PRECISION");
    assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 0, 0), "VARCHAR(8191)");
    assertEquals(backend.columnTypeToString(SqlType.CLOB, 0, 0), "BLOB SUB_TYPE TEXT");
    assertEquals(backend.columnTypeToString(SqlType.JSON, 0, 0), "BLOB SUB_TYPE TEXT");   // no native JSON type
    assertEquals(backend.columnTypeToString(SqlType.BLOB, 0, 0), "BLOB SUB_TYPE BINARY");
    // a bare DECIMAL would be DECIMAL(9,0)
    assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 0, 0), "DECIMAL(19)");
    assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 10, 2), "DECIMAL(10,2)");
    // the default comes before the null constraint
    assertEquals(backend.columnTypeNullDefaultToString("flag", SqlType.BIT, 0, 0, false, false),
                 "BOOLEAN DEFAULT FALSE NOT NULL");
  }

  @Test
  public void testDdl() {
    // no COLUMN keyword
    assertEquals(backend.sqlAddColumn("foo", "extra", null, SqlType.INTEGER, 0, 0, false, 0),
                 "ALTER TABLE foo ADD extra INTEGER DEFAULT 0 NOT NULL;\n");
    assertEquals(backend.sqlDropColumn("foo", "extra"), "ALTER TABLE foo DROP extra;\n");
    assertEquals(backend.sqlRenameColumn("foo", "name", "label"), "ALTER TABLE foo ALTER COLUMN name TO label;\n");
    // the null constraint and the default are separate alterations
    assertEquals(backend.sqlAlterColumnType("foo", "name", null, SqlType.VARCHAR, 64, 0, false, "x"),
                 "ALTER TABLE foo ALTER COLUMN name TYPE VARCHAR(64);\n");
    assertEquals(backend.sqlAlterColumnNullConstraint("foo", "name", false),
                 "ALTER TABLE foo ALTER COLUMN name SET NOT NULL;\n");
    assertEquals(backend.sqlAlterColumnNullConstraint("foo", "name", true),
                 "ALTER TABLE foo ALTER COLUMN name DROP NOT NULL;\n");
    assertEquals(backend.sqlAlterColumnDefault("foo", "name", SqlType.VARCHAR, "x"),
                 "ALTER TABLE foo ALTER COLUMN name SET DEFAULT 'x';\n");
    assertEquals(backend.getDummySelect(), "SELECT 1 FROM RDB$DATABASE");
  }

  /**
   * Neither tables nor indexes can be renamed.
   */
  @Test
  public void testRenames() {
    assertTrue(expectThrows(BackendException.class, () -> backend.sqlRenameTable("foo", "bar"))
                   .getMessage().contains("cannot rename"));
    assertNull(backend.sqlRenameIndex("foo", "old", "new"), "drop and create instead");
  }

  /**
   * The sort order belongs to the index, not to its columns.
   */
  @Test
  public void testIndexSortOrder() {
    assertEquals(backend.sqlCreateIndex("foo", "foo_idx", false, null, "a", "b"),
                 "CREATE INDEX foo_idx ON foo (a, b);\n");
    assertEquals(backend.sqlCreateIndex("foo", "foo_idx", true, null, "-a", "-b"),
                 "CREATE UNIQUE DESCENDING INDEX foo_idx ON foo (a, b);\n");
    expectThrows(BackendException.class, () -> backend.sqlCreateIndex("foo", "foo_idx", false, null, "a", "-b"));
    assertFalse(backend.isFilteredIndexSupported());
    assertFalse(backend.isFunctionBasedIndexSupported());
  }

  @Test
  public void testSequences() {
    // START WITH before INCREMENT BY
    assertEquals(backend.sqlCreateSequence("numpool_seq", 100L, 5L),
                 "CREATE SEQUENCE numpool_seq START WITH 100 INCREMENT BY 5");
    assertEquals(backend.sqlNextFromSequence("numpool_seq"), "SELECT NEXT VALUE FOR numpool_seq FROM RDB$DATABASE");
  }

  @Test
  public void testNames() {
    assertFalse(backend.isSchemaSupported());
    // unquoted identifiers must start with a letter
    assertTrue(backend.isTemporaryName("tmp_foo"));
    assertFalse(backend.isTemporaryName("_foo"));
    expectThrows(BackendException.class, () -> backend.assertValidName(SqlNameType.COLUMN_NAME, "_foo"));
    // the system tables
    assertTrue(backend.isReservedTableName("RDB$RELATIONS"));
    assertTrue(backend.isReservedTableName("mon$attachments"));
    assertFalse(backend.isReservedTableName("foo"));
    assertEquals(backend.getMaxNameLength(SqlNameType.TABLE_NAME), 63);
  }

  /**
   * Depending on the driver, the default is reported including the DEFAULT keyword.
   */
  @Test
  public void testDefaultValueMetaData() {
    assertEquals(defaultValue("DEFAULT 'x'"), "'x'");
    assertEquals(defaultValue("default 0"), "0");
    assertEquals(defaultValue("'DEFAULT x'"), "'DEFAULT x'", "a string literal is left alone");
    assertEquals(defaultValue("TRUE"), "TRUE");
    assertNull(defaultValue("DEFAULT "));
  }

  private String defaultValue(String reported) {
    TableMetaData table = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    ColumnMetaData column = backend.createColumnMetaData(table);
    assertTrue(column instanceof FirebirdColumnMetaData);
    column.setDefaultValue(reported);
    column.validate();
    return column.getDefaultValue();
  }

}
