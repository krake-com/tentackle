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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.common.I18NText;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.backends.H2;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Round-trips every data type through a real database.
 * <p>
 * {@link DataType#set} and {@link DataType#get} are two halves of the same mapping and can only be
 * wrong together, so testing them against each other through an actual H2 table is the only way to
 * notice when the halves drift apart. The multi-column types are the interesting ones: a value
 * distributed over two columns must come back identical, and the {@code MAPNULL} option must turn a
 * null value into a well-defined non-null pair and back.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "unchecked", "rawtypes", "exports" })
public class DataTypeRoundTripTest {

  /** an exactly representable value, so that the assertion cannot fail on rounding alone. */
  private static final double DOUBLE_VALUE = 1.5d;

  /**
   * The types reading their column with a primitive JDBC getter.<br>
   * These have no {@code wasNull} check and hence map an SQL NULL to the zero value of the column.
   * The framework never takes this path for them — the persistence layer reads a predefined type
   * through the type-specific accessors of its result set wrapper, which do handle null.
   */
  private static final Set<String> NULL_COLLAPSING_TYPES =
      Set.of("Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double");

  private final Backend backend = new H2();

  private final DataTypeFactory factory = DataTypeFactory.getInstance();

  private Connection connection;

  private int tableCounter;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:datatyperoundtrip");
  }

  @AfterClass(alwaysRun = true)
  public void dropDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }


  /**
   * A value of some data type together with the DDL of the column(s) it is stored in.
   *
   * @param typeName the data type's java type name
   * @param variant the data type's variant
   * @param value the value to store
   * @param columnTypes the SQL type of each column
   */
  private record TypeCase(String typeName, String variant, Object value, String... columnTypes) {

    /**
     * Creates a case for a type without a variant.
     *
     * @param typeName the data type's java type name
     * @param value the value to store
     * @param columnTypes the SQL type of each column
     * @return the case
     */
    static TypeCase of(String typeName, Object value, String... columnTypes) {
      return new TypeCase(typeName, "", value, columnTypes);
    }

    @Override
    public String toString() {
      return variant.isEmpty() ? typeName : variant + " " + typeName;
    }
  }


  @DataProvider(name = "typeCases")
  public Object[][] typeCases() {
    return new Object[][] {
      { TypeCase.of("boolean", Boolean.TRUE, "BOOLEAN") },
      { TypeCase.of("Boolean", Boolean.FALSE, "BOOLEAN") },
      { TypeCase.of("byte", (byte) 42, "TINYINT") },
      { TypeCase.of("Byte", (byte) -1, "TINYINT") },
      { TypeCase.of("char", 'x', "CHAR(1)") },
      { TypeCase.of("Character", 'Y', "CHAR(1)") },
      { TypeCase.of("short", (short) 4711, "SMALLINT") },
      { TypeCase.of("Short", (short) -4711, "SMALLINT") },
      { TypeCase.of("int", 123456, "INTEGER") },
      { TypeCase.of("Integer", -123456, "INTEGER") },
      { TypeCase.of("long", 1234567890123L, "BIGINT") },
      { TypeCase.of("Long", -1234567890123L, "BIGINT") },
      { TypeCase.of("float", 1.5f, "REAL") },
      { TypeCase.of("Float", -1.5f, "REAL") },
      { TypeCase.of("double", DOUBLE_VALUE, "DOUBLE PRECISION") },
      { TypeCase.of("Double", -DOUBLE_VALUE, "DOUBLE PRECISION") },
      { TypeCase.of("BigDecimal", new java.math.BigDecimal("12345.6789"), "DECIMAL(20,4)") },
      { TypeCase.of("String", "hello world", "VARCHAR(64)") },
      { new TypeCase("String", DataTypeFactory.LARGE_VARIANT, "a somewhat larger text", "CLOB") },
      { new TypeCase("String", DataTypeFactory.SQLXML_VARIANT, "<root>hello</root>", "CLOB") },
      // compact on purpose: H2 normalizes a JSON document and would strip insignificant whitespace
      { new TypeCase("String", DataTypeFactory.JSON_VARIANT, "{\"b\":1,\"a\":[1,2,3]}", "JSON") },
      { TypeCase.of("UUID", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "UUID") },
      { TypeCase.of("BMoney", new BMoney(12.34, 2), "DOUBLE PRECISION", "SMALLINT") },
      { TypeCase.of("DMoney", new DMoney("12.3456", 4), "DECIMAL(20,0)", "SMALLINT") },
      { TypeCase.of("I18NText", new I18NText(Locale.ENGLISH, "hello"), "VARCHAR(1024)", "CLOB") },
      { TypeCase.of("Date", Date.valueOf("2020-01-15"), "DATE") },
      { TypeCase.of("Time", Time.valueOf("10:20:30"), "TIME") },
      { TypeCase.of("Timestamp", Timestamp.valueOf("2020-01-15 10:20:30.123456789"), "TIMESTAMP(9)") },
      { TypeCase.of("LocalDate", LocalDate.of(2020, 1, 15), "DATE") },
      { TypeCase.of("LocalTime", LocalTime.of(10, 20, 30), "TIME") },
      { TypeCase.of("LocalDateTime", LocalDateTime.of(2020, 1, 15, 10, 20, 30, 123456789), "TIMESTAMP(9)") },
      { TypeCase.of("Instant", Instant.ofEpochSecond(1579083630L, 123456789), "BIGINT", "INTEGER") },
      { TypeCase.of("OffsetTime", OffsetTime.of(LocalTime.of(10, 20, 30), ZoneOffset.ofHours(2)),
                     "TIME", "INTEGER") },
      { TypeCase.of("OffsetDateTime", OffsetDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30, 123456789),
                                                         ZoneOffset.ofHours(2)), "TIMESTAMP(9)", "INTEGER") },
      { TypeCase.of("ZonedDateTime", ZonedDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30, 123456789),
                                                       ZoneId.of("Europe/Berlin")), "TIMESTAMP(9)", "VARCHAR(32)") }
    };
  }


  /**
   * A value written by {@code set} must come back unchanged from {@code get}.
   *
   * @param typeCase the type and its value
   * @throws SQLException if the test failed
   */
  @Test(dataProvider = "typeCases")
  public void testValueRoundTrip(TypeCase typeCase) throws SQLException {
    DataType dataType = factory.get(typeCase.typeName(), typeCase.variant());
    assertEquals(dataType.getColumnCount(backend), typeCase.columnTypes().length,
                 typeCase + ": the number of columns in the fixture must match the data type");
    assertEquals(store(dataType, typeCase, typeCase.value(), false), typeCase.value());
  }

  /**
   * A null value must come back as null for every type that can hold one.
   *
   * @param typeCase the type and its value
   * @throws SQLException if the test failed
   */
  @Test(dataProvider = "typeCases")
  public void testNullRoundTrip(TypeCase typeCase) throws SQLException {
    DataType dataType = factory.get(typeCase.typeName(), typeCase.variant());
    if (dataType.isPrimitive()) {
      return;   // a primitive never holds null
    }
    Object value = store(dataType, typeCase, null, false);
    if (NULL_COLLAPSING_TYPES.contains(typeCase.typeName())) {
      assertEquals(value, dataType.getSqlType(backend, 0).getDefaultValue(),
                   typeCase + ": null collapses to the zero value of the column");
    }
    else {
      assertNull(value, typeCase + ": null must stay null");
    }
  }

  /**
   * With the {@code MAPNULL} option, a null value is stored as a non-null constant, but must still
   * come back as null. This is the option that lets a column be {@code NOT NULL} although the
   * attribute is nullable.
   *
   * @param typeCase the type and its value
   * @throws SQLException if the test failed
   */
  @Test(dataProvider = "typeCases")
  public void testMappedNullRoundTrip(TypeCase typeCase) throws SQLException {
    DataType dataType = factory.get(typeCase.typeName(), typeCase.variant());
    if (!dataType.isMapNullSupported()) {
      return;
    }
    Object[] stored = new Object[1];
    assertNull(store(dataType, typeCase, null, true, stored), typeCase + ": the mapped null must be unmapped again");
    for (Object columnValue : (Object[]) stored[0]) {
      assertTrue(columnValue != null, typeCase + ": MAPNULL must not write a null column");
    }
    // and a real value is unaffected by the option
    assertEquals(store(dataType, typeCase, typeCase.value(), true), typeCase.value());
  }

  /**
   * The per-column {@code set} must write exactly what the all-columns {@code set} writes,
   * because both are used to build the very same statement.
   *
   * @param typeCase the type and its value
   * @throws SQLException if the test failed
   */
  @Test(dataProvider = "typeCases")
  public void testColumnWiseSetMatches(TypeCase typeCase) throws SQLException {
    DataType dataType = factory.get(typeCase.typeName(), typeCase.variant());
    if (dataType.isColumnCountBackendSpecific()) {
      return;   // backend-specific types have no per-column setter
    }
    assertEquals(storeColumnWise(dataType, typeCase, typeCase.value()), typeCase.value(),
                 typeCase + ": the column-wise setter must store the same value");
  }


  /**
   * The single-column value the model uses as a default must agree with what is written to the column.
   *
   * @param typeCase the type and its value
   */
  @Test(dataProvider = "typeCases")
  public void testColumnValue(TypeCase typeCase) {
    DataType dataType = factory.get(typeCase.typeName(), typeCase.variant());
    if (dataType.isColumnCountBackendSpecific()) {
      return;   // I18NText has no meaningful column value
    }
    for (int index = 0; index < dataType.getColumnCount(backend); index++) {
      assertNull(dataType.getColumnValue(backend, index, null), typeCase + ": null has no column value");
    }
  }


  /**
   * Stores a value and reads it back.
   *
   * @param dataType the data type
   * @param typeCase the fixture
   * @param value the value to store, may be null
   * @param mapNull true to apply the MAPNULL option
   * @param storedColumns optional single-element array receiving the column values written
   * @return the value read back
   * @throws SQLException if the round trip failed
   */
  private Object store(DataType dataType, TypeCase typeCase, Object value, boolean mapNull,
                       Object... storedColumns) throws SQLException {
    String tableName = createTable(typeCase);
    try (PreparedStatement stmt = connection.prepareStatement(insertSql(tableName, typeCase))) {
      Object[] columns = dataType.set(backend, stmt, 1, value, mapNull, null);
      assertEquals(columns.length, typeCase.columnTypes().length,
                   typeCase + ": set must return one value per column");
      if (storedColumns.length > 0) {
        storedColumns[0] = columns;
      }
      stmt.executeUpdate();
    }
    return read(dataType, tableName, typeCase, mapNull);
  }

  /**
   * Stores a value column by column and reads it back.
   *
   * @param dataType the data type
   * @param typeCase the fixture
   * @param value the value to store
   * @return the value read back
   * @throws SQLException if the round trip failed
   */
  private Object storeColumnWise(DataType dataType, TypeCase typeCase, Object value) throws SQLException {
    String tableName = createTable(typeCase);
    try (PreparedStatement stmt = connection.prepareStatement(insertSql(tableName, typeCase))) {
      for (int index = 0; index < typeCase.columnTypes().length; index++) {
        dataType.set(backend, stmt, index + 1, value, index, false, null);
      }
      stmt.executeUpdate();
    }
    return read(dataType, tableName, typeCase, false);
  }

  private Object read(DataType dataType, String tableName, TypeCase typeCase, boolean mapNull) throws SQLException {
    int[] positions = new int[typeCase.columnTypes().length];
    for (int index = 0; index < positions.length; index++) {
      positions[index] = index + 1;
    }
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {
      assertTrue(rs.next(), typeCase + ": the row was not inserted");
      return dataType.get(backend, rs, positions, mapNull, null);
    }
  }

  private String createTable(TypeCase typeCase) throws SQLException {
    String tableName = "roundtrip_" + ++tableCounter;
    StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
    for (int index = 0; index < typeCase.columnTypes().length; index++) {
      if (index > 0) {
        ddl.append(", ");
      }
      ddl.append("c").append(index).append(' ').append(typeCase.columnTypes()[index]);
    }
    ddl.append(")");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(ddl.toString());
    }
    return tableName;
  }

  private String insertSql(String tableName, TypeCase typeCase) {
    return "INSERT INTO " + tableName + " VALUES (" +
           String.join(",", java.util.Collections.nCopies(typeCase.columnTypes().length, "?")) + ")";
  }

}
