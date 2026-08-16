/*
 * Tentackle - https://tentackle.org.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.sql.BackendPreparedStatement;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Tests for the H2 backend.
 * <p>
 * H2 is the default backend for Tentackle's own tests, so its generated SQL is worth pinning
 * down. The paging and locking clauses are the interesting part: unlike postgres, H2 insists on
 * the SQL standard order {@code OFFSET ... FETCH FIRST ... FOR UPDATE}, and anything else is a
 * syntax error rather than a wrong result.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class H2Test {

  private static final String SELECT = "SELECT * FROM h2test WHERE 1=1";

  private final H2 backend = new H2();

  private Connection connection;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:h2test");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE h2test (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32))");
    }
  }

  @AfterClass(alwaysRun = true)
  public void dropDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }


  /**
   * The clause combinations a query can be built from.
   *
   * @return writeLock, limit, offset and the expected SQL suffix
   */
  @DataProvider(name = "selects")
  public Object[][] selects() {
    return new Object[][] {
      // writeLock, limit, offset, expected suffix
      { false, 0, 0, "" },
      { false, 5, 0, " FETCH FIRST ? ROWS ONLY" },
      { false, 0, 3, " OFFSET ? ROWS" },
      { false, 5, 3, " OFFSET ? ROWS FETCH FIRST ? ROWS ONLY" },
      { true,  0, 0, " FOR UPDATE" },
      { true,  5, 0, " FETCH FIRST ? ROWS ONLY FOR UPDATE" },
      { true,  0, 3, " OFFSET ? ROWS FOR UPDATE" },
      { true,  5, 3, " OFFSET ? ROWS FETCH FIRST ? ROWS ONLY FOR UPDATE" }
    };
  }


  /**
   * Asserts the generated SQL and proves H2 really accepts it.
   */
  @Test(dataProvider = "selects")
  public void testBuildSelectSql(boolean writeLock, int limit, int offset, String expectedSuffix) throws SQLException {
    StringBuilder sqlBuilder = new StringBuilder(SELECT);
    backend.buildSelectSql(sqlBuilder, writeLock, limit, offset);
    String sql = sqlBuilder.toString();
    assertEquals(sql, SELECT + expectedSuffix);

    // a syntax error here means the clause order is wrong
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      RecordingStatement recorder = new RecordingStatement(stmt);
      BackendPreparedStatement backendStmt = recorder.proxy();
      int index = backend.setLeadingSelectParameters(backendStmt, limit, offset);
      backend.setTrailingSelectParameters(backendStmt, index, limit, offset);
      stmt.executeQuery().close();
    }
  }


  /**
   * The trailing parameters must be set in the same order the clauses were appended in,
   * which for H2 is offset before limit.
   */
  @Test
  public void testTrailingSelectParameterOrder() {
    RecordingStatement recorder = new RecordingStatement(null);
    BackendPreparedStatement backendStmt = recorder.proxy();
    int index = backend.setLeadingSelectParameters(backendStmt, 5, 3);
    index = backend.setTrailingSelectParameters(backendStmt, index, 5, 3);
    assertEquals(recorder.values, List.of(3, 5), "offset must be bound before limit");
    assertEquals(index, 3);
  }


  @Test
  public void testIsDatabaseInMemory() {
    assertEquals(backend.isDatabaseInMemory("jdbc:h2:mem:default"), true);
    assertEquals(backend.isDatabaseInMemory("jdbc:h2:mem:default;DB_CLOSE_DELAY=-1"), true);
    assertEquals(backend.isDatabaseInMemory("jdbc:h2:./target/h2/tentackle;AUTO_SERVER=TRUE"), false);
    assertEquals(backend.isDatabaseInMemory("jdbc:postgresql://localhost/tentackle"), false);
  }


  /**
   * Records the ints bound and forwards them to a real statement, if given.
   */
  private static class RecordingStatement implements InvocationHandler {

    private final PreparedStatement stmt;
    private final List<Integer> values = new ArrayList<>();

    RecordingStatement(PreparedStatement stmt) {
      this.stmt = stmt;
    }

    /**
     * Creates the proxy. {@link BackendPreparedStatement} is far too large to implement by hand
     * and only {@code setInt} is of interest here.
     *
     * @return the proxy
     */
    BackendPreparedStatement proxy() {
      return (BackendPreparedStatement) Proxy.newProxyInstance(
          BackendPreparedStatement.class.getClassLoader(),
          new Class<?>[] { BackendPreparedStatement.class }, this);
    }

    @Override
    public Object invoke(Object p, java.lang.reflect.Method method, Object[] args) throws Throwable {
      if ("setInt".equals(method.getName())) {
        int index = (Integer) args[0];
        int value = (Integer) args[1];
        values.add(value);
        if (stmt != null) {
          stmt.setInt(index, value);
        }
      }
      return null;
    }
  }

}
