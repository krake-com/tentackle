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

import org.tentackle.sql.Backend;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the prepared statement cache of a {@link ManagedConnection}.<br>
 * Statements dropped from the cache without being closed would be leaked
 * until the physical connection is closed.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class PreparedStatementCacheTest extends AbstractDbTest {

  private static final StatementId CACHED_ID = new StatementId();
  private static final StatementId ALWAYS_PREPARED_ID = new StatementId();


  /**
   * Consumed statements must survive the cleanup of the pending ones.<br>
   * Dropping them from the cache unclosed would leak them.
   */
  @Test
  public void testCleanupOfPendingStatementsKeepsCachedStatements() {
    Db db = getSession();
    StatementKey key = new StatementKey(CACHED_ID, getClass());

    PreparedStatementWrapper stmt = db.getPreparedStatement(key, false, Backend::getDummySelect);
    try (ResultSetWrapper rs = stmt.executeQuery()) {
      assertTrue(rs.next());
    }
    assertFalse(stmt.isMarkedReady(), "the statement should have been consumed by closing the result set");

    // clean up the pending statements, as done when a connection is force-detached
    db.connection().closePreparedStatements(true);

    assertFalse(stmt.isClosed(), "a consumed statement must not be dropped from the cache without being closed");

    PreparedStatementWrapper again = db.getPreparedStatement(key, false, Backend::getDummySelect);
    // notice that both statements print the same, so compare the identities explicitly
    assertSame(again, stmt, "the cached statement should have been re-used, but it was prepared again: " +
                            System.identityHashCode(stmt) + " != " + System.identityHashCode(again));
    again.consume();
  }


  /**
   * Pending statements must be closed by the cleanup.
   */
  @Test
  public void testCleanupClosesPendingStatements() {
    Db db = getSession();
    StatementKey key = new StatementKey(new StatementId(), getClass());

    PreparedStatementWrapper stmt = db.getPreparedStatement(key, false, Backend::getDummySelect);
    assertTrue(stmt.isMarkedReady(), "a requested statement is pending until consumed");

    db.connection().closePreparedStatements(true);

    assertTrue(stmt.isClosed(), "a pending statement must be closed by the cleanup");
  }


  /**
   * An always-prepared statement replaces the cached one, which must be closed and not leaked.
   */
  @Test
  public void testAlwaysPrepareClosesReplacedStatement() {
    Db db = getSession();
    StatementKey key = new StatementKey(ALWAYS_PREPARED_ID, getClass());

    PreparedStatementWrapper first = db.getPreparedStatement(key, true, Backend::getDummySelect);
    first.consume();

    PreparedStatementWrapper second = db.getPreparedStatement(key, true, Backend::getDummySelect);
    second.consume();

    assertNotSame(second, first, "alwaysPrepare must create a new statement");
    assertTrue(first.isClosed(), "the replaced statement must be closed, else it is leaked");
  }

}
