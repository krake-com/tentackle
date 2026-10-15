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

import org.testng.annotations.Test;

import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.BackendInfo;

import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that the statements of a transaction whose connection got lost are never executed in autocommit mode.
 * <p>
 * The connection is lost if the transaction supervisor kills the connection of an idle transaction,
 * or if the connection is force-detached, for example by the attach-timeout thread, in between the
 * check in {@link Db#attach()} and the attach by the connection manager.
 * Either way, the next statement of the transaction must fail instead of being committed on its own,
 * and the session must be usable again once the application rolled back.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ConnectionLostWithinTransactionTest {

  private static final long AWAIT_MILLIS = 10000;


  /**
   * A default manager that optionally force-detaches the session right before attaching it,
   * i.e., after {@link Db#attach()} found the connection still attached.
   */
  private static class RacingDefaultManager extends DefaultConnectionManager {

    private volatile boolean releaseBeforeAttach;

    private RacingDefaultManager(String name) {
      super(name, 2, 8, 1, 720, 2160);
    }

    @Override
    public void attach(Db session) {
      if (releaseBeforeAttach) {
        releaseBeforeAttach = false;
        forceDetach(session);   // as the attach-timeout thread would do
      }
      super.attach(session);
    }
  }

  /**
   * Same for the multiplexing manager.
   */
  private static class RacingMpxManager extends MpxConnectionManager {

    private volatile boolean releaseBeforeAttach;

    private RacingMpxManager(String name) {
      super(name, new BackendInfo(new DefaultSessionInfo().getProperties()), 4, 1, 2, 1, 2, 2, 720, 2160);
    }

    @Override
    public void attach(Db session) {
      if (releaseBeforeAttach) {
        releaseBeforeAttach = false;
        forceDetach(session);
      }
      super.attach(session);
    }
  }


  @Test(timeOut = 60000)
  public void testIdleTransactionTimeout() throws Exception {
    DbTransactionFactory factory = DbTransactionFactory.getInstance();
    long txIdleInterval = factory.getTxIdleInterval();
    int txIdleTimeout = factory.getTxIdleTimeout();
    DefaultConnectionManager conMgr = new DefaultConnectionManager("tx-timeout-lost-test", 2, 8, 1, 720, 2160);
    String table = "lost_tx_timeout";
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      createTable(db, table);

      long txVoucher = db.begin("idle");
      insert(db, table, 1);
      ManagedConnection con = db.connection();

      factory.setTxIdleInterval(50);
      factory.setTxIdleTimeout(2);
      long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
      while (!con.isDead() && System.currentTimeMillis() < deadline) {
        Thread.sleep(20);
      }
      factory.setTxIdleTimeout(txIdleTimeout);
      factory.setTxIdleInterval(txIdleInterval);
      assertTrue(con.isDead(), "the supervisor did not time out the idle transaction");

      // the next statement must not be executed on a reopened connection in autocommit mode
      PersistenceException px = expectThrows(PersistenceException.class, () -> insert(db, table, 2));
      assertTrue(px.getMessage().contains("timed out"), px.getMessage());

      db.rollback(txVoucher);   // the application's exception handling
      assertFalse(db.isTxRunning());

      // nothing committed, and the session is usable again
      assertEquals(count(db, table), 0, "statements of the timed out transaction committed");
      assertWorksAfterRecovery(db, table);
    }
    finally {
      factory.setTxIdleTimeout(txIdleTimeout);
      factory.setTxIdleInterval(txIdleInterval);
      dropTable(db, table);
      db.close();
      conMgr.shutdown();
    }
  }

  @Test
  public void testForceDetachedBeforeAttachDefaultManager() {
    RacingDefaultManager conMgr = new RacingDefaultManager("race-lost-default-test");
    try {
      testForceDetachedBeforeAttach(conMgr, "lost_race_default", flag -> conMgr.releaseBeforeAttach = flag);
    }
    finally {
      conMgr.shutdown();
    }
  }

  @Test
  public void testForceDetachedBeforeAttachMpxManager() {
    RacingMpxManager conMgr = new RacingMpxManager("race-lost-mpx-test");
    try {
      testForceDetachedBeforeAttach(conMgr, "lost_race_mpx", flag -> conMgr.releaseBeforeAttach = flag);
    }
    finally {
      conMgr.shutdown();
    }
  }


  private void testForceDetachedBeforeAttach(ConnectionManager conMgr, String table, Consumer<Boolean> releaseBeforeAttach) {
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      createTable(db, table);

      long txVoucher = db.begin("racing");
      insert(db, table, 1);

      releaseBeforeAttach.accept(true);
      assertThrows(PersistenceException.class, () -> insert(db, table, 2));

      db.rollback(txVoucher);
      assertFalse(db.isTxRunning());

      assertEquals(count(db, table), 0, "statement executed in autocommit mode after the forced detach");
      assertWorksAfterRecovery(db, table);
    }
    finally {
      dropTable(db, table);
      db.close();
    }
  }

  private void assertWorksAfterRecovery(Db db, String table) {
    long txVoucher = db.begin("recovered");
    insert(db, table, 3);
    db.commit(txVoucher);
    assertEquals(count(db, table), 1);
  }

  private void createTable(Db db, String table) {
    dropTable(db, table);
    db.createStatement().executeUpdate("CREATE TABLE " + table + " (id INTEGER NOT NULL)");
  }

  private void dropTable(Db db, String table) {
    if (db.isOpen() && !db.isTxRunning()) {
      db.createStatement().executeUpdate("DROP TABLE IF EXISTS " + table);
    }
  }

  private void insert(Db db, String table, int id) {
    db.createStatement().executeUpdate("INSERT INTO " + table + " (id) VALUES (" + id + ")");
  }

  private int count(Db db, String table) {
    try (ResultSetWrapper rs = db.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertTrue(rs.next());
      return rs.getInt(1);
    }
  }

}
