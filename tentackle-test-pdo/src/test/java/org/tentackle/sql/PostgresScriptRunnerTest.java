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

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests aborting a script against a real PostgreSQL server.
 * <p>
 * Cancelling a statement is the one part of the {@link ScriptRunner} that cannot be verified
 * against the in-memory H2 database the rest of the suite uses: it needs a statement that
 * blocks for a controllable amount of time ({@code PG_SLEEP}) and it asserts the wording
 * PostgreSQL uses to report the abort. It also is the only coverage of
 * {@code PostgresScriptRunner}, the sole backend-specific script runner.
 * <p>
 * Therefore this test is in the {@code postgres} group, which the default suite excludes. Run
 * it with {@code mvn -Ppostgres test} against a prepared database.
 * <p>
 * Notice that the test does <em>not</em> extend {@code AbstractPdoTest}: that base class opens
 * its session in a {@code @BeforeSuite} shared by every test class, so a single suite has a
 * single backend. A test that needs a different one has to bring its own session, just as the
 * remote tests do.
 *
 * @author harald
 */
public class PostgresScriptRunnerTest {

  private static final String SLEEP = "SELECT PG_SLEEP(10)";

  private static final String EXPECTED_MESSAGE_TEMPLATE = """
      script execution failed at line %d: %s ...
      ERROR: canceling statement due to user request""";

  private Db session;
  private long txVoucher;


  /**
   * Opens a session of its own on the PostgreSQL database and starts a transaction.
   * <p>
   * Per method rather than per class on purpose: the session is opened on the default
   * connection manager, whose connections are a shared budget. {@code SessionPoolExecutorTest}
   * sizes its pool to exactly what is left over, so holding a connection for the whole class
   * would starve it. The suite is single-threaded, so a per-method session never overlaps.
   * The transaction is what attaches the session to a connection in the first place.
   */
  @BeforeMethod(alwaysRun = true)
  public void openPostgresSession() {
    try {
      session = (Db) Pdo.createSession(Pdo.createSessionInfo("postgres"));
    }
    catch (PersistenceException ex) {
      throw new SkipException("no postgres backend -> no tests", ex);
    }
    if (!"PostgreSQL".equals(session.getBackend().getName())) {
      // guards against silently passing against another backend, whose message would differ
      session.close();
      session = null;
      throw new SkipException("postgres.properties does not point at PostgreSQL -> no tests");
    }
    txVoucher = session.begin("postgresScriptRunnerTest");
  }

  @AfterMethod(alwaysRun = true)
  public void closePostgresSession() {
    if (session != null) {
      if (session.isTxRunning()) {
        session.rollback(txVoucher);
      }
      session.close();
      session = null;
    }
  }


  @Test(groups = "postgres")
  public void testTimeout() {
    Reporter.log("PostgresScriptRunnerTest.testTimeout <br>", true);

    ScriptRunner scriptRunner = session.getBackend().createScriptRunner(session.connection().getConnection());
    scriptRunner.setExecutionTimeout(2);
    try {
      scriptRunner.addExecutionListener(s -> assertEquals(s, SLEEP));
      scriptRunner.addResultListener(r -> fail("should not reach here: " + r));
      scriptRunner.run("-- just a comment\n" + SLEEP + ";");
      fail("the script should have been aborted");
    }
    catch (ScriptRunnerException ex) {
      List<ScriptRunnerResult> results = ex.getResults();
      assertEquals(results.size(), 0);
      assertEquals(ex.getMessage(), EXPECTED_MESSAGE_TEMPLATE.formatted(2, SLEEP));
    }
  }


  @Test(groups = "postgres")
  public void testCancel() {
    Reporter.log("PostgresScriptRunnerTest.testCancel <br>", true);

    ScriptRunner scriptRunner = session.getBackend().createScriptRunner(session.connection().getConnection());
    try {
      new Thread(() -> {
        try {
          Thread.sleep(2000);
          scriptRunner.cancel();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }).start();
      scriptRunner.run(SLEEP + ";");
      fail("the script should have been cancelled");
    }
    catch (ScriptRunnerException ex) {
      List<ScriptRunnerResult> results = ex.getResults();
      assertEquals(results.size(), 0);
      assertEquals(ex.getMessage(), EXPECTED_MESSAGE_TEMPLATE.formatted(1, SLEEP));
    }
  }

}
