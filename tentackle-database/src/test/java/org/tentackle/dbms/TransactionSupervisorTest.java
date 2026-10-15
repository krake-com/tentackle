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
import org.tentackle.session.SessionInfo;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the lifecycle of the transaction supervisor of the {@link DbTransactionFactory}.
 */
public class TransactionSupervisorTest extends AbstractDbTest {

  /**
   * A session making the supervisor die once when it times out the session's transaction.
   */
  private static class FailingDb extends Db {

    private volatile boolean fail = true;

    private FailingDb(ConnectionManager conMgr, SessionInfo sessionInfo) {
      super(conMgr, sessionInfo);
    }

    @Override
    public ConnectionManager getConnectionManager() {
      if (fail && Thread.currentThread().getName().equals("Transaction Supervisor")) {
        fail = false;
        throw new SupervisorKiller();
      }
      return super.getConnectionManager();
    }
  }

  /**
   * An error thrown in the supervisor thread.
   */
  private static class SupervisorKiller extends Error {
    private SupervisorKiller() {
      super("simulated error in the transaction supervisor");
    }
  }


  public TransactionSupervisorTest() {
    super(TransactionType.NONE);
  }


  @Test
  public void testRestartAfterTermination() throws InterruptedException {
    DbTransactionFactory factory = new DbTransactionFactory();    // not the singleton used by the other tests
    factory.setTxIdleInterval(50);
    Db db = getSession();
    try {
      assertFalse(factory.isSupervisorRunning(), "supervisor must not run before the first transaction");

      factory.finish(factory.create(db, "first", false), false);
      assertTrue(factory.isSupervisorRunning(), "first transaction must start the supervisor");

      factory.requestTermination();
      assertFalse(factory.isSupervisorRunning(), "supervisor must be terminated");

      factory.finish(factory.create(db, "second", false), false);
      Thread.sleep(500);    // several supervisor intervals
      assertTrue(factory.isSupervisorRunning(), "next transaction must restart the supervisor");
    }
    finally {
      factory.requestTermination();
    }
  }

  @Test
  public void testSupervisesRunningTransactionsAfterTermination() throws InterruptedException {
    DbTransactionFactory factory = new DbTransactionFactory();    // not the singleton used by the other tests
    factory.setTxIdleInterval(50);
    Db db = getSession();
    DbTransaction tx = factory.create(db, "running", false);
    try {
      factory.requestTermination();
      assertTrue(factory.isSupervisorRunning(), "supervisor must keep running while transactions are running");

      Thread.sleep(300);    // several supervisor intervals
      assertTrue(factory.isSupervisorRunning(), "supervisor must still supervise the running transaction");

      factory.finish(tx, false);
      tx = null;
      for (int i = 0; i < 40 && factory.isSupervisorRunning(); i++) {
        Thread.sleep(50);
      }
      assertFalse(factory.isSupervisorRunning(), "supervisor must terminate when no transactions are running");
    }
    finally {
      if (tx != null) {
        factory.finish(tx, false);
      }
      factory.requestTermination();
    }
  }


  @Test
  public void testRestartAfterSupervisorDied() throws InterruptedException {
    DbTransactionFactory factory = new DbTransactionFactory();    // not the singleton used by the other tests
    factory.setTxIdleInterval(20);
    factory.setTxIdleTimeout(1);    // time out after 2 idle intervals
    ConnectionManager conMgr = new DefaultConnectionManager("supervisor-died-test", 2, 8, 1, 720, 2160);
    FailingDb failingDb = new FailingDb(conMgr, new DefaultSessionInfo());
    try {
      DbTransaction idleTx = factory.create(failingDb, "idle", false);    // never alive -> timed out
      for (int i = 0; i < 100 && factory.isSupervisorRunning(); i++) {
        Thread.sleep(20);
      }
      assertFalse(factory.isSupervisorRunning(), "supervisor should have died");
      factory.finish(idleTx, true);

      // the next transaction must start a new supervisor, which must keep running
      DbTransaction tx = factory.create(getSession(), "next", false);
      try {
        Thread.sleep(200);    // several supervisor intervals
        assertTrue(factory.isSupervisorRunning(), "supervisor not restarted");
      }
      finally {
        factory.finish(tx, false);
      }
    }
    finally {
      factory.requestTermination();
      failingDb.close();
      conMgr.shutdown();
    }
  }

}
