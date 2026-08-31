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

package org.tentackle.dbms;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ObjectId}, the counter-table based {@link IdSource} used for backends without
 * sequences, completing the {@code IdSource} coverage alongside {@link ObjectSequenceIdTest}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ObjectIdTest extends AbstractDbTest {

  private static final String TABLE_NAME = "objectidtest";
  private static final String EMPTY_TABLE_NAME = "objectidtest_empty";

  /*
   * ObjectId caches its prepared statements by StatementKey(id, getClass()) - the class alone,
   * not the configured table name (see ObjectId#nextId). Two ObjectId instances with different
   * table names would therefore silently share (and corrupt) each other's cached statements
   * unless they are of different classes. Real applications never hit this because there is
   * normally exactly one id source per name; this test uses two distinct subclasses to keep
   * their statement caches apart.
   */
  private static class CounterObjectId extends ObjectId {
    CounterObjectId(String name) {
      super(name);
    }
  }

  private static class EmptyCounterObjectId extends ObjectId {
    EmptyCounterObjectId(String name) {
      super(name);
    }
  }

  public ObjectIdTest() {
    super(TransactionType.NONE);   // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }

  private void executeUpdate(Db db, String sql) {
    // each StatementWrapper is single-use: it detaches the session once executeUpdate() returns
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createCounterTables() {
    Db db = getSession();
    Backend backend = db.getBackend();
    // TransactionType.NONE means no transaction is active yet here: the seed row must be
    // committed explicitly, or it would not be durably visible to the test methods' own transactions
    long voucher = db.begin("createCounterTables");
    for (String tableName : new String[] { TABLE_NAME, EMPTY_TABLE_NAME }) {
      StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(tableName, null));
      ddl.append("    ").append(backend.sqlCreateColumn("id", null, SqlType.BIGINT, 0, 0, false, null, false, false));
      ddl.append(backend.sqlCreateTableClosing(tableName, null));
      executeUpdate(db, ddl.toString());
    }
    executeUpdate(db, "INSERT INTO " + TABLE_NAME + " (id) VALUES (0)");
    // EMPTY_TABLE_NAME is left without any row on purpose
    db.commit(voucher);
  }

  private long selectPersistedValue(Db db, String tableName) {
    try (StatementWrapper st = db.createStatement();
         ResultSetWrapper rs = st.executeQuery("SELECT id FROM " + tableName)) {
      assertTrue(rs.next());
      return rs.getLong(1);
    }
  }


  @Test
  public void testNextIdIncrementsAndPersists() {
    Db db = getSession();
    ObjectId source = new CounterObjectId(TABLE_NAME);
    long voucher = db.begin("nextIdIncrementsTest");

    long first = source.nextId(db);
    long second = source.nextId(db);
    long third = source.nextId(db);
    assertEquals(second, first + 1);
    assertEquals(third, first + 2);

    db.commit(voucher);
    assertEquals(selectPersistedValue(db, TABLE_NAME), third,
                 "after commit the persisted counter must match the last id returned");
  }


  @Test
  public void testNextIdWithinSameTransactionOptimizes() {
    Db db = getSession();
    ObjectId source = new CounterObjectId(TABLE_NAME);
    long voucher = db.begin("nextIdOptimizesTest");

    long first = source.nextId(db);   // this call alone does the real UPDATE+SELECT roundtrip
    long persistedAfterFirst = selectPersistedValue(db, TABLE_NAME);
    assertEquals(persistedAfterFirst, first, "the first call must have written its increment already");

    long second = source.nextId(db);  // not used, just skip
    long third = source.nextId(db);
    assertNotEquals(persistedAfterFirst, third, "nextId() must keep returning fresh values even though the row isn't touched again yet");
    assertEquals(selectPersistedValue(db, TABLE_NAME), persistedAfterFirst,
                 "further calls within the same transaction must not hit the database until commit");

    db.commit(voucher);
    assertEquals(selectPersistedValue(db, TABLE_NAME), third,
                 "commit must flush the final counter value via the registered pre-commit hook");
  }


  @Test
  public void testIsLockFree() {
    ObjectId source = new CounterObjectId(TABLE_NAME);
    assertFalse(source.isLockFree(), "ObjectId becomes part of the transaction and is therefore not lock-free");
  }


  @Test
  public void testEmptyTableThrows() {
    Db db = getSession();
    ObjectId source = new EmptyCounterObjectId(EMPTY_TABLE_NAME);
    long voucher = db.begin("emptyTableTest");

    assertThrows(PersistenceException.class, () -> source.nextId(db));

    db.rollback(voucher);
  }

}
