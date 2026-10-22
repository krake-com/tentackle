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
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link MultiUserDbPool}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MultiUserDbPoolTest {

  private static final int USER_COUNT = 3;    // must be > 1 to shut down more than one sub-pool


  /**
   * Shutting down must shut down all sub-pools.<br>
   * Notice that the sub-pools remove themselves from the pool map while being shut down.
   */
  @Test
  public void testShutdown() {
    ConnectionManager conMgr = new DefaultConnectionManager("multi-pool-test-mgr", 2, 16, 1, 720, 2160);
    MultiUserDbPool pool = new MultiUserDbPool("multi-pool-test", conMgr, 0, 4, 16, 0, 0);
    try {
      // one sub-pool per user
      List<Session> sessions = new ArrayList<>();
      for (int i = 0; i < USER_COUNT; i++) {
        sessions.add(pool.get(createSessionInfo(i)));
      }
      assertEquals(pool.getSize(), USER_COUNT, "there should be one session per user");
      for (Session session : sessions) {
        pool.put(session);
      }

      assertFalse(pool.isShutdown());
      pool.shutdown();    // must shut down every sub-pool, not just the first one
      assertTrue(pool.isShutdown());
      assertEquals(pool.getSize(), 0, "all sessions should have been closed");

      for (Session session : sessions) {
        assertFalse(session.isOpen(), "session " + session + " was not closed by the shutdown");
      }
    }
    finally {
      if (!pool.isShutdown()) {
        pool.shutdown();
      }
      conMgr.shutdown();
    }
  }


  /**
   * Closing a single user's pool must not affect the others.
   */
  @Test
  public void testCloseSingleUser() {
    ConnectionManager conMgr = new DefaultConnectionManager("multi-pool-close-mgr", 2, 16, 1, 720, 2160);
    MultiUserDbPool pool = new MultiUserDbPool("multi-pool-close", conMgr, 0, 4, 16, 0, 0);
    try {
      for (int i = 0; i < USER_COUNT; i++) {
        pool.put(pool.get(createSessionInfo(i)));
      }
      assertEquals(pool.getSize(), USER_COUNT);

      pool.close(createSessionInfo(0));
      assertEquals(pool.getSize(), USER_COUNT - 1, "only the closed user's sessions should be gone");

      // the pool of a closed user is created again on demand
      pool.put(pool.get(createSessionInfo(0)));
      assertEquals(pool.getSize(), USER_COUNT);
    }
    finally {
      pool.shutdown();
      conMgr.shutdown();
    }
  }


  /**
   * Shutting down must shut down all sub-pools, even if a sub-pool is shut down concurrently.<br>
   * The timeout thread shuts down an empty sub-pool on its own. While its sessions are being closed,
   * the sub-pool is already shut down but still mapped.
   */
  @Test
  public void testShutdownWhileSubPoolShutsDown() throws InterruptedException {
    ConnectionManager conMgr = new DefaultConnectionManager("multi-pool-concurrent-mgr", 2, 16, 1, 720, 2160);
    MultiUserDbPool pool = new MultiUserDbPool("multi-pool-concurrent", conMgr, 0, 4, 16, 0, 0);
    try {
      List<Session> sessions = new ArrayList<>();
      for (int i = 0; i < USER_COUNT; i++) {
        sessions.add(pool.get(createSessionInfo(i)));
      }
      Session session0 = sessions.getFirst();
      DbPool subPool0 = (DbPool) session0.getPool();
      for (Session session : sessions) {
        pool.put(session);
      }

      AtomicReference<RuntimeException> failure = new AtomicReference<>();
      CountDownLatch shutdownDone = new CountDownLatch(1);
      session0.registerCloseHandler(new SessionCloseHandler() {
        @Override
        public void beforeClose(Session session) {
          // the sub-pool of this session is shut down, but not removed from the map yet
          try {
            pool.shutdown();
          }
          catch (RuntimeException rx) {
            failure.set(rx);
          }
          finally {
            shutdownDone.countDown();
          }
        }

        @Override
        public void afterClose(Session session) {
          // nothing to do
        }
      });

      subPool0.shutdown();    // as if invoked by the timeout thread
      assertTrue(shutdownDone.await(10, TimeUnit.SECONDS), "close handler not invoked");

      assertNull(failure.get(), "shutting down a concurrently shut down sub-pool must not fail");
      assertTrue(pool.isShutdown());
      for (Session session : sessions.subList(1, USER_COUNT)) {
        assertFalse(session.isOpen(), "session " + session + " was not closed by the shutdown");
      }
    }
    finally {
      if (!pool.isShutdown()) {
        pool.shutdown();
      }
      conMgr.shutdown();
    }
  }


  private SessionInfo createSessionInfo(int index) {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    sessionInfo.setUserName("pooltestuser" + index);    // the user name selects the sub-pool
    return sessionInfo;
  }

}
