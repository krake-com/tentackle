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
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the transaction supervisor of the {@link DbTransactionFactory} only kills the connection
 * of the transaction that timed out.
 * <p>
 * The supervisor rolls back and closes the low-level connection of an idle transaction in a separate thread.
 * If the transaction completes in the meantime, the {@link MpxConnectionManager} may attach the very same
 * connection to another session, whose transaction must not be affected.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class TransactionTimeoutTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();
  private static final String SUPERVISOR_THREAD_NAME = "Transaction Supervisor";
  private static final long AWAIT_SECONDS = 10;


  /**
   * A manager with a single physical connection.
   */
  private static class TestManager extends MpxConnectionManager {

    private volatile Runnable supervisorHook;   // run once when the supervisor logs the connection of a timed out transaction

    private TestManager(String name) {
      super(name, new BackendInfo(new DefaultSessionInfo().getProperties()), 4, 1, 1, 1, 1, 1, 720, 2160);
    }

    @Override
    public String toString() {
      // ManagedConnection.toDiagnosticString() is the only callout between picking the connection and killing it
      Runnable hook = supervisorHook;
      if (hook != null && SUPERVISOR_THREAD_NAME.equals(Thread.currentThread().getName())) {
        supervisorHook = null;
        hook.run();
      }
      return super.toString();
    }
  }


  @Test(timeOut = 60000)
  public void testTimeoutDoesNotKillReusedConnection() throws Exception {
    DbTransactionFactory factory = DbTransactionFactory.getInstance();
    long txIdleInterval = factory.getTxIdleInterval();
    int txIdleTimeout = factory.getTxIdleTimeout();
    TestManager conMgr = new TestManager("tx-timeout-test");
    Db db1 = new Db(conMgr, new DefaultSessionInfo());
    Db db2 = new Db(conMgr, new DefaultSessionInfo());
    try {
      long voucher1 = db1.begin("idle");    // attaches the single connection

      AtomicReference<Throwable> commitFailure = new AtomicReference<>();
      AtomicReference<Throwable> hookFailure = new AtomicReference<>();
      AtomicLong voucher2 = new AtomicLong();
      CountDownLatch hookDone = new CountDownLatch(1);
      conMgr.supervisorHook = () -> {
        try {
          // the session commits just after the supervisor picked its connection ...
          try {
            db1.commit(voucher1);
          }
          catch (PersistenceException px) {
            commitFailure.set(px);
            db1.rollback(voucher1);
          }
          // ... and the connection is attached to another session
          voucher2.set(db2.begin("reused"));
        }
        catch (Throwable t) {
          hookFailure.set(t);
        }
        finally {
          hookDone.countDown();
        }
      };

      factory.setTxIdleInterval(50);
      factory.setTxIdleTimeout(2);
      assertTrue(hookDone.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the supervisor did not time out the idle transaction");
      factory.setTxIdleTimeout(txIdleTimeout);
      factory.setTxIdleInterval(txIdleInterval);
      assertNull(hookFailure.get(), "the second session could not begin its transaction: " + hookFailure.get());

      Thread.sleep(500);    // gives the supervisor's cleanup thread time to roll back and close the connection

      // the transaction of the second session must not be affected
      PreparedStatementWrapper st = db2.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                             false, Backend::getDummySelect);
      try (ResultSetWrapper rs = st.executeQuery()) {
        assertTrue(rs.next());
      }
      db2.commit(voucher2.get());

      assertTrue(commitFailure.get() instanceof PersistenceException,
                 "the commit of the timed out transaction must be refused, got " + commitFailure.get());
    }
    finally {
      factory.setTxIdleTimeout(txIdleTimeout);
      factory.setTxIdleInterval(txIdleInterval);
      db2.close();
      db1.close();
      conMgr.shutdown();
    }
  }

}
