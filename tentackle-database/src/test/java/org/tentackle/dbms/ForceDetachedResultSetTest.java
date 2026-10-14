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
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;

import java.util.function.Function;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests that closing a result set after its session has been force-detached
 * does not detach another session the connection has been attached to in the meantime.
 * <p>
 * The {@link MpxConnectionManager} hands out a force-detached connection to the next session immediately.
 * The test uses a manager with a single physical connection, so the second session gets the very same connection.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ForceDetachedResultSetTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();


  /*
   * Notice that cached prepared statements are not affected: the forced detach closes them,
   * and with them their open result sets, while the session is still attached.
   * One-shot prepared statements (as used by Query) and plain statements, however, are not cached by the
   * connection and their result sets stay open until closed by the application or the cleaner.
   */

  @Test
  public void testCloseOneShotPreparedResultSetAfterForceDetach() {
    assertForeignSessionNotDetached("rs-force-detach-one-shot", db ->
      db.createPreparedStatement(Backend::getDummySelect).executeQuery());
  }

  @Test
  public void testCloseResultSetAfterForceDetach() {
    assertForeignSessionNotDetached("rs-force-detach-plain", db ->
      db.createStatement().executeQuery(db.getBackend().getDummySelect()));
  }


  private void assertForeignSessionNotDetached(String name, Function<Db, ResultSetWrapper> query) {
    DefaultSessionInfo sessionInfo = new DefaultSessionInfo();
    BackendInfo backendInfo = new BackendInfo(sessionInfo.getProperties());
    // exactly one physical connection
    MpxConnectionManager conMgr = new MpxConnectionManager(name, backendInfo, 4, 1, 1, 1, 1, 1, 720, 2160);
    try {
      Db db1 = new Db(conMgr, new DefaultSessionInfo());
      Db db2 = new Db(conMgr, new DefaultSessionInfo());
      try {
        ResultSetWrapper rs = query.apply(db1);
        ManagedConnection con = db1.getConnection();
        assertNotNull(con, "the open result set keeps the session attached");

        // for example, due to an exception while reading the result set outside a transaction
        db1.forceDetached();
        assertNull(db1.getConnection());

        long txVoucher = db2.begin("foreignSession");
        assertSame(db2.getConnection(), con, "the released connection must be reused by the second session");

        rs.close();   // late close by the application or by the cleaner if unreferenced

        assertTrue(db2.isOpen(), "the second session must not be closed");
        assertSame(db2.getConnection(), con, "the second session must still be attached");
        assertTrue(db2.isTxRunning());
        assertFalse(con.isDead());

        PreparedStatementWrapper st = db2.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                               false, Backend::getDummySelect);
        try (ResultSetWrapper rs2 = st.executeQuery()) {
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
