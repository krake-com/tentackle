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

import org.tentackle.sql.Backend;
import org.tentackle.sql.MigrationStrategy;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ColumnMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.TableMetaData;
import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the migration strategy the backends derive from the difference between the model and the
 * database.
 * <p>
 * The strategy list is what the SQL plugin turns into {@code ALTER} statements, and its order is
 * part of the contract: a rename has to come first, because every following statement addresses the
 * column by its new name. A missing entry silently leaves the database behind the model, a spurious
 * one rewrites a column for nothing.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MigrationStrategyTest {

  private static final String COLUMN = "name";
  private static final String COMMENT = "the name";


  /**
   * Creates the metadata of an existing {@code VARCHAR(32) NOT NULL} column without a default.
   *
   * @param backend the backend
   * @return the column metadata
   */
  private ColumnMetaData column(Backend backend) {
    TableMetaData table = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    ColumnMetaData column = new ColumnMetaData(table);
    column.setColumnName(COLUMN);
    column.setComment(COMMENT);
    column.setType(Types.VARCHAR);
    column.setTypeName("VARCHAR");
    column.setSize(32);
    column.setScale(0);
    column.setNullable(false);
    column.setDefaultValue(null);
    return column;
  }

  /**
   * Compares the column against the model attribute it was created from.
   *
   * @param backend the backend
   * @param column the existing column
   * @return the strategies
   */
  private List<MigrationStrategy> unchanged(Backend backend, ColumnMetaData column) {
    return List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT, SqlType.VARCHAR, 32, 0, false, null));
  }


  /**
   * Creates the metadata of an existing column the driver reports as {@link Types#OTHER}.
   *
   * @param backend the backend
   * @param typeName the backend-specific type name
   * @return the column metadata
   */
  private ColumnMetaData otherColumn(Backend backend, String typeName) {
    TableMetaData table = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    ColumnMetaData column = new ColumnMetaData(table);
    column.setColumnName(COLUMN);
    column.setComment(COMMENT);
    column.setType(Types.OTHER);
    column.setTypeName(typeName);
    column.setSize(0);
    column.setScale(0);
    column.setNullable(false);
    column.setDefaultValue(null);
    return column;
  }


  /**
   * A JSON column must be recognized as one, or the migration would alter it on every single run.
   * <p>
   * The JDBC type alone cannot tell: pgjdbc reports {@code uuid}, {@code json} and {@code jsonb} all
   * as {@link Types#OTHER}, so only the type name disambiguates them.
   */
  @Test
  public void testJsonColumnIsRecognized() {
    for (Backend backend : List.of(new Postgres(), new H2())) {
      ColumnMetaData column = otherColumn(backend, backend instanceof Postgres ? "jsonb" : "JSON");
      assertTrue(column.matchesSqlType(SqlType.JSON), backend + ": a json column must match a JSON attribute");
      assertFalse(column.matchesSqlType(SqlType.UUID), backend + ": a json column is not a uuid one");
      assertEquals(List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT, SqlType.JSON, 0, 0, false, null)),
                   List.of(), backend + ": an unchanged json column needs no migration");
    }
  }

  /**
   * The converse: a uuid column must not be mistaken for a JSON one, which would make the
   * migrator rename or alter it for nothing.
   */
  @Test
  public void testUuidColumnIsNotMistakenForJson() {
    for (Backend backend : List.of(new Postgres(), new H2())) {
      ColumnMetaData column = otherColumn(backend, "uuid");
      assertTrue(column.matchesSqlType(SqlType.UUID), backend + ": a uuid column must match a UUID attribute");
      assertFalse(column.matchesSqlType(SqlType.JSON), backend + ": a uuid column is not a json one");
    }
  }

  /**
   * A backend without a native JSON type emulates it as CLOB, so such a column must match a
   * JSON attribute as well.
   */
  @Test
  public void testEmulatedJsonColumnIsRecognized() {
    Backend backend = new Oracle();
    ColumnMetaData column = column(backend);
    column.setType(Types.CLOB);
    column.setTypeName("CLOB");
    column.setSize(0);
    assertTrue(column.matchesSqlType(SqlType.JSON), backend + ": a CLOB column must match a JSON attribute");
  }

  @Test
  public void testNothingChanged() {
    Backend backend = new H2();
    assertEquals(unchanged(backend, column(backend)), List.of(), "an unchanged column needs no migration");
  }

  @Test
  public void testRenamedColumn() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), "label", COMMENT,
                                                      SqlType.VARCHAR, 32, 0, false, null)),
                 List.of(MigrationStrategy.NAME));
  }

  @Test
  public void testChangedComment() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, "another comment",
                                                      SqlType.VARCHAR, 32, 0, false, null)),
                 List.of(MigrationStrategy.COMMENT));
  }

  @Test
  public void testChangedNullConstraint() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 32, 0, true, null)),
                 List.of(MigrationStrategy.NULL));
  }

  @Test
  public void testChangedDefault() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 32, 0, false, "x")),
                 List.of(MigrationStrategy.DEFAULT));
  }

  /**
   * The default read back from the database is a literal, so it only compares equal to the model's
   * value after being parsed according to the column's type.
   */
  @Test
  public void testDefaultIsComparedAsALiteral() {
    Backend backend = new H2();
    ColumnMetaData column = column(backend);
    column.setDefaultValue("'x'");
    assertEquals(unchanged(backend, column), List.of(MigrationStrategy.DEFAULT),
                 "the model has no default, the column has one");
    assertEquals(List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 32, 0, false, "x")),
                 List.of(), "the quoted literal is the same default as the plain model value");
  }

  @Test
  public void testChangedType() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.INTEGER, 0, 0, false, null)),
                 List.of(MigrationStrategy.TYPE));
  }

  /**
   * A column may grow, and that is a plain type change.
   */
  @Test
  public void testGrownSize() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 64, 0, false, null)),
                 List.of(MigrationStrategy.TYPE));
  }

  /**
   * Shrinking a column would truncate the data, so the migration only warns about it in a comment
   * rather than executing it.
   */
  @Test
  public void testShrunkSizeOnlyWarns() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 16, 0, false, null)),
                 List.of(MigrationStrategy.TYPE_WARNING));
  }

  /**
   * A model that drops the size means "as large as the backend allows", which is a change unless
   * the column already is that large.
   */
  @Test
  public void testSizeDroppedFromTheModel() {
    Backend backend = new Db2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 0, 0, false, null)),
                 List.of(MigrationStrategy.TYPE));

    ColumnMetaData maximal = column(backend);
    maximal.setSize(backend.getMaxSize(SqlType.VARCHAR));
    assertEquals(List.of(backend.getMigrationStrategy(maximal, COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 0, 0, false, null)),
                 List.of(), "the column already has the maximum size");
  }

  /**
   * H2 reports an unbounded {@code VARCHAR} with the size {@link Integer#MAX_VALUE} rather than with
   * the limit it accepts in the DDL, so that is what a model without a size has to compare against.
   */
  @Test
  public void testSizeDroppedFromTheModelOnH2() {
    Backend backend = new H2();
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 0, 0, false, null)),
                 List.of(MigrationStrategy.TYPE));

    ColumnMetaData unbounded = column(backend);
    unbounded.setSize(Integer.MAX_VALUE);
    assertEquals(List.of(backend.getMigrationStrategy(unbounded, COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 0, 0, false, null)),
                 List.of(), "the column is already unbounded");
  }

  /**
   * A sizeless type such as INTEGER must not be migrated just because the model mentions no size.
   */
  @Test
  public void testSizelessTypeIgnoresTheSize() {
    Backend backend = new H2();
    ColumnMetaData column = column(backend);
    column.setType(Types.INTEGER);
    column.setTypeName("INTEGER");
    column.setSize(10);
    assertEquals(List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT,
                                                      SqlType.INTEGER, 0, 0, false, null)),
                 List.of(), "INTEGER has no size to compare");
  }

  @Test
  public void testChangedScale() {
    Backend backend = new H2();
    ColumnMetaData column = column(backend);
    column.setType(Types.DECIMAL);
    column.setTypeName("DECIMAL");
    column.setSize(20);
    column.setScale(2);
    assertEquals(List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT,
                                                      SqlType.DECIMAL, 20, 4, false, null)),
                 List.of(MigrationStrategy.TYPE), "a growing scale is migrated");
    assertEquals(List.of(backend.getMigrationStrategy(column, COLUMN, COMMENT,
                                                      SqlType.DECIMAL, 20, 1, false, null)),
                 List.of(MigrationStrategy.TYPE_WARNING), "a shrinking scale only warns");
  }

  /**
   * The rename has to come first, because every following statement addresses the column by its
   * new name.
   */
  @Test
  public void testStrategyOrder() {
    Backend backend = new H2();
    MigrationStrategy[] strategies = backend.getMigrationStrategy(column(backend), "label", "another comment",
                                                                  SqlType.INTEGER, 0, 0, true, 0);
    assertEquals(List.of(strategies), List.of(MigrationStrategy.NAME, MigrationStrategy.TYPE,
                                              MigrationStrategy.NULL, MigrationStrategy.DEFAULT,
                                              MigrationStrategy.COMMENT));
  }

  /**
   * Postgres cannot change the type and the null constraint in one statement, but it arrives at the
   * same set of strategies as the standard implementation.
   */
  @Test
  public void testPostgresAgreesWithTheStandardStrategy() {
    Backend backend = new Postgres();
    assertEquals(unchanged(backend, column(backend)), List.of());
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 64, 0, false, null)),
                 List.of(MigrationStrategy.TYPE));
    assertEquals(List.of(backend.getMigrationStrategy(column(backend), COLUMN, COMMENT,
                                                      SqlType.VARCHAR, 32, 0, true, null)),
                 List.of(MigrationStrategy.NULL));
  }

}
