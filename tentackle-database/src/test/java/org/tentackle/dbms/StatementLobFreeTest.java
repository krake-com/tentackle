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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the LOBs created for statement parameters are freed after execution.<br>
 * Otherwise, temporary LOBs would be held by the driver or database until the connection is closed.
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatementLobFreeTest extends AbstractDbTest {

  private static final StatementId QUERY_ID = new StatementId();
  private static final StatementId CONSUMED_ID = new StatementId();


  @Test
  public void testLobFreedAfterQuery() {
    Db db = getSession();
    assertTrue(db.getBackend().isClobSupported(), "test requires a backend supporting CLOBs");

    PreparedStatementWrapper stmt = db.getPreparedStatement(new StatementKey(QUERY_ID, getClass()), false,
                                                            _ -> "SELECT LENGTH(CAST(? AS CLOB))");
    stmt.setLargeString(1, "large string");
    assertTrue(stmt.isLobPending(), "a CLOB should have been created for the parameter");

    try (ResultSetWrapper rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(rs.getLong(1), 12L);
    }
    assertFalse(stmt.isLobPending(), "the CLOB must be freed after the result set is closed");
  }


  @Test
  public void testLobFreedWhenConsumedWithoutExecution() {
    Db db = getSession();
    PreparedStatementWrapper stmt = db.getPreparedStatement(new StatementKey(CONSUMED_ID, getClass()), false,
                                                            _ -> "SELECT LENGTH(CAST(? AS CLOB))");
    stmt.setLargeString(1, "never executed");
    assertTrue(stmt.isLobPending(), "a CLOB should have been created for the parameter");

    stmt.consume();
    assertFalse(stmt.isLobPending(), "the CLOB must be freed when the statement is consumed");
  }

}
