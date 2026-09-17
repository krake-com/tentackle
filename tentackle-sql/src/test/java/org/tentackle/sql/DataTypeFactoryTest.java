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

import org.tentackle.common.BMoney;
import org.tentackle.common.Binary;
import org.tentackle.common.DMoney;
import org.tentackle.common.I18NText;
import org.tentackle.sql.datatypes.ConvertibleType;
import org.tentackle.sql.datatypes.LargeStringType;
import org.tentackle.sql.datatypes.JsonType;
import org.tentackle.sql.datatypes.SQLXMLType;
import org.tentackle.sql.datatypes.StringType;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link DefaultDataTypeFactory}.
 * <p>
 * Data types are looked up by the plain java type name plus a variant, and the factory hands out
 * singletons. Because the lookup key is what the model and the wurblets address a type by, a type
 * missing from the SPI index would break code generation rather than the runtime, which is why every
 * predefined type is asserted here.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DataTypeFactoryTest {

  private final DataTypeFactory factory = DataTypeFactory.getInstance();


  @Test
  public void testSingleton() {
    assertSame(DataTypeFactory.getInstance(), factory);
    assertTrue(factory instanceof DefaultDataTypeFactory);
  }

  @Test
  public void testDataTypesAreSingletons() {
    assertSame(factory.get("String"), factory.get("String"));
    assertSame(factory.get("String"), factory.get(String.class));
  }

  @Test
  public void testLookupByJavaType() {
    assertEquals(factory.get("String").getJavaType(), "String");
    assertEquals(factory.get("int").getJavaType(), "int");
    assertNull(factory.get("NoSuchType"), "an unknown type must not fail, just be absent");
    assertNull(factory.get("String", "NoSuchVariant"));
  }

  @Test
  public void testLookupByClass() {
    assertEquals(factory.get(Integer.class).getJavaType(), "Integer");
    assertEquals(factory.get(BigDecimal.class).getJavaType(), "BigDecimal");
    assertEquals(factory.get(UUID.class).getJavaType(), "UUID");
    assertEquals(factory.get(BMoney.class).getJavaType(), "BMoney");
    assertEquals(factory.get(DMoney.class).getJavaType(), "DMoney");
    assertEquals(factory.get(Binary.class).getJavaType(), "Binary");
    assertEquals(factory.get(I18NText.class).getJavaType(), "I18NText");
    assertEquals(factory.get(Instant.class).getJavaType(), "Instant");
    assertEquals(factory.get(LocalDate.class).getJavaType(), "LocalDate");
    assertEquals(factory.get(LocalTime.class).getJavaType(), "LocalTime");
    assertEquals(factory.get(LocalDateTime.class).getJavaType(), "LocalDateTime");
    assertEquals(factory.get(OffsetTime.class).getJavaType(), "OffsetTime");
    assertEquals(factory.get(OffsetDateTime.class).getJavaType(), "OffsetDateTime");
    assertEquals(factory.get(ZonedDateTime.class).getJavaType(), "ZonedDateTime");
  }

  /**
   * The variant is part of the key: the large string is a different type than the plain one,
   * although both describe {@code java.lang.String}.
   */
  @Test
  public void testVariant() {
    DataType<?> plain = factory.get("String");
    DataType<?> large = factory.get("String", DataTypeFactory.LARGE_VARIANT);
    assertTrue(plain instanceof StringType);
    assertTrue(large instanceof LargeStringType);
    assertEquals(plain.getVariant(), "");
    assertEquals(large.getVariant(), DataTypeFactory.LARGE_VARIANT);
    assertEquals(plain.getSqlType(null, 0), SqlType.VARCHAR);
    assertEquals(large.getSqlType(null, 0), SqlType.CLOB);
    assertEquals(large.getDataTypeConstant(), "DT_STRING_LARGE");
  }

  /**
   * Same as {@link #testVariant()}, for the SQLXML-bridged variant of {@code String}.
   * <p>
   * Unlike the other string variants this one does not support MAPNULL, because the empty string
   * is not an XML document.
   */
  @Test
  public void testSqlxmlVariant() {
    DataType<?> plain = factory.get("String");
    DataType<?> sqlxml = factory.get("String", DataTypeFactory.SQLXML_VARIANT);
    assertTrue(plain instanceof StringType);
    assertTrue(sqlxml instanceof SQLXMLType);
    assertEquals(sqlxml.getVariant(), DataTypeFactory.SQLXML_VARIANT);
    assertEquals(sqlxml.getSqlType(null, 0), SqlType.SQLXML);
    assertEquals(sqlxml.getDataTypeConstant(), "DT_STRING_SQLXML");
    assertTrue(plain.isMapNullSupported());
    assertFalse(sqlxml.isMapNullSupported());
    assertTrue(plain.isComparable());
    assertFalse(sqlxml.isComparable(), "databases provide no comparator for XML documents");
  }

  /**
   * {@code "SQLXML"} is not a {@link DataType} key of its own: looking it up by plain java type
   * name must alias the SQLXML variant of {@code String}, since that is what the model uses
   * (see the {@code SQLXML} attribute type in the model definition).
   */
  @Test
  public void testSqlxmlAlias() {
    assertSame(factory.get("SQLXML"), factory.get("String", DataTypeFactory.SQLXML_VARIANT));
  }

  /**
   * Same as {@link #testVariant()}, for the JSON variant of {@code String}.
   * <p>
   * Unlike the other string variants this one does not support MAPNULL, because the empty string
   * is not a JSON document.
   */
  @Test
  public void testJsonVariant() {
    DataType<?> plain = factory.get("String");
    DataType<?> json = factory.get("String", DataTypeFactory.JSON_VARIANT);
    assertTrue(plain instanceof StringType);
    assertTrue(json instanceof JsonType);
    assertEquals(json.getVariant(), DataTypeFactory.JSON_VARIANT);
    assertEquals(json.getSqlType(null, 0), SqlType.JSON);
    assertEquals(json.getDataTypeConstant(), "DT_STRING_JSON");
    assertTrue(plain.isMapNullSupported());
    assertFalse(json.isMapNullSupported());
    assertTrue(plain.isComparable());
    assertFalse(json.isComparable(), "databases provide no comparator for JSON documents");
  }

  /**
   * {@code "JSON"} is not a {@link DataType} key of its own: looking it up by plain java type
   * name must alias the JSON variant of {@code String}, since that is what the model uses
   * (see the {@code JSON} attribute type in the model definition).
   */
  @Test
  public void testJsonAlias() {
    assertSame(factory.get("JSON"), factory.get("String", DataTypeFactory.JSON_VARIANT));
  }

  @Test
  public void testConvertibleType() {
    ConvertibleType convertibleType = factory.getConvertibleType();
    assertNotNull(convertibleType);
    assertSame(convertibleType, factory.get(DataTypeFactory.CONVERTIBLE_TYPE));
    assertEquals(convertibleType.getSqlType(null, 0), SqlType.JAVA_OBJECT);
    assertTrue(convertibleType.isModelProvidingInnerType());
  }

  /**
   * All predefined types must be reachable through the SPI index, otherwise the model would
   * silently reject a perfectly valid attribute type.
   */
  @Test
  public void testAllPredefinedTypesArePresent() {
    for (String javaType : new String[] {
        "boolean", "Boolean", "byte", "Byte", "char", "Character", "short", "Short",
        "int", "Integer", "long", "Long", "float", "Float", "double", "Double",
        "BigDecimal", "String", "Binary", "TBinary", "UUID",
        "BMoney", "DMoney", "I18NText",
        "Date", "Time", "Timestamp",
        "LocalDate", "LocalTime", "LocalDateTime", "OffsetTime", "OffsetDateTime", "ZonedDateTime", "Instant" }) {
      DataType<?> dataType = factory.get(javaType);
      assertNotNull(dataType, javaType + " is not registered as a DataType service");
      assertEquals(dataType.getJavaType(), javaType);
    }
  }

}
