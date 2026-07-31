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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.common.Binary;
import org.tentackle.common.TBinary;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

/**
 * Tests reading and writing {@link Binary} and {@link TBinary} columns.
 * <p>
 * Both ways to apply a binary are covered: via the {@link DataType} (as done by the generated
 * persistence layer) and via the setters of the {@link PreparedStatementWrapper} (as done by
 * {@link Query}). Notice that {@code PreparedStatement#setBlob} must not be used, because
 * some backends map {@code BLOB} to a plain binary type (postgres: {@code BYTEA}) whereas the
 * jdbc drivers create a large object and bind its object id.
 *
 * @author harald
 */
public class BinaryColumnTest extends AbstractDbTest {

  private static final String TABLE_NAME = "binarytest";
  private static final String BINARY_TEXT = "some binary";
  private static final String TBINARY_TEXT = "some other binary";


  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createTestTable() {
    Db db = getSession();
    Backend backend = db.getBackend();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(TABLE_NAME, null));
    ddl.append("    ").append(backend.sqlCreateColumn(
        "bin", null, SqlType.BLOB, 0, 0, true, null, false, true));
    ddl.append("    ").append(backend.sqlCreateColumn(
        "tbin", null, SqlType.BLOB, 0, 0, true, null, false, false));
    ddl.append(backend.sqlCreateTableClosing(TABLE_NAME, null));

    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(ddl.toString());
    }
  }


  /**
   * Applies the binaries via their datatype, as done by the persistence layer.
   */
  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testApplyByDataType() throws IOException, ClassNotFoundException {
    Db db = getSession();
    DataType binaryType = DataTypeFactory.getInstance().get(Binary.class);
    DataType tbinaryType = DataTypeFactory.getInstance().get(TBinary.class);

    try (PreparedStatementWrapper st = db.createPreparedStatement(b -> insertSql())) {
      st.set(binaryType, 1, new Binary<>(BINARY_TEXT), false, null);
      st.set(tbinaryType, 2, new TBinary<>(TBINARY_TEXT), false, null);
      assertEquals(st.executeUpdate(), 1);
    }

    assertBinaries();
  }


  /**
   * Applies the binaries via the statement setters, as done by {@link Query}.
   */
  @Test(dependsOnMethods = "testApplyByDataType")
  public void testApplyBySetter() throws IOException, ClassNotFoundException {
    Db db = getSession();

    try (PreparedStatementWrapper st = db.createPreparedStatement(b -> insertSql())) {
      st.setBinary(1, new Binary<>(BINARY_TEXT));
      st.setTBinary(2, new TBinary<>(TBINARY_TEXT));
      assertEquals(st.executeUpdate(), 1);
    }

    assertBinaries();
  }


  private String insertSql() {
    return "INSERT INTO " + TABLE_NAME + " (bin, tbin) VALUES (?, ?)";
  }


  /**
   * Asserts that all rows written so far contain the expected binaries.
   */
  private void assertBinaries() throws IOException, ClassNotFoundException {
    Db db = getSession();
    int rows = 0;

    try (PreparedStatementWrapper st = db.createPreparedStatement(b -> "SELECT bin, tbin FROM " + TABLE_NAME);
         ResultSetWrapper rs = st.executeQuery()) {
      while (rs.next()) {
        Binary<String> binary = rs.getBinary(1, 0);
        TBinary<String> tbinary = rs.getTBinary(2, 0);
        assertEquals(binary.getObject(), BINARY_TEXT);
        assertEquals(tbinary.getObject(), TBINARY_TEXT);
        rows++;
      }
    }

    assertEquals(rows > 0, true, "no rows written");
  }

}
