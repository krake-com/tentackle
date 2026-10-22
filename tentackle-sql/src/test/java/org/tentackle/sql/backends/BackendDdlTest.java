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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the DDL every backend generates.
 * <p>
 * The DDL is what the SQL plugin writes into the migration scripts, so a change to it changes what
 * an application's database will look like. Every statement is therefore asserted verbatim for the
 * backends that cannot be reached from the build, and executed against a real database for H2, where
 * a syntax error is a hard failure rather than a diff.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class BackendDdlTest {

  private static final String TABLE = "ddltest";

  private Connection connection;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:ddltest");
  }

  @AfterClass(alwaysRun = true)
  public void dropDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }


  /**
   * All non-deprecated backends.
   *
   * @return each backend as a single argument
   */
  @DataProvider(name = "backends")
  public Object[][] backends() {
    return BackendFactory.getInstance().getAllBackends().stream()
        .map(backend -> new Object[] { backend }).toArray(Object[][]::new);
  }


  // ---- invariants that hold for every backend ------------------------------

  /**
   * A statement of a migration script is only separated from the next one by the statement
   * separator, so a missing one would glue two statements together.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testStatementsAreTerminated(Backend backend) {
    for (String sql : Arrays.asList(backend.sqlCreateIndex(TABLE, "ddltest_name", false, null, "name"),
                                    backend.sqlAddColumn(TABLE, "extra", null, SqlType.INTEGER, 0, 0, true, null),
                                    backend.sqlDropColumn(TABLE, "extra"),
                                    backend.sqlAlterColumnType(TABLE, "name", null, SqlType.VARCHAR, 64, 0, true, null),
                                    backend.sqlAlterColumnDefault(TABLE, "name", SqlType.VARCHAR, "x"),
                                    backend.sqlUpdateToNotNull(TABLE, "name", SqlType.VARCHAR, null, true),
                                    backend.sqlDropIndex(null, TABLE, "ddltest_name"),
                                    backend.sqlDropTable(null, TABLE))) {
      if (sql == null) {
        continue;   // the backend needs another statement for that, such as HANA for the default
      }
      assertEquals(sql.charAt(sql.length() - 1), '\n', backend + ": [" + sql + "] must end with a newline");
      assertTrue(sql.trim().endsWith(backend.getStatementSeparator()),
                 backend + ": [" + sql + "] must be terminated by " + backend.getStatementSeparator());
    }
  }

  /**
   * The generated statements must name the object they act on.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testStatementsNameTheirTarget(Backend backend) {
    assertTrue(backend.sqlCreateTableIntro(TABLE, null).contains(TABLE), backend.toString());
    assertTrue(backend.sqlAddColumn(TABLE, "extra", null, SqlType.INTEGER, 0, 0, true, null).contains("extra"),
               backend.toString());
    assertTrue(backend.sqlDropColumn(TABLE, "extra").contains("extra"), backend.toString());
    assertTrue(backend.sqlDropIndex(null, TABLE, "ddltest_name").contains("ddltest_name"), backend.toString());
  }

  /**
   * A comment given to the create-table DDL must survive, either inline or as a separate statement.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testTableCommentIsEmitted(Backend backend) {
    String comment = "the ddltest table";
    String inline = backend.sqlCreateTableIntro(TABLE, comment) + backend.sqlCreateTableClosing(TABLE, comment);
    String separate = backend.sqlCreateTableComment(TABLE, comment);
    assertTrue(inline.contains(comment) || separate.contains(comment),
               backend + ": the table comment is lost");
    assertFalse(backend.sqlCreateTableIntro(TABLE, null).contains("null"),
                backend + ": a missing comment must not be rendered");
  }

  /**
   * Every SQL type must have a rendering, otherwise no column of that type could ever be created.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testEverySqlTypeHasAColumnType(Backend backend) {
    for (SqlType sqlType : SqlType.values()) {
      if (sqlType == SqlType.UUID && !backend.isUUIDSupported() ||
          sqlType == SqlType.CLOB && !backend.isClobSupported() ||
          sqlType == SqlType.JAVA_OBJECT) {
        continue;
      }
      String columnType = backend.columnTypeToString(sqlType, backend.getDefaultSize(sqlType), 0);
      assertNotNull(columnType, backend + ": no column type for " + sqlType);
      assertFalse(columnType.isBlank(), backend + ": no column type for " + sqlType);
    }
  }

  /**
   * The migration reads a column's JDBC type from the database and has to recognize the SQL type it
   * created it with, so the mapping must be the inverse of {@link Backend#sqlTypeToString}.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testJdbcTypeMapsBackToSqlType(Backend backend) {
    for (int[] pair : new int[][] { { java.sql.Types.BIT, 0 }, { java.sql.Types.BOOLEAN, 0 },
                                    { java.sql.Types.INTEGER, 0 }, { java.sql.Types.BIGINT, 0 },
                                    { java.sql.Types.DOUBLE, 0 }, { java.sql.Types.DECIMAL, 0 },
                                    { java.sql.Types.VARCHAR, 32 }, { java.sql.Types.DATE, 0 },
                                    { java.sql.Types.TIME, 0 }, { java.sql.Types.TIMESTAMP, 0 },
                                    { java.sql.Types.BLOB, 0 } }) {
      SqlType[] sqlTypes = backend.jdbcTypeToSqlType(pair[0], pair[1], 0);
      assertTrue(sqlTypes.length > 0, backend + ": jdbc type " + pair[0] + " maps to nothing");
    }
    assertEquals(backend.jdbcTypeToSqlType(java.sql.Types.STRUCT, 0, 0).length, 0,
                 backend + ": an unknown jdbc type must map to nothing at all");
  }

  /**
   * A name longer than the backend allows would be silently truncated by the database and no longer
   * be found by the migration.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testMaxNameLength(Backend backend) {
    for (SqlNameType nameType : SqlNameType.values()) {
      assertTrue(backend.getMaxNameLength(nameType) > 0, backend + ": no limit for " + nameType);
    }
  }

  /**
   * Renaming an index is not universally supported, but a backend that claims to rename must
   * mention both names.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testRenames(Backend backend) {
    if (backend instanceof Firebird) {
      // must fail loudly rather than generate a migration that cannot be applied
      expectThrows(BackendException.class, () -> backend.sqlRenameTable(TABLE, "renamed"));
    }
    else {
      assertTrue(backend.sqlRenameTable(TABLE, "renamed").contains("renamed"), backend.toString());
    }
    assertTrue(backend.sqlRenameColumn(TABLE, "name", "label").contains("label"), backend.toString());
    String renameIndex = backend.sqlRenameIndex(TABLE, "ddltest_name", "ddltest_label");
    if (renameIndex != null) {
      assertTrue(renameIndex.contains("ddltest_name") && renameIndex.contains("ddltest_label"), backend.toString());
    }
  }

  /**
   * Schemas and sequences are optional features and the backend must be consistent about them:
   * whoever says no must also refuse to generate the corresponding DDL.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testOptionalFeaturesAreConsistent(Backend backend) {
    if (backend.isSchemaSupported()) {
      assertTrue(backend.sqlCreateSchema("myschema").contains("myschema"), backend.toString());
    }
    else {
      expectThrows(BackendException.class, () -> backend.sqlCreateSchema("myschema"));
    }

    if (backend.isSequenceSupported()) {
      assertTrue(backend.sqlCreateSequence("myseq", 100L, 5L).contains("myseq"), backend.toString());
      assertTrue(backend.sqlNextFromSequence("myseq").contains("myseq"), backend.toString());
    }
    else {
      expectThrows(BackendException.class, () -> backend.sqlCreateSequence("myseq", 100L, 5L));
      expectThrows(BackendException.class, () -> backend.sqlNextFromSequence("myseq"));
    }
  }

  /**
   * {@code IF EXISTS} may only appear when the backend supports it, and only when it was asked for.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testDropIfExists(Backend backend) {
    boolean supported = backend.sqlDropTable(null, TABLE).contains(Backend.SQL_IF_EXISTS);
    try {
      backend.setDropIfExistsEnabled(false);
      assertFalse(backend.sqlDropTable(null, TABLE).contains(Backend.SQL_IF_EXISTS),
                  backend + ": IF EXISTS must be off when not asked for");
      assertFalse(backend.sqlDropIndex(null, TABLE, "ddltest_name").contains(Backend.SQL_IF_EXISTS), backend.toString());
      assertFalse(backend.sqlDropColumn(TABLE, "extra").contains(Backend.SQL_IF_EXISTS), backend.toString());

      backend.setDropIfExistsEnabled(true);
      assertEquals(backend.sqlDropTable(null, TABLE).contains(Backend.SQL_IF_EXISTS), backend.isDropIfExistsEnabled(),
                   backend + ": IF EXISTS must appear if and only if the backend supports it");
    }
    finally {
      backend.setDropIfExistsEnabled(supported);
    }
  }


  // ---- the exact DDL of the backends that cannot be reached from the build --

  @Test
  public void testDb2Ddl() {
    Db2 backend = new Db2();
    // the continuation character, because DB2 scripts are line-oriented
    assertEquals(backend.sqlCreateTableIntro(TABLE, "a comment"), "CREATE TABLE " + TABLE + " ( -- a comment \\\n");
    // the column name belongs to the ALTER COLUMN clause only, and DB2 wants SET DATA TYPE
    assertEquals(backend.sqlAlterColumnType(TABLE, "name", null, SqlType.VARCHAR, 64, 0, false, "x"),
                 "ALTER TABLE " + TABLE + " ALTER COLUMN name SET DATA TYPE VARCHAR(64);\n");
    // DB2 groups added columns in parentheses
    assertEquals(backend.sqlAddColumn(TABLE, "extra", null, SqlType.INTEGER, 0, 0, true, null),
                 "ALTER TABLE " + TABLE + " ADD (extra INTEGER);\n");
    assertEquals(backend.sqlRenameIndex(TABLE, "old", "new"), "RENAME INDEX old TO new;\n");
    assertTrue(backend.isSchemaSupported());
  }

  @Test
  public void testInformixDdl() {
    Informix backend = new Informix();
    // informix has MODIFY instead of ALTER COLUMN, and wraps the alteration in parentheses
    assertEquals(backend.sqlAlterColumnType(TABLE, "name", null, SqlType.VARCHAR, 64, 0, true, null),
                 "ALTER TABLE " + TABLE + " MODIFY (name VARCHAR(64));\n");
    assertEquals(backend.sqlAlterColumnDefault(TABLE, "name", SqlType.VARCHAR, "x"),
                 "ALTER TABLE " + TABLE + " MODIFY (name SET DEFAULT 'x');\n");
    assertEquals(backend.sqlAlterColumnDefault(TABLE, "name", SqlType.VARCHAR, null),
                 "ALTER TABLE " + TABLE + " MODIFY (name DROP DEFAULT);\n");
    assertEquals(backend.sqlDropColumn(TABLE, "extra"), "ALTER TABLE " + TABLE + " DROP (extra);\n");
    assertEquals(backend.sqlRenameColumn(TABLE, "name", "label"), "RENAME COLUMN " + TABLE + ".name TO label;\n");
    // informix has no COALESCE
    assertEquals(backend.getCoalesceKeyword(), Informix.SQL_NVL);
    assertFalse(backend.isSchemaSupported());
  }

  @Test
  public void testMsSqlDdl() {
    MsSql backend = new MsSql();
    // strings are unicode on mssql
    assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 32, 0), "NVARCHAR(32)");
    assertEquals(backend.columnTypeToString(SqlType.CHAR, 0, 0), "NCHAR(1)");
    // mssql pages with TOP, which is prepended to the SELECT rather than appended to the statement.
    // The SQL is handed in without the leading SELECT, which the backend adds itself.
    StringBuilder sql = new StringBuilder("* FROM " + TABLE);
    backend.buildSelectSql(sql, false, 10, 0);
    assertEquals(sql.toString(), "SELECT " + MsSql.SQL_TOP_PAR + "* FROM " + TABLE);
    // with an offset TOP is of no use and mssql falls back to the SQL:2003 window function
    sql = new StringBuilder("* FROM " + TABLE);
    backend.buildSelectSql(sql, false, 10, 20);
    assertTrue(sql.toString().contains("ROW_NUMBER() OVER()"), sql.toString());
    // renaming an index needs sp_rename, which is not plain DDL
    assertNull(backend.sqlRenameIndex(TABLE, "old", "new"));
  }

  @Test
  public void testOracleDdl() {
    Oracle backend = new Oracle();
    // oracle has no boolean and no true CLOB comment
    assertEquals(backend.sqlAlterColumnType(TABLE, "name", null, SqlType.VARCHAR, 64, 0, true, null),
                 "ALTER TABLE " + TABLE + " MODIFY (name VARCHAR(64));\n");
    assertEquals(backend.sqlAlterColumnNullConstraint(TABLE, "name", false),
                 "ALTER TABLE " + TABLE + " MODIFY (name NOT NULL);\n");
    assertEquals(backend.sqlRenameTable(TABLE, "renamed"), "ALTER TABLE " + TABLE + " RENAME TO renamed;\n");
    assertEquals(backend.sqlNextFromSequence("myseq"), "SELECT myseq.NEXTVAL FROM DUAL");
    // an empty string is NULL on oracle, so MAPNULL has to fall back to a blank
    assertEquals(backend.getEmptyString(), " ");
  }

  @Test
  public void testMySqlDdl() {
    MySql backend = new MySql();
    // mysql carries the comments inside the create-table statement
    assertEquals(backend.sqlCreateTableComment(TABLE, "a comment"), "");
    assertTrue(backend.sqlCreateTableClosing(TABLE, "a comment").contains("COMMENT='a comment'"));
    assertTrue(backend.sqlCreateColumn("name", "a comment", SqlType.VARCHAR, 32, 0, true, null, false, false)
                   .contains("COMMENT 'a comment'"));
    // MODIFY COLUMN takes the whole definition, so the comment has to be repeated
    assertEquals(backend.sqlAlterColumnType(TABLE, "name", "a comment", SqlType.VARCHAR, 64, 0, true, null),
                 "ALTER TABLE " + TABLE + " MODIFY COLUMN name VARCHAR(64) COMMENT 'a comment';\n");
    assertEquals(backend.sqlRenameIndex(TABLE, "old", "new"),
                 "ALTER TABLE " + TABLE + " RENAME INDEX old TO new;\n");
    assertEquals(backend.sqlDropIndex("s", TABLE, "old"), "DROP INDEX old ON s." + TABLE + ";\n");
    assertFalse(backend.isSchemaSupported());
    assertFalse(backend.isSequenceSupported());
  }

  @Test
  public void testPostgresDdl() {
    Postgres backend = new Postgres();
    // postgres has TEXT rather than CLOB
    assertFalse(backend.isClobSupported());
    assertEquals(backend.getMaxSize(SqlType.VARCHAR), 0, "postgres has no VARCHAR limit worth mentioning");
    assertEquals(backend.getDefaultSize(SqlType.VARCHAR), 0);
    // the type change and the null constraint are separate statements
    assertEquals(backend.sqlAlterColumnType(TABLE, "name", null, SqlType.VARCHAR, 64, 0, false, "x"),
                 "ALTER TABLE " + TABLE + " ALTER COLUMN name TYPE VARCHAR(64);\n");
    assertEquals(backend.sqlAlterColumnNullConstraint(TABLE, "name", false),
                 "ALTER TABLE " + TABLE + " ALTER COLUMN name SET NOT NULL;\n");
    assertEquals(backend.sqlAlterColumnNullConstraint(TABLE, "name", true),
                 "ALTER TABLE " + TABLE + " ALTER COLUMN name DROP NOT NULL;\n");
    assertTrue(backend.isUUIDSupported());
    assertTrue(backend.isReservedSchemaName("pg_catalog"));
    assertFalse(backend.isReservedSchemaName("myschema"));
  }

  @Test
  public void testH2Specifics() {
    H2 backend = new H2();
    assertTrue(backend.isReservedSchemaName("INFORMATION_SCHEMA"));
    assertFalse(backend.isReservedSchemaName("myschema"));
    assertTrue(backend.isUUIDSupported());
    assertTrue(backend.isClobSupported());
    // a bare DECIMAL would be read as DECIMAL(100000,0) and silently round the fractions away
    assertTrue(backend.getDefaultSize(SqlType.DECIMAL) > 0);
    assertEquals(backend.getDefaultSize(SqlType.INTEGER), 0);
  }


  // ---- the H2 DDL against a real database ----------------------------------

  /**
   * Generates a complete life cycle of a table and runs it against H2.
   * <p>
   * Everything the SQL plugin emits for a schema migration is covered: creating the table with a
   * comment, adding, altering and dropping columns, the null constraint, the default value, the
   * index and the renames. A syntax error fails here rather than at the customer's site.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testGeneratedH2DdlIsAccepted() throws SQLException {
    H2 backend = new H2();
    List<String> script = new ArrayList<>();

    script.add(backend.sqlCreateTableIntro(TABLE, "the ddl test table") +
               backend.sqlCreateColumn("id", "the id", SqlType.BIGINT, 0, 0, false, null, true, true) +
               backend.sqlCreateColumn("name", "the name", SqlType.VARCHAR, 32, 0, true, null, false, false) +
               backend.sqlCreateTableClosing(TABLE, "the ddl test table"));
    script.add(backend.sqlCreateTableComment(TABLE, "the ddl test table"));
    script.add(backend.sqlCreateColumnComment(TABLE, "name", "the name"));
    script.add(backend.sqlCreateIndex(TABLE, "ddltest_name", true, null, "name"));
    script.add(backend.sqlCreateIndexComment(TABLE, "ddltest_name", "by name"));
    script.add(backend.sqlAddColumn(TABLE, "extra", "an extra", SqlType.INTEGER, 0, 0, true, null));
    script.add(backend.sqlAlterColumnType(TABLE, "name", "the name", SqlType.VARCHAR, 64, 0, true, null));
    script.add(backend.sqlAlterColumnDefault(TABLE, "extra", SqlType.INTEGER, 0));
    script.add(backend.sqlUpdateToNotNull(TABLE, "extra", SqlType.INTEGER, 0, true));
    script.add(backend.sqlAlterColumnNullConstraint(TABLE, "extra", false));
    script.add(backend.sqlAlterColumnDefault(TABLE, "extra", SqlType.INTEGER, null));
    script.add(backend.sqlRenameColumn(TABLE, "extra", "renamed"));
    script.add(backend.sqlRenameIndex(TABLE, "ddltest_name", "ddltest_label"));
    script.add(backend.sqlDropIndex(null, TABLE, "ddltest_label"));
    script.add(backend.sqlDropColumn(TABLE, "renamed"));
    script.add(backend.sqlRenameTable(TABLE, TABLE + "2"));
    script.add(backend.sqlDropTable(null, TABLE + "2"));

    try (Statement stmt = connection.createStatement()) {
      for (String sql : script) {
        try {
          stmt.execute(sql);
        }
        catch (SQLException sqx) {
          throw new SQLException("H2 rejected the generated DDL:\n" + sql, sqx);
        }
      }
    }
  }

  /**
   * The sequence DDL, which needs no table.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testGeneratedH2SequenceDdlIsAccepted() throws SQLException {
    H2 backend = new H2();
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(backend.sqlCreateSchema("ddlschema"));
      stmt.execute(backend.sqlCreateSequence("ddlseq", 100L, 5L));
      stmt.execute(backend.sqlCreateSequenceComment("ddlseq", "the ddl sequence"));
      try (ResultSet rs = stmt.executeQuery(backend.sqlNextFromSequence("ddlseq"))) {
        assertTrue(rs.next());
        assertEquals(rs.getLong(1), 100L, "the sequence must start where it was told to");
      }
    }
  }

}
