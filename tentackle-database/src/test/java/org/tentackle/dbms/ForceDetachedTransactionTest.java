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

import java.sql.SQLException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a connection force-detached within a transaction, for example by the attach-timeout thread,
 * is not left in transaction mode, and that the session cannot continue the rolled back transaction.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ForceDetachedTransactionTest {

  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();

  private void assertConnectionWorks(Db db) {
    PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()),
                                                           false, Backend::getDummySelect);
    try (ResultSetWrapper rs = st.executeQuery()) {
      assertTrue(rs.next());
    }
  }

  private boolean isPhysicallyAutoCommit(ManagedConnection con) throws SQLException {
    return con.getConnection().getAutoCommit();
  }


  @Test
  public void testDefaultManager() throws Exception {
    DefaultConnectionManager conMgr = new DefaultConnectionManager("force-detached-tx-test", 2, 8, 1, 720, 2160);
    try {
      Db db = new Db(conMgr, new DefaultSessionInfo());
      try {
        long txVoucher = db.begin("force-detached");
        ManagedConnection con = db.connection();
        conMgr.forceDetach(db);

        assertTrue(isPhysicallyAutoCommit(con), "force-detached connection left in transaction mode");
        assertTrue(db.isTxRunning());
        // continuing the rolled back transaction must fail
        assertThrows(PersistenceException.class, db::attach);

        db.rollback(txVoucher);   // the application's exception handling
        assertFalse(db.isTxRunning());

        // back to normal
        db.attach();
        assertSame(db.connection(), con);
        assertTrue(isPhysicallyAutoCommit(con));
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
  public void testMpxManager() throws Exception {
    // a single connection: the next session attached gets the force-detached one
    MpxConnectionManager conMgr = new MpxConnectionManager("force-detached-tx-mpx-test",
        new BackendInfo(new DefaultSessionInfo().getProperties()), 4, 1, 1, 1, 1, 1, 720, 2160);
    try {
      Db db1 = new Db(conMgr, new DefaultSessionInfo());
      Db db2 = new Db(conMgr, new DefaultSessionInfo());
      try {
        long txVoucher = db1.begin("force-detached");
        ManagedConnection con = db1.connection();
        conMgr.forceDetach(db1);

        // continuing the rolled back transaction must fail instead of auto-committing on another connection
        assertThrows(PersistenceException.class, db1::attach);

        db2.attach();
        assertSame(db2.connection(), con);
        assertTrue(isPhysicallyAutoCommit(con), "pooled connection handed over in transaction mode");
        assertConnectionWorks(db2);
        db2.detach();
        assertTrue(db2.isOpen(), "innocent session closed on detach");
        assertFalse(con.isDead());

        db1.rollback(txVoucher);
        db1.attach();
        assertConnectionWorks(db1);
        db1.detach();
      }
      finally {
        db1.close();
        db2.close();
      }
    }
    finally {
      conMgr.shutdown();
    }
  }

}
