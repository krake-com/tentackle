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

package org.tentackle.sql.backends;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Statement;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests how the backends classify {@link SQLException}s.
 * <p>
 * The classification decides whether a transaction is retried, whether a connection is dropped from
 * the pool or whether the exception reaches the application, so a misclassification is either a lost
 * retry or an endless one. Drivers report the interesting condition on a chained exception more often
 * than on the one thrown, which is the case worth pinning down.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class BackendExceptionMappingTest {

  /**
   * All non-deprecated backends.
   *
   * @return each backend as a single argument
   */
  @DataProvider(name = "backends")
  public Object[][] backends() {
    return BackendFactory.getInstance().getAllBackends().stream()
        .map(backend -> new Object[] { backend }).toArray(Object[][]::new);
  }


  /**
   * Creates an exception carrying an SQL state.
   *
   * @param sqlState the SQL state
   * @return the exception
   */
  private static SQLException withState(String sqlState) {
    return new SQLException("failed", sqlState);
  }

  /**
   * Creates an exception carrying a vendor error code.
   *
   * @param errorCode the vendor error code
   * @return the exception
   */
  private static SQLException withErrorCode(int errorCode) {
    return new SQLException("failed", null, errorCode);
  }

  /**
   * Chains an exception behind a first one that carries nothing useful.
   *
   * @param next the exception carrying the information
   * @return the leading exception
   */
  private static SQLException chained(SQLException next) {
    SQLException head = new SQLException("wrapper");
    head.setNextException(next);
    return head;
  }


  // ---- the SQL standard classes every backend understands ------------------

  @Test(dataProvider = "backends")
  public void testConstraintViolation(Backend backend) {
    assertTrue(backend.isConstraintException(withState("23505")), backend + ": unique violation");
    assertTrue(backend.isConstraintException(withState("23000")), backend + ": integrity constraint");
    assertFalse(backend.isConstraintException(withState("42000")), backend + ": syntax error is no constraint");
    assertFalse(backend.isConstraintException(new SQLException("no state at all")), backend.toString());
  }

  @Test(dataProvider = "backends")
  public void testCommunicationLink(Backend backend) {
    assertTrue(backend.isCommunicationLinkException(withState("08006")), backend + ": connection failure");
    assertTrue(backend.isCommunicationLinkException(withState("57P01")), backend + ": operator intervention");
    assertFalse(backend.isCommunicationLinkException(withState("23505")), backend.toString());
  }

  @Test(dataProvider = "backends")
  public void testTransientTransaction(Backend backend) {
    assertTrue(backend.isTransientTransactionException(withState("40001")),
               backend + ": a serialization failure is retryable");
    assertTrue(backend.isTransientTransactionException(new SQLTransactionRollbackException("rolled back")),
               backend + ": the typed exception is retryable whatever the state says");
    assertFalse(backend.isTransientTransactionException(withState("23505")),
                backend + ": a constraint violation is not retryable");
  }

  /**
   * The condition is regularly reported on a chained exception rather than on the one thrown,
   * so the whole chain has to be walked.
   *
   * @param backend the backend
   */
  @Test(dataProvider = "backends")
  public void testChainedExceptionIsInspected(Backend backend) {
    assertTrue(backend.isConstraintException(chained(withState("23505"))), backend.toString());
    assertTrue(backend.isCommunicationLinkException(chained(withState("08006"))), backend.toString());
    assertTrue(backend.isTransientTransactionException(chained(withState("40001"))), backend.toString());
  }


  // ---- the vendor-specific conditions --------------------------------------

  @Test
  public void testBackendSpecificTransientConditions() {
    // H2: deadlock, lock timeout and concurrent update
    assertTrue(new H2().isTransientTransactionException(withState("50200")));
    assertTrue(new H2().isTransientTransactionException(withState("90131")));

    // postgres: deadlock detected and lock not available
    assertTrue(new Postgres().isTransientTransactionException(withState("40P01")));
    assertTrue(new Postgres().isTransientTransactionException(withState("55P03")));

    // mysql reports the deadlock only as a vendor error code
    assertTrue(new MySql().isTransientTransactionException(withErrorCode(1213)));
    assertTrue(new MySql().isTransientTransactionException(withErrorCode(1205)));
    assertFalse(new MySql().isTransientTransactionException(withErrorCode(1062)), "duplicate entry is permanent");

    // oracle's ORA-00060 deadlock
    assertTrue(new Oracle().isTransientTransactionException(withErrorCode(60)));
    assertTrue(new Oracle().isTransientTransactionException(withErrorCode(54)));

    assertTrue(new Db2().isTransientTransactionException(withState("57014")));
    assertTrue(new Db2().isTransientTransactionException(withErrorCode(-952)));

    assertTrue(new MsSql().isTransientTransactionException(withErrorCode(1222)));
    assertTrue(new MsSql().isTransientTransactionException(withState("HY008")));

    // and a chained vendor error code counts as well
    assertTrue(new MySql().isTransientTransactionException(chained(withErrorCode(1213))));
  }


  // ---- the backend id ------------------------------------------------------

  /**
   * The backend id identifies the database session and is logged with the SQL, so it must not
   * come back empty.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testH2BackendId() throws SQLException {
    H2 backend = new H2();
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:backendidtest")) {
      String id = backend.getBackendId(connection);
      assertNotNull(id);
      assertTrue(id.startsWith("ID-"), id);
    }
  }

  /**
   * A backend id that cannot be determined must fail loudly instead of returning something wrong.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testBackendIdOnClosedConnection() throws SQLException {
    Connection connection = DriverManager.getConnection("jdbc:h2:mem:backendidfail");
    connection.close();
    org.testng.Assert.expectThrows(org.tentackle.sql.BackendException.class,
                                   () -> new H2().getBackendId(connection));
  }

  /**
   * The keep-alive select must be a statement the backend really accepts, because it is sent on
   * a connection that is otherwise idle and a failure there looks like a lost connection.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testKeepAliveSelectIsValid() throws SQLException {
    H2 backend = new H2();
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:keepalivetest");
         Statement stmt = connection.createStatement()) {
      stmt.executeQuery(backend.getDummySelect()).close();
    }
  }

}
