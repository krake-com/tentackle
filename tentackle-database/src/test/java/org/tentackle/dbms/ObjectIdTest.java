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
import org.tentackle.session.SavepointHandle;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    ObjectId source = new ObjectId(TABLE_NAME);
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
    ObjectId source = new ObjectId(TABLE_NAME);
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
  public void testNextIdFromPreCommitAfterFlushIsPersisted() {
    Db db = getSession();
    ObjectId source = new ObjectId(TABLE_NAME);
    long voucher = db.begin("nextIdPreCommitAfterFlushTest");

    source.nextId(db);
    source.nextId(db);    // registers the final update at the nested tx level of nextId()

    // registered at the outer tx level: runs after the final update of the deeper level
    long[] fromPreCommit = new long[1];
    db.preCommit(_ -> fromPreCommit[0] = source.nextId(db));

    db.commit(voucher);
    assertEquals(selectPersistedValue(db, TABLE_NAME), fromPreCommit[0],
                 "an id handed out after the final update must be persisted, or the next transaction would get it again");
    assertEquals(source.nextId(db), fromPreCommit[0] + 1, "the next transaction must continue after the last id");
  }


  @Test
  public void testNextIdFromPreCommitWithoutFlushIsPersisted() {
    Db db = getSession();
    ObjectId source = new ObjectId(TABLE_NAME);
    long voucher = db.begin("nextIdPreCommitWithoutFlushTest");

    long first = source.nextId(db);   // updates the database directly, no final update registered yet

    // the final update cannot be registered from within a pre-commit anymore
    long[] fromPreCommit = new long[1];
    db.preCommit(_ -> fromPreCommit[0] = source.nextId(db));

    db.commit(voucher);
    assertEquals(fromPreCommit[0], first + 1);
    assertEquals(selectPersistedValue(db, TABLE_NAME), fromPreCommit[0],
                 "an id handed out from within a pre-commit must be persisted");
  }


  @Test
  public void testNextIdAfterSavepointRollbackKeepsEarlierIds() {
    Db db = getSession();
    ObjectId source = new ObjectId(TABLE_NAME);
    long voucher = db.begin("savepointKeepsEarlierIdsTest");

    source.nextId(db);
    long beforeSavepoint = source.nextId(db);   // counted in memory, still valid after the rollback below
    SavepointHandle savepoint = db.setSavepoint();
    source.nextId(db);
    db.rollback(savepoint);

    // the counter is synchronized with the database, which still holds the value before the in-memory counting
    long afterRollback = source.nextId(db);
    assertTrue(afterRollback > beforeSavepoint,
               "id " + afterRollback + " was already returned before the savepoint (" + beforeSavepoint + ")");

    db.commit(voucher);
    assertEquals(selectPersistedValue(db, TABLE_NAME), afterRollback,
                 "after commit the persisted counter must match the last id returned");
  }


  @Test
  public void testConcurrentSessionsDontBlockOrCollide() throws Exception {
    Db db1 = getSession();
    Db db2 = db1.clone("objectIdTest");
    ObjectId source = new ObjectId(TABLE_NAME);    // shared by both sessions, like the id source of a class
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      long voucher1 = db1.begin("objectIdTest1");
      long a = source.nextId(db1);    // db1 holds the row lock until its commit

      Future<Long> worker = executor.submit(() -> {
        db2.setOwnerThread(Thread.currentThread());
        long voucher2 = db2.begin("objectIdTest2");
        long b = source.nextId(db2);   // waits for the row lock of db1
        db2.commit(voucher2);
        return b;
      });
      Thread.sleep(300);    // let the worker block on the database lock

      // must not wait for the worker: the id source holds no lock while the worker waits for the database
      long c = executor.submit(() -> source.nextId(db1)).get(5, TimeUnit.SECONDS);
      assertEquals(c, a + 1);
      db1.commit(voucher1);

      long b = worker.get(10, TimeUnit.SECONDS);
      assertTrue(b > c, "the waiting session must continue after the persisted counter: " + b + " <= " + c);
      assertEquals(selectPersistedValue(db1, TABLE_NAME), b);
    }
    finally {
      executor.shutdownNow();
      db2.close();
    }
  }


  @Test
  public void testIsLockFree() {
    ObjectId source = new ObjectId(TABLE_NAME);
    assertFalse(source.isLockFree(), "ObjectId becomes part of the transaction and is therefore not lock-free");
  }


  @Test
  public void testEmptyTableThrows() {
    Db db = getSession();
    ObjectId source = new ObjectId(EMPTY_TABLE_NAME);
    long voucher = db.begin("emptyTableTest");

    assertThrows(PersistenceException.class, () -> source.nextId(db));

    db.rollback(voucher);
  }

}
