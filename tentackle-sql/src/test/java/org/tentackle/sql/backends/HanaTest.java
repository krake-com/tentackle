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
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the SAP HANA backend.
 * <p>
 * HANA cannot be reached from the build, so the generated SQL is asserted verbatim. The interesting
 * parts are the column alterations, which replace the whole declaration, and the paging, which needs
 * a LIMIT for every OFFSET.
 */
@SuppressWarnings("missing-explicit-ctor")
public class HanaTest {

  private static final String COLUMN = "name";
  private static final String COMMENT = "the name";

  private final Hana backend = new Hana();


  @Test
  public void testValidateVersion() {
    expectThrows(BackendException.class, () -> backend.validateVersion(1, 0));
    backend.validateVersion(2, 0);
    backend.validateVersion(4, 0);    // HANA Cloud
  }

  @Test
  public void testBuildSelectSql() {
    assertEquals(backend.buildSelectSql("* FROM foo", false, 0, 0), "SELECT * FROM foo");
    assertEquals(backend.buildSelectSql("* FROM foo", false, 10, 0), "SELECT * FROM foo LIMIT ?");
    // the locking clause comes last
    assertEquals(backend.buildSelectSql("* FROM foo", true, 10, 20), "SELECT * FROM foo LIMIT ? OFFSET ? FOR UPDATE");
    // there is no OFFSET without a LIMIT
    assertEquals(backend.buildSelectSql("* FROM foo", false, 0, 20), "SELECT * FROM foo LIMIT ? OFFSET ?");
  }

  /**
   * An offset without a limit must still bind a limit, since the SQL contains one.
   */
  @Test
  public void testOffsetWithoutLimitBindsMaximumLimit() {
    List<Integer> values = new ArrayList<>();
    BackendPreparedStatement stmt = recorder(values);
    int index = backend.setLeadingSelectParameters(stmt, 0, 20);
    index = backend.setTrailingSelectParameters(stmt, index, 0, 20);
    assertEquals(values, List.of(Hana.MAX_LIMIT, 20));
    assertEquals(index, 3);
  }

