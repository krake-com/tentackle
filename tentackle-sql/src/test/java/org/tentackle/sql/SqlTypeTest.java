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

package org.tentackle.sql;

import org.tentackle.sql.datatypes.UUIDType;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests {@link SqlType#parse(String)} and {@link SqlType#getDefaultValue()}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SqlTypeTest {

  @Test
  public void testDefaultValues() {
    assertEquals(SqlType.VARCHAR.getDefaultValue(), "");
    assertEquals(SqlType.CLOB.getDefaultValue(), "");
    assertEquals(SqlType.CHAR.getDefaultValue(), ' ');
    assertEquals(SqlType.BIT.getDefaultValue(), false);
    assertEquals(SqlType.TINYINT.getDefaultValue(), (byte) 0);
    assertEquals(SqlType.SMALLINT.getDefaultValue(), (short) 0);
    assertEquals(SqlType.INTEGER.getDefaultValue(), 0);
    assertEquals(SqlType.BIGINT.getDefaultValue(), 0L);
    assertEquals(SqlType.FLOAT.getDefaultValue(), 0.0f);
    assertEquals(SqlType.DOUBLE.getDefaultValue(), 0.0d);
    assertEquals(SqlType.DECIMAL.getDefaultValue(), new BigDecimal(0));
    assertEquals(SqlType.DATE.getDefaultValue(), new Date(0));
    assertEquals(SqlType.TIME.getDefaultValue(), new Time(0));
    assertEquals(SqlType.TIMESTAMP.getDefaultValue(), new Timestamp(0));
    assertEquals(SqlType.UUID.getDefaultValue(), UUIDType.NIL);
    assertNull(SqlType.BLOB.getDefaultValue());
    assertNull(SqlType.JAVA_OBJECT.getDefaultValue());
  }

  @Test
  public void testRemoveSingleQuotes() {
    assertEquals(SqlType.VARCHAR.parse("'abc'"), "abc");
    assertEquals(SqlType.VARCHAR.parse("abc"), "abc");     // no quotes
    assertEquals(SqlType.VARCHAR.parse("''"), "");         // just the quotes
    assertEquals(SqlType.VARCHAR.parse("'"), "'");         // too short to be a quoted string
    assertEquals(SqlType.CLOB.parse("'x'"), "x");
  }

  @Test
  public void testParseNumbers() {
    assertEquals(SqlType.INTEGER.parse("42"), 42);
    assertEquals(SqlType.BIGINT.parse("42"), 42L);
    assertEquals(SqlType.TINYINT.parse("7"), (byte) 7);
    assertEquals(SqlType.SMALLINT.parse("7"), (short) 7);
    assertEquals(SqlType.FLOAT.parse("1.5"), 1.5f);
    assertEquals(SqlType.DOUBLE.parse("1.5"), 1.5d);
    assertEquals(SqlType.DECIMAL.parse("3.5"), BigDecimal.valueOf(3.5));
    // quotes are stripped before parsing
    assertEquals(SqlType.INTEGER.parse("'42'"), 42);
  }

  @Test
  public void testParseNumbersFallbackOnError() {
    assertEquals(SqlType.INTEGER.parse("notanumber"), 0);
    assertEquals(SqlType.BIGINT.parse("x"), 0L);
    assertEquals(SqlType.TINYINT.parse("x"), (byte) 0);
    assertEquals(SqlType.SMALLINT.parse("x"), (short) 0);
    assertEquals(SqlType.FLOAT.parse("x"), 0.0f);
    assertEquals(SqlType.DOUBLE.parse("x"), 0.0d);
    assertEquals(SqlType.DECIMAL.parse("x"), new BigDecimal(0));
  }

  @Test
  public void testParseBoolean() {
    assertEquals(SqlType.BIT.parse("true"), true);
    assertEquals(SqlType.BIT.parse("TRUE"), true);
    assertEquals(SqlType.BIT.parse("false"), false);
    assertEquals(SqlType.BIT.parse("nonsense"), false);
  }

  @Test
  public void testParseChar() {
    assertEquals(SqlType.CHAR.parse("A"), 'A');
    assertEquals(SqlType.CHAR.parse("'B'"), 'B');
    assertEquals(SqlType.CHAR.parse(""), ' ');       // empty -> default char
  }

  @Test
  public void testParseTemporal() {
    assertEquals(SqlType.DATE.parse("2020-01-15"), Date.valueOf("2020-01-15"));
    assertEquals(SqlType.TIME.parse("10:20:30"), Time.valueOf("10:20:30"));
    assertEquals(SqlType.TIMESTAMP.parse("2020-01-15 10:20:30"), Timestamp.valueOf("2020-01-15 10:20:30"));
    // parse failure -> epoch fallbacks
    assertEquals(SqlType.DATE.parse("x"), new Date(0));
    assertEquals(SqlType.TIME.parse("x"), new Time(0));
    assertEquals(SqlType.TIMESTAMP.parse("x"), new Timestamp(0));
  }

  @Test
  public void testParseUuid() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertEquals(SqlType.UUID.parse(uuid.toString()), uuid);
    assertEquals(SqlType.UUID.parse("not-a-uuid"), UUIDType.NIL);
  }

  @Test
  public void testUnconvertibleTypes() {
    assertNull(SqlType.BLOB.parse("anything"));
    assertNull(SqlType.JAVA_OBJECT.parse("anything"));
  }
}
