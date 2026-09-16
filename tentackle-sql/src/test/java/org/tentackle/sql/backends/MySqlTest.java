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

package org.tentackle.sql.backends;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the MySQL backend.
 * <p>
 * All methods under test are pure string transformations, so no database is required.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MySqlTest {

  private final MySql backend = new MySql();


  /**
   * Creates a statement recording the integer parameters.
   *
   * @param parameters collects "index=value" for each setInt
   * @return the statement
   */
  static BackendPreparedStatement createRecordingStatement(List<String> parameters) {
    return (BackendPreparedStatement) Proxy.newProxyInstance(
        MySqlTest.class.getClassLoader(),
        new Class<?>[] { BackendPreparedStatement.class },
        (proxy, method, args) -> {
          if ("setInt".equals(method.getName())) {
            parameters.add(args[0] + "=" + args[1]);
          }
          return null;
        });
  }


  @Test
  public void testSelectWithoutLimit() {
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo WHERE id=?", false, 0, 0),
                        "SELECT * FROM foo WHERE id=?");
  }

  @Test
  public void testSelectWithLimitAndOffset() {
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", false, 10, 0),
                        "SELECT * FROM foo LIMIT ?");
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", false, 10, 20),
                        "SELECT * FROM foo LIMIT ? OFFSET ?");
    // mysql cannot do OFFSET without LIMIT -> both parameters are always present
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", false, 0, 20),
                        "SELECT * FROM foo LIMIT ? OFFSET ?");
  }

  /**
   * The locking clause must follow LIMIT and OFFSET, otherwise mysql rejects the statement.
   */
  @Test
  public void testSelectWithWriteLock() {
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", true, 0, 0),
                        "SELECT * FROM foo FOR UPDATE");
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", true, 1, 0),
                        "SELECT * FROM foo LIMIT ? FOR UPDATE");
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", true, 10, 20),
                        "SELECT * FROM foo LIMIT ? OFFSET ? FOR UPDATE");
  }

  /**
   * The parameters must be applied in the same order as the placeholders appear in the SQL.
   */
  @Test
  public void testSelectParameterOrder() {
    List<String> parameters = new ArrayList<>();
    BackendPreparedStatement stmt = createRecordingStatement(parameters);
    Assert.assertEquals(backend.setLeadingSelectParameters(stmt, 10, 20), 1);
    Assert.assertTrue(parameters.isEmpty(), "no leading parameters expected");
    Assert.assertEquals(backend.setTrailingSelectParameters(stmt, 3, 10, 20), 5);
    Assert.assertEquals(parameters, List.of("3=10", "4=20"));

    parameters.clear();
    backend.setTrailingSelectParameters(stmt, 1, 0, 20);
    Assert.assertEquals(parameters, List.of("1=" + MySql.MAX_LIMIT, "2=20"));
  }

  @Test
  public void testSubSelectAlias() {
    // "every derived table must have its own alias"
    Assert.assertTrue(backend.isAliasRequiredForSubSelect());
  }

  @Test
  public void testStringTypes() {
    // a sized string stays a VARCHAR
    Assert.assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 100, 0), "VARCHAR(100)");
    // an unsized string must not become VARCHAR(4096): it would blow the row- and index-key limits
    Assert.assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 0, 0), "LONGTEXT");
    // mysql has no CLOB, and TEXT is limited to 64k
    Assert.assertEquals(backend.columnTypeToString(SqlType.CLOB, 0, 0), "LONGTEXT");
    // unlike MariaDB, mysql has a native JSON column type
    Assert.assertEquals(backend.columnTypeToString(SqlType.JSON, 0, 0), "JSON");
    // BLOB is limited to 64k as well
    Assert.assertEquals(backend.columnTypeToString(SqlType.BLOB, 0, 0), "LONGBLOB");
  }

  @Test
  public void testDecimalKeepsScale() {
    // without an explicit size mysql would default to DECIMAL(10,0) and drop all fraction digits
    Assert.assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 0, 2), "DECIMAL(19,2)");
    Assert.assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 0, 0), "DECIMAL(19)");
    Assert.assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 12, 4), "DECIMAL(12,4)");
    // the scale is limited to 30, regardless of the size
    Assert.assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 64, 40), "DECIMAL(64,30)");
  }

  @Test
  public void testTemporalTypesKeepFractionalSeconds() {
    Assert.assertEquals(backend.columnTypeToString(SqlType.TIMESTAMP, 0, 0), "DATETIME(6)");
    Assert.assertEquals(backend.columnTypeToString(SqlType.TIME, 0, 0), "TIME(6)");
    Assert.assertEquals(backend.columnTypeToString(SqlType.DATE, 0, 0), "DATE");
  }

  @Test
  public void testDefaultValues() {
    Assert.assertEquals(backend.columnTypeNullDefaultToString("name", SqlType.VARCHAR, 20, 0, false, "abc"),
                        "VARCHAR(20) NOT NULL DEFAULT 'abc'");
    // TEXT- and BLOB-columns only accept a default written as an expression
    Assert.assertEquals(backend.columnTypeNullDefaultToString("name", SqlType.VARCHAR, 0, 0, false, "abc"),
                        "LONGTEXT NOT NULL DEFAULT ('abc')");
    Assert.assertEquals(backend.columnTypeNullDefaultToString("name", SqlType.CLOB, 0, 0, true, "abc"),
                        "LONGTEXT DEFAULT ('abc')");
    Assert.assertEquals(backend.columnTypeNullDefaultToString("num", SqlType.INTEGER, 0, 0, true, 42),
                        "INT DEFAULT 42");
  }

  @Test
  public void testAlterColumn() {
    // ALTER COLUMN can only set or drop the default in mysql
    Assert.assertEquals(backend.sqlAlterColumnType("foo", "bar", null, SqlType.VARCHAR, 20, 0, false, null),
                        "ALTER TABLE foo MODIFY COLUMN bar VARCHAR(20) NOT NULL;\n");
    // MODIFY replaces the whole declaration, so the comment must be repeated
    Assert.assertEquals(backend.sqlAlterColumnType("foo", "bar", "hello", SqlType.VARCHAR, 20, 0, true, null),
                        "ALTER TABLE foo MODIFY COLUMN bar VARCHAR(20) COMMENT 'hello';\n");
  }

  /**
   * The null-constraint cannot be changed on its own, so the type must be repeated.
   */
  @Test
  public void testAlterNullConstraint() {
    Assert.assertNull(backend.sqlAlterColumnNullConstraint("foo", "bar", false));
    Assert.assertEquals(backend.sqlAlterColumnNullConstraint("foo", "bar", SqlType.BIGINT, 0, 0, false, null),
                        "ALTER TABLE foo MODIFY COLUMN bar BIGINT NOT NULL;\n");
    Assert.assertEquals(backend.sqlAlterColumnNullConstraint("foo", "bar", SqlType.BIGINT, 0, 0, true, null),
                        "ALTER TABLE foo MODIFY COLUMN bar BIGINT;\n");
  }

  @Test
  public void testAlterTableComment() {
    Assert.assertEquals(backend.sqlAlterTableComment("foo", "hello"),
                        "ALTER TABLE foo COMMENT='hello';\n");
    Assert.assertEquals(backend.sqlAlterTableComment("foo", null),
                        "ALTER TABLE foo COMMENT='';\n");
  }

  @Test
  public void testDropForeignKey() {
    // mysql knows DROP CONSTRAINT only since 8.0.19
    Assert.assertEquals(backend.sqlDropForeignKey("foo", "fk_foo_bar"),
                        "ALTER TABLE foo DROP FOREIGN KEY fk_foo_bar;\n");
  }

  @Test
  public void testCreateIndex() {
    Assert.assertEquals(backend.sqlCreateIndex("foo", "foo_idx", false, null, "a", "-b"),
                        "CREATE INDEX foo_idx ON foo (a, b DESC);\n");
    // a functional key part needs an extra pair of parentheses
    Assert.assertEquals(backend.sqlCreateIndex("foo", "foo_idx", true, null, "LOWER(a)", "-UPPER(b)"),
                        "CREATE UNIQUE INDEX foo_idx ON foo ((LOWER(a)), (UPPER(b)) DESC);\n");
  }

  @Test
  public void testReservedWords() {
    // not part of SQL92, but reserved in mysql
    for (String word: new String[] { "limit", "index", "lock", "rank", "replace", "long", "dual" }) {
      Assert.assertThrows(BackendException.class, () -> backend.assertValidName(SqlNameType.COLUMN_NAME, word));
    }
    backend.assertValidName(SqlNameType.COLUMN_NAME, "amount");
  }

  @Test
  public void testReplaceDatabaseInUrl() {
    Assert.assertEquals(backend.replaceDatabaseInUrl("jdbc:mysql://localhost:3306/mydb", "md"),
                        "jdbc:mysql://localhost:3306/md");
    Assert.assertEquals(backend.replaceDatabaseInUrl("jdbc:mysql://localhost/mydb?useSSL=false", "md"),
                        "jdbc:mysql://localhost/md?useSSL=false");
    // a slash within the parameters must not be mistaken for the path separator
    Assert.assertEquals(backend.replaceDatabaseInUrl("jdbc:mysql://localhost/mydb?serverTimezone=Europe/Berlin", "md"),
                        "jdbc:mysql://localhost/md?serverTimezone=Europe/Berlin");
    Assert.assertThrows(BackendException.class, () -> backend.replaceDatabaseInUrl("jdbc:mysql://localhost/", "md"));
  }

  @Test
  public void testValidateVersion() {
    Assert.assertThrows(BackendException.class, () -> backend.validateVersion(5, 7));
    backend.validateVersion(8, 0);
    backend.validateVersion(9, 1);
  }

}