  @Test
  public void testTypeMapping() {
    // HANA's TINYINT is unsigned and cannot hold a negative byte
    assertEquals(backend.columnTypeToString(SqlType.TINYINT, 0, 0), "SMALLINT");
    // VARCHAR is ASCII-only on HANA 2.0
    assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 32, 0), "NVARCHAR(32)");
    assertEquals(backend.columnTypeToString(SqlType.VARCHAR, 0, 0), "NVARCHAR(5000)");
    assertEquals(backend.columnTypeToString(SqlType.CHAR, 0, 0), "NCHAR(1)");
    assertEquals(backend.columnTypeToString(SqlType.CLOB, 0, 0), "NCLOB");
    assertEquals(backend.columnTypeToString(SqlType.BIT, 0, 0), "BOOLEAN");
    assertEquals(backend.columnTypeToString(SqlType.DECIMAL, 0, 0), "DECIMAL");
    assertFalse(backend.isUUIDSupported());
  }

  @Test
  public void testColumnDeclaration() {
    // the default comes before the null constraint
    assertEquals(backend.columnTypeNullDefaultToString("flag", SqlType.BIT, 0, 0, false, true),
                 "BOOLEAN DEFAULT TRUE NOT NULL");
  }

  /**
   * An alteration replaces the whole declaration, so the null constraint and the default must
   * always be part of it.
   */
  @Test
  public void testAlterColumnReplacesTheWholeDeclaration() {
    assertEquals(backend.sqlAlterColumnType("foo", COLUMN, null, SqlType.VARCHAR, 64, 0, false, "x"),
                 "ALTER TABLE foo ALTER (name NVARCHAR(64) DEFAULT 'x' NOT NULL);\n");
    assertEquals(backend.sqlAlterColumnNullConstraint("foo", COLUMN, SqlType.VARCHAR, 64, 0, true, null),
                 "ALTER TABLE foo ALTER (name NVARCHAR(64) NULL);\n");
    assertNull(backend.sqlAlterColumnDefault("foo", COLUMN, SqlType.VARCHAR, "x"),
               "the default cannot be changed alone");
  }

  @Test
  public void testDdl() {
    assertEquals(backend.sqlAddColumn("foo", "extra", null, SqlType.INTEGER, 0, 0, true, null),
                 "ALTER TABLE foo ADD (extra INTEGER);\n");
    assertEquals(backend.sqlDropColumn("foo", "extra"), "ALTER TABLE foo DROP (extra);\n");
    assertEquals(backend.sqlRenameTable("foo", "bar"), "RENAME TABLE foo TO bar;\n");
    assertEquals(backend.sqlRenameColumn("foo", COLUMN, "label"), "RENAME COLUMN foo.name TO label;\n");
    assertEquals(backend.sqlRenameIndex("s.foo", "s.old", "new"), "RENAME INDEX s.old TO new;\n");
    assertEquals(backend.sqlNextFromSequence("numpool_seq"), "SELECT numpool_seq.NEXTVAL FROM DUMMY");
    assertEquals(backend.getDummySelect(), "SELECT 1 FROM DUMMY");
  }

  /**
   * Since the default cannot be changed alone, a changed default must be migrated along with the type,
   * and a changed null constraint carries both.
   */
  @Test
  public void testMigrationStrategy() {
    assertEquals(strategies(column(), COLUMN, COMMENT, 32, false, null), List.of());
    assertEquals(strategies(column(), COLUMN, COMMENT, 32, false, "x"), List.of(MigrationStrategy.TYPE));
    assertEquals(strategies(column(), COLUMN, COMMENT, 64, false, null), List.of(MigrationStrategy.TYPE));
    assertEquals(strategies(column(), COLUMN, COMMENT, 64, true, "x"), List.of(MigrationStrategy.NULL));
    assertEquals(strategies(column(), COLUMN, COMMENT, 16, false, null), List.of(MigrationStrategy.TYPE_WARNING));
    assertEquals(strategies(column(), "label", "another comment", 32, false, null),
                 List.of(MigrationStrategy.NAME, MigrationStrategy.COMMENT));
  }

  @Test
  public void testNames() {
    assertTrue(backend.getReservedWords().contains("TOP"));
    assertTrue(backend.getReservedWords().contains("LIMIT"));
    assertTrue(backend.isReservedSchemaName("SYS"));
    assertFalse(backend.isReservedSchemaName("myschema"));
    assertTrue(backend.isSchemaSupported());
    assertEquals(backend.getMaxNameLength(null), 127);
  }


  /**
   * Creates the metadata of an existing {@code NVARCHAR(32) NOT NULL} column without a default.
   *
   * @return the column metadata
   */
  private ColumnMetaData column() {
    TableMetaData table = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    ColumnMetaData column = new ColumnMetaData(table);
    column.setColumnName(COLUMN);
    column.setComment(COMMENT);
    column.setType(Types.NVARCHAR);
    column.setTypeName("NVARCHAR");
    column.setSize(32);
    column.setScale(0);
    column.setNullable(false);
    column.setDefaultValue(null);
    return column;
  }

  private List<MigrationStrategy> strategies(ColumnMetaData column, String columnName, String comment,
                                             int size, boolean nullable, Object defaultValue) {
    return List.of(backend.getMigrationStrategy(column, columnName, comment, SqlType.VARCHAR, size, 0,
                                                nullable, defaultValue));
  }

  /**
   * Creates a statement recording the ints bound.
   *
   * @param values the recorded values
   * @return the statement proxy
   */
  static BackendPreparedStatement recorder(List<Integer> values) {
    return (BackendPreparedStatement) Proxy.newProxyInstance(
        BackendPreparedStatement.class.getClassLoader(),
        new Class<?>[] { BackendPreparedStatement.class },
        (_, method, args) -> {
          if ("setInt".equals(method.getName())) {
            values.add((Integer) args[1]);
          }
          return null;
        });
  }

}
