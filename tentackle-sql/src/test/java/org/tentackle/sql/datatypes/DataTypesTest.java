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

package org.tentackle.sql.datatypes;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.SqlType;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the scalar, non-temporal {@link org.tentackle.sql.DataType} implementations:
 * {@code valueOf} conversion, java-type and SQL-type mapping, and the classification flags.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DataTypesTest {

  // ---- valueOf conversion --------------------------------------------------

  @Test
  public void testNumericValueOf() {
    assertEquals(new IntegerType().valueOf("42"), Integer.valueOf(42));
    assertEquals(new LongType().valueOf("42"), Long.valueOf(42));
    assertEquals(new ShortType().valueOf("7"), Short.valueOf((short) 7));
    assertEquals(new ByteType().valueOf("7"), Byte.valueOf((byte) 7));
    assertEquals(new DoubleType().valueOf("1.5"), Double.valueOf(1.5));
    assertEquals(new FloatType().valueOf("1.5"), Float.valueOf(1.5f));
    assertEquals(new BigDecimalType().valueOf("3.50"), new BigDecimal("3.50"));
  }

  @Test
  public void testStringValueOf() {
    assertEquals(new StringType().valueOf("hello"), "hello");
    assertEquals(new StringType().valueOf("'quoted'"), "quoted");   // quotes are stripped
  }

  @Test
  public void testBooleanValueOf() {
    BooleanType type = new BooleanType();
    assertEquals(type.valueOf("true"), Boolean.TRUE);
    assertEquals(type.valueOf("t"), Boolean.TRUE);
    assertEquals(type.valueOf("1"), Boolean.TRUE);
    assertEquals(type.valueOf("false"), Boolean.FALSE);
    assertEquals(type.valueOf("f"), Boolean.FALSE);
    assertEquals(type.valueOf("0"), Boolean.FALSE);
  }

  @Test
  public void testBooleanValueOfInvalid() {
    assertThrows(BackendException.class, () -> new BooleanType().valueOf("maybe"));
  }

  @Test
  public void testCharacterValueOf() {
    assertEquals(new CharacterType().valueOf("A"), Character.valueOf('A'));
    assertThrows(TentackleRuntimeException.class, () -> new CharacterType().valueOf("ab"));
    assertThrows(TentackleRuntimeException.class, () -> new CharacterType().valueOf(""));
  }

  @Test
  public void testUuidValueOf() {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertEquals(new UUIDType().valueOf(uuid.toString()), uuid);
    assertThrows(IllegalArgumentException.class, () -> new UUIDType().valueOf("not-a-uuid"));
  }


  // ---- getJavaType ---------------------------------------------------------

  @Test
  public void testJavaTypes() {
    assertEquals(new IntegerType().getJavaType(), "Integer");
    assertEquals(new IntegerPrimitiveType().getJavaType(), "int");
    assertEquals(new LongType().getJavaType(), "Long");
    assertEquals(new LongPrimitiveType().getJavaType(), "long");
    assertEquals(new ShortType().getJavaType(), "Short");
    assertEquals(new ByteType().getJavaType(), "Byte");
    assertEquals(new DoubleType().getJavaType(), "Double");
    assertEquals(new FloatType().getJavaType(), "Float");
    assertEquals(new BigDecimalType().getJavaType(), "BigDecimal");
    assertEquals(new StringType().getJavaType(), "String");
    assertEquals(new BooleanType().getJavaType(), "Boolean");
    assertEquals(new CharacterType().getJavaType(), "Character");
    assertEquals(new UUIDType().getJavaType(), "UUID");
  }


  // ---- classification flags ------------------------------------------------

  @Test
  public void testPrimitiveFlag() {
    assertFalse(new IntegerType().isPrimitive());
    assertTrue(new IntegerPrimitiveType().isPrimitive());
    assertTrue(new LongPrimitiveType().isPrimitive());
    assertTrue(new BooleanPrimitiveType().isPrimitive());
  }

  @Test
  public void testNumericFlag() {
    assertTrue(new IntegerType().isNumeric());
    assertTrue(new BigDecimalType().isNumeric());
    assertFalse(new StringType().isNumeric());
    assertFalse(new BooleanType().isNumeric());
  }

  @Test
  public void testBoolFlag() {
    assertTrue(new BooleanType().isBool());
    assertFalse(new IntegerType().isBool());
    assertFalse(new StringType().isBool());
  }


  // ---- getSqlType ----------------------------------------------------------

  @Test
  public void testSqlTypeMapping() {
    assertEquals(new IntegerType().getSqlType(null, 0), SqlType.INTEGER);
    assertEquals(new LongType().getSqlType(null, 0), SqlType.BIGINT);
    assertEquals(new ShortType().getSqlType(null, 0), SqlType.SMALLINT);
    assertEquals(new ByteType().getSqlType(null, 0), SqlType.TINYINT);
    assertEquals(new DoubleType().getSqlType(null, 0), SqlType.DOUBLE);
    assertEquals(new FloatType().getSqlType(null, 0), SqlType.FLOAT);
    assertEquals(new BigDecimalType().getSqlType(null, 0), SqlType.DECIMAL);
    assertEquals(new StringType().getSqlType(null, 0), SqlType.VARCHAR);
    assertEquals(new BooleanType().getSqlType(null, 0), SqlType.BIT);
    assertEquals(new CharacterType().getSqlType(null, 0), SqlType.CHAR);
  }

  @Test
  public void testSqlTypeRejectsBadIndex() {
    assertThrows(IndexOutOfBoundsException.class, () -> new IntegerType().getSqlType(null, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> new StringType().getSqlType(null, 1));
  }
}
