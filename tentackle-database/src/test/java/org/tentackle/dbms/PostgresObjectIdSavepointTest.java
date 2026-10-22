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

import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfoFactory;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests {@link ObjectId} with savepoints against a real PostgreSQL server.
 * <p>
 * PostgreSQL releases a row lock acquired after a savepoint as soon as the savepoint is rolled back.
 * If the first {@link ObjectId#nextId(Db)} of a transaction is undone that way, other sessions may
 * allocate IDs before the transaction requests the next one or commits. The IDs counted in memory
 * must not collide with them, and the final update must not set the counter back.
 * <p>
 * Therefore this test is in the {@code postgres} group, which the default build excludes. Run
 * it with {@code mvn -Ppostgres test} against a prepared database.
 * <p>
 * Notice that the test does <em>not</em> extend {@link AbstractDbTest}: that base class opens
 * its session in a {@code @BeforeSuite} shared by every test class, so a single suite has a
 * single backend.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class PostgresObjectIdSavepointTest {

  private static final String TABLE_NAME = "objectidsavepointtest";
  private static final long LOCK_TIMEOUT_SECONDS = 10;

  private Db dbA;                     // the session rolling back to a savepoint
  private Db dbB;                     // the concurrent session
  private ExecutorService executor;   // runs dbB, so that a lock not released fails the test instead of hanging


  @BeforeClass(alwaysRun = true)
  public void openPostgresSessions() {
    try {
      dbA = (Db) SessionFactory.getInstance().create(SessionInfoFactory.getInstance().create("postgres"));
    }
    catch (PersistenceException ex) {
      throw new SkipException("no postgres backend -> no tests", ex);
    }
    if (!"PostgreSQL".equals(dbA.getBackend().getName())) {
      // guards against silently passing against another backend, which may keep the lock
      dbA.close();
      dbA = null;
      throw new SkipException("postgres.properties does not point at PostgreSQL -> no tests");
    }
    dbB = dbA.clone("objectIdSavepointTest");
    executor = Executors.newSingleThreadExecutor();

    Backend backend = dbA.getBackend();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(TABLE_NAME, null));
    ddl.append("    ").append(backend.sqlCreateColumn("id", null, SqlType.BIGINT, 0, 0, false, null, false, false));
    ddl.append(backend.sqlCreateTableClosing(TABLE_NAME, null));
    long voucher = dbA.begin("createCounterTable");
    executeUpdate(dbA, "DROP TABLE IF EXISTS " + TABLE_NAME);
    executeUpdate(dbA, ddl.toString());
    executeUpdate(dbA, "INSERT INTO " + TABLE_NAME + " (id) VALUES (0)");
    dbA.commit(voucher);
  }

  @AfterMethod(alwaysRun = true)
  public void rollbackPendingTransaction() {
    if (dbA != null) {
      while (dbA.isTxRunning()) {
        dbA.rollbackImmediately(null);
      }
    }
  }

  @AfterClass(alwaysRun = true)
  public void dropCounterTable() {
    if (executor != null) {
      executor.shutdownNow();
    }
    if (dbA != null) {
      try {
        executeUpdate(dbA, "DROP TABLE IF EXISTS " + TABLE_NAME);
      }
      finally {
        dbB.close();
        dbA.close();
      }
    }
  }


  private void executeUpdate(Db db, String sql) {
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  private long selectPersistedValue(Db db) {
    try (StatementWrapper st = db.createStatement();
         ResultSetWrapper rs = st.executeQuery("SELECT id FROM " + TABLE_NAME)) {
      assertTrue(rs.next());
      return rs.getLong(1);
    }
  }

  /**
   * Allocates IDs in a transaction of the concurrent session.
   *
   * @param source the id source
   * @param count the number of IDs to allocate
   * @return the last ID allocated
   */
  private long allocateConcurrently(ObjectId source, int count) throws Exception {
    try {
      return executor.submit(() -> {
        dbB.setOwnerThread(Thread.currentThread());
        try {
          long voucher = dbB.begin("concurrentSession");
          long id = 0;
          for (int i = 0; i < count; i++) {
            id = source.nextId(dbB);
          }
          dbB.commit(voucher);
          return id;
        }
        finally {
          dbB.setOwnerThread(null);
        }
      }).get(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
    catch (TimeoutException tx) {
      fail("the row lock acquired after the savepoint was not released by its rollback");
      return 0;
    }
  }


  @Test(groups = "postgres")
  public void testNextIdAfterSavepointRollback() throws Exception {
    ObjectId source = new ObjectId(TABLE_NAME);
    long voucher = dbA.begin("nextIdAfterSavepointRollback");

    SavepointHandle savepoint = dbA.setSavepoint();
    source.nextId(dbA);           // increments the counter and locks the row after the savepoint
    dbA.rollback(savepoint);      // undoes the increment and releases the row lock

    long lastConcurrentId = allocateConcurrently(source, 2);

    long id = source.nextId(dbA);
    assertTrue(id > lastConcurrentId, "id " + id + " already allocated by the concurrent session (" + lastConcurrentId + ")");
    dbA.commit(voucher);
    assertEquals(selectPersistedValue(dbA), id, "the persisted counter must match the last id returned");
  }


  @Test(groups = "postgres")
  public void testCommitAfterSavepointRollback() throws Exception {
    ObjectId source = new ObjectId(TABLE_NAME);
    long voucher = dbA.begin("commitAfterSavepointRollback");

    SavepointHandle savepoint = dbA.setSavepoint();
    source.nextId(dbA);
    source.nextId(dbA);           // counted in memory, registers the final update
    dbA.rollback(savepoint);

    long lastConcurrentId = allocateConcurrently(source, 3);

    dbA.commit(voucher);          // runs the final update
    long persisted = selectPersistedValue(dbA);
    assertTrue(persisted >= lastConcurrentId, "the final update set the counter back from " + lastConcurrentId + " to " + persisted);
    assertTrue(source.nextId(dbA) > lastConcurrentId, "the next transaction must continue after the concurrent session");
  }

}
