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

package org.tentackle.sql;

import org.testng.annotations.Test;

import org.tentackle.sql.backends.H2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;

/**
 * Testing the transaction- and read-only handling of the script runner.<br>
 * Only a transaction started by the script runner itself is terminated by it,
 * and it is committed if and only if the script ran to completion. Note that a failed
 * statement does not roll back the transaction on all backends, and that switching
 * back to autocommit would implicitly commit whatever the script executed so far.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScriptRunnerTransactionTest {

  private static final String FIRST_SQL = "INSERT INTO blah VALUES (1)";
  private static final String SECOND_SQL = "INSERT INTO blah VALUES (2)";

  private static final String SCRIPT = FIRST_SQL + ";\n" + SECOND_SQL + ";\n";


  @Test
  public void testCommitOnSuccess() {
    FakeConnection connection = new FakeConnection();
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setTransactional(true);

    List<ScriptRunnerResult> results = scriptRunner.run(SCRIPT);

    assertEquals(results.size(), 2);
    assertEquals(connection.getCalls(), List.of("setAutoCommit(false)",
                                                "execute(" + FIRST_SQL + ")",
                                                "execute(" + SECOND_SQL + ")",
                                                "commit",
                                                "setAutoCommit(true)"));
  }

  @Test
  public void testRollbackOnFailure() {
    FakeConnection connection = new FakeConnection();
    connection.failOn(SECOND_SQL);
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setTransactional(true);

    expectThrows(ScriptRunnerException.class, () -> scriptRunner.run(SCRIPT));

    assertFalse(connection.getCalls().contains("commit"), "a failed script must not be committed");
    assertEquals(connection.getCalls(), List.of("setAutoCommit(false)",
                                                "execute(" + FIRST_SQL + ")",
                                                "execute(" + SECOND_SQL + ")",
                                                "rollback",
                                                "setAutoCommit(true)"));
  }

  @Test
  public void testRollbackOnCancel() {
    FakeConnection connection = new FakeConnection();
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setTransactional(true);
    // cancel while the first statement is about to be executed: the second one is not executed anymore
    scriptRunner.addExecutionListener(sql -> {
      if (FIRST_SQL.equals(sql)) {
        scriptRunner.cancel();
      }
    });

    List<ScriptRunnerResult> results = scriptRunner.run(SCRIPT);

    assertEquals(results.size(), 1);
    assertFalse(connection.getCalls().contains("commit"), "a canceled script must not be committed");
    assertEquals(connection.getCalls(), List.of("setAutoCommit(false)",
                                                "execute(" + FIRST_SQL + ")",
                                                "rollback",
                                                "setAutoCommit(true)"));
  }

  @Test
  public void testRunningTransactionIsLeftAlone() {
    FakeConnection connection = new FakeConnection();
    connection.setAutoCommit(false);    // the caller is already within a transaction
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setTransactional(true);

    scriptRunner.run(SCRIPT);

    // the transaction belongs to the caller and must be neither terminated nor switched to autocommit
    assertEquals(connection.getCalls(), List.of("execute(" + FIRST_SQL + ")",
                                                "execute(" + SECOND_SQL + ")"));
  }

  @Test
  public void testReadOnlyIsRestored() {
    FakeConnection connection = new FakeConnection();
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setReadOnly(true);

    scriptRunner.run(SCRIPT);

    assertEquals(connection.getCalls(), List.of("setReadOnly(true)",
                                                "execute(" + FIRST_SQL + ")",
                                                "execute(" + SECOND_SQL + ")",
                                                "setReadOnly(false)"));
  }

  @Test
  public void testReadOnlyConnectionIsNotSwitchedToReadWrite() {
    FakeConnection connection = new FakeConnection();
    connection.setReadOnly(true);         // the connection is read-only to begin with
    connection.failOnCreateStatement();   // fails before the runner could inspect the connection
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new H2(), connection.getConnection());
    scriptRunner.setReadOnly(true);

    expectThrows(BackendException.class, () -> scriptRunner.run(SCRIPT));

    assertEquals(connection.getCalls(), List.of(), "the script runner changed nothing, hence must revert nothing");
  }


  /**
   * A connection recording the calls relevant for transaction- and read-only handling.<br>
   * The same handler serves the statements created by this connection.
   */
  private static final class FakeConnection implements InvocationHandler {

    private final List<String> calls = new ArrayList<>();
    private final List<String> failingSql = new ArrayList<>();
    private boolean autoCommit = true;
    private boolean readOnly;
    private boolean failingCreateStatement;

    /**
     * Gets the connection.
     *
     * @return the connection
     */
    private Connection getConnection() {
      return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                                                 new Class<?>[] { Connection.class }, this);
    }

    /**
     * Gets the recorded calls.
     *
     * @return the calls in the order they were made
     */
    private List<String> getCalls() {
      return calls;
    }

    /**
     * Sets the initial autocommit state.
     *
     * @param autoCommit the autocommit state before the script runner is invoked
     */
    private void setAutoCommit(boolean autoCommit) {
      this.autoCommit = autoCommit;
    }

    /**
     * Sets the initial read-only state.
     *
     * @param readOnly the read-only state before the script runner is invoked
     */
    private void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
    }

    /**
     * Lets the given statement fail.
     *
     * @param sql the SQL statement failing with an {@link SQLException}
     */
    private void failOn(String sql) {
      failingSql.add(sql);
    }

    /**
     * Lets the creation of the statement fail.
     */
    private void failOnCreateStatement() {
      failingCreateStatement = true;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
      switch (method.getName()) {
        // --- connection ---
        case "createStatement": {
          if (failingCreateStatement) {
            throw new SQLException("cannot create statement");
          }
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Statement.class }, this);
        }
        case "getAutoCommit":
          return autoCommit;
        case "setAutoCommit":
          autoCommit = (Boolean) args[0];
          calls.add("setAutoCommit(" + autoCommit + ")");
          return null;
        case "isReadOnly":
          return readOnly;
        case "setReadOnly":
          readOnly = (Boolean) args[0];
          calls.add("setReadOnly(" + readOnly + ")");
          return null;
        case "commit":
        case "rollback":
          calls.add(method.getName());
          return null;

        // --- statement ---
        case "execute": {
          String sql = (String) args[0];
          calls.add("execute(" + sql + ")");
          if (failingSql.contains(sql)) {
            throw new SQLException("statement failed", "42000");
          }
          return false;   // no result set
        }
        case "getUpdateCount":
          return 1;
        case "isClosed":
          return false;
        case "setEscapeProcessing":
        case "setMaxRows":
        case "setQueryTimeout":
        case "cancel":
        case "close":
        case "clearWarnings":
          return null;

        // --- both ---
        case "getWarnings":
          return null;
        case "toString":
          return "fake connection";
        case "hashCode":
          return System.identityHashCode(proxy);
        case "equals":
          return proxy == args[0];
        default:
          throw new UnsupportedOperationException("fake connection does not implement " + method);
      }
    }
  }

}
