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

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Tests paging for the backends that emulate LIMIT and OFFSET by numbering the rows in a nested select.
 * <p>
 * The generated SQL of {@link Db2} ({@code ROW_NUMBER()}) and {@link Oracle8} ({@code ROWNUM}) is run
 * against H2, which understands both, so the tests prove that the right rows are returned rather than
 * just pinning down the SQL text.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class PagingTest {

  private static final int ROWS = 10;

  private Connection connection;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:pagingtest");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE paging (id BIGINT NOT NULL PRIMARY KEY)");
      for (int i = 1; i <= ROWS; i++) {
        stmt.execute("INSERT INTO paging (id) VALUES (" + i + ")");
      }
    }
  }

  @AfterClass(alwaysRun = true)
  public void dropDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }


  /**
   * The backends and the paging parameters.
   *
   * @return the backend, the inner SQL, limit, offset and the expected ids
   */
  @DataProvider(name = "pages")
  public Object[][] pages() {
    List<Object[]> pages = new ArrayList<>();
    for (Backend backend : List.of(new Db2(), new Oracle8())) {
      // with and without the leading SELECT
      for (String sql : List.of("* FROM paging ORDER BY id", "SELECT * FROM paging ORDER BY id")) {
        pages.add(new Object[] { backend, sql, 3, 0, List.of(1L, 2L, 3L) });
        pages.add(new Object[] { backend, sql, 0, 7, List.of(8L, 9L, 10L) });
        pages.add(new Object[] { backend, sql, 3, 4, List.of(5L, 6L, 7L) });
        pages.add(new Object[] { backend, sql, 5, 8, List.of(9L, 10L) });
        pages.add(new Object[] { backend, sql, 0, 0, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L) });
      }
    }
    return pages.toArray(new Object[0][]);
  }

  @Test(dataProvider = "pages")
  public void testPaging(Backend backend, String innerSql, int limit, int offset, List<Long> expectedIds)
         throws SQLException {
    String sql = backend.buildSelectSql(innerSql, false, limit, offset);
    List<Long> ids = new ArrayList<>();
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      RecordingStatement recorder = new RecordingStatement(stmt);
      int index = backend.setLeadingSelectParameters(recorder.proxy(), limit, offset);
      backend.setTrailingSelectParameters(recorder.proxy(), index, limit, offset);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getLong("id"));
        }
      }
    }
    assertEquals(ids, expectedIds, backend + ": " + sql);
  }


  @Test
  public void testLeadingSelectIsReplacedByPagingKeywords() {
    assertEquals(new MsSql().buildSelectSql("SELECT * FROM foo", false, 10, 0), "SELECT TOP ? * FROM foo");
    assertEquals(new MsSql().buildSelectSql("* FROM foo", false, 10, 0), "SELECT TOP ? * FROM foo");
    assertEquals(new Informix().buildSelectSql("SELECT * FROM foo", false, 10, 20), "SELECT SKIP ? FIRST ? * FROM foo");
    assertEquals(new Informix().buildSelectSql("* FROM foo", false, 10, 0), "SELECT FIRST ? * FROM foo");
    assertEquals(new Informix().buildSelectSql("SELECT * FROM foo", false, 0, 0), "SELECT * FROM foo");
  }

  @Test
  public void testWithCannotBePaged() {
    expectThrows(BackendException.class, () -> new Db2().buildSelectSql("WITH x AS (SELECT 1) SELECT * FROM x", false, 10, 0));
    // without paging, a WITH select is passed unchanged
    assertEquals(new Db2().buildSelectSql("WITH x AS (SELECT 1) SELECT * FROM x", false, 0, 0),
                 "WITH x AS (SELECT 1) SELECT * FROM x");
  }

}
