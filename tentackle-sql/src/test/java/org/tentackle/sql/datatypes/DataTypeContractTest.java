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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.backends.H2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the contract {@link AbstractDataType} implements on behalf of all data types.
 * <p>
 * The column naming is the part with the widest blast radius: it is what the wurblets generate the
 * {@code CN_...} constants from and what the DDL generator emits, so a suffix changing its shape would
 * silently rename columns of an existing database.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DataTypeContractTest {

  private final Backend backend = new H2();

  private final DataTypeFactory factory = DataTypeFactory.getInstance();


  @AfterMethod
  public void resetMaxLinesInLiteral() {
    AbstractDataType.maxLinesInLiteral = -1;
  }


  /**
   * All registered data types.
   *
   * @return each data type as a single argument
   */
  @DataProvider(name = "allDataTypes")
  public Object[][] allDataTypes() {
    return List.of("boolean", "Boolean", "byte", "Byte", "char", "Character", "short", "Short",
                   "int", "Integer", "long", "Long", "float", "Float", "double", "Double",
                   "BigDecimal", "String", "Binary", "TBinary", "UUID",
                   "BMoney", "DMoney", "I18NText",
                   "Date", "Time", "Timestamp",
                   "LocalDate", "LocalTime", "LocalDateTime",
                   "OffsetTime", "OffsetDateTime", "ZonedDateTime", "Instant")
               .stream().map(name -> new Object[] { factory.get(name) }).toArray(Object[][]::new);
  }


  // ---- invariants every data type must obey --------------------------------

  @Test(dataProvider = "allDataTypes")
  public void testColumnNamingIsConsistent(DataType<?> dataType) {
    int columnCount = dataType.getColumnCount(backend);
    assertTrue(columnCount >= 1, dataType + " must map to at least one column");

    List<String> columnNames = dataType.createColumnNames(backend, "foo");
    assertEquals(columnNames.size(), columnCount);
    assertEquals(columnNames.get(0), "foo", "the first column must carry the plain basename");
    assertEquals(columnNames.stream().distinct().count(), (long) columnCount, "column names must be unique");
    assertEquals(dataType.createColumnNamesAsString(backend, "foo", ", "), String.join(", ", columnNames));

    for (int index = 0; index < columnCount; index++) {
      assertNotNull(dataType.getSqlType(backend, index), dataType + " has no SQL type for column " + index);
      assertEquals(dataType.getColumnSuffix(backend, index).orElse(""),
                   columnNames.get(index).substring("foo".length()));
    }
  }

  @Test(dataProvider = "allDataTypes")
  public void testColumnSuffixRejectsBadIndex(DataType<?> dataType) {
    expectThrows(IndexOutOfBoundsException.class, () -> dataType.getColumnSuffix(backend, -1));
    expectThrows(IndexOutOfBoundsException.class,
                 () -> dataType.getColumnSuffix(backend, dataType.getColumnCount(backend)));
  }

  @Test(dataProvider = "allDataTypes")
  public void testDataTypeConstant(DataType<?> dataType) {
    String constant = dataType.getDataTypeConstant();
    assertTrue(constant.startsWith("DT_"), constant);
    assertEquals(constant, constant.toUpperCase(java.util.Locale.ROOT), "constants are upper case");
  }

  @Test(dataProvider = "allDataTypes")
  public void testPrimitiveConversion(DataType<?> dataType) {
    if (dataType.isPrimitive()) {
      DataType<?> nonPrimitive = dataType.toNonPrimitive();
      assertFalse(nonPrimitive.isPrimitive(), dataType + " must map to a non-primitive");
      assertSame(nonPrimitive.toPrimitive().orElseThrow(), dataType, "the mapping must be symmetric");
    }
    else {
      assertSame(dataType.toNonPrimitive(), dataType, "a non-primitive maps to itself");
      dataType.toPrimitive().ifPresent(primitive -> assertTrue(primitive.isPrimitive()));
    }
  }

  @Test(dataProvider = "allDataTypes")
  public void testComparableByDefault(DataType<?> dataType) {
    assertTrue(dataType.isComparable(), dataType + " must be comparable unless overridden");
  }

  @Test(dataProvider = "allDataTypes")
  public void testEqualsAndHashCode(DataType<?> dataType) {
    // data types are singletons, but equals/hashCode must still be sane
    assertEquals(dataType, dataType);
    assertFalse(dataType.equals(null));
    assertFalse(dataType.equals("not a data type"));
    assertEquals(dataType.hashCode(), dataType.hashCode());
    assertFalse(dataType.equals(factory.get("Boolean")) && !"Boolean".equals(dataType.getJavaType()),
                dataType + " must not equal an unrelated type");
  }


  // ---- single-column defaults ----------------------------------------------

  @Test
  public void testSingleColumnDefaults() {
    DataType<?> stringType = factory.get("String");
    assertEquals(stringType.getColumnCount(backend), 1);
    assertEquals(stringType.getColumnCount(null), 1, "a non backend-specific type needs no backend");
    assertEquals(stringType.getColumnSuffix(backend, 0), Optional.empty());
    assertEquals(stringType.getCommentSuffix(backend, 0), Optional.empty());
    assertEquals(stringType.getColumnGetter(0, "value"), "");
    assertEquals(stringType.getColumnAlias(0), "");
    assertEquals(stringType.getSortableColumns(), new int[] { 0 });
    assertEquals(stringType.createColumnNames(backend, "name"), List.of("name"));
  }

  @Test
  public void testSizeAndScaleDefaults() {
    DataType<?> stringType = factory.get("String");
    assertEquals(stringType.getSize(backend, 0, null), 0, "no size in the model means no size");
    assertEquals(stringType.getSize(backend, 0, 32), 32);
    assertEquals(stringType.getScale(backend, 0, null), 0);
    assertEquals(factory.get("BigDecimal").getScale(backend, 0, 4), 4);
  }

  @Test
  public void testGetColumnValue() {
    DataType<String> stringType = factory.get(String.class);
    assertEquals(stringType.getColumnValue(backend, 0, "hello"), "hello");
    assertEquals(stringType.getColumnValue(null, 0, "hello"), "hello");
  }

  /**
   * The column names of the multi-column types feed the DDL, so their exact shape is part of the
   * on-disk schema.
   */
  @Test
  public void testMultiColumnNaming() {
    DataType<?> bmoney = factory.get("BMoney");
    assertEquals(bmoney.getColumnCount(backend), 2);
    assertEquals(bmoney.createColumnNames(backend, "amount"), List.of("amount", "amount_2"));
    assertEquals(bmoney.createColumnNamesAsString(backend, "amount", ","), "amount,amount_2");
    assertEquals(bmoney.getColumnSuffix(backend, 1), Optional.of("_2"));
  }

  @Test
  public void testColumnNamesAreUnmodifiable() {
    List<String> names = factory.get("String").createColumnNames(backend, "foo");
    expectThrows(UnsupportedOperationException.class, () -> names.add("bar"));
  }


  // ---- literals ------------------------------------------------------------

  @Test
  public void testToLiteral() {
    DataType<?> stringType = factory.get("String");
    assertEquals(stringType.toLiteral("abc", null), "'abc'");
    assertEquals(stringType.toLiteral("'abc'", null), "'abc'", "already quoted");
    assertEquals(stringType.toLiteral("", null), "", "an empty string is left alone");
    assertEquals(stringType.toLiteral("abc", 0), "abc", "a column literal is not quoted");
    assertEquals(factory.get("Integer").toLiteral("42", null), "42", "numbers are never quoted");
  }

  @Test
  public void testIsLiteralSupported() {
    assertTrue(factory.get("String").isLiteralSupported(null));
    assertTrue(factory.get("String").isLiteralSupported(0));
    assertFalse(factory.get("BMoney").isLiteralSupported(null), "a multi-column type has no single literal");
    assertTrue(factory.get("BMoney").isLiteralSupported(0), "but each of its columns has");
    assertFalse(factory.get("I18NText").isLiteralSupported(null));
    assertFalse(factory.get("I18NText").isLiteralSupported(0));
  }

  @Test
  public void testValueOfLiteralToCode() {
    // a literal that the java compiler accepts as-is
    assertEquals(factory.get("Integer").valueOfLiteralToCode("42", null), "42");
    // a string given in quotes is parsed at runtime
    assertEquals(factory.get("Integer").valueOfLiteralToCode("\"42\"", null), "Integer.valueOf(\"42\")");
    assertEquals(factory.get("int").valueOfLiteralToCode("\"42\"", null), "int.parseInteger(\"42\")");
    // strings become java string literals
    assertEquals(factory.get("String").valueOfLiteralToCode("abc", null), "\"abc\"");
    // a multi-column type has no literal, so the value is parsed at runtime
    assertEquals(factory.get("BMoney").valueOfLiteralToCode("1.23", null), "BMoney.valueOf(\"1.23\")");
    assertEquals(factory.get("BMoney").valueOfLiteralToCode("1.23", 0), "1.23");
  }

  /**
   * A multi-line default value must become a java text block, else the generated code would not compile.
   */
  @Test
  public void testMultiLineLiteralBecomesTextBlock() {
    assertEquals(factory.get("String").valueOfLiteralToCode("line1\nline2", null),
                 "\"\"\"\nline1\nline2\n\"\"\"");
    // a trailing blank would be swallowed by the compiler -> it must be escaped
    assertEquals(factory.get("String").valueOfLiteralToCode("line1 \nline2", null),
                 "\"\"\"\nline1\\s\nline2\n\"\"\"");
  }

  @Test
  public void testMultiLineLiteralIsTruncated() {
    AbstractDataType.maxLinesInLiteral = 2;
    assertEquals(factory.get("String").valueOfLiteralToCode("a\nb\nc\nd", null),
                 "\"\"\"\na\nb\n... <2/4> ...\n\"\"\"");
  }

  @Test
  public void testToString() {
    assertEquals(factory.get("String").toString(), "String");
    assertEquals(factory.get("Binary").toString(), "Binary<?>", "generified types are marked");
    assertEquals(factory.get(Integer.class).toString(42), "42");
  }


  // ---- MAPNULL -------------------------------------------------------------

  @Test
  public void testMapNull() {
    DataType<?> stringType = factory.get("String");
    assertTrue(stringType.isMapNullSupported());
    assertEquals(stringType.getMappedNullValue(backend, 0), backend.getEmptyString());
    assertTrue(expectThrows(BackendException.class, () -> stringType.getMappedNullValue(null, 0))
                   .getMessage().contains("backend-specific"));

    DataType<?> integerType = factory.get("Integer");
    assertFalse(integerType.isMapNullSupported());
    assertTrue(expectThrows(BackendException.class, () -> integerType.getMappedNullValue(backend, 0))
                   .getMessage().contains("MAPNULL"));

    assertFalse(factory.get("char").isMapNullSupported(), "the primitive char has no null to map");
  }


  // ---- backend-specific types ----------------------------------------------

  /**
   * A type whose column count depends on the backend must refuse to answer without one,
   * rather than guessing.
   */
  @Test
  public void testBackendSpecificTypeNeedsBackend() {
    DataType<?> i18nText = factory.get("I18NText");
    assertTrue(i18nText.isColumnCountBackendSpecific());
    assertTrue(expectThrows(BackendException.class, i18nText::getSortableColumns)
                   .getMessage().contains("backend-agnostic"));

    assertFalse(factory.get("String").isColumnCountBackendSpecific());
    assertEquals(factory.get("String").getSortableColumns(), new int[] { 0 });
  }


  // ---- the fallbacks a custom multi-column type inherits -------------------

  /**
   * An application-provided multi-column type that does not implement the column accessors must
   * fail loudly instead of generating nonsense.
   */
  @Test
  public void testUnimplementedMultiColumnFallbacks() {
    TwoColumnType type = new TwoColumnType();
    assertEquals(type.getCommentSuffix(backend, 0), Optional.empty());
    assertEquals(type.getCommentSuffix(backend, 1), Optional.of(" (2)"));
    expectThrows(UnsupportedOperationException.class, () -> type.getColumnGetter(0, "value"));
    expectThrows(UnsupportedOperationException.class, () -> type.getColumnAlias(0));
    expectThrows(UnsupportedOperationException.class,
                 () -> type.set(backend, null, 1, "x", 0, false, null));
    assertTrue(expectThrows(BackendException.class, () -> type.getMappedNullValue(backend, 0))
                   .getMessage().contains("MAPNULL"));
    assertFalse(type.isPredefined());
    assertEquals(type.getDataTypeConstant(), "DT_TWOCOLUMN_TEST");
    assertEquals(type.valueOfLiteralToCode("x", null), "DT_TWOCOLUMN_TEST.valueOf(\"x\")");
  }


  /**
   * A minimal application-specific type with two columns and no column accessors.
   */
  private static class TwoColumnType extends AbstractDataType<String> {

    @Override
    public String getJavaType() {
      return "TwoColumn";
    }

    @Override
    public String getVariant() {
      return "Test";
    }

    @Override
    public boolean isPredefined() {
      return false;
    }

    @Override
    public int getColumnCount(Backend backend) {
      return 2;
    }

    @Override
    public SqlType getSqlType(Backend backend, int index) {
      return SqlType.VARCHAR;
    }

    @Override
    public String valueOf(String str) {
      return str;
    }

    @Override
    public Object[] set(Backend backend, PreparedStatement statement, int pos, String object,
                        boolean mapNull, Integer size) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) {
      throw new UnsupportedOperationException();
    }
  }

}
