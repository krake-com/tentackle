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

import org.testng.annotations.Test;

import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.GlobalTransactionMode;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.sql.BackendInfo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;

/**
 * Runs the {@link RequiredModeTest} with multiplexed XA connections.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MpxRequiredModeTest extends RequiredModeTest {

  @Override
  protected ConnectionManager createConnectionManager(String name, BackendInfo backendInfo) {
    return new XaMpxConnectionManager(name, backendInfo, 10, 1, 1, 1, 1, 2, 720, 2160);
  }



  @Test
  public void testConnectionHandedOverBetweenGlobalTransactions() {
    String dbName = "mpxhandover";
    var properties = PropertiesUtilities.getInstance().create();
    properties.setProperty("url", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
    properties.setProperty("user", "tentackle");
    properties.setProperty("password", "tentackle");
    // a single physical connection shared by two sessions
    ConnectionManager singleConnection = new XaMpxConnectionManager(dbName + "-mgr", new BackendInfo(properties),
                                                                    10, 1, 1, 1, 1, 1, 720, 2160);
    try {
      Db a = openSession(dbName, GlobalTransactionMode.REQUIRED, singleConnection);
      Db b = openSession(dbName, GlobalTransactionMode.REQUIRED, singleConnection);
      createTable(a);

      long voucher = a.begin("first");
      assertNotEquals(voucher, 0L);
      insertRow(a, 1);
      ManagedConnection connection = a.connection();
      a.commit(voucher);
      assertFalse(connection.isDead(), "the connection must be back in autocommit mode when detached");

      voucher = b.begin("second");
      assertNotEquals(voucher, 0L);
      assertSame(b.connection(), connection, "the only connection must be handed over to the other session");
      insertRow(b, 2);
      b.rollback(voucher);

      voucher = b.begin("third");
      insertRow(b, 3);
      b.commit(voucher);

      assertEquals(countRows(a), 2);
      assertFalse(connection.isDead());
      a.close();
      b.close();
    }
    finally {
      singleConnection.shutdown();
    }
  }

}
