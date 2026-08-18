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
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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


  private SessionInfo createSessionInfo(int index) {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    sessionInfo.setUserName("pooltestuser" + index);    // the user name selects the sub-pool
    return sessionInfo;
  }

}
