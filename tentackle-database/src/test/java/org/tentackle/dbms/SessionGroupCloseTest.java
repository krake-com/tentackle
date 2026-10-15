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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests closing the root session of a group while a member of the group is closed concurrently.
 * <p>
 * The member is closed after the root has taken its snapshot of the open sessions but before it is ungrouped.
 * The root session must be logged out from the connection manager nevertheless.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SessionGroupCloseTest {

  /**
   * A session closing itself when being ungrouped, as if closed by another thread in the meantime.
   */
  private static class ClosingMemberDb extends Db {

    private volatile boolean closeOnUngroup;

    private ClosingMemberDb(ConnectionManager conMgr, SessionInfo sessionInfo) {
      super(conMgr, sessionInfo);
    }

    @Override
    public void setSessionGroupId(int groupId) {
      if (groupId == 0 && closeOnUngroup) {
        closeOnUngroup = false;
        close();
      }
      super.setSessionGroupId(groupId);
    }
  }


  @Test(timeOut = 60000)
  public void testRootLoggedOutWhenMemberClosedConcurrently() {
    // session groups are looked up by session ID and URL: the IDs must not clash with sessions of other tests
    DefaultConnectionManager conMgr = new DefaultConnectionManager("group-close-test", 2, 8, 9000, 720, 2160);
    try {
      Db root = new Db(conMgr, new DefaultSessionInfo());
      ClosingMemberDb member = new ClosingMemberDb(conMgr, new DefaultSessionInfo());
      member.groupWith(root.getSessionId());
      assertEquals(member.getSessionGroupId(), root.getSessionId());
      assertEquals(conMgr.getNumSessions(), 2);

      AtomicBoolean afterCloseInvoked = new AtomicBoolean();
      root.registerCloseHandler(new SessionCloseHandler() {
        @Override
        public void beforeClose(Session session) {
          // nothing to do
        }

        @Override
        public void afterClose(Session session) {
          afterCloseInvoked.set(true);
        }
      });

      member.closeOnUngroup = true;
      root.close();

      assertFalse(member.isOpen());
      assertFalse(root.isOpen());
      assertTrue(afterCloseInvoked.get(), "close handlers skipped");
      assertEquals(conMgr.getNumSessions(), 0, "session leaked in the connection manager");
      assertEquals(conMgr.getNumConnections(), 0, "connection leaked in the connection manager");
    }
    finally {
      conMgr.shutdown();
    }
  }

}
