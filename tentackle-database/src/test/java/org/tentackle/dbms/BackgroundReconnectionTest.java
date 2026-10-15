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
import org.tentackle.session.SessionInfo;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the non-blocking reconnection of a session.
 * <p>
 * Each statement failing due to a dead connection requests a reconnection.
 * The session must be reopened only once at a time in background, and must not be used by other threads meanwhile.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class BackgroundReconnectionTest {

  private static final long AWAIT_MILLIS = 10000;


  /**
   * A session counting its reopens.
   */
  private static class CountingDb extends Db {

    private final AtomicInteger reOpenCount = new AtomicInteger();
    private volatile int failingReOpens;    // number of reopens to fail
    private volatile boolean errorOnReOpen;       // true if the next reopen throws an Error
    private volatile boolean interruptOnReOpen;   // true if the next reopen fails and interrupts the reconnecting thread

    private CountingDb(ConnectionManager conMgr, SessionInfo sessionInfo) {
      super(conMgr, sessionInfo);
    }

    @Override
    public void reOpen() {
      reOpenCount.incrementAndGet();
      if (errorOnReOpen) {
        errorOnReOpen = false;
        throw new SimulatedError();
      }
      if (interruptOnReOpen) {
        interruptOnReOpen = false;
        Thread.currentThread().interrupt();   // the reconnector's next sleep is interrupted
        throw new PersistenceException(this, "simulated reconnection failure");
      }
      if (failingReOpens > 0) {
        failingReOpens--;
        throw new PersistenceException(this, "simulated reconnection failure");
      }
      super.reOpen();
    }
  }


  /**
   * An error thrown by the reconnection.
   */
  private static class SimulatedError extends Error {

    @Serial
    private static final long serialVersionUID = 1L;

    private SimulatedError() {
      super("simulated error");
    }
  }


  @Test(timeOut = 60000)
  public void testReconnectedOnlyOnce() throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("bg-reconnect-once-test", 2, 8, 1, 720, 2160);
    CountingDb db = new CountingDb(conMgr, new DefaultSessionInfo());
    try {
      db.enableReconnection(false, 200);

      // several statements fail before the session is reconnected
      for (int i = 0; i < 5; i++) {
        assertFalse(db.optionallyReconnect(), "non-blocking reconnection must not block");
      }
      assertTrue(db.isReconnecting());

      // the session must not be used while being reopened in background
      PersistenceException px = expectThrows(PersistenceException.class, db::attach);
      assertTrue(px.isTemporary(), "the application should retry later");

      awaitReconnected(db);
      assertEquals(db.reOpenCount.get(), 1, "session reopened more than once");
      assertEquals(conMgr.getNumSessions(), 1, "sessions leaked by concurrent reopens");
      assertSessionWorks(db);

      // the next failure reconnects again
      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);
      assertEquals(db.reOpenCount.get(), 2);
      assertEquals(conMgr.getNumSessions(), 1);
      assertSessionWorks(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }

  @Test(timeOut = 60000)
  public void testRetriedUntilReconnected() throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("bg-reconnect-retry-test", 2, 8, 1, 720, 2160);
    CountingDb db = new CountingDb(conMgr, new DefaultSessionInfo());
    try {
      db.enableReconnection(false, 50);
      db.failingReOpens = 2;

      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);
      assertEquals(db.reOpenCount.get(), 3, "failed attempts must be retried by the same reconnection");
      assertEquals(conMgr.getNumSessions(), 1);
      assertSessionWorks(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }

  @Test(timeOut = 60000)
  public void testPendingClearedOnError() throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("bg-reconnect-error-test", 2, 8, 1, 720, 2160);
    CountingDb db = new CountingDb(conMgr, new DefaultSessionInfo());
    try {
      db.enableReconnection(false, 50);
      db.errorOnReOpen = true;

      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);   // the Error terminates the reconnection: the session must not refuse to attach forever
      assertEquals(db.reOpenCount.get(), 1);
      assertSessionWorks(db);

      // the next failure reconnects again
      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);
      assertEquals(db.reOpenCount.get(), 2);
      assertEquals(conMgr.getNumSessions(), 1);
      assertSessionWorks(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }

  @Test(timeOut = 60000)
  public void testPendingClearedOnInterrupt() throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("bg-reconnect-interrupt-test", 2, 8, 1, 720, 2160);
    CountingDb db = new CountingDb(conMgr, new DefaultSessionInfo());
    try {
      db.enableReconnection(false, 50);
      db.interruptOnReOpen = true;

      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);   // interrupted while waiting for the next attempt
      assertEquals(db.reOpenCount.get(), 1);
      assertSessionWorks(db);

      assertFalse(db.optionallyReconnect());
      awaitReconnected(db);
      assertEquals(db.reOpenCount.get(), 2);
      assertEquals(conMgr.getNumSessions(), 1);
      assertSessionWorks(db);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }


  private void awaitReconnected(Db db) throws InterruptedException {
    long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
    while (db.isReconnecting() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertFalse(db.isReconnecting(), "session not reconnected in time");
    assertTrue(db.isOpen());
  }

  private void assertSessionWorks(Db db) {
    try (ResultSetWrapper rs = db.createStatement().executeQuery(db.getBackend().getDummySelect())) {
      assertTrue(rs.next());
    }
  }

}
