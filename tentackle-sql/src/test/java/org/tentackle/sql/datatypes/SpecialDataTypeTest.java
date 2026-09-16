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
import org.testng.annotations.Test;

import org.tentackle.common.BMoney;
import org.tentackle.common.Binary;
import org.tentackle.common.DMoney;
import org.tentackle.common.I18NText;
import org.tentackle.common.TBinary;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.backends.H2;
import org.tentackle.sql.backends.Oracle;
import org.tentackle.sql.backends.Postgres;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the data types that are more than a plain column.
 * <p>
 * These are the ones the wurblets and the DDL generator have to ask about the shape of the mapping:
 * how many columns, what they are called and how a value is spread over them. {@code I18NText} even
 * answers differently per backend, because a database with an unbounded string type needs no CLOB
 * overflow column at all.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SpecialDataTypeTest {

  private final Backend h2 = new H2();

  private Connection connection;


  @BeforeClass
  public void createDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:specialtypes");
  }

  @AfterClass(alwaysRun = true)
  public void dropDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  /**
   * Creates a table and returns an insert statement for it.
   *
   * @param tableName the table name
   * @param columns the column DDL
   * @return the insert statement, to be closed by the caller
   * @throws SQLException if the table could not be created
   */
  private PreparedStatement table(String tableName, String... columns) throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE " + tableName + " (" + String.join(", ", columns) + ")");
    }
    return connection.prepareStatement("INSERT INTO " + tableName + " VALUES (" +
                                       String.join(",", java.util.Collections.nCopies(columns.length, "?")) + ")");
  }

  private ResultSet select(String tableName) throws SQLException {
    ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM " + tableName);
    assertTrue(rs.next(), "no row in " + tableName);
    return rs;
  }


  // ---- the money types -----------------------------------------------------

  /**
   * The scale travels in its own column, because a {@code DECIMAL} column has one fixed scale while
   * a money value carries its own.
   */
  @Test
  public void testMoneyColumns() {
    BMoneyType bmoney = new BMoneyType();
    assertEquals(bmoney.getColumnCount(h2), 2);
    assertTrue(bmoney.isNumeric());
    assertEquals(bmoney.getSqlType(h2, 0), SqlType.DOUBLE);
    assertEquals(bmoney.getSqlType(h2, 1), SqlType.SMALLINT);
    assertEquals(bmoney.getCommentSuffix(h2, 0), Optional.empty());
    assertEquals(bmoney.getCommentSuffix(h2, 1), Optional.of(" (scale)"));
    assertEquals(bmoney.getColumnGetter(0, "amount"), "amount.doubleValue()");
    assertEquals(bmoney.getColumnGetter(1, "amount"), "(short) amount.scale()");
    assertEquals(bmoney.getColumnAlias(0), "doubleValue");
    assertEquals(bmoney.getColumnAlias(1), "scale");
    // the scale column has neither a size nor a scale of its own
    assertEquals(bmoney.getSize(h2, 0, 20), 20);
    assertEquals(bmoney.getSize(h2, 1, 20), 0);
    assertEquals(bmoney.getScale(h2, 0, 2), 0, "the value column is a DOUBLE and needs no scale");
    // BMoney is sortable by its value, DMoney is not, because the value column holds the unscaled value
    assertEquals(bmoney.getSortableColumns(), new int[] { 0 });
    assertNull(new DMoneyType().getSortableColumns());

    DMoneyType dmoney = new DMoneyType();
    assertEquals(dmoney.getSqlType(h2, 0), SqlType.DECIMAL);
    assertEquals(dmoney.getColumnGetter(0, "amount"), "amount.bigDecimalValue()");
    assertEquals(dmoney.getColumnAlias(0), "bigDecimalValue");
  }

  @Test
  public void testMoneyColumnValues() {
    BMoney value = new BMoney(12.34, 2);
    BMoneyType bmoney = new BMoneyType();
    assertEquals(bmoney.getColumnValue(h2, 0, value), 12.34);
    assertEquals(bmoney.getColumnValue(h2, 1, value), 2);
    assertNull(bmoney.getColumnValue(h2, 0, null));

    DMoney decimal = new DMoney("12.34", 2);
    DMoneyType dmoney = new DMoneyType();
    assertEquals(dmoney.getColumnValue(h2, 0, decimal), new BigDecimal("1234"),
                 "the decimal column holds the unscaled value");
    assertEquals(dmoney.getColumnValue(h2, 1, decimal), 2);
    assertNull(dmoney.getColumnValue(h2, 1, null));
  }

  @Test
  public void testMoneyRejectsBadColumnIndex() {
    BMoneyType bmoney = new BMoneyType();
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getSqlType(h2, 2));
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getCommentSuffix(h2, 2));
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getColumnGetter(2, "amount"));
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getColumnAlias(2));
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getSize(h2, 2, 0));
    expectThrows(IndexOutOfBoundsException.class, () -> bmoney.getColumnValue(h2, 2, new BMoney(1.0, 2)));
    expectThrows(IndexOutOfBoundsException.class, () -> new DMoneyType().getColumnAlias(2));
  }

  @Test
  public void testMoneyValueOf() {
    assertEquals(new BMoneyType().valueOf("12.34"), new BMoney(new BigDecimal("12.34")));
    assertEquals(new DMoneyType().valueOf("12.34"), new DMoney(new BigDecimal("12.34")));
    assertEquals(new BMoneyType().valueOf("'12.34'"), new BMoney(new BigDecimal("12.34")), "quotes are stripped");
  }


  // ---- I18NText ------------------------------------------------------------

  /**
   * A backend with an unbounded string type stores the text in a single column; everything else
   * needs a CLOB to fall back to.
   */
  @Test
  public void testI18NTextColumnCountDependsOnTheBackend() {
    I18NTextType type = new I18NTextType();
    assertTrue(type.isColumnCountBackendSpecific());
    assertFalse(type.isPredefined());

    assertEquals(type.getColumnCount(h2), 2, "H2 has a bounded VARCHAR and needs the CLOB column");
    assertEquals(type.getSqlType(h2, 0), SqlType.VARCHAR);
    assertEquals(type.getSqlType(h2, 1), SqlType.CLOB);
    assertEquals(type.getCommentSuffix(h2, 1), Optional.of(" (CLOB)"));
    assertEquals(type.getSize(h2, 0, 100), 100, "the model may limit the VARCHAR column further");
    assertEquals(type.getSize(h2, 0, null), h2.getMaxSize(SqlType.VARCHAR));
    assertEquals(type.getSize(h2, 1, 100), 0, "a CLOB has no size");

    Backend postgres = new Postgres();
    assertEquals(type.getColumnCount(postgres), 1, "postgres has TEXT and needs no overflow column");
    assertEquals(type.getSqlType(postgres, 0), SqlType.VARCHAR);
    assertEquals(type.getCommentSuffix(postgres, 0), Optional.empty());
    assertEquals(type.getSize(postgres, 0, 100), 0);
    assertEquals(type.getScale(h2, 0, 4), 0);
  }

  /**
   * A backend that needs two columns but has no CLOB cannot store the type at all, and that has to
   * be said while the DDL is generated.
   */
  @Test
  public void testI18NTextNeedsClob() {
    Backend noClob = new Oracle() {
      @Override
      public boolean isClobSupported() {
        return false;
      }
    };
    assertEquals(new I18NTextType().getColumnCount(noClob), 2);
    assertTrue(expectThrows(BackendException.class, () -> new I18NTextType().getSqlType(noClob, 0))
                   .getMessage().contains("CLOB"));
  }

  @Test
  public void testI18NTextHasNoColumnAccessors() {
    I18NTextType type = new I18NTextType();
    expectThrows(BackendException.class, () -> type.getColumnGetter(0, "text"));
    expectThrows(BackendException.class, () -> type.getColumnAlias(0));
    expectThrows(BackendException.class, () -> type.set(h2, null, 1, null, 0, false, null));
    assertNull(type.getColumnValue(h2, 0, new I18NText(Locale.ENGLISH, "x")),
               "a default value in the model makes no sense for a translated text");
    assertFalse(type.isLiteralSupported(null));
    assertEquals(type.valueOfLiteralToCode("hello", null), "DT_I18NTEXT.valueOf(\"hello\")");
  }

  /**
   * A text too large for the VARCHAR column goes into the CLOB column instead, and must come back
   * from there.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testI18NTextOverflowsIntoTheClob() throws SQLException {
    I18NTextType type = new I18NTextType();
    I18NText text = new I18NText(Locale.ENGLISH, "x".repeat(100));
    int maxSize = 16;   // the model's size, which limits the VARCHAR column

    try (PreparedStatement stmt = table("i18noverflow", "c0 VARCHAR(16)", "c1 CLOB")) {
      Object[] columns = type.set(h2, stmt, 1, text, false, maxSize);
      assertNull(columns[0], "the text does not fit into the VARCHAR column");
      assertNotNull(columns[1], "so it goes into the CLOB column");
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("i18noverflow")) {
      assertEquals(type.get(h2, rs, new int[] { 1, 2 }, false, maxSize), text);
    }
  }

  /**
   * A text that fits leaves the CLOB column empty, so that the common case needs no LOB at all.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testI18NTextFittingIntoTheVarchar() throws SQLException {
    I18NTextType type = new I18NTextType();
    I18NText text = new I18NText(Locale.ENGLISH, "short");

    try (PreparedStatement stmt = table("i18nshort", "c0 VARCHAR(64)", "c1 CLOB")) {
      Object[] columns = type.set(h2, stmt, 1, text, false, 64);
      assertNotNull(columns[0]);
      assertNull(columns[1], "the CLOB column stays empty");
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("i18nshort")) {
      assertEquals(type.get(h2, rs, new int[] { 1, 2 }, false, 64), text);
    }
  }

  /**
   * Malformed contents must be reported rather than silently swallowed.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testI18NTextRejectsMalformedContents() throws SQLException {
    try (PreparedStatement stmt = table("i18nbroken", "c0 VARCHAR(64)", "c1 CLOB")) {
      // an unterminated quoted value
      stmt.setString(1, "en='hello");
      stmt.setNull(2, java.sql.Types.CLOB);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("i18nbroken")) {
      assertTrue(expectThrows(BackendException.class,
                              () -> new I18NTextType().get(h2, rs, new int[] { 1, 2 }, false, null))
                     .getMessage().contains("I18NText"));
    }
  }


  // ---- the binary types ----------------------------------------------------

  @Test
  public void testBinaryFlags() {
    for (org.tentackle.sql.DataType<?> type : new org.tentackle.sql.DataType<?>[] {
        new BinaryType(), new TBinaryType() }) {
      assertEquals(type.getSqlType(h2, 0), SqlType.BLOB);
      assertTrue(type.isMutable(), type + " is a mutable object and has to be freezable");
      assertTrue(type.isJavaTypeGenerified(), type + " carries the type of the object it wraps");
      assertTrue(type.isModelProvidingInnerType());
      expectThrows(IndexOutOfBoundsException.class, () -> type.getSqlType(h2, 1));
      assertTrue(expectThrows(BackendException.class, () -> type.valueOf("anything"))
                     .getMessage().contains("String"), type + " cannot be parsed from a string");
    }
  }

  /**
   * An empty binary is indistinguishable from none, so it is stored as NULL and comes back as null.
   *
   * @throws SQLException if the test failed
   * @throws IOException if the test failed
   */
  @Test
  public void testBinaryRoundTrip() throws SQLException, IOException {
    BinaryType type = new BinaryType();
    byte[] bytes = "the payload".getBytes(StandardCharsets.UTF_8);
    Binary<?> binary = Binary.create(new ByteArrayInputStream(bytes), 0, true);

    try (PreparedStatement stmt = table("binaryroundtrip", "c0 BLOB")) {
      type.set(h2, stmt, 1, binary, false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("binaryroundtrip")) {
      Binary<?> read = type.get(h2, rs, new int[] { 1 }, false, null);
      assertNotNull(read);
      assertEquals(read.getLength(), bytes.length);
    }
  }

  @Test
  public void testEmptyBinaryIsNull() throws SQLException, IOException {
    TBinaryType type = new TBinaryType();
    TBinary<?> empty = new TBinary<>(new ByteArrayInputStream(new byte[0]), 0, true);

    try (PreparedStatement stmt = table("emptybinary", "c0 BLOB")) {
      assertNull(type.set(h2, stmt, 1, empty, false, null)[0], "an empty binary is stored as null");
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("emptybinary")) {
      assertNull(type.get(h2, rs, new int[] { 1 }, false, null));
    }
  }


  // ---- the large string ----------------------------------------------------

  /**
   * On a backend without a CLOB the large string falls back to the plain string handling, so it
   * keeps working on postgres, where the type is TEXT.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testLargeStringFallsBackWithoutClob() throws SQLException {
    LargeStringType type = new LargeStringType();
    Backend postgres = new Postgres();
    assertFalse(postgres.isClobSupported());

    try (PreparedStatement stmt = table("largestringfallback", "c0 VARCHAR(64)")) {
      type.set(postgres, stmt, 1, "a large text", false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("largestringfallback")) {
      assertEquals(type.get(postgres, rs, new int[] { 1 }, false, null), "a large text");
    }
  }

  /**
   * With the MAPNULL option a null string is stored as the backend's empty string and unmapped
   * again on the way back.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testLargeStringMapNull() throws SQLException {
    LargeStringType type = new LargeStringType();

    try (PreparedStatement stmt = table("largestringmapnull", "c0 CLOB")) {
      assertEquals(type.set(h2, stmt, 1, null, true, null)[0], h2.getEmptyString());
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("largestringmapnull")) {
      assertNull(type.get(h2, rs, new int[] { 1 }, true, null));
    }
  }


  // ---- the JSON string -----------------------------------------------------

  /**
   * The document must survive a real, native JSON column unchanged.
   * <p>
   * This is the test that catches a plain {@code setString}: H2 would take it as a JSON
   * <em>string value</em> and store {@code "{\"a\":1}"} instead of the document itself.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testJsonUsesTheNativeColumn() throws SQLException {
    JsonType type = new JsonType();
    assertTrue(h2.isJsonSupported());

    try (PreparedStatement stmt = table("jsonnative", "c0 JSON")) {
      type.set(h2, stmt, 1, "{\"a\":1}", false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("jsonnative")) {
      assertEquals(type.get(h2, rs, new int[] { 1 }, false, null), "{\"a\":1}");
    }
  }

  /**
   * On a backend without a native JSON column the document falls back to the plain string
   * handling and is stored verbatim.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testJsonFallsBackWithoutNativeSupport() throws SQLException {
    JsonType type = new JsonType();
    Backend oracle = new Oracle();
    assertFalse(oracle.isJsonSupported());

    try (PreparedStatement stmt = table("jsonfallback", "c0 VARCHAR(64)")) {
      type.set(oracle, stmt, 1, "{\"a\":1}", false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("jsonfallback")) {
      assertEquals(type.get(oracle, rs, new int[] { 1 }, false, null), "{\"a\":1}");
    }
  }

  /**
   * A null document stays null, on the native column as well as on the emulating one.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testJsonNull() throws SQLException {
    JsonType type = new JsonType();

    try (PreparedStatement stmt = table("jsonnull", "c0 JSON")) {
      type.set(h2, stmt, 1, null, false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("jsonnull")) {
      assertNull(type.get(h2, rs, new int[] { 1 }, false, null));
    }
  }

  /**
   * MAPNULL is not applicable to JSON: the empty string is not a document, and a native column
   * rejects it outright.
   */
  @Test
  public void testJsonRejectsMapNull() {
    JsonType type = new JsonType();
    assertFalse(type.isMapNullSupported());
    BackendException ex = expectThrows(BackendException.class, () -> type.getMappedNullValue(h2, 0));
    assertTrue(ex.getMessage().contains("MAPNULL"), ex.getMessage());
  }


  // ---- the UUID ------------------------------------------------------------

  /**
   * A backend without a native UUID stores it as a {@code VARCHAR(36)}.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testUuidWithoutNativeSupport() throws SQLException {
    UUIDType type = new UUIDType();
    Backend noUuid = new Oracle();
    assertFalse(noUuid.isUUIDSupported());
    assertEquals(type.getSqlType(noUuid, 0), SqlType.VARCHAR);
    assertEquals(type.getSize(noUuid, 0, null), 36);
    assertEquals(type.getSize(h2, 0, null), 0, "a native UUID has no size");
    assertNull(type.getSortableColumns(), "a UUID has no meaningful order in the database");
    assertFalse(type.isPredefined(), "the persistence layer has to go through setObject/getObject");

    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    try (PreparedStatement stmt = table("uuidasvarchar", "c0 VARCHAR(36)")) {
      type.set(noUuid, stmt, 1, uuid, false, null);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("uuidasvarchar")) {
      assertEquals(type.get(noUuid, rs, new int[] { 1 }, false, null), uuid);
    }
  }

  /**
   * The MAPNULL option maps a null UUID to the nil UUID, which is a value the database can store
   * in a NOT NULL column.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testUuidMapNull() throws SQLException {
    UUIDType type = new UUIDType();
    assertTrue(type.isMapNullSupported());
    assertEquals(type.getMappedNullValue(h2, 0), UUIDType.NIL);
    assertEquals(UUIDType.NIL_STR, "00000000-0000-0000-0000-000000000000");

    try (PreparedStatement stmt = table("uuidmapnull", "c0 VARCHAR(36) NOT NULL")) {
      assertEquals(type.set(new Oracle(), stmt, 1, null, true, null)[0], UUIDType.NIL);
      stmt.executeUpdate();
    }
    try (ResultSet rs = select("uuidmapnull")) {
      assertNull(type.get(new Oracle(), rs, new int[] { 1 }, true, null));
    }
  }

  @Test
  public void testUuidLiteral() {
    UUIDType type = new UUIDType();
    assertEquals(type.valueOfLiteralToCode("123e4567-e89b-12d3-a456-426614174000", null),
                 "UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\")");
    assertEquals(type.valueOfLiteralToCode("\"already-quoted\"", null), "UUID.fromString(\"already-quoted\")");
  }

}
