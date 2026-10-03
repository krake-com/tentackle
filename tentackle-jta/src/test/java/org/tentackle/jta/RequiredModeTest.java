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

package org.tentackle.jta;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultConnectionManager;
import org.tentackle.dbms.GlobalTransactionMode;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@link GlobalTransactionMode#REQUIRED}: the sessions start and complete global transactions themselves.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class RequiredModeTest extends AbstractJtaTest {

  @Override
  protected GlobalTransactionMode getMode() {
    return GlobalTransactionMode.REQUIRED;
  }


  @Test
  public void testCommitSpansBothDatabases() throws SystemException {
    long voucher = db1.begin("both");
    assertNotEquals(voucher, 0L, "the first session starts the global transaction");
    assertEquals(tm.getStatus(), Status.STATUS_ACTIVE);
    insertRow(db1, 1);

    long voucher2 = db2.begin("second");
    assertEquals(voucher2, 0L, "the second session joins the global transaction");
    insertRow(db2, 2);
    assertFalse(db2.commit(voucher2), "a joined session never commits by itself");
    assertTrue(db2.isTxRunning(), "the joined session remains in the global transaction");

    assertTrue(db1.commit(voucher));
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION);
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning(), "completing the global transaction ends the joined session's transaction");

    assertEquals(countRows(db1), 1);
    assertEquals(countRows(db2), 1);
  }

  @Test
  public void testRollbackSpansBothDatabases() throws SystemException {
    long voucher = db1.begin("both");
    insertRow(db1, 1);
    long voucher2 = db2.begin("second");
    insertRow(db2, 2);
    db2.commit(voucher2);

    assertTrue(db1.rollback(voucher));
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION);
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());

    assertEquals(countRows(db1), 0);
    assertEquals(countRows(db2), 0);
  }

  @Test
  public void testTransactionBlock() {
    db1.transaction("block", () -> {
      insertRow(db1, 1);
      return db2.transaction("inner", () -> {
        insertRow(db2, 2);
        return null;
      });
    });
    assertEquals(countRows(db1), 1);
    assertEquals(countRows(db2), 1);

    assertThrows(IllegalStateException.class, () -> db1.transaction("failing", () -> {
      insertRow(db1, 3);
      db2.transaction("inner", () -> {
        insertRow(db2, 4);
        return null;
      });
      throw new IllegalStateException("fail");
    }));
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    assertEquals(countRows(db1), 1);
    assertEquals(countRows(db2), 1);
  }

  @Test
  public void testFailingPrepareRollsBackAll() throws Exception {
    long voucher = db1.begin("prepare fails");
    insertRow(db1, 1);
    long voucher2 = db2.begin("second");
    insertRow(db2, 2);
    db2.commit(voucher2);
    tm.getTransaction().enlistResource(new FailingXAResource(XAException.XA_RBDEADLOCK));

    PersistenceException ex = expectThrows(PersistenceException.class, () -> db1.commit(voucher));
    assertTrue(ex.isTemporary(), "a deadlock is a temporary failure");
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    db1.rollback(voucher);    // like the TransactionInterceptor after a failed commit: must be a no-op

    assertEquals(countRows(db1), 0);
    assertEquals(countRows(db2), 0);
  }

  @Test
  public void testPreAndPostCommits() {
    List<String> events = new ArrayList<>();
    long voucher = db1.begin("commits");
    db1.preCommit(_ -> events.add("pre1"));
    db1.postCommit(_ -> events.add("post1"));
    insertRow(db1, 1);
    long voucher2 = db2.begin("second");
    db2.preCommit(_ -> events.add("pre2"));
    db2.postCommit(_ -> events.add("post2"));
    insertRow(db2, 2);
    db2.commit(voucher2);
    assertTrue(events.isEmpty(), "nothing runs before the global transaction completes");

    db1.commit(voucher);
    assertEquals(events.size(), 4);
    assertTrue(events.indexOf("pre1") < events.indexOf("post1"));
    assertTrue(events.indexOf("pre2") < events.indexOf("post1"), "all pre-commits run before any post-commit");
    assertTrue(events.indexOf("pre1") < events.indexOf("post2"));
  }

  @Test
  public void testNoPostCommitsAfterRollback() {
    List<String> events = new ArrayList<>();
    long voucher = db1.begin("rollback");
    db1.postCommit(_ -> events.add("post"));
    insertRow(db1, 1);
    db1.rollback(voucher);
    assertTrue(events.isEmpty());

    voucher = db1.begin("committed afterwards");
    insertRow(db1, 2);
    db1.commit(voucher);
    assertTrue(events.isEmpty(), "discarded post-commits must not run with the next transaction");
  }

  @Test
  public void testPreCommitFailureIsRethrown() {
    PersistenceException failure = new PersistenceException("optimistic lock");
    failure.setTemporary(true);
    long voucher = db1.begin("pre-commit fails");
    insertRow(db1, 1);
    long voucher2 = db2.begin("second");
    insertRow(db2, 2);
    db2.preCommit(_ -> { throw failure; });
    db2.commit(voucher2);

    PersistenceException ex = expectThrows(PersistenceException.class, () -> db1.commit(voucher));
    assertSame(ex, failure, "the original exception must be rethrown");
    assertTrue(ex.isTemporary());
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    assertEquals(countRows(db1), 0);
    assertEquals(countRows(db2), 0);
  }

  @Test
  public void testPostCommitFailureIsRethrown() {
    long voucher = db1.begin("post-commit fails");
    insertRow(db1, 1);
    db1.postCommit(_ -> { throw new IllegalStateException("post-commit"); });

    IllegalStateException ex = expectThrows(IllegalStateException.class, () -> db1.commit(voucher));
    assertEquals(ex.getMessage(), "post-commit");
    assertFalse(db1.isTxRunning());
    assertEquals(countRows(db1), 1, "the transaction itself is committed");
  }

  @Test
  public void testRollbackImmediately() throws SystemException {
    long voucher = db1.begin("immediately");
    insertRow(db1, 1);
    long voucher2 = db2.begin("second");
    insertRow(db2, 2);

    db1.rollbackImmediately(null);
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION);
    assertFalse(db1.isTxRunning());
    assertFalse(db2.isTxRunning());
    db2.rollback(voucher2);   // pending rollbacks of the application
    db1.rollback(voucher);
    assertEquals(countRows(db1), 0);
    assertEquals(countRows(db2), 0);
  }

  @Test
  public void testIsolationRejected() throws SystemException {
    assertThrows(PersistenceException.class,
                 () -> db1.begin("isolation", TransactionIsolation.SERIALIZABLE, TransactionWritability.DEFAULT));
    assertFalse(db1.isTxRunning());
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION);
  }

  @Test
  public void testNonXaConnectionRejected() throws SystemException {
    DefaultConnectionManager plainManager = new DefaultConnectionManager();
    try {
      Db plainDb = openSession("requiredmodetestplain", GlobalTransactionMode.REQUIRED, plainManager);
      assertThrows(PersistenceException.class, () -> plainDb.begin("plain"));
      assertFalse(plainDb.isTxRunning());
      assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION, "the started global transaction must be rolled back");
      plainDb.close();
    }
    finally {
      plainManager.shutdown();
    }
  }

  @Test
  public void testFailedSynchronizationLeavesConnectionUnenlisted() throws SystemException {
    List<String> calls = new ArrayList<>();
    JtaTransactionSupport.setTransactionManager(createFailingTransactionManager("registerSynchronization", calls));
    try {
      assertThrows(PersistenceException.class, () -> db1.begin("synchronization fails"));
    }
    finally {
      JtaTransactionSupport.setTransactionManager(tm);
    }
    assertFalse(calls.contains("enlistResource"),
                "the connection must not be enlisted, since the session is detached from it: " + calls);
    assertFalse(db1.isTxRunning());
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION, "the started global transaction must be rolled back");
    assertCommitSpansBothDatabases();
  }

  @Test
  public void testFailedEnlistmentIsCleanedUp() throws SystemException {
    List<String> calls = new ArrayList<>();
    JtaTransactionSupport.setTransactionManager(createFailingTransactionManager("enlistResource", calls));
    try {
      assertThrows(PersistenceException.class, () -> db1.begin("enlistment fails"));
    }
    finally {
      JtaTransactionSupport.setTransactionManager(tm);
    }
    assertTrue(calls.contains("enlistResource"), calls.toString());
    assertFalse(db1.isTxRunning());
    assertEquals(tm.getStatus(), Status.STATUS_NO_TRANSACTION, "the started global transaction must be rolled back");
    assertCommitSpansBothDatabases();   // the synchronization of the failed attempt must not interfere
  }


  /**
   * Runs a global transaction across both databases and checks that it is committed.
   */
  private void assertCommitSpansBothDatabases() {
    long voucher = db1.begin("both");
    insertRow(db1, 1);
    long voucher2 = db2.begin("joined");
    insertRow(db2, 2);
    db2.commit(voucher2);
    assertTrue(db1.commit(voucher));
    assertEquals(countRows(db1), 1);
    assertEquals(countRows(db2), 1);
  }

  /**
   * Creates a transaction manager delegating to the test's transaction manager, whose transactions fail
   * in the given method.
   *
   * @param failingMethod the name of the {@link Transaction} method that throws an {@link IllegalStateException}
   * @param calls the names of the invoked {@link Transaction} methods
   * @return the transaction manager
   */
  private TransactionManager createFailingTransactionManager(String failingMethod, List<String> calls) {
    return (TransactionManager) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[] { TransactionManager.class }, (_, method, args) -> {
          Object result = invoke(method, tm, args);
          if (result instanceof Transaction transaction) {
            return Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { Transaction.class }, (_, txMethod, txArgs) -> {
                  calls.add(txMethod.getName());
                  if (txMethod.getName().equals(failingMethod)) {
                    throw new IllegalStateException(failingMethod + " fails");
                  }
                  return invoke(txMethod, transaction, txArgs);
                });
          }
          return result;
        });
  }

  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    }
    catch (InvocationTargetException ix) {
      throw ix.getCause();
    }
  }


  /**
   * An XA resource whose prepare fails.
   */
  private record FailingXAResource(int errorCode) implements XAResource {

    @Override
    public void commit(Xid xid, boolean onePhase) {
      // nothing to do
    }

    @Override
    public void end(Xid xid, int flags) {
      // nothing to do
    }

    @Override
    public void forget(Xid xid) {
      // nothing to do
    }

    @Override
    public int getTransactionTimeout() {
      return 0;
    }

    @Override
    public boolean isSameRM(XAResource xares) {
      return xares == this;
    }

    @Override
    public int prepare(Xid xid) throws XAException {
      throw new XAException(errorCode);
    }

    @Override
    public Xid[] recover(int flag) {
      return new Xid[0];
    }

    @Override
    public void rollback(Xid xid) {
      // nothing to do
    }

    @Override
    public boolean setTransactionTimeout(int seconds) {
      return false;
    }

    @Override
    public void start(Xid xid, int flags) {
      // nothing to do
    }
  }

}
