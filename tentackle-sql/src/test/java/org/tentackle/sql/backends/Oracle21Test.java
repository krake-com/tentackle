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
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.SqlType;

import java.sql.Types;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the Oracle21 backend, which opts in to the native {@code JSON} column type.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class Oracle21Test {

  @Test
  public void testMatchesUrlOnlyByName() {
    Oracle21 backend = new Oracle21();
    assertFalse(backend.isMatchingUrl("jdbc:oracle:thin:@localhost:1521:tentackle"),
                "Oracle21 must never be auto-selected: it shares the URL with Oracle");
    assertEquals(backend.getName(), "Oracle21");
    Assert.assertFalse(backend.isDeprecated());
  }

  @Test
  public void testJsonIsNative() {
    Oracle21 backend = new Oracle21();
    assertTrue(backend.isJsonSupported());
    assertEquals(backend.sqlTypeToString(SqlType.JSON, 0), "JSON");
    // every other type is unaffected
    assertEquals(backend.sqlTypeToString(SqlType.CLOB, 0), new Oracle().sqlTypeToString(SqlType.CLOB, 0));
  }

  @Test
  public void testJdbcTypeMapsNativeJsonColumn() {
    Oracle21 backend = new Oracle21();
    assertEquals(backend.jdbcTypeToSqlType(Types.OTHER, "JSON", 0, 0), new SqlType[] { SqlType.JSON });
    assertEquals(backend.jdbcTypeToSqlType(Types.OTHER, "json", 0, 0), new SqlType[] { SqlType.JSON },
                 "the type name must match case-insensitively");
    // an unrelated OTHER column must not be mistaken for JSON
    assertEquals(backend.jdbcTypeToSqlType(Types.OTHER, "SOMETHING_ELSE", 0, 0).length, 0);
  }

  @Test
  public void testValidateVersion() {
    Oracle21 backend = new Oracle21();
    assertTrue(expectThrows(BackendException.class,
                            () -> backend.validateVersion(new BackendVersionInfo(19, 0)))
                   .getMessage().contains("21"));
    backend.validateVersion(new BackendVersionInfo(21, 0));   // must not throw
    backend.validateVersion(new BackendVersionInfo(23, 0));   // must not throw
  }

}
