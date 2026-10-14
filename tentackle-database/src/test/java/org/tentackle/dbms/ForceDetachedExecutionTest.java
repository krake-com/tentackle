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
import org.testng.annotations.Test;

import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests a forced detach racing with the execution of a statement.
 * <p>
 * The session is force-detached after the statement verified that it is still attached, but before the
 * statement is executed. The {@link MpxConnectionManager} hands out the released connection to the next
 * session immediately, so the statement must neither run on behalf of that session nor run while the
 * connection is being cleaned up.
 * <p>
 * The tests use a manager with a single physical connection, so the second session gets the very same connection.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ForceDetachedExecutionTest {

  private static final String VARIABLE = "@tt_force_detached";
  private static final String SET_VARIABLE = "SET " + VARIABLE + " = 1";
  private static final String SELECT_VARIABLE = "SELECT " + VARIABLE;


  /**
   * Connection invoking a hook right before a statement is executed.
   */
  private static class HookedConnection extends ManagedConnection {

    private final AtomicReference<Runnable> beforeExecution = new AtomicReference<>();

    HookedConnection(ConnectionManager manager, Backend backend, BackendVersionInfo backendVersionInfo, Connection connection) {
      super(manager, backend, backendVersionInfo, connection);
    }

    @Override
    StatementHistory logStatementHistory(StatementWrapper statement, String sql) {
      // invoked after the statement checked its session, immediately before the execution
      Runnable hook = beforeExecution.getAndSet(null);
      if (hook != null) {
        hook.run();
      }
      return super.logStatementHistory(statement, sql);
    }
  }


  /*
   * Force-detached between the check of the session and the execution.
   * Prepared statements are covered as well, since the forced detach closes them.
   */

  @Test
  public void testQueryForceDetachedBeforeExecution() {
    assertNotExecutedForForeignSession("fd-exec-query",
      st -> st.executeQuery(st.con.getBackend().getDummySelect()));
  }

  @Test
  public void testQueryWithinTxForceDetachedBeforeExecution() {
    assertNotExecutedForForeignSession("fd-exec-query-tx",
      st -> st.executeQuery(st.con.getBackend().getDummySelect(), true));
  }

  @Test
  public void testUpdateForceDetachedBeforeExecution() {
    assertNotExecutedForForeignSession("fd-exec-update", st -> st.executeUpdate(SET_VARIABLE));
  }

  @Test
  public void testBatchForceDetachedBeforeExecution() {
    assertNotExecutedForForeignSession("fd-exec-batch", st -> {
      st.addBatch(SET_VARIABLE);
      st.executeBatch(true);
    });
  }


  /*
   * Force-detached while the statement is being executed.
   */

  @Test
  public void testReleaseWaitsForExecution() throws InterruptedException {
    withSessions("fd-exec-wait", (conMgr, db1, db2) -> {
      StatementWrapper st = db1.createStatement();
      ManagedConnection con = db1.getConnection();
      con.enterExecution(db1, st);    // as if the statement is being executed
      boolean entered = true;
      try {
        // for example, by the attach timeout thread of the connection manager
        Thread detacher = new Thread(db1::forceDetached, "force-detach");
        detacher.setDaemon(true);
        detacher.start();
        detacher.join(1000);
        assertTrue(detacher.isAlive(), "the cleanup must wait for the execution to terminate");
        assertNull(con.getSession(), "the session must be released nevertheless");

        con.exitExecution();
        entered = false;
        detacher.join(BlockingConnection.AWAIT_MILLIS);
        assertFalse(detacher.isAlive(), "the cleanup must continue once the execution terminated");
        assertFalse(con.isDead());
        assertFalse(con.isClosed());

        long txVoucher = db2.begin("foreignSession");
        assertSame(db2.getConnection(), con, "the released connection must be reused by the second session");
        db2.commit(txVoucher);
      }
      finally {
        if (entered) {
          con.exitExecution();
        }
        st.close();
      }
    });
  }

  @Test
  public void testReleaseClosesConnectionIfExecutionDoesNotTerminate() throws InterruptedException {
    long maxCancelWaitMillis = ManagedConnection.getMaxCancelWaitMillis();
    ManagedConnection.setMaxCancelWaitMillis(200);
    try {
      withSessions("fd-exec-stuck", (conMgr, db1, db2) -> {
        StatementWrapper st = db1.createStatement();
        ManagedConnection con = db1.getConnection();
        con.enterExecution(db1, st);    // as if the statement got stuck and ignores the cancellation
        try {
          db1.forceDetached();
          assertTrue(con.isDead(), "the connection must be marked dead");
          assertTrue(con.isClosed(), "the connection must be closed to abort the execution");
        }
        finally {
          con.exitExecution();
          st.close();
        }

        long txVoucher = db2.begin("foreignSession");
        assertNotSame(db2.getConnection(), con, "the closed connection must not be reused");
        db2.commit(txVoucher);
      });
    }
    finally {
      ManagedConnection.setMaxCancelWaitMillis(maxCancelWaitMillis);
    }
  }


  @FunctionalInterface
  private interface SessionsTest {
    void run(MpxConnectionManager conMgr, Db db1, Db db2) throws InterruptedException;
  }

  private void withSessions(String name, SessionsTest test) throws InterruptedException {
    DefaultSessionInfo sessionInfo = new DefaultSessionInfo();
    BackendInfo backendInfo = new BackendInfo(sessionInfo.getProperties());
    // exactly one physical connection
    MpxConnectionManager conMgr = new MpxConnectionManager(name, backendInfo, 4, 1, 1, 1, 1, 1, 720, 2160) {
      @Override
      protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
        return new HookedConnection(this, backend, versionInfo, connection);
      }
    };
    try {
      Db db1 = new Db(conMgr, new DefaultSessionInfo());
      Db db2 = new Db(conMgr, new DefaultSessionInfo());
      try {
        test.run(conMgr, db1, db2);
      }
      finally {
        db2.close();
        db1.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }

  private void assertNotExecutedForForeignSession(String name, Consumer<StatementWrapper> execution) {
    try {
      withSessions(name, (conMgr, db1, db2) -> {
        if (!"H2".equals(db1.getBackend().getName())) {
          throw new SkipException("session variables are H2 specific");
        }
        StatementWrapper st = db1.createStatement();
        HookedConnection con = (HookedConnection) db1.getConnection();
        assertNotNull(con, "the statement marked ready keeps the session attached");

        long[] txVoucher = new long[1];
        con.beforeExecution.set(() -> {
          // for example, by the attach timeout thread of the connection manager
          db1.forceDetached();
          assertNull(db1.getConnection());
          txVoucher[0] = db2.begin("foreignSession");
          assertSame(db2.getConnection(), con, "the released connection must be reused by the second session");
        });

        // late execution by the thread of the first session
        assertThrows(PersistenceException.class, () -> execution.accept(st));
        assertNull(con.beforeExecution.get(), "the session was not force-detached before the execution");
        assertFalse(st.isRunning());

        assertTrue(db2.isOpen(), "the second session must not be closed");
        assertSame(db2.getConnection(), con, "the second session must still be attached");
        assertTrue(db2.isTxRunning());
        assertFalse(con.isDead());

        StatementWrapper st2 = db2.createStatement();
        try (ResultSetWrapper rs2 = st2.executeQuery(SELECT_VARIABLE)) {
          assertTrue(rs2.next());
          assertNull(rs2.getString(1), "the statement of the first session was executed for the second session");
        }
        db2.commit(txVoucher[0]);
        assertNull(db2.getConnection(), "the commit must detach the second session");
        assertFalse(db1.isTxRunning(), "the first session must not be left in a transaction");
        st.close();
      });
    }
    catch (InterruptedException ix) {
      throw new AssertionError(ix);
    }
  }

}
