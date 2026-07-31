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

import org.tentackle.common.BMoney;
import org.tentackle.common.Binary;
import org.tentackle.common.DMoney;
import org.tentackle.common.I18NText;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.TBinary;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that {@link Query#applyParameters} covers all predefined datatypes.
 * <p>
 * A test table is created with a column set for each datatype.
 * The values are applied to an {@code INSERT} statement covering all columns, which verifies
 * that each value is applied to the statement, that the number of consumed question marks matches
 * the number of columns of the datatype, and that the values are accepted by the backend.
 * The row is then selected again, once per datatype, which verifies that the parameters of a
 * where-clause are applied the same way as the inserted values.
 *
 * @author harald
 */
public class QueryParameterTest extends AbstractDbTest {

  private static final String TABLE_NAME = "querytest";

  private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.of(2026, 7, 31, 12, 34, 56);
  private static final ZoneId ZONE_ID = ZoneId.of("Europe/Berlin");
  private static final ZoneOffset ZONE_OFFSET = ZONE_ID.getRules().getOffset(LOCAL_DATE_TIME);


  /**
   * Creates one value per predefined datatype.
   *
   * @return the values
   */
  private static Object[] createValues() {
    // I18NText requires a supported locale (the tests run with en_US, but only en is supported)
    LocaleProvider.getInstance().setCurrentLocale(Locale.ENGLISH);
    return new Object[] {
        Boolean.TRUE,
        (byte) 42,
        (short) 4711,
        123456,
        1234567890123L,
        3.14f,
        2.718281828d,
        new BigDecimal("1234.5678"),
        new BMoney(17.11, 2),
        new DMoney(17.11, 2),
        'X',
        "some string",
        I18NText.valueOf("some text"),
        UUID.randomUUID(),
        Date.valueOf(LOCAL_DATE_TIME.toLocalDate()),
        Time.valueOf(LOCAL_DATE_TIME.toLocalTime()),
        Timestamp.valueOf(LOCAL_DATE_TIME),
        LOCAL_DATE_TIME.toLocalDate(),
        LOCAL_DATE_TIME.toLocalTime(),
        LOCAL_DATE_TIME,
        OffsetDateTime.of(LOCAL_DATE_TIME, ZONE_OFFSET),
        OffsetTime.of(LOCAL_DATE_TIME.toLocalTime(), ZONE_OFFSET),
        ZonedDateTime.of(LOCAL_DATE_TIME, ZONE_ID),
        Instant.ofEpochSecond(1785000000L, 123456789),
        createBinary("some binary"),
        createTBinary("some other binary")
    };
  }

  private static Binary<String> createBinary(String text) {
    try {
      return new Binary<>(text);
    }
    catch (IOException iox) {
      throw new IllegalStateException(iox);
    }
  }

  private static TBinary<String> createTBinary(String text) {
    try {
      return new TBinary<>(text);
    }
    catch (IOException iox) {
      throw new IllegalStateException(iox);
    }
  }


  /**
   * The columns, the where-condition, and the value for a single datatype.
   *
   * @param columns the comma separated column names
   * @param placeholders the question marks, one per column
   * @param columnCount the number of columns of the datatype
   * @param condition the where-condition, null if the datatype cannot be compared by the backend
   * @param value the value
   */
  private record TestCase(String columns, String placeholders, int columnCount,
                          String condition, Object value) { }


  private final List<TestCase> testCases = new ArrayList<>();


  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createTestTable() {
    Db db = getSession();
    Backend backend = db.getBackend();
    Object[] values = createValues();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(TABLE_NAME, null));

    for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
      Object value = values[valueIndex];
      DataType<?> dataType = DataTypeFactory.getInstance().get(value.getClass());
      assertNotNull(dataType, "no datatype for " + value.getClass());
      int columnCount = dataType.getColumnCount(backend);
      StringJoiner columns = new StringJoiner(", ");
      StringJoiner placeholders = new StringJoiner(", ");
      StringJoiner condition = new StringJoiner(" AND ");
      boolean comparable = true;

      for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
        String columnName = "c" + valueIndex + "_" + columnIndex;
        SqlType sqlType = dataType.getSqlType(backend, columnIndex);
        ddl.append("    ").append(backend.sqlCreateColumn(
            columnName, null, sqlType,
            dataType.getSize(backend, columnIndex, null), dataType.getScale(backend, columnIndex, null),
            true, null, false,
            valueIndex < values.length - 1 || columnIndex < columnCount - 1));
        columns.add(columnName);
        placeholders.add("?");
        condition.add(columnName + " = ?");
        // the CLOB column of an I18NText is null as long as the text fits into the VARCHAR column
        comparable &= sqlType != SqlType.CLOB;
      }

      testCases.add(new TestCase(columns.toString(), placeholders.toString(), columnCount,
                                 comparable ? condition.toString() : null, value));
    }

    ddl.append(backend.sqlCreateTableClosing(TABLE_NAME, null));

    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(ddl.toString());
    }
  }


  /**
   * Inserts a value of each predefined datatype and verifies that all question marks are consumed.
   */
  @Test
  public void testApplyAllPredefinedDataTypes() {
    Db db = getSession();
    Query insert = createInsertQuery();
    int columnCount = testCases.stream().mapToInt(TestCase::columnCount).sum();

    try (PreparedStatementWrapper st = db.createPreparedStatement(insert::buildInnerSql)) {
      assertEquals(insert.applyParameters(st, 1), columnCount + 1, "wrong number of applied parameters");
      assertEquals(st.getParameters().size(), columnCount, "not all parameters applied");
      assertEquals(st.executeUpdate(), 1);
    }
  }


  /**
   * Selects the inserted row by each comparable datatype.
   */
  @Test(dependsOnMethods = "testApplyAllPredefinedDataTypes")
  public void testSelectByAllComparableDataTypes() {
    Db db = getSession();

    for (TestCase testCase : testCases) {
      if (testCase.condition() != null) {
        Query query = new Query();
        query.add("SELECT * FROM " + TABLE_NAME + " WHERE " + testCase.condition(), testCase.value());
        assertEquals(query.getRowCount(db), 1, "row not found by " + testCase.value().getClass().getName());
      }
    }
  }


  /**
   * Verifies that values without a datatype are rejected.
   */
  @Test
  public void testUnsupportedDataType() {
    Query query = new Query();
    query.add("SELECT * FROM " + TABLE_NAME + " WHERE c3_0 = ?", BigInteger.ONE);   // no datatype for BigInteger
    PersistenceException pex = expectThrows(PersistenceException.class, () -> query.getRowCount(getSession()));
    assertTrue(pex.getMessage().contains("unsupported type"), "unexpected message: " + pex.getMessage());
  }


  /**
   * Creates the INSERT query for all test cases.
   *
   * @return the query
   */
  private Query createInsertQuery() {
    StringJoiner columns = new StringJoiner(", ");
    StringJoiner placeholders = new StringJoiner(", ");
    Query query = new Query();

    for (TestCase testCase : testCases) {
      columns.add(testCase.columns());
      placeholders.add(testCase.placeholders());
    }
    query.add("INSERT INTO " + TABLE_NAME + " (" + columns + ") VALUES (" + placeholders + ")");
    for (TestCase testCase : testCases) {
      query.add("", testCase.value());
    }
    return query;
  }

}
