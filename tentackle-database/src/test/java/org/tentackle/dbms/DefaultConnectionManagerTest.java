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

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link DefaultConnectionManager}, the strict 1:1 session-to-connection manager — the
 * variant not exercised by {@link DbPoolTest}/{@link MultiUserDbPoolTest}, which both drive
 * {@link MpxConnectionManager} instead.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DefaultConnectionManagerTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();

  private void assertConnectionWorks(Db db) {
    PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                           false, Backend::getDummySelect);
    try (ResultSetWrapper rs = st.executeQuery()) {
      assertTrue(rs.next());
    }
  }


  @Test
  public void testAttachDetachRoundTrip() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("attach-detach-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      try {
        db.attach();
        assertConnectionWorks(db);
        db.detach();

        // a second session must be able to attach cleanly afterward
        Db db2 = new Db(conMgr, new DefaultSessionInfo());
        try {
          db2.attach();
          assertConnectionWorks(db2);
          db2.detach();
        }
        finally {
          db2.close();
        }
      }
      finally {
        db.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }


  @Test
  public void testMaxSizeIsEnforced() {
    int maxSize = 2;
    DefaultConnectionManager conMgr = new DefaultConnectionManager("max-size-test", maxSize, maxSize, 1, 720, 2160);
    List<Db> sessions = new ArrayList<>();
    try {
      for (int i = 0; i < maxSize; i++) {
        sessions.add(new Db(conMgr, new DefaultSessionInfo()));
      }
      assertEquals(conMgr.getNumSessions(), maxSize);

      assertThrows(PersistenceException.class, () -> new Db(conMgr, new DefaultSessionInfo()));
    }
    finally {
      sessions.forEach(Db::close);
      conMgr.shutdown();
    }
  }


  @Test
  public void testForceDetach() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("force-detach-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      try {
        db.attach();
        conMgr.forceDetach(db);

        // the session must still be usable afterward: attach transparently reopens if necessary
        db.attach();
        assertConnectionWorks(db);
        db.detach();
      }
      finally {
        db.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }


  @Test
  public void testShutdownClosesAllConnections() {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("shutdown-test", 2, 8, 1, 720, 2160);
    new Db(conMgr, new DefaultSessionInfo());
    new Db(conMgr, new DefaultSessionInfo());

    assertTrue(conMgr.getNumConnections() > 0, "logging in must have created physical connections");
    conMgr.shutdown();
    assertEquals(conMgr.getNumConnections(), 0, "shutdown must close every managed connection");
  }

}
