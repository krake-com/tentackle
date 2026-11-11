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

package org.tentackle.persist;

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.Constants;
import org.tentackle.dbms.Db;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.server.TestServer;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.sql.SQLException;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests that an exception thrown by the PostgreSQL JDBC driver on the server arrives at the client
 * as a plain {@link SQLException}.
 * <p>
 * The client usually doesn't have the JDBC driver on its classpath, so the driver's exception class
 * must not travel over the wire, or the client could not deserialize it. {@code DriverSQLExceptionTripTest}
 * in {@code tentackle-database} covers this with a hand-made driver exception; this test verifies it with
 * a real one in a real client/server round trip: the out-of-process {@link TestServer} runs on PostgreSQL
 * and the client provokes a unique constraint violation.
 * <p>
 * The client of this test does have the driver on its classpath. Receiving exactly an {@link SQLException}
 * instead of a {@code PSQLException} therefore proves that the server did not send the driver's class.
 * <p>
 * Belongs to the {@code postgres} group, which the default suite excludes. Run it with
 * {@code mvn -Ppostgres test} against a prepared database.
 *
 * @author harald
 */
public class RemotePostgresExceptionTest {

  private static final String PSQL_EXCEPTION = "org.postgresql.util.PSQLException";
  private static final String UNIQUE_VIOLATION = "23505";


  /**
   * Skips the test if the PostgreSQL database is not available.<br>
   * Otherwise, the server would just fail to start.
   */
  @BeforeMethod(alwaysRun = true)
  public void checkPostgres() {
    Session session;
    try {
      session = Pdo.createSession(Pdo.createSessionInfo("postgres"));
    }
    catch (PersistenceException ex) {
      throw new SkipException("no postgres backend -> no tests", ex);
    }
    try {
      if (!(session instanceof Db db) || !"PostgreSQL".equals(db.getBackend().getName())) {
        throw new SkipException("postgres.properties does not point at PostgreSQL -> no tests");
      }
    }
    finally {
      session.close();
    }
  }


  @Test(groups = "postgres")
  public void testUniqueViolation() throws Exception {
    Reporter.log("RemotePostgresExceptionTest.testUniqueViolation <br>", true);

    // the server listens where the client connects to
    String service = Pdo.createSessionInfo("client").getProperties().getProperty("url");
    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(
        TestServer.class, "--" + Constants.BACKEND_PROPS + "=postgres", "--service=" + service));
    try {
      server.awaitReady(30000);

      Session session = Pdo.createSession(Pdo.createSessionInfo("client"));
      try {
        assertTrue(session.isRemote());
        DomainContext context = Pdo.createDomainContext(session);
        String name = "RPET-" + UUID.randomUUID().toString().substring(0, 20);

        try {
          // the exception rolls back the transaction, i.e. the database remains unchanged
          session.transaction(() -> {
            createPool(context, name).persist();
            createPool(context, name).persist();
            return null;
          });
          fail("unique constraint violation expected");
        }
        catch (PersistenceException ex) {
          SQLException sx = null;
          for (Throwable t = ex; t != null; t = t.getCause()) {
            assertFalse(t.getClass().getName().startsWith("org.postgresql."), "driver class sent to the client");
            if (sx == null && t instanceof SQLException sqlException) {
              sx = sqlException;
            }
          }
          assertNotNull(sx, "no SQLException in cause chain of " + ex);
          for (SQLException next = sx.getNextException(); next != null; next = next.getNextException()) {
            assertFalse(next.getClass().getName().startsWith("org.postgresql."), "driver class sent to the client");
          }
          assertSame(sx.getClass(), SQLException.class);
          assertEquals(sx.getSQLState(), UNIQUE_VIOLATION);
          assertTrue(sx.getMessage().contains("numpool_udk"), sx.getMessage());
          assertTrue(sx.getMessage().endsWith(" (" + PSQL_EXCEPTION + ")"), sx.getMessage());
        }

        // the session is still usable and nothing was persisted
        assertTrue(session.isOpen());
        assertEquals(Pdo.create(NumberPool.class, context).selectByUniqueDomainKey(name), null);
      }
      finally {
        session.close();
      }
    }
    finally {
      server.stopAndWait();
    }
  }


  private NumberPool createPool(DomainContext context, String name) {
    NumberPool pool = Pdo.create(NumberPool.class, context);
    pool.setName(name);
    pool.setRealm("test");
    return pool;
  }

}
