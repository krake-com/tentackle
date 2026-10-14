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

import java.util.function.Consumer;
import java.util.function.Function;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests that executing a statement after its session has been force-detached
 * neither uses nor detaches another session the connection has been attached to in the meantime.
 * <p>
 * The {@link MpxConnectionManager} hands out a force-detached connection to the next session immediately.
 * The test uses a manager with a single physical connection, so the second session gets the very same connection.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ForceDetachedStatementTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();
  private static final StatementId TEST_STATEMENT_ID = new StatementId();


  /*
   * Cached prepared statements are closed by the forced detach.
   */

  @Test
  public void testPreparedUpdateAfterForceDetach() {
    assertForeignSessionNotUsed("st-force-detach-prepared-update",
      db -> db.getPreparedStatement(new StatementKey(TEST_STATEMENT_ID, getClass()), false, Backend::getDummySelect),
      st -> ((PreparedStatementWrapper) st).executeUpdate());
  }

  @Test
  public void testPreparedQueryAfterForceDetach() {
    assertForeignSessionNotUsed("st-force-detach-prepared-query",
      db -> db.getPreparedStatement(new StatementKey(TEST_STATEMENT_ID, getClass()), false, Backend::getDummySelect),
      st -> ((PreparedStatementWrapper) st).executeQuery());
  }


  /*
   * Plain statements are not cached by the connection and stay open.
   */

  @Test
  public void testPlainUpdateAfterForceDetach() {
    assertForeignSessionNotUsed("st-force-detach-plain-update", Db::createStatement,
      st -> st.executeUpdate(st.con.getBackend().getDummySelect()));
  }

  @Test
  public void testPlainQueryAfterForceDetach() {
    assertForeignSessionNotUsed("st-force-detach-plain-query", Db::createStatement,
      st -> st.executeQuery(st.con.getBackend().getDummySelect()));
  }


  private void assertForeignSessionNotUsed(String name, Function<Db, StatementWrapper> statement,
                                           Consumer<StatementWrapper> execution) {
    DefaultSessionInfo sessionInfo = new DefaultSessionInfo();
    BackendInfo backendInfo = new BackendInfo(sessionInfo.getProperties());
    // exactly one physical connection
    MpxConnectionManager conMgr = new MpxConnectionManager(name, backendInfo, 4, 1, 1, 1, 1, 1, 720, 2160);
    try {
      Db db1 = new Db(conMgr, new DefaultSessionInfo());
      Db db2 = new Db(conMgr, new DefaultSessionInfo());
      try {
        StatementWrapper st = statement.apply(db1);
        ManagedConnection con = db1.getConnection();
        assertNotNull(con, "the statement marked ready keeps the session attached");

        // for example, by the attach timeout thread of the connection manager
        db1.forceDetached();
        assertNull(db1.getConnection());

        long txVoucher = db2.begin("foreignSession");
        assertSame(db2.getConnection(), con, "the released connection must be reused by the second session");

        // late execution by the thread of the first session
        assertThrows(PersistenceException.class, () -> execution.accept(st));

        assertTrue(db2.isOpen(), "the second session must not be closed");
        assertSame(db2.getConnection(), con, "the second session must still be attached");
        assertTrue(db2.isTxRunning());
        assertFalse(con.isDead());

        PreparedStatementWrapper st2 = db2.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                                false, Backend::getDummySelect);
        try (ResultSetWrapper rs2 = st2.executeQuery()) {
          assertTrue(rs2.next());
        }
        db2.commit(txVoucher);
        assertNull(db2.getConnection(), "the commit must detach the second session");
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

}
