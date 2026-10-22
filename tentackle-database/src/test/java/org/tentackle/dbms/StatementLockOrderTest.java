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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a statement used wrongly does not deadlock with a concurrent forced detach.
 * <p>
 * A statement marked ready twice, or consumed twice, force-detaches its session.
 * A forced detach by another thread, for example the attach-timeout thread of the {@link DefaultConnectionManager},
 * locks the connection manager and then the statements of the connection.
 * Hence, the statement must not hold its own monitor while force-detaching.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class StatementLockOrderTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();
  private static final long AWAIT_MILLIS = 10000;


  @Test(timeOut = 60000)
  public void testMarkReadyTwiceWhileForceDetached() throws Exception {
    assertNoDeadlock("lock-order-mark", StatementWrapper::markReady);    // already marked by getPreparedStatement
  }

  @Test(timeOut = 60000)
  public void testUnmarkReadyTwiceWhileForceDetached() throws Exception {
    assertNoDeadlock("lock-order-unmark", st -> {
      st.unmarkReady();
      st.unmarkReady();
    });
  }


  private void assertNoDeadlock(String name, Consumer<PreparedStatementWrapper> misuse) throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager(name, 2, 8, 1, 720, 2160);
    Db db = new Db(conMgr, new DefaultSessionInfo());
    PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                          false, Backend::getDummySelect);   // attaches the session

    AtomicReference<Throwable> misuseFailure = new AtomicReference<>();
    Thread misuser = new Thread(() -> {
      try {
        misuse.accept(st);
      }
      catch (Throwable t) {
        misuseFailure.set(t);
      }
    }, name + " misuser");
    misuser.setDaemon(true);    // left behind in case of a deadlock

    AtomicReference<Throwable> detachFailure = new AtomicReference<>();
    Thread detacher = new Thread(() -> {
      try {
        synchronized (conMgr) {
          // the misuser force-detaches its session and waits for the connection manager
          misuser.start();
          long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
          while (misuser.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
          }
          assertEquals(misuser.getState(), Thread.State.BLOCKED, "the misuser did not wait for the connection manager");
          // like the attach-timeout thread: cleans up the connection's statements with the manager locked
          conMgr.forceDetach(db);
        }
      }
      catch (Throwable t) {
        detachFailure.set(t);
      }
    }, name + " detacher");
    detacher.setDaemon(true);

    detacher.start();
    detacher.join(AWAIT_MILLIS);
    misuser.join(AWAIT_MILLIS);

    boolean deadlocked = detacher.isAlive() || misuser.isAlive();
    if (!deadlocked) {
      db.close();
      conMgr.shutdown();
    }
    assertFalse(deadlocked, "statement and connection manager deadlocked");
    assertEquals(detachFailure.get(), null, "forced detach failed: " + detachFailure.get());
    assertTrue(misuseFailure.get() instanceof PersistenceException,
               "the misuse must be reported, got " + misuseFailure.get());
  }

}
