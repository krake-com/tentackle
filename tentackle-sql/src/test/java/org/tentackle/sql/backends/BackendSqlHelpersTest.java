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
import org.tentackle.sql.JoinType;
import org.tentackle.sql.SqlNameType;
import org.tentackle.sql.SqlType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the SQL fragments the backends assemble for the query layer.
 * <p>
 * The join helpers are the delicate part: they rewrite a generated select in place, relying on the
 * {@code WHERE 1=1} the wurblets always emit as the anchor for the query conditions. Getting the
 * insertion point wrong yields SQL that still parses but selects the wrong rows.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class BackendSqlHelpersTest {

  private static final String SELECT = "SELECT foo.* FROM foo" + Backend.SQL_WHEREALL;

  private Connection connection;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:sqlhelpers");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE foo (id BIGINT NOT NULL PRIMARY KEY, bar_id BIGINT, name VARCHAR(32))");
      stmt.execute("CREATE TABLE bar (id BIGINT NOT NULL PRIMARY KEY, label VARCHAR(32))");
      stmt.execute("INSERT INTO bar VALUES (1, 'one')");
      stmt.execute("INSERT INTO foo VALUES (10, 1, 'ten')");
      stmt.execute("INSERT INTO foo VALUES (11, NULL, 'eleven')");
    }
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


  // ---- comments ------------------------------------------------------------

  @Test(dataProvider = "backends")
  public void testSqlComment(Backend backend) {
    String prefix = backend.getSingleLineComment();
    assertEquals(backend.sqlComment("hello"), prefix + " hello\n", backend.toString());
    assertEquals(backend.sqlComment("line1\nline2"), prefix + " line1\n" + prefix + " line2\n", backend.toString());
    // an already commented line must not be commented twice
    assertEquals(backend.sqlComment(prefix + " already"), prefix + " already\n", backend.toString());
    assertNull(backend.sqlComment(null), backend.toString());
  }


  // ---- joins ---------------------------------------------------------------

  @Test(dataProvider = "backends")
  public void testSqlJoin(Backend backend) {
    assertEquals(backend.sqlJoin(JoinType.INNER, "bar", null, "bar.id = foo.bar_id"),
                 " JOIN bar ON bar.id = foo.bar_id", backend.toString());
    assertEquals(backend.sqlJoin(JoinType.LEFT, "bar", "b", "b.id = foo.bar_id"),
                 " LEFT JOIN bar" + backend.sqlAsBeforeTableAlias() + "b ON b.id = foo.bar_id", backend.toString());
  }

  /**
   * The joined select is spliced in between the FROM clause and the {@code WHERE 1=1},
   * so that the conditions appended afterwards still land in the WHERE clause.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testSqlJoinSelects(Backend backend) {
    String joined = backend.sqlJoinSelects(JoinType.LEFT, true, SELECT,
                                           "SELECT bar.* FROM bar" + Backend.SQL_WHEREALL,
                                           null, "b", "b.id = foo.bar_id");
    assertTrue(joined.startsWith("SELECT foo.*,b.* FROM foo LEFT JOIN ("), joined);
    assertTrue(joined.endsWith(Backend.SQL_WHEREALL), backend + ": the WHERE 1=1 anchor must be preserved: " + joined);
    assertFalse(joined.contains("1=1)"), backend + ": the inner WHERE 1=1 must be optimized away: " + joined);

    // without the extra columns
    assertFalse(backend.sqlJoinSelects(JoinType.INNER, false, SELECT, "SELECT bar.* FROM bar", null, "b", "b.id = 1")
                    .startsWith("SELECT foo.*,b.*"), backend.toString());
  }

  /**
   * Joining a plain table name rather than a select needs no parentheses.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testSqlJoinSelectsWithPlainTable(Backend backend) {
    String joined = backend.sqlJoinSelects(JoinType.INNER, false, SELECT, "bar", null, "b", "b.id = foo.bar_id");
    assertEquals(joined, "SELECT foo.* FROM foo JOIN bar" + backend.sqlAsBeforeTableAlias() +
                         "b ON b.id = foo.bar_id" + Backend.SQL_WHEREALL, backend.toString());
  }

  /**
   * The id alias is added to the joined select's column list so that the caller can refer to it.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testSqlJoinSelectsWithIdAlias(Backend backend) {
    String joined = backend.sqlJoinSelects(JoinType.INNER, false, SELECT,
                                           "SELECT bar.label FROM bar", "bar.id AS barid", "b", "b.barid = foo.bar_id");
    assertTrue(joined.contains("SELECT bar.label,bar.id AS barid FROM bar"), joined);
  }

  /**
   * The rewriting relies on the shape the wurblets generate and must not silently produce
   * nonsense for anything else.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testSqlJoinSelectsRejectsMalformedSql(Backend backend) {
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.sqlJoinSelects(JoinType.INNER, false, "SELECT foo.*",
                                                         "bar", null, "b", "b.id = 1"))
                   .getMessage().contains("FROM"), backend.toString());
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.sqlJoinSelects(JoinType.INNER, false, "SELECT foo.* FROM foo",
                                                         "bar", null, "b", "b.id = 1"))
                   .getMessage().contains("WHERE 1=1"), backend.toString());
    expectThrows(BackendException.class,
                 () -> backend.sqlJoinSelects(JoinType.INNER, false, SELECT, "SELECT bar.*", "bar.id", "b", "b.id = 1"));
  }

  /**
   * The generated join must be SQL H2 really accepts and must find the joined row.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testJoinedSelectRunsOnH2() throws SQLException {
    H2 backend = new H2();
    String joined = backend.optimizeSql(
        backend.sqlJoinSelects(JoinType.INNER, true, SELECT,
                               "SELECT bar.* FROM bar" + Backend.SQL_WHEREALL,
                               null, "b", "b.id = foo.bar_id"));
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery(joined)) {
      assertTrue(rs.next(), "the joined row must be found: " + joined);
      assertEquals(rs.getLong("id"), 10L);
      assertEquals(rs.getString("label"), "one");
      assertFalse(rs.next(), "the row without a bar must not be joined");
    }
  }


  // ---- functions and literals ----------------------------------------------

  @Test(dataProvider = "backends")
  public void testSqlFunction(Backend backend) {
    assertEquals(backend.sqlFunction("upper", "name"), "UPPER(name)", backend.toString());
    assertEquals(backend.sqlFunction("count", null), "COUNT()", backend.toString());
  }

  @Test(dataProvider = "backends")
  public void testQuotedString(Backend backend) {
    assertEquals(backend.toQuotedString("abc"), "'abc'", backend.toString());
    assertEquals(backend.toQuotedString("it's"), "'it''s'", backend + ": a quote must be doubled");
    assertEquals(backend.toQuotedString(null), "''", backend.toString());
  }

  @Test(dataProvider = "backends")
  public void testValueToLiteral(Backend backend) {
    assertEquals(backend.valueToLiteral(SqlType.VARCHAR, "abc"), "'abc'", backend.toString());
    assertEquals(backend.valueToLiteral(SqlType.INTEGER, 42), "42", backend.toString());
    // oracle has no boolean type and renders it as a number
    assertEquals(backend.valueToLiteral(SqlType.BIT, true),
                 backend instanceof Oracle8 ? "1" : "'true'", backend.toString());
  }

  @Test(dataProvider = "backends")
  public void testOptimizeSql(Backend backend) {
    // the WHERE 1=1 anchor disappears once the conditions are known
    assertEquals(backend.optimizeSql("SELECT * FROM foo" + Backend.SQL_WHEREAND + "id=?"),
                 "SELECT * FROM foo WHERE id=?", backend.toString());
    assertEquals(backend.optimizeSql("SELECT * FROM foo" + Backend.SQL_WHEREOR + "id=?"),
                 "SELECT * FROM foo WHERE id=?", backend.toString());
    assertEquals(backend.optimizeSql("SELECT * FROM foo" + Backend.SQL_WHEREALL),
                 "SELECT * FROM foo", backend.toString());
  }


  // ---- names ---------------------------------------------------------------

  @Test(dataProvider = "backends")
  public void testReservedWords(Backend backend) {
    assertTrue(backend.getReservedWords().contains("SELECT"), backend + ": SELECT must be reserved");
    assertFalse(backend.getReservedWords().contains("MYCOLUMN"), backend.toString());
    // isReservedTableName and isReservedSchemaName check the same set, case-insensitively
    assertTrue(backend.isReservedTableName("select"), backend + ": the check is case-insensitive");
    assertFalse(backend.isReservedTableName("mytable"), backend.toString());
  }

  /**
   * A name that the database would truncate, reject or resolve to something else must be
   * refused while the DDL is generated, not when it is run.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testAssertValidName(Backend backend) {
    backend.assertValidName(SqlNameType.COLUMN_NAME, "mycolumn");
    backend.assertValidName(SqlNameType.TABLE_NAME, null);   // nothing to check

    int maxNameLength = backend.getMaxNameLength(SqlNameType.COLUMN_NAME);
    if (maxNameLength < 1024) {    // some backends impose no practical limit at all
      String tooLong = "x".repeat(maxNameLength + 1);
      assertTrue(expectThrows(BackendException.class,
                              () -> backend.assertValidName(SqlNameType.COLUMN_NAME, tooLong))
                     .getMessage().contains("characters long"), backend.toString());
      backend.assertValidName(SqlNameType.COLUMN_NAME, tooLong.substring(1));
    }

    assertTrue(expectThrows(BackendException.class,
                            () -> backend.assertValidName(SqlNameType.COLUMN_NAME, "select"))
                   .getMessage().contains("reserved word"), backend.toString());

    // the prefix the migration marks its own scratch objects with is off limits for the model.
    // Oracle uses "tmp_" because it does not allow an identifier to start with an underscore.
    String temporary = backend.isTemporaryName("_scratch") ? "_scratch" : "tmp_scratch";
    assertTrue(backend.isTemporaryName(temporary), backend + ": no temporary name prefix known");
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.assertValidName(SqlNameType.TABLE_NAME, temporary))
                   .getMessage().contains("temporary identifier"), backend.toString());
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.assertValidName(SqlNameType.TABLE_NAME, temporary + ".foo"))
                   .getMessage().contains("temporary identifier"), backend + ": also in the schema part");
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.assertValidName(SqlNameType.INDEX_NAME, temporary))
                   .getMessage().contains("temporary identifier"), backend.toString());
  }


  // ---- arrays --------------------------------------------------------------

  /**
   * An {@code IN (?)} over an array is a backend extension; whoever claims to support the operator
   * must be able to bind the array.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testArrayBinding() throws SQLException {
    H2 backend = new H2();
    assertTrue(backend.isArrayOperatorSupported(Backend.SQL_ARRAY_ALL));
    assertTrue(backend.isArrayOperatorSupported(Backend.SQL_ARRAY_ANY));
    assertFalse(backend.isArrayOperatorSupported(Backend.SQL_ARRAY_IN), "H2 has no IN over an array");

    try (PreparedStatement stmt = connection.prepareStatement(
             "SELECT id FROM foo WHERE id = " + Backend.SQL_ARRAY_ANY_PAR + " ORDER BY id")) {
      backend.setArray(stmt, 1, SqlType.BIGINT, List.of(10L, 11L), Backend.SQL_ARRAY_ANY);
      try (ResultSet rs = stmt.executeQuery()) {
        assertTrue(rs.next());
        assertEquals(rs.getLong(1), 10L);
        assertTrue(rs.next());
        assertEquals(rs.getLong(1), 11L);
        assertFalse(rs.next());
      }
    }
  }

  @Test(dataProvider = "backends")
  public void testArrayOperatorSupportIsDecidable(Backend backend) {
    // the answer may be no, but it must not blow up
    for (String operator : new String[] { Backend.SQL_ARRAY_IN, Backend.SQL_ARRAY_NOT_IN,
                                          Backend.SQL_ARRAY_ANY, Backend.SQL_ARRAY_ALL, "NONSENSE" }) {
      backend.isArrayOperatorSupported(operator);
    }
    assertFalse(backend.isArrayOperatorSupported("NONSENSE"), backend.toString());
  }

}
