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

import org.tentackle.common.DateHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.backends.H2;
import org.testng.annotations.Test;

import java.sql.Date;
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
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests how the date and time types are described to the model and printed into it.
 * <p>
 * {@link DataType#valueOf} and {@link DataType#toString} are the pair the model is dumped and read
 * back with, and {@code valueOfLiteralToCode} turns a model default into the java expression the
 * wurblets generate — an expression that has to compile. The types with an offset or a zone spread
 * their value over two columns, and each column needs a literal of its own.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TemporalDataTypeTest {

  private final Backend backend = new H2();


  // ---- the model's textual representation ----------------------------------

  /**
   * The value dumped into the model must be readable back as the same value.
   */
  @Test
  public void testValueOfIsTheInverseOfToString() {
    assertRoundTrip(new DateType(), Date.valueOf("2020-01-15"));
    assertRoundTrip(new TimeType(), Time.valueOf("10:20:30"));
    assertRoundTrip(new TimestampType(), Timestamp.valueOf("2020-01-15 10:20:30.123"));
    assertRoundTrip(new LocalDateType(), LocalDate.of(2020, 1, 15));
    assertRoundTrip(new LocalTimeType(), LocalTime.of(10, 20, 30));
    assertRoundTrip(new LocalDateTimeType(), LocalDateTime.of(2020, 1, 15, 10, 20, 30, 123000000));
    assertRoundTrip(new OffsetTimeType(), OffsetTime.of(LocalTime.of(10, 20, 30), ZoneOffset.ofHours(2)));
    assertRoundTrip(new OffsetDateTimeType(),
                    OffsetDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30), ZoneOffset.ofHours(2)));
    assertRoundTrip(new ZonedDateTimeType(),
                    ZonedDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30), ZoneId.of("Europe/Berlin")));
    assertRoundTrip(new InstantType(), Instant.ofEpochSecond(1579083630L));
  }

  /**
   * Dumps a value and reads it back.
   *
   * @param <T> the java type
   * @param dataType the data type
   * @param value the value
   */
  private <T> void assertRoundTrip(DataType<T> dataType, T value) {
    String dumped = dataType.toString(value);
    assertEquals(dataType.valueOf(dumped), value, dataType + ": dumped as " + dumped);
  }

  /**
   * A string that is not a timestamp at all must be rejected rather than silently become the epoch.
   */
  @Test
  public void testParseRejectsGarbage() {
    assertTrue(expectThrows(BackendException.class, () -> new TimestampType().valueOf("not a timestamp"))
                   .getMessage().contains("not a timestamp"));
    expectThrows(BackendException.class, () -> new DateType().valueOf("2020-13-45"));
    expectThrows(BackendException.class, () -> new TimeType().valueOf("25:00:00"));
    expectThrows(BackendException.class, () -> new LocalDateType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new LocalTimeType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new LocalDateTimeType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new OffsetTimeType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new OffsetDateTimeType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new ZonedDateTimeType().valueOf("nonsense"));
    expectThrows(BackendException.class, () -> new InstantType().valueOf("nonsense"));
  }

  /**
   * The precision of the dump follows the value: no fraction is printed when there is none,
   * and nanoseconds are only printed when they are not whole milliseconds.
   */
  @Test
  public void testPrecisionFollowsTheValue() {
    InstantType type = new InstantType();
    assertFalse(type.format(Instant.ofEpochSecond(1579083630L)).contains("."), "whole seconds need no fraction");
    assertTrue(type.format(Instant.ofEpochSecond(1579083630L, 123000000)).endsWith(".123"));
    assertTrue(type.format(Instant.ofEpochSecond(1579083630L, 123456789)).endsWith(".123456789"));

    TimestampType timestampType = new TimestampType();
    assertFalse(timestampType.format(Timestamp.valueOf("2020-01-15 10:20:30")).contains("."));
  }


  // ---- the java code the wurblets generate ---------------------------------

  /**
   * A model default becomes a java expression, so a value that starts with a digit has to be quoted
   * before it is handed to the parse method.
   */
  @Test
  public void testValueOfLiteralToCode() {
    assertEquals(new DateType().valueOfLiteralToCode("2020-01-15", null), "Date.valueOf(\"2020-01-15\")");
    assertEquals(new LocalDateType().valueOfLiteralToCode("2020-01-15", null), "LocalDate.valueOf(\"2020-01-15\")");
    assertEquals(new InstantType().valueOfLiteralToCode("2020-01-15 10:20:30", null),
                 "Instant.parse(\"2020-01-15 10:20:30\")");
    assertEquals(new OffsetTimeType().valueOfLiteralToCode("10:20:30 +02:00", null),
                 "OffsetTime.parse(\"10:20:30 +02:00\")");
    assertEquals(new OffsetDateTimeType().valueOfLiteralToCode("2020-01-15 10:20:30 +02:00", null),
                 "OffsetDateTime.parse(\"2020-01-15 10:20:30 +02:00\")");
    assertEquals(new ZonedDateTimeType().valueOfLiteralToCode("2020-01-15 10:20:30 Europe/Berlin", null),
                 "ZonedDateTime.parse(\"2020-01-15 10:20:30 Europe/Berlin\")");

    // an expression the developer wrote himself is left alone
    assertEquals(new DateType().valueOfLiteralToCode("null", null), "null");
    assertEquals(new InstantType().valueOfLiteralToCode("SOME_CONSTANT", null), "Instant.parse(SOME_CONSTANT)");
  }

  /**
   * Each column of a two-column type gets a literal of its own: the first one is a timestamp or a
   * time, the second one a plain number or string that needs no conversion.
   */
  @Test
  public void testColumnWiseValueOfLiteralToCode() {
    OffsetDateTimeType offsetDateTime = new OffsetDateTimeType();
    assertEquals(offsetDateTime.valueOfLiteralToCode("2020-01-15 10:20:30", 0), "Timestamp.valueOf(\"2020-01-15 10:20:30\")");
    assertEquals(offsetDateTime.valueOfLiteralToCode("7200", 1), "7200");
    assertEquals(offsetDateTime.valueOfLiteralToCode("null", 0), "null");
    expectThrows(IndexOutOfBoundsException.class, () -> offsetDateTime.valueOfLiteralToCode("x", 2));

    OffsetTimeType offsetTime = new OffsetTimeType();
    assertEquals(offsetTime.valueOfLiteralToCode("10:20:30", 0), "Time.valueOf(\"10:20:30\")");
    assertEquals(offsetTime.valueOfLiteralToCode("7200", 1), "7200");
    assertEquals(offsetTime.valueOfLiteralToCode("null", 0), "null");
    expectThrows(IndexOutOfBoundsException.class, () -> offsetTime.valueOfLiteralToCode("x", 2));

    ZonedDateTimeType zonedDateTime = new ZonedDateTimeType();
    assertEquals(zonedDateTime.valueOfLiteralToCode("2020-01-15 10:20:30", 0), "Timestamp.valueOf(\"2020-01-15 10:20:30\")");
    assertEquals(zonedDateTime.valueOfLiteralToCode("Europe/Berlin", 1), "Europe/Berlin");
    assertEquals(zonedDateTime.valueOfLiteralToCode("null", 0), "null");
    expectThrows(IndexOutOfBoundsException.class, () -> zonedDateTime.valueOfLiteralToCode("x", 2));

    InstantType instant = new InstantType();
    assertEquals(instant.valueOfLiteralToCode("1579083630", 0), "1579083630");
  }

  /**
   * Only the temporal column of a two-column type needs quoting in an SQL literal; the offset is a
   * number and the zone is written as a string only when it stands for the whole value.
   */
  @Test
  public void testToLiteral() {
    OffsetDateTimeType offsetDateTime = new OffsetDateTimeType();
    assertEquals(offsetDateTime.toLiteral("2020-01-15 10:20:30", 0), "'2020-01-15 10:20:30'");
    assertEquals(offsetDateTime.toLiteral("'2020-01-15 10:20:30'", 0), "'2020-01-15 10:20:30'", "already quoted");
    assertEquals(offsetDateTime.toLiteral("7200", 1), "7200", "the offset is a number");
    assertEquals(offsetDateTime.toLiteral("", 0), "");

    assertEquals(new OffsetTimeType().toLiteral("10:20:30", 0), "'10:20:30'");
    assertEquals(new OffsetTimeType().toLiteral("7200", 1), "7200");

    // the zone of a ZonedDateTime is a string in both columns
    assertEquals(new ZonedDateTimeType().toLiteral("Europe/Berlin", 1), "'Europe/Berlin'");
    assertEquals(new ZonedDateTimeType().toLiteral("2020-01-15 10:20:30", 0), "'2020-01-15 10:20:30'");
  }


  // ---- the shape of the two-column types -----------------------------------

  @Test
  public void testOffsetDateTimeColumns() {
    OffsetDateTimeType type = new OffsetDateTimeType();
    assertEquals(type.getColumnCount(backend), 2);
    assertTrue(type.isDateOrTime());
    assertEquals(type.getSqlType(backend, 0), SqlType.TIMESTAMP);
    assertEquals(type.getSqlType(backend, 1), SqlType.INTEGER);
    assertEquals(type.getCommentSuffix(backend, 0), Optional.empty());
    assertEquals(type.getCommentSuffix(backend, 1), Optional.of(" [+-s]"));
    assertEquals(type.getColumnGetter(0, "at"), "OffsetDateTimeType.timestampOf(at)");
    assertEquals(type.getColumnGetter(1, "at"), "OffsetDateTimeType.offsetOf(at)");
    assertEquals(type.getColumnAlias(0), "timestamp");
    assertEquals(type.getColumnAlias(1), "offset");
    assertEquals(type.getSize(backend, 1, 20), 0, "the offset column has no size");
    assertEquals(type.getSize(backend, 0, 20), 20);

    OffsetDateTime value = OffsetDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30), ZoneOffset.ofHours(2));
    assertEquals(type.getColumnValue(backend, 0, value), OffsetDateTimeType.timestampOf(value));
    assertEquals(type.getColumnValue(backend, 1, value), 7200);
    assertNull(type.getColumnValue(backend, 0, null));

    // MAPNULL stores the epoch in GMT
    assertTrue(type.isMapNullSupported());
    assertEquals(type.getMappedNullValue(backend, 0), DateHelper.MIN_TIMESTAMP);
    assertEquals(type.getMappedNullValue(backend, 1), 0);

    assertBadIndex(type);
  }

  @Test
  public void testOffsetTimeColumns() {
    OffsetTimeType type = new OffsetTimeType();
    assertEquals(type.getColumnCount(backend), 2);
    assertEquals(type.getSqlType(backend, 0), SqlType.TIME);
    assertEquals(type.getSqlType(backend, 1), SqlType.INTEGER);
    assertEquals(type.getCommentSuffix(backend, 1), Optional.of(" [+-s]"));
    assertEquals(type.getColumnGetter(0, "at"), "OffsetTimeType.timeOf(at)");
    assertEquals(type.getColumnGetter(1, "at"), "OffsetTimeType.offsetOf(at)");
    assertEquals(type.getColumnAlias(0), "time");
    assertEquals(type.getColumnAlias(1), "offset");
    assertEquals(type.getSize(backend, 1, 20), 0);

    OffsetTime value = OffsetTime.of(LocalTime.of(10, 20, 30), ZoneOffset.ofHours(2));
    assertEquals(type.getColumnValue(backend, 0, value), OffsetTimeType.timeOf(value));
    assertEquals(type.getColumnValue(backend, 1, value), 7200);
    assertNull(type.getColumnValue(backend, 1, null));
    assertFalse(type.isMapNullSupported(), "a time has no epoch to fall back to");

    assertBadIndex(type);
  }

  @Test
  public void testZonedDateTimeColumns() {
    ZonedDateTimeType type = new ZonedDateTimeType();
    assertEquals(type.getColumnCount(backend), 2);
    assertEquals(type.getSqlType(backend, 0), SqlType.TIMESTAMP);
    assertEquals(type.getSqlType(backend, 1), SqlType.VARCHAR);
    assertEquals(type.getCommentSuffix(backend, 1), Optional.of(" (TZ)"));
    assertEquals(type.getColumnGetter(0, "at"), "ZonedDateTimeType.timestampOf(at)");
    assertEquals(type.getColumnGetter(1, "at"), "ZonedDateTimeType.zoneOf(at)");
    assertEquals(type.getColumnAlias(0), "timestamp");
    assertEquals(type.getColumnAlias(1), "zone");
    assertTrue(type.getSize(backend, 1, null) > 0, "the zone column holds a zone id and needs a size");

    ZonedDateTime value = ZonedDateTime.of(LocalDateTime.of(2020, 1, 15, 10, 20, 30), ZoneId.of("Europe/Berlin"));
    assertEquals(type.getColumnValue(backend, 0, value), ZonedDateTimeType.timestampOf(value));
    assertEquals(type.getColumnValue(backend, 1, value), "Europe/Berlin");
    assertNull(type.getColumnValue(backend, 1, null));
    assertEquals(type.getMappedNullValue(backend, 0), DateHelper.MIN_TIMESTAMP);
    assertEquals(type.getMappedNullValue(backend, 1), ZonedDateTimeType.GMT);

    // an absent or unset zone counts as GMT, so that the MAPNULL value is recognized again
    assertTrue(ZonedDateTimeType.isGMT(null));
    assertTrue(ZonedDateTimeType.isGMT(""));
    assertTrue(ZonedDateTimeType.isGMT(" "));
    assertTrue(ZonedDateTimeType.isGMT("UTC"));
    assertTrue(ZonedDateTimeType.isGMT(ZonedDateTimeType.GMT));
    assertFalse(ZonedDateTimeType.isGMT("Europe/Berlin"));

    assertBadIndex(type);
  }

  @Test
  public void testInstantColumns() {
    InstantType type = new InstantType();
    assertEquals(type.getColumnCount(backend), 2);
    assertEquals(type.getSqlType(backend, 0), SqlType.BIGINT);
    assertEquals(type.getSqlType(backend, 1), SqlType.INTEGER);
    assertEquals(type.getCommentSuffix(backend, 0), Optional.of(" [s]"));
    assertEquals(type.getCommentSuffix(backend, 1), Optional.of(" [ns]"));
    assertEquals(type.getColumnGetter(0, "at"), "at.getEpochSecond()");
    assertEquals(type.getColumnGetter(1, "at"), "at.getNano()");
    assertEquals(type.getColumnAlias(0), "epochSecond");
    assertEquals(type.getColumnAlias(1), "nano");
    assertEquals(type.getSize(backend, 1, 20), 0);
    // both columns are sortable, seconds before nanoseconds
    assertEquals(type.getSortableColumns(), new int[] { 0, 1 });

    Instant value = Instant.ofEpochSecond(1579083630L, 123456789);
    assertEquals(type.getColumnValue(backend, 0, value), 1579083630L);
    assertEquals(type.getColumnValue(backend, 1, value), 123456789);
    assertNull(type.getColumnValue(backend, 0, null));
    assertEquals(type.getMappedNullValue(backend, 0), 0L);
    assertEquals(type.getMappedNullValue(backend, 1), 0);

    assertBadIndex(type);
  }

  /**
   * Asserts that a two-column type rejects a column index it does not have.
   *
   * @param type the data type
   */
  private void assertBadIndex(DataType<?> type) {
    expectThrows(IndexOutOfBoundsException.class, () -> type.getSqlType(backend, 2));
    expectThrows(IndexOutOfBoundsException.class, () -> type.getCommentSuffix(backend, 2));
    expectThrows(IndexOutOfBoundsException.class, () -> type.getColumnGetter(2, "at"));
    expectThrows(IndexOutOfBoundsException.class, () -> type.getColumnAlias(2));
    expectThrows(IndexOutOfBoundsException.class, () -> type.getSize(backend, 2, 0));
  }

}
