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

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.SqlType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the Oracle23 backend, which opts in to the native {@code BOOLEAN} column type
 * and {@code DROP ... IF EXISTS}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class Oracle23Test {

  private static final String TABLE = "oracle23test";

  @Test
  public void testMatchesUrlOnlyByName() {
    Oracle23 backend = new Oracle23();
    assertFalse(backend.isMatchingUrl("jdbc:oracle:thin:@localhost:1521:tentackle"),
                "Oracle23 must never be auto-selected: it shares the URL with Oracle");
    assertEquals(backend.getName(), "Oracle23");
    Assert.assertFalse(backend.isDeprecated());
  }

  @Test
  public void testBooleanIsNative() {
    Oracle23 backend = new Oracle23();
    assertEquals(backend.sqlTypeToString(SqlType.BIT, 0), "BOOLEAN");
    // every other type is unaffected, including the JSON support inherited from Oracle21
    assertEquals(backend.sqlTypeToString(SqlType.CLOB, 0), new Oracle21().sqlTypeToString(SqlType.CLOB, 0));
    assertEquals(backend.sqlTypeToString(SqlType.JSON, 0), new Oracle21().sqlTypeToString(SqlType.JSON, 0));
    assertTrue(backend.isJsonSupported(), "Oracle23 must still support native JSON, inherited from Oracle21");
  }

  @Test
  public void testBooleanLiteral() {
    Oracle23 backend = new Oracle23();
    assertEquals(backend.valueToLiteral(SqlType.BIT, Boolean.TRUE), "TRUE");
    assertEquals(backend.valueToLiteral(SqlType.BIT, Boolean.FALSE), "FALSE");
    // other types are unaffected
    assertEquals(backend.valueToLiteral(SqlType.INTEGER, 42), new Oracle().valueToLiteral(SqlType.INTEGER, 42));
  }

  @Test
  public void testDropIfExists() {
    Oracle23 backend = new Oracle23();
    backend.setDropIfExistsEnabled(false);
    assertFalse(backend.sqlDropTable(null, TABLE).contains(Backend.SQL_IF_EXISTS));

    backend.setDropIfExistsEnabled(true);
    assertTrue(backend.isDropIfExistsEnabled());
    assertTrue(backend.sqlDropTable(null, TABLE).contains(Backend.SQL_IF_EXISTS));
    assertTrue(backend.sqlDropIndex(null, TABLE, "oracle23test_name").contains(Backend.SQL_IF_EXISTS));
    assertTrue(backend.sqlDropColumn(TABLE, "extra").contains(Backend.SQL_IF_EXISTS));

    // Oracle21 never supports IF EXISTS, unlike its Oracle23 subclass
    Oracle21 oracle21 = new Oracle21();
    oracle21.setDropIfExistsEnabled(true);
    assertFalse(oracle21.isDropIfExistsEnabled());
  }

  @Test
  public void testValidateVersion() {
    Oracle23 backend = new Oracle23();
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.validateVersion(new BackendVersionInfo(22, 0)))
                   .getMessage().contains("23"));
    backend.validateVersion(new BackendVersionInfo(23, 0));   // must not throw
    backend.validateVersion(new BackendVersionInfo(24, 0));   // must not throw
  }

}
