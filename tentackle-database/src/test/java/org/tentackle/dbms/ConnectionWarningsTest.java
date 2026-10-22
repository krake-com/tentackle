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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests logging and clearing the warnings of a connection that is closed concurrently.
 * <p>
 * The cleanup of a force-detached connection closes its prepared statements, while the session
 * may still clear the warnings. Reading the warnings of a statement closed in the meantime must not fail.
 * Furthermore, closing a connection must close the physical connection even if reading its warnings fails.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ConnectionWarningsTest {

  private static final StatementId TEST_STATEMENT_ID = new StatementId();


  /**
   * A manager wrapping the JDBC connections to inject failures.
   */
  private static class FailingManager extends DefaultConnectionManager {

    private volatile boolean failConnectionWarnings;            // reading the connection's warnings fails
    private volatile PreparedStatementWrapper closeOnWarnings;  // closed while reading its warnings
    private volatile boolean physicallyClosed;                  // the JDBC connection was closed

    private FailingManager(String name) {
      super(name, 2, 8, 1, 720, 2160);
    }

    @Override
    protected Connection connect(BackendInfo backendInfo) throws SQLException {
      Connection connection = super.connect(backendInfo);
      return proxy(Connection.class, (_, method, args) -> {
        switch (method.getName()) {
          case "getWarnings" -> {
            if (failConnectionWarnings) {
              throw new SQLException("reading warnings failed");
            }
          }
          case "close" -> physicallyClosed = true;
          case "prepareStatement" -> {
            PreparedStatement statement = (PreparedStatement) invoke(connection, method, args);
            return proxy(PreparedStatement.class, (stmtProxy, stmtMethod, stmtArgs) -> {
              PreparedStatementWrapper wrapper = closeOnWarnings;
              if ("getWarnings".equals(stmtMethod.getName()) && wrapper != null && wrapper.getStatement() == stmtProxy) {
                // closed by the cleanup of another thread, as JDBC drivers report it
                closeOnWarnings = null;
                wrapper.close();
                throw new SQLException("statement is closed");
              }
              return invoke(statement, stmtMethod, stmtArgs);
            });
          }
          default -> { }
        }
        return invoke(connection, method, args);
      });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> clazz, InvocationHandler handler) {
      return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(target, args);
      }
      catch (InvocationTargetException ix) {
        throw ix.getCause();
      }
    }
  }


  @Test
  public void testStatementClosedWhileClearingWarnings() {
    FailingManager conMgr = new FailingManager("warnings-closed-stmt-test");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      long txVoucher = db.begin("warnings");
      PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(TEST_STATEMENT_ID, getClass()), false,
                                                            Backend::getDummySelect);
      ManagedConnection con = db.connection();
      conMgr.closeOnWarnings = st;
      con.logAndClearWarnings();    // must not fail
      assertTrue(st.isClosed());
      db.rollback(txVoucher);
    }
    finally {
      db.close();
      conMgr.shutdown();
    }
  }

  @Test
  public void testClosedAlthoughWarningsFailed() {
    FailingManager conMgr = new FailingManager("warnings-close-test");
    Db db = new Db(conMgr, new DefaultSessionInfo());
    try {
      long txVoucher = db.begin("warnings");
      ManagedConnection con = db.connection();
      db.rollback(txVoucher);

      conMgr.failConnectionWarnings = true;
      con.close();    // must not fail
      assertTrue(con.isClosed(), "connection not closed");
      assertTrue(conMgr.physicallyClosed, "JDBC connection leaked");
      assertFalse(con.isAttached());
    }
    finally {
      conMgr.failConnectionWarnings = false;
      db.close();
      conMgr.shutdown();
    }
  }

}
