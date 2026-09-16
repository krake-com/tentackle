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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.sql.BackendException;
import org.tentackle.sql.SqlType;

/**
 * Tests for the MariaDB backend.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MariaDbTest {

  private final MariaDb backend = new MariaDb();


  /**
   * MariaDB's NEXTVAL takes the sequence's identifier, not a string literal as in postgres.
   */
  @Test
  public void testSequences() {
    Assert.assertTrue(backend.isSequenceSupported());
    Assert.assertEquals(backend.sqlNextFromSequence("numpool_seq"), "SELECT NEXTVAL(numpool_seq)");
    Assert.assertEquals(backend.sqlCreateSequence("numpool_seq", null, null), "CREATE SEQUENCE numpool_seq");
    Assert.assertEquals(backend.sqlCreateSequence("numpool_seq", 100L, 2L),
                        "CREATE SEQUENCE numpool_seq INCREMENT BY 2 START WITH 100");
  }

  @Test
  public void testValidateVersion() {
    // sequences need at least 10.3
    Assert.assertThrows(BackendException.class, () -> backend.validateVersion(10, 2));
    // the mariadb driver connects to mysql servers as well
    Assert.assertThrows(BackendException.class, () -> backend.validateVersion(8, 0));
    backend.validateVersion(10, 3);
    backend.validateVersion(11, 4);
  }

  /**
   * Unlike MySQL, MariaDB cannot index an expression at all.
   */
  @Test
  public void testFunctionBasedIndexNotSupported() {
    Assert.assertFalse(backend.isFunctionBasedIndexSupported());
    Assert.assertFalse(backend.isFilteredIndexSupported());
  }

  @Test
  public void testDropIfExists() {
    backend.setDropIfExistsEnabled(true);
    try {
      Assert.assertTrue(backend.isDropIfExistsEnabled());
      Assert.assertEquals(backend.sqlDropTable(null, "foo"), "DROP TABLE IF EXISTS foo;\n");
      Assert.assertEquals(backend.sqlDropForeignKey("foo", "fk_foo_bar"),
                          "ALTER TABLE foo DROP FOREIGN KEY IF EXISTS fk_foo_bar;\n");
    }
    finally {
      backend.setDropIfExistsEnabled(false);
    }
  }

  /**
   * MariaDB's JSON is only an alias for LONGTEXT plus a {@code json_valid()} check constraint, and
   * the driver reports it as LONGTEXT. Declaring it as JSON would make the metadata drift away from
   * the model and provoke an ALTER on every migration run.
   */
  @Test
  public void testJsonIsLongText() {
    Assert.assertFalse(backend.isJsonSupported());
    Assert.assertEquals(backend.columnTypeToString(SqlType.JSON, 0, 0), "LONGTEXT");
    // unlike MariaDB, mysql has a real JSON column type
    MySql mySql = new MySql();
    Assert.assertTrue(mySql.isJsonSupported());
    Assert.assertEquals(mySql.columnTypeToString(SqlType.JSON, 0, 0), "JSON");
  }

  /**
   * The MySQL peculiarities are inherited.
   */
  @Test
  public void testInheritedFromMySql() {
    Assert.assertEquals(backend.buildSelectSql("SELECT * FROM foo", true, 10, 20),
                        "SELECT * FROM foo LIMIT ? OFFSET ? FOR UPDATE");
    Assert.assertTrue(backend.isAliasRequiredForSubSelect());
    Assert.assertFalse(backend.isSchemaSupported());
  }

}
