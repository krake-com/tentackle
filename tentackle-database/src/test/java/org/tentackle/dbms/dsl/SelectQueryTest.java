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

package org.tentackle.dbms.dsl;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.common.BMoney;
import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.Query;
import org.tentackle.dbms.StatementWrapper;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the query DSL against the database.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SelectQueryTest extends AbstractDbTest {

  private static final String POOL_TABLE = "dslpool";
  private static final String LOG_TABLE = "dsllog";

  private static final BMoney AMOUNT_1 = new BMoney(17.11, 2);
  private static final BMoney AMOUNT_2 = new BMoney(25.00, 2);

  private static final Table POOL = DSL.table(POOL_TABLE, "p");
  private static final Field<Long> ID = POOL.field("id", Long.class);
  private static final Field<String> NAME = POOL.field("name", String.class);
  private static final Field<String> REALM = POOL.field("realm", String.class);
  private static final Field<BMoney> AMOUNT = POOL.field("amount", BMoney.class);
  private static final Field<Integer> POINTS = POOL.field("points", Integer.class);
  private static final Field<Boolean> ONLINE = POOL.field("online", Boolean.class);

  private static final Table LOG = DSL.table(LOG_TABLE, "l");
  private static final Field<Long> LOG_POOL_ID = LOG.field("poolid", Long.class);
  private static final Field<String> LOG_TEXT = LOG.field("text", String.class);


  /** A row of the pool table. */
  public record PoolDTO(Long id, String name, String realm, BMoney amount, Integer points, Boolean online) { }

  /** The points per realm. */
  public record RealmDTO(String realm, Long points) { }

  /** A joined row. */
  public record LogDTO(String name, String text) { }


  /**
   * Creates and populates the test tables.
   */
  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createTestTables() {
    Db db = getSession();

    createTable(db, POOL_TABLE, List.of(
        new Column("id", Long.class), new Column("name", String.class), new Column("realm", String.class),
        new Column("amount", BMoney.class), new Column("points", Integer.class),
        new Column("online", Boolean.class)));

    createTable(db, LOG_TABLE, List.of(
        new Column("poolid", Long.class), new Column("text", String.class)));

    insertPool(db, 1L, "pool1", "r1", AMOUNT_1, 10, true);
    insertPool(db, 2L, "pool2", "r1", AMOUNT_2, 20, false);
    insertPool(db, 3L, "other", "r2", null, 30, true);

    insertLog(db, 1L, "err1");
    insertLog(db, 1L, "info");
    insertLog(db, 2L, "err2");
  }


  /**
   * Selects rows into a record DTO.
   */
  @Test
  public void testProjection() {
    List<PoolDTO> pools = DSL.select(ID, NAME, REALM, AMOUNT, POINTS, ONLINE)
                             .from(POOL)
                             .where(REALM.eq("r1"))
                             .orderBy(NAME.asc())
                             .toList(getSession(), PoolDTO.class);

    assertEquals(pools.size(), 2);
    assertEquals(pools.get(0), new PoolDTO(1L, "pool1", "r1", AMOUNT_1, 10, true));
    assertEquals(pools.get(1), new PoolDTO(2L, "pool2", "r1", AMOUNT_2, 20, false));
  }


  /**
   * Selects a value spanning two columns by an equality condition.
   */
  @Test
  public void testMultiColumnValue() {
    List<PoolDTO> pools = DSL.select(ID, NAME, REALM, AMOUNT, POINTS, ONLINE)
                             .from(POOL)
                             .where(AMOUNT.eq(AMOUNT_1))
                             .toList(getSession(), PoolDTO.class);

    assertEquals(pools.size(), 1);
    assertEquals(pools.getFirst().amount(), AMOUNT_1);

    // a value spanning more than one column is null only if all of its columns are null
    assertEquals(DSL.select(ID).from(POOL).where(AMOUNT.isNull()).getRowCount(getSession()), 1);
    assertEquals(DSL.select(ID).from(POOL).where(AMOUNT.isNotNull()).getRowCount(getSession()), 2);
  }


  /**
   * Aggregates rows and applies a having-clause.
   */
  @Test
  public void testAggregates() {
    Db db = getSession();

    List<RealmDTO> realms = DSL.select(REALM, DSL.sum(POINTS).as("points"))
                               .from(POOL)
                               .groupBy(REALM)
                               .having(DSL.count().gt(1L))
                               .orderBy(REALM.asc())
                               .toList(db, RealmDTO.class);

    assertEquals(realms.size(), 1);
    assertEquals(realms.getFirst(), new RealmDTO("r1", 30L));

    assertEquals(DSL.select(DSL.count()).from(POOL).toScalar(db, Long.class), Optional.of(3L));
    assertEquals(DSL.select(DSL.max(NAME)).from(POOL).toScalar(db, String.class), Optional.of("pool2"));
    assertEquals(DSL.select(DSL.countDistinct(REALM)).from(POOL).toScalar(db, Long.class), Optional.of(2L));
  }


  /**
   * Joins two tables.
   */
  @Test
  public void testJoin() {
    List<LogDTO> logs = DSL.select(NAME, LOG_TEXT)
                           .from(POOL)
                           .join(LOG).on(LOG_POOL_ID.eq(ID))
                           .where(LOG_TEXT.like("err%"))
                           .orderBy(LOG_TEXT.asc())
                           .toList(getSession(), LogDTO.class);

    assertEquals(logs, List.of(new LogDTO("pool1", "err1"), new LogDTO("pool2", "err2")));
  }


  /**
   * Selects by the value set conditions.
   */
  @Test
  public void testValueSets() {
    Db db = getSession();

    assertEquals(DSL.select(NAME).from(POOL).where(NAME.in(List.of("pool1", "other")))
                    .orderBy(NAME.asc()).toScalarList(db, String.class),
                 List.of("other", "pool1"));

    assertEquals(DSL.select(NAME).from(POOL).where(POINTS.between(15, 25))
                    .toScalarList(db, String.class),
                 List.of("pool2"));

    assertEquals(DSL.select(NAME).from(POOL).where(NAME.like("pool%"))
                    .orderBy(NAME.desc()).toScalarList(db, String.class),
                 List.of("pool2", "pool1"));

    // an empty value set matches nothing at all
    assertTrue(DSL.select(NAME).from(POOL).where(NAME.in(List.of())).toScalarList(db, String.class).isEmpty());
  }


  /**
   * Selects by a sub-select.
   */
  @Test
  public void testSubSelect() {
    Db db = getSession();

    assertEquals(DSL.select(NAME).from(POOL)
                    .where(DSL.exists(DSL.select(LOG_POOL_ID).from(LOG).where(LOG_POOL_ID.eq(ID))))
                    .orderBy(NAME.asc())
                    .toScalarList(db, String.class),
                 List.of("pool1", "pool2"));

    assertEquals(DSL.select(NAME).from(POOL)
                    .where(ID.notIn(DSL.select(LOG_POOL_ID).from(LOG)))
                    .toScalarList(db, String.class),
                 List.of("other"));
  }


  /**
   * Limits the number of rows and skips rows.
   */
  @Test
  public void testLimitAndOffset() {
    Db db = getSession();
    Select select = DSL.select(NAME).from(POOL).orderBy(NAME.asc()).limit(1).offset(1);

    assertEquals(select.toScalarList(db, String.class), List.of("pool1"));

    // the row count refers to the query without limit and offset
    assertEquals(select.getRowCount(db), 3);
  }


  /**
   * Retrieves at most one row.
   */
  @Test
  public void testOptional() {
    Db db = getSession();

    Optional<PoolDTO> pool = DSL.select(ID, NAME, REALM, AMOUNT, POINTS, ONLINE)
                                .from(POOL).where(NAME.eq("pool1"))
                                .toOptional(db, PoolDTO.class);
    assertTrue(pool.isPresent());
    assertEquals(pool.get().name(), "pool1");

    assertFalse(DSL.select(ID, NAME, REALM, AMOUNT, POINTS, ONLINE)
                   .from(POOL).where(NAME.eq("nosuchpool"))
                   .toOptional(db, PoolDTO.class).isPresent());

    // more than one row is an error for both the DTO- and the scalar variant
    PersistenceException pex = expectThrows(PersistenceException.class,
        () -> DSL.select(ID, NAME, REALM, AMOUNT, POINTS, ONLINE).from(POOL).toOptional(db, PoolDTO.class));
    assertTrue(pex.getMessage().contains("more than one row"), "unexpected message: " + pex.getMessage());

    pex = expectThrows(PersistenceException.class, () -> DSL.select(NAME).from(POOL).toScalar(db, String.class));
    assertTrue(pex.getMessage().contains("more than one row"), "unexpected message: " + pex.getMessage());
  }


  /**
   * Reuses a cached prepared statement.
   */
  @Test
  public void testStatementCaching() {
    Db db = getSession();
    Select select = DSL.select(NAME).from(POOL).where(REALM.eq("r1")).orderBy(NAME.asc()).statementCached(true);

    for (int loop = 0; loop < 3; loop++) {
      assertEquals(select.toScalarList(db, String.class), List.of("pool1", "pool2"));
    }
  }


  // ---------------------- test data ---------------------------

  /**
   * A column of a test table.
   *
   * @param name the column name
   * @param type the java type
   */
  private record Column(String name, Class<?> type) { }


  /**
   * Creates a test table.
   *
   * @param db the session
   * @param tableName the table name
   * @param columns the columns
   */
  private void createTable(Db db, String tableName, List<Column> columns) {
    Backend backend = db.getBackend();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(tableName, null));

    for (int columnNdx = 0; columnNdx < columns.size(); columnNdx++) {
      Column column = columns.get(columnNdx);
      DataType<?> dataType = DataTypeFactory.getInstance().get(column.type());
      List<String> columnNames = dataType.createColumnNames(backend, column.name());

      for (int index = 0; index < columnNames.size(); index++) {
        SqlType sqlType = dataType.getSqlType(backend, index);
        ddl.append("    ").append(backend.sqlCreateColumn(
            columnNames.get(index), null, sqlType,
            dataType.getSize(backend, index, null), dataType.getScale(backend, index, null),
            true, null, false,
            columnNdx < columns.size() - 1 || index < columnNames.size() - 1));
      }
    }

    ddl.append(backend.sqlCreateTableClosing(tableName, null));

    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(ddl.toString());
    }
  }

  /**
   * Inserts a row into the pool table.
   *
   * @param db the session
   * @param id the id
   * @param name the name
   * @param realm the realm
   * @param amount the amount, null if none
   * @param points the points
   * @param online true if online
   */
  private void insertPool(Db db, Long id, String name, String realm, BMoney amount, Integer points, Boolean online) {
    Query query = new Query();
    if (amount == null) {
      // null values cannot be applied as parameters
      query.add("INSERT INTO " + POOL_TABLE +
                " (id, name, realm, amount, amount_2, points, online) VALUES (?, ?, ?, NULL, NULL, ?, ?)",
                id, name, realm, points, online);
    }
    else {
      query.add("INSERT INTO " + POOL_TABLE +
                " (id, name, realm, amount, amount_2, points, online) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, name, realm, amount, points, online);
    }
    executeUpdate(db, query);
  }

  /**
   * Inserts a row into the log table.
   *
   * @param db the session
   * @param poolId the id of the pool
   * @param text the text
   */
  private void insertLog(Db db, Long poolId, String text) {
    Query query = new Query();
    query.add("INSERT INTO " + LOG_TABLE + " (poolid, text) VALUES (?, ?)", poolId, text);
    executeUpdate(db, query);
  }

  /**
   * Executes an update query.
   *
   * @param db the session
   * @param query the query
   */
  private void executeUpdate(Db db, Query query) {
    try (PreparedStatementWrapper st = db.createPreparedStatement(query::buildInnerSql)) {
      query.applyParameters(st, 1);
      assertEquals(st.executeUpdate(), 1);
    }
  }

}
