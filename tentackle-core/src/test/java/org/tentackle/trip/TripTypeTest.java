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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.common.BMoney;
import org.tentackle.common.Binary;
import org.tentackle.common.DMoney;
import org.tentackle.common.Date;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.I18NText;
import org.tentackle.common.TBinary;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.ChainedProperties;
import org.tentackle.misc.CopyOnWriteCollection;
import org.tentackle.misc.CopyOnWriteList;
import org.tentackle.misc.ImmutableArrayList;
import org.tentackle.misc.TrackedArrayList;
import org.tentackle.misc.TrackedList;
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;
import org.tentackle.trip.impl.CanonicalDto;
import org.tentackle.trip.impl.CanonicalList;
import org.tentackle.trip.impl.ControlledDeduplicatableRecord;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.DtoWithCanonicalBuilder;
import org.tentackle.trip.impl.EchoTransceiver;
import org.tentackle.trip.impl.Employees;
import org.tentackle.trip.impl.EmployeesArray;
import org.tentackle.trip.impl.TestBodyEnum;
import org.tentackle.trip.impl.NoNoArgsInstantiatable;
import org.tentackle.trip.impl.NotReferencableCanonicalDto;
import org.tentackle.trip.impl.NotReferencableClass;
import org.tentackle.trip.impl.NotReferencableRecord;
import org.tentackle.trip.impl.PartialCanonicalTripType;
import org.tentackle.trip.impl.PreparedSerializableClass;
import org.tentackle.trip.impl.TripError;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TestClassWithArray;
import org.tentackle.trip.impl.TestEnum;
import org.tentackle.trip.impl.TestExceptionGenerator;
import org.tentackle.trip.impl.TestInvocationHandler;
import org.tentackle.trip.impl.TestJOSClass;
import org.tentackle.trip.impl.TestProxyInterface;
import org.tentackle.trip.impl.TestProxyTarget;
import org.tentackle.trip.impl.TestRecord;
import org.tentackle.trip.impl.TestSubClass;
import org.tentackle.trip.impl.ValidateSerializableClass;
import org.tentackle.trip.impl.ValuesInstantiatable;
import org.tentackle.trip.types.CollectionTripType;
import org.tentackle.trip.types.ImmutableCollectionTripType;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@TripSerializeLambda
@SuppressWarnings("missing-explicit-ctor")
public class TripTypeTest implements Serializable { // serializable only for JOS tests!

  @Serial
  private static final long serialVersionUID = 1L;

  private final transient EchoTransceiver transceiver = new EchoTransceiver();

  @Test
  void testBoolean () {
    TripType<Boolean> tripType = TripFactory.getInstance().getType(Boolean.class);
    tunnel(true, tripType);
    tunnel(false, tripType);
    tunnel(null, tripType);
    tripType = TripFactory.getInstance().getType(Boolean.TYPE);
    tunnel(true, tripType);
    tunnel(false, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Boolean.TYPE));
    TripType<Boolean[]> arrayType = TripFactory.getInstance().getType(Boolean[].class);
    tunnel(new Boolean[] {false, true, null, null, null, false, null, true, null, null, null, null}, arrayType);
    TripType<boolean[]> primArrayType = TripFactory.getInstance().getType(boolean[].class);
    tunnel(new boolean[] {false, true, false, false, false, false, false, true, false, false, false, false}, primArrayType);
  }

  @Test
  void testByte() {
    TripType<Byte> tripType = TripFactory.getInstance().getType(Byte.class);
    tunnel((byte) 123, tripType);
    tunnel((byte) -123, tripType);
    tunnel((byte) 2, tripType);
    tunnel((byte) -1, tripType);
    tunnel(Byte.MAX_VALUE, tripType);
    tunnel(Byte.MIN_VALUE, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Byte.TYPE));
    TripType<Byte[]> arrayType = TripFactory.getInstance().getType(Byte[].class);
    tunnel(new Byte[] {123, -123, 2, -1, null, null, null, Byte.MAX_VALUE, null, Byte.MIN_VALUE, null, null, null, null}, arrayType);
    TripType<byte[]> primArrayType = TripFactory.getInstance().getType(byte[].class);
    tunnel(new byte[] {123, -123, 2, -1, 0, 0, 0, Byte.MAX_VALUE, 0, Byte.MIN_VALUE, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testCharacter() {
    TripType<Character> tripType = TripFactory.getInstance().getType(Character.class);
    tunnel('a', tripType);
    tunnel('Z', tripType);
    tunnel('ü', tripType);
    tunnel('Ä', tripType);
    tunnel('Ʊ', tripType);
    tunnel('ʩ', tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Character.TYPE));
    TripType<Character[]> arrayType = TripFactory.getInstance().getType(Character[].class);
    tunnel(new Character[] {'a', 'Z', 'b', 'Y', 'ʩ', null, null, null, Character.MAX_VALUE, null, Character.MIN_VALUE, null, null, null, null}, arrayType);
    TripType<char[]> primArrayType = TripFactory.getInstance().getType(char[].class);
    tunnel(new char[] {'a', 'Z', 'b', 'Y', 'ʩ', 0, 0, 0, Character.MAX_VALUE, 0, Character.MIN_VALUE, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testShort() {
    TripType<Short> tripType = TripFactory.getInstance().getType(Short.class);
    tunnel((short) 123, tripType);
    tunnel((short) -123, tripType);
    tunnel((short) -1, tripType);
    tunnel((short) 8000, tripType);
    tunnel((short) -8000, tripType);
    tunnel(Short.MAX_VALUE, tripType);
    tunnel(Short.MIN_VALUE, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Short.TYPE));
    TripType<Short[]> arrayType = TripFactory.getInstance().getType(Short[].class);
    tunnel(new Short[] {123, -123, -1, 8000, null, null, null, -8000, Short.MAX_VALUE, null, Short.MIN_VALUE, null, null, null, null}, arrayType);
    TripType<short[]> primArrayType = TripFactory.getInstance().getType(short[].class);
    tunnel(new short[] {123, -123, -1, 8000, 0, 0, 0, -8000, Short.MAX_VALUE, 0, Short.MIN_VALUE, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testInteger() {
    TripType<Integer> tripType = TripFactory.getInstance().getType(Integer.class);
    tunnel(123, tripType);
    tunnel(-1, tripType);
    tunnel(8000000, tripType);
    tunnel(-8000000, tripType);
    tunnel(TripConstants.INT3_MAX, tripType);
    tunnel(TripConstants.INT3_MIN, tripType);
    tunnel(TripConstants.INT3_MAX + 1, tripType);
    tunnel(TripConstants.INT3_MIN - 1, tripType);
    tunnel(Integer.MAX_VALUE, tripType);
    tunnel(Integer.MIN_VALUE, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Integer.TYPE));
    TripType<Integer[]> arrayType = TripFactory.getInstance().getType(Integer[].class);
    tunnel(new Integer[] { 123, -1, 8000000, null, null, null, -8000000, Integer.MAX_VALUE, null, Integer.MIN_VALUE, null, null, null, null}, arrayType);
    TripType<int[]> primArrayType = TripFactory.getInstance().getType(int[].class);
    tunnel(new int[] { 123, -1, 8000000, 0, 0, 0, -8000000, Integer.MAX_VALUE, 0, Integer.MIN_VALUE, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testLong() {
    TripType<Long> tripType = TripFactory.getInstance().getType(Long.class);
    tunnel(123L, tripType);
    tunnel(-123L, tripType);
    tunnel((long) TripConstants.INT3_MAX, tripType);
    tunnel((long) TripConstants.INT3_MIN, tripType);
    tunnel(TripConstants.INT3_MAX + 1L, tripType);
    tunnel(TripConstants.INT3_MIN - 1L, tripType);
    tunnel(TripConstants.LONG5_MAX, tripType);
    tunnel(TripConstants.LONG5_MIN, tripType);
    tunnel(TripConstants.LONG5_MAX + 1, tripType);
    tunnel(TripConstants.LONG5_MIN - 1, tripType);
    tunnel(TripConstants.LONG6_MAX, tripType);
    tunnel(TripConstants.LONG6_MIN, tripType);
    tunnel(TripConstants.LONG6_MAX + 1, tripType);
    tunnel(TripConstants.LONG6_MIN - 1, tripType);
    tunnel(TripConstants.LONG7_MAX, tripType);
    tunnel(TripConstants.LONG7_MIN, tripType);
    tunnel(TripConstants.LONG7_MAX + 1, tripType);
    tunnel(TripConstants.LONG7_MIN - 1, tripType);
    tunnel(800000000L, tripType);
    tunnel(-800000000L, tripType);
    tunnel(Long.MAX_VALUE, tripType);
    tunnel(Long.MIN_VALUE, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Long.TYPE));
    TripType<Long[]> arrayType = TripFactory.getInstance().getType(Long[].class);
    tunnel(new Long[] {123L, -123L, 800000000L, null, null, null, -800000000L, Long.MAX_VALUE, null, Long.MIN_VALUE, null, null, null, null}, arrayType);
    TripType<long[]> primArrayType = TripFactory.getInstance().getType(long[].class);
    tunnel(new long[] {123L, -123L, 800000000L, 0, 0, 0, -800000000L, Long.MAX_VALUE, 0, Long.MIN_VALUE, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testFloat() {
    TripType<Float> tripType = TripFactory.getInstance().getType(Float.class);
    tunnel(123.456f, tripType);
    tunnel(-123.456f, tripType);
    tunnel(0.0f, tripType);
    tunnel(Float.MAX_VALUE, tripType);
    tunnel(Float.MIN_VALUE, tripType);
    tunnel(Float.NaN, tripType);
    tunnel(Float.POSITIVE_INFINITY, tripType);
    tunnel(Float.NEGATIVE_INFINITY, tripType);
    tunnel(Float.MIN_NORMAL, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Float.TYPE));
    TripType<Float[]> arrayType = TripFactory.getInstance().getType(Float[].class);
    tunnel(new Float[] {123.456f, -123.456f, 0.0f, null, null, null, Float.MAX_VALUE, null, Float.MIN_VALUE,
                        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL, null, null, null, null}, arrayType);
    TripType<float[]> primArrayType = TripFactory.getInstance().getType(float[].class);
    tunnel(new float[] {123.456f, -123.456f, 0.0f, 0.0f, 0.0f, 0.0f, Float.MAX_VALUE, 0.0f, Float.MIN_VALUE,
                        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testDouble() {
    TripType<Double> tripType = TripFactory.getInstance().getType(Double.class);
    tunnel(1234567890.0123456789d, tripType);
    tunnel(-1234567890.0123456789d, tripType);
    tunnel(123.0d, tripType);
    tunnel(-123.0d, tripType);
    tunnel(0.0d, tripType);
    tunnel(Double.MAX_VALUE, tripType);
    tunnel(Double.MIN_VALUE, tripType);
    tunnel(Double.NaN, tripType);
    tunnel(Double.POSITIVE_INFINITY, tripType);
    tunnel(Double.NEGATIVE_INFINITY, tripType);
    tunnel(Double.MIN_NORMAL, tripType);
    tunnel(null, tripType);
    tunnelNullPrimitive(TripFactory.getInstance().getType(Double.TYPE));
    TripType<Double[]> arrayType = TripFactory.getInstance().getType(Double[].class);
    tunnel(new Double[] {1234567890.0123456789d, -1234567890.0123456789d, 123.0d, -123.0d, 0.0d, null, null, null,
                         Double.MAX_VALUE, null, Double.MIN_VALUE,
                         Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL, null, null, null, null}, arrayType);
    TripType<double[]> primArrayType = TripFactory.getInstance().getType(double[].class);
    tunnel(new double[] {1234567890.0123456789d, -1234567890.0123456789d, 123.0d, -123.0d, 0.0d, 0.0d, 0.0d, 0.0d,
                         Double.MAX_VALUE, 0.0d, Double.MIN_VALUE,
                         Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL, 0, 0, 0, 0}, primArrayType);
  }

  @Test
  void testBigDecimal() {
    TripType<BigDecimal> tripType = TripFactory.getInstance().getType(BigDecimal.class);
    BigDecimal bd = new BigDecimal("1234567890123456789012345678901234567890.0123456789");
    tunnel(bd, tripType);
    bd = BigDecimal.valueOf(1000L);
    tunnel(bd, tripType);
    bd = new BMoney(12345600, 2);
    tunnel(bd, tripType);
    bd = new DMoney(12345600, 2);
    tunnel(bd, tripType);
  }

  @Test
  void testBigDecimalCornerCases() {
    TripType<BigDecimal> tripType = TripFactory.getInstance().getType(BigDecimal.class);
    tunnel(BigDecimal.ZERO, tripType);
    tunnel(new BigDecimal("-123.456"), tripType);
    tunnel(new BigDecimal("-1.23E+10"), tripType);      // negative scale
    tunnel(new BigDecimal("1E-30"), tripType);          // large positive scale
    tunnel(new BigDecimal("-1234567890123456789012345678901234567890.0123456789"), tripType);
    tunnel(new BMoney(-12345600, 2), tripType);
    tunnel(new DMoney(-12345600, 2), tripType);
  }

  @Test
  void testNegativeZero() {
    TripType<Float> floatType = TripFactory.getInstance().getType(Float.class);
    Float tunnelledFloat = tunnel(-0.0f, floatType);
    assertEquals(Float.floatToIntBits(tunnelledFloat), Float.floatToIntBits(-0.0f));
    TripType<Double> doubleType = TripFactory.getInstance().getType(Double.class);
    Double tunnelledDouble = tunnel(-0.0d, doubleType);
    assertEquals(Double.doubleToLongBits(tunnelledDouble), Double.doubleToLongBits(-0.0d));
  }

  @Test
  void testDate() {
    TripType<java.util.Date> tripType = TripFactory.getInstance().getType(java.util.Date.class);
    tunnel(new java.util.Date(), tripType);
    tunnel(new java.sql.Date(System.currentTimeMillis()), tripType);
    Date date = new Date();
    tunnelPhenotypic(date, tripType);
    date.freeze();
    Date tunnelled = (Date) tunnelPhenotypic(date, tripType);
    assertTrue(tunnelled.isFrozen());
  }

  @Test
  void testPre1970Date() {
    TripType<java.util.Date> tripType = TripFactory.getInstance().getType(java.util.Date.class);
    tunnel(new java.util.Date(-1234567890123L), tripType);
    tunnel(new java.sql.Date(java.sql.Date.valueOf("1888-03-15").getTime()), tripType);
    Date date = new Date(java.sql.Date.valueOf("1888-03-15").getTime());
    tunnelPhenotypic(date, tripType);
  }

  @Test
  void testTime() {
    TripType<java.sql.Time> tripType = TripFactory.getInstance().getType(java.sql.Time.class);
    tunnel(new java.sql.Time(System.currentTimeMillis()), tripType);
    Time time = new Time();
    tunnelPhenotypic(time, tripType);
    time.freeze();
    Time tunnelled = (Time) tunnelPhenotypic(time, tripType);
    assertTrue(tunnelled.isFrozen());
  }

  @Test
  void testTimestamp() {
    TripType<java.sql.Timestamp> tripType = TripFactory.getInstance().getType(java.sql.Timestamp.class);
    tunnel(new java.sql.Timestamp(System.currentTimeMillis()), tripType);
    Timestamp timestamp = new Timestamp();
    tunnel(timestamp, tripType);
    timestamp.freeze();
    Timestamp tunnelled = (Timestamp) tunnel(timestamp, tripType);
    assertTrue(tunnelled.isFrozen());
    timestamp.setUTC(true);
    tunnelled = (Timestamp) tunnel(timestamp, tripType);
    assertTrue(tunnelled.isUTC());
  }

  @Test
  void testTimestampNanos() {
    // sub-millisecond nanos are not covered by currentTimeMillis-based timestamps
    TripType<java.sql.Timestamp> tripType = TripFactory.getInstance().getType(java.sql.Timestamp.class);
    Timestamp timestamp = new Timestamp(1234567890123L);
    timestamp.setNanos(123456789);
    Timestamp tunnelled = (Timestamp) tunnel(timestamp, tripType);
    assertEquals(tunnelled.getNanos(), timestamp.getNanos());
    java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(1234567890123L);
    sqlTimestamp.setNanos(987654321);
    java.sql.Timestamp tunnelledSql = tunnel(sqlTimestamp, tripType);
    assertEquals(tunnelledSql.getNanos(), sqlTimestamp.getNanos());
  }

  @Test
  void testUTCTimestampAcrossTimeZones() {
    // A UTC timestamp preserves its YYYYMMDDHHMMSS rendering across timezones;
    // the epoch millis are re-derived in the receiver's zone (see Timestamp#writeObject).
    TimeZone originalZone = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
      Timestamp timestamp = new Timestamp(1234567890123L, true);
      String sentText = timestamp.toString();
      TripType<java.sql.Timestamp> tripType = TripFactory.getInstance().getType(java.sql.Timestamp.class);
      tripType.write(timestamp, transceiver.getStream());
      transceiver.echo();
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      Timestamp tunnelled = (Timestamp) tripType.read(transceiver.getStream(),
                                                      transceiver.getStream().getDeserializer().deserializeOpCode());
      assertTrue(tunnelled.isUTC());
      assertEquals(tunnelled.toString(), sentText);   // same wall clock in another zone
    }
    finally {
      TimeZone.setDefault(originalZone);
      transceiver.reset();
    }
  }

  @Test
  void testLocalDate() {
    TripType<LocalDate> tripType = TripFactory.getInstance().getType(LocalDate.class);
    tunnel(LocalDate.now(), tripType);
  }

  @Test
  void testLocalTime() {
    TripType<LocalTime> tripType = TripFactory.getInstance().getType(LocalTime.class);
    tunnel(LocalTime.now(), tripType);
  }

  @Test
  void testLocalDateTime() {
    TripType<LocalDateTime> tripType = TripFactory.getInstance().getType(LocalDateTime.class);
    tunnel(LocalDateTime.now(), tripType);
  }

  @Test
  void testZonedDateTime() {
    TripType<ZonedDateTime> tripType = TripFactory.getInstance().getType(ZonedDateTime.class);
    tunnel(ZonedDateTime.now(), tripType);
  }

  @Test
  void testOffsetDateTime() {
    TripType<OffsetDateTime> tripType = TripFactory.getInstance().getType(OffsetDateTime.class);
    tunnel(OffsetDateTime.now(), tripType);
  }

  @Test
  void testOffsetTime() {
    TripType<OffsetTime> tripType = TripFactory.getInstance().getType(OffsetTime.class);
    tunnel(OffsetTime.now(), tripType);
  }

  @Test
  void testInstant() {
    TripType<Instant> tripType = TripFactory.getInstance().getType(Instant.class);
    tunnel(Instant.now(), tripType);
  }

  @Test
  void testUUID() {
    TripType<UUID> tripType = TripFactory.getInstance().getType(UUID.class);
    tunnel(UUID.randomUUID(), tripType);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testBinary() throws IOException {
    BMoney money = new BMoney(80000.0, 2);
    TripType<Binary> tripType = TripFactory.getInstance().getType(Binary.class);
    Binary<BMoney> binary = new Binary<>(money);
    tunnel(binary, tripType);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testTBinary() throws IOException {
    BMoney money = new BMoney(80000.0, 2);
    TripType<TBinary> tripType = TripFactory.getInstance().getType(TBinary.class);
    TBinary<BMoney> binary = new TBinary<>(money);
    tunnel(binary, tripType);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testEmptyBinary() {
    // empty binaries travel as they are and don't become null
    TripType<Binary> binaryType = TripFactory.getInstance().getType(Binary.class);
    assertNotNull(tunnel(new Binary<>(), binaryType));
    TripType<TBinary> tbinaryType = TripFactory.getInstance().getType(TBinary.class);
    assertNotNull(tunnel(new TBinary<>(), tbinaryType));
  }

  @Test
  void testI18NText() {
    TripType<I18NText> tripType = TripFactory.getInstance().getType(I18NText.class);
    I18NText i18NText = new I18NText(Locale.ENGLISH, "Hello, World!");
    tunnel(i18NText,tripType);
  }

  @Test
  void testCanonicalBuilder() {
    DtoWithCanonicalBuilder john = DtoWithCanonicalBuilder.builder().userId(100).userName("John").build();
    tunnel(john, TripFactory.getInstance().getType(DtoWithCanonicalBuilder.class));
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testCanonicalBuilderReference() {
    // the same instance twice -> the second occurrence is sent as a back-reference
    DtoWithCanonicalBuilder john = DtoWithCanonicalBuilder.builder().userId(100).userName("John").build();
    List<DtoWithCanonicalBuilder> list = new ArrayList<>();
    list.add(john);
    list.add(john);
    TripType<List> tripType = TripFactory.getInstance().getType(ArrayList.class);
    List tunneled = tunnel(list, tripType);
    assertSame(tunneled.get(0), tunneled.get(1));
  }

  @Test
  void testCanonicalDto() {
    TripType<CanonicalDto> tripType = TripFactory.getInstance().getType(CanonicalDto.class);
    tunnel(new CanonicalDto("John", 42), tripType);
    tunnel(new CanonicalDto(null, 0), tripType);
  }

  /**
   * A component the sender does not provide — because its version of the class does not have it,
   * or because it is at its default value — must not prevent the object from being created.
   * The missing argument of the canonical constructor keeps its default value instead of the
   * deserialization silently yielding null.
   */
  @Test
  void testCanonicalConstructorWithMissingComponents() {
    TripType<CanonicalDto> reader = TripFactory.getInstance().getType(CanonicalDto.class);
    CanonicalDto john = new CanonicalDto("John", 42);

    // the primitive component is missing -> keeps its default
    CanonicalDto withoutAge = tunnelValue(john, new PartialCanonicalTripType("age"), reader);
    assertNotNull(withoutAge);
    assertEquals(withoutAge.getName(), "John");
    assertEquals(withoutAge.getAge(), 0);

    // the reference component is missing -> keeps its default
    CanonicalDto withoutName = tunnelValue(john, new PartialCanonicalTripType("name"), reader);
    assertNotNull(withoutName);
    assertNull(withoutName.getName());
    assertEquals(withoutName.getAge(), 42);

    // no component at all arrives
    CanonicalDto empty = tunnelValue(john, new PartialCanonicalTripType("name", "age"), reader);
    assertNotNull(empty);
    assertNull(empty.getName());
    assertEquals(empty.getAge(), 0);
  }

  /**
   * A null first component of a non-referencable object must not be taken for a null object:
   * without a reference, its null code would directly follow the object's header.
   * This applies to all components without a default value, such as those of a canonical
   * constructor or a record, and to those whose default value is not null.
   */
  @Test
  void testNullFirstComponentOfNotReferencable() {
    TripType<NotReferencableCanonicalDto> canonicalType =
        TripFactory.getInstance().getType(NotReferencableCanonicalDto.class);
    tunnel(new NotReferencableCanonicalDto(null, 42), canonicalType);
    tunnel(new NotReferencableCanonicalDto(null, 0), canonicalType);

    TripType<NotReferencableRecord> recordType = TripFactory.getInstance().getType(NotReferencableRecord.class);
    tunnel(new NotReferencableRecord(null, 42), recordType);
    tunnel(new NotReferencableRecord(null, 0), recordType);

    TripType<NotReferencableClass> classType = TripFactory.getInstance().getType(NotReferencableClass.class);
    NotReferencableClass obj = new NotReferencableClass();
    obj.name = null;    // not the default value
    tunnel(obj, classType);

    // as a component of another object, too
    TestRecord parent = new TestRecord("kathrin", 59, false, null, null,
                                       new NotReferencableCanonicalDto(null, 7));
    tunnel(parent, TripFactory.getInstance().getType(TestRecord.class));
  }

  @Test
  void testValuesInstanceCreator() {
    ValuesInstantiatable john = new ValuesInstantiatable("John", 42);
    tunnel(john, TripFactory.getInstance().getType(ValuesInstantiatable.class));
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testValuesInstanceCreatorReference() {
    // the same instance twice -> the second occurrence is sent as a back-reference and
    // must resolve to the object created by the instance creator, not to the bare instance
    ValuesInstantiatable john = new ValuesInstantiatable("John", 42);
    List<ValuesInstantiatable> list = new ArrayList<>();
    list.add(john);
    list.add(john);
    TripType<List> tripType = TripFactory.getInstance().getType(ArrayList.class);
    List tunneled = tunnel(list, tripType);
    assertSame(tunneled.get(0), tunneled.get(1));
    assertEquals(tunneled.get(1), john);
  }

  @Test
  void testString() {
    tunnel(null);
    tunnel("");
    tunnel("Stop!\n\rPay Troll!\f");
    tunnel(" ABCDEFGHIJKLMNOPQRSTUVWXYZ\\rabcdefghijklmnopqrstuvwxyz\\n1234567890\\t\\\"!`?'.,;:()[]{}<>|/@\\\\^$-%+=#_&~*");
    tunnel("äüöß€ ÄÜÖ ƱʩčŇ\ngrmpf");
    tunnel("\u00E4\u00FC\u00F6\u00DF\u20AC\u00C4\u00DC\u00D6\u01B1\u02A9\u010D\u0147");
    // longer than CHAR_BUF_SIZE
    tunnel("""
             Lorem ipsum dolor sit amet, consectetur adipisici elit, sed eiusmod tempor incidunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquid ex ea commodi consequat. Quis aute iure reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint obcaecat cupiditat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
             
             --
             
             Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.
             
             Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.
             
             Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.
             
             Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.
             
             At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.
             
             Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.
             Lorem ipsum dolor sit amet, consectetur adipisici elit, sed eiusmod tempor incidunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquid ex ea commodi consequat. Quis aute iure reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint obcaecat cupiditat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
             
             --
             
             Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.
             
             Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.
             
             Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.
             
             Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.
             
             At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.
             
             Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.
             """);

    TripType<String[]> arrayType = TripFactory.getInstance().getType(String[].class);
    tunnel(new String[] {null, "", "Stop!\n\rPay Troll!\f",
                         " ABCDEFGHIJKLMNOPQRSTUVWXYZ\\rabcdefghijklmnopqrstuvwxyz\\n1234567890\\t\\\"!`?'.,;:()[]{}<>|/@\\\\^$-%+=#_&~*",
                         "äüöß€ ÄÜÖ ƱʩčŇ\ngrmpf",
                         "\u00E4\u00FC\u00F6\u00DF\u20AC\u00C4\u00DC\u00D6\u01B1\u02A9\u010D\u0147",
                         null, null, null, null}, arrayType);
  }

  @Test
  void testStringLengthBoundaries() {
    // wire lengths are UTF-8 bytes, not chars: exercise the 1-, 2- and 4-byte length encodings
    tunnel("x".repeat(255));
    tunnel("x".repeat(256));
    tunnel("x".repeat(65535));
    tunnel("x".repeat(65536));
    tunnel("ä".repeat(128));      // 256 UTF-8 bytes from 128 chars
    tunnel("x".repeat(70000));    // exceeds the serializer's internal buffer
  }

  @Test
  void testStringSupplementaryPlane() {
    tunnel("hello 😀 world");                   // emoji, 4-byte UTF-8
    tunnel("😀".repeat(100));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testTwoDimensionalArray() {
    int[][] testArray = new int[][] { {1, 2, 3}, {4, 5}, {6, 7, 8, 9}, null, {}, {10}};
    TripType<int[][]> tripType = TripFactory.getInstance().getType(testArray.getClass());
    int[][] tunneledArray = tunnel(testArray, tripType);
    assertNotSame(testArray, tunneledArray);
    assertSame(tunneledArray, tunnel(testArray, tripType));   // same reference!
    assertNull(tunnel(null, tripType));
  }

  @Test
  void testDictionaryEntry() {
    TripType<DictionaryEntry> tripType = TripFactory.getInstance().getType(DictionaryEntry.class);
    DictionaryEntry entry = TripFactory.getInstance()
                                       .createDictionary(UUID.randomUUID(), true)
                                       .createEntry(TestClass.class);
    tunnel(entry, tripType);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testClassVariable() {
    TripType<Class> tripType = TripFactory.getInstance().getType(Class.class);
    tunnel(Timestamp.class, tripType);
    tunnel(Timestamp[].class, tripType);
    tunnel(int[][].class, tripType);
  }

  @Test
  void testObject() {
    TripType<TestClass> tripType = TripFactory.getInstance().getType(TestClass.class);
    TestClass obj = new TestClass();
    obj.name = "test";
    obj.age = 123;
    obj.child = new TestSubClass(obj, 5.0, new Timestamp(true));
    obj.child.name = "child";
    obj.transientField = "transient"; // will not be serialized and is not part of equals/hashCode
    obj.money = new BMoney(10000, 2);
    obj.color = TestEnum.RED;
    obj.person = new TestRecord("kerrin", 29, false, null, null, 100.0d);
    HashMap<String, Integer> stringIntegerHashMap = new HashMap<>();
    stringIntegerHashMap.put("one", 1);
    stringIntegerHashMap.put("two", 2);
    obj.someObject = stringIntegerHashMap;
    TestClass tunnelled = tunnel(obj, tripType);
    assertSame(((TestSubClass) tunnelled.child).parent, tunnelled); // not part of TestSubObject.equals because of endless loop!
    tunnel(null, tripType);
  }

  @Test
  void testEnum() {
    TripType<TestEnum> tripType = TripFactory.getInstance().getType(TestEnum.class);
    tunnel(TestEnum.RED, tripType);
    tunnel(TestEnum.GREEN, tripType);
    tunnel(TestEnum.BLUE, tripType);
    tunnel(null, tripType);
  }

  @Test
  void testEnumWithBody() {
    TripType<TestBodyEnum> tripType = TripFactory.getInstance().getType(TestBodyEnum.class);
    tunnel(TestBodyEnum.PLAIN, tripType);
    tunnel(TestBodyEnum.FANCY, tripType);

    // held polymorphically, the runtime class is the anonymous subclass of the enum
    TripType<TestClass> objectType = TripFactory.getInstance().getType(TestClass.class);
    TestClass obj = new TestClass();
    obj.someObject = TestBodyEnum.FANCY;
    TestClass tunnelled = tunnel(obj, objectType);
    assertSame(tunnelled.someObject, TestBodyEnum.FANCY);
  }

  @Test
  void testLocale() {
    TripType<Locale> tripType = TripFactory.getInstance().getType(Locale.class);
    tunnel(Locale.of("de", "DE"), tripType);
    tunnel(Locale.of("en"), tripType);
    tunnel(Locale.of("", "US"), tripType);
    tunnel(Locale.of("en", "US", "WIN"), tripType);
    tunnel(Locale.of("en", "", "WIN"), tripType);
    tunnel(Locale.ROOT, tripType);
    tunnel(null, tripType);
  }

  @Test
  void testLocaleCornerCases() {
    TripType<Locale> tripType = TripFactory.getInstance().getType(Locale.class);
    tunnel(Locale.of("de", "DE", "NY_X"), tripType);                // variant containing an underscore
    tunnel(Locale.forLanguageTag("zh-Hant-TW"), tripType);          // script
    tunnel(Locale.forLanguageTag("ja-JP-u-ca-japanese"), tripType); // extension
    tunnel(Locale.of("th", "TH", "TH"), tripType);                  // legacy special case
    tunnel(Locale.of("ja", "JP", "JP"), tripType);                  // legacy special case
  }

  @Test
  void testTimeZone() {
    TripType<TimeZone> tripType = TripFactory.getInstance().getType(TimeZone.class);
    tunnel(TimeZone.getTimeZone("Europe/Berlin"), tripType);
    tunnel(TimeZone.getTimeZone("EDT"), tripType);
    tunnel(TimeZone.getTimeZone("GMT+2"), tripType);
    tunnel(TimeZone.getTimeZone("CEST"), tripType);
  }

  @Test
  void testRecord() {
    TripType<TestRecord> tripType = TripFactory.getInstance().getType(TestRecord.class);
    TestRecord child = new TestRecord("kerrin", 29, false, null, null, "blah-blah");
    TestRecord mother = new TestRecord("kathrin", 59, false, child,
                                       new TestSubClass(null, 5.0, new Timestamp(true)), "blah-blah");
    TestRecord tunneledRecord = tunnel(mother, tripType);
    assertNotSame(child, tunneledRecord.child());
    assertSame(tunnel(mother, tripType), tunneledRecord);

    TestRecord dedupChild = new TestRecord("kerrin", 29, false, null, null, "blah-blah");
    TestRecord tunneledDedup = tunnel(dedupChild, tripType);
    assertSame(tunneledDedup, tunneledRecord.child());
  }

  /**
   * Records nested in records, each holding a list of the next level.<br>
   * The whole graph must arrive component by component, including the empty and the {@code null} list.
   */
  @Test
  void testNestedRecords() {
    TripType<Employees> tripType = TripFactory.getInstance().getType(Employees.class);

    Employees.Attribute shared = new Employees.Attribute("department", "development");
    Employees employees = new Employees(
      List.of(new Employees.Employee("kerrin", 29,
                                     List.of(shared, new Employees.Attribute("room", "1.02"))),
              new Employees.Employee("kathrin", 59,
                                     // an equal but distinct attribute of another employee
                                     List.of(new Employees.Attribute("department", "development"))),
              new Employees.Employee("hugo", 42, List.of()),
              new Employees.Employee(null, null, null)));

    Employees tunnelled = tunnel(employees, tripType);
    assertNotSame(tunnelled, employees);
    assertEquals(tunnelled.employees().size(), 4);
    assertEquals(tunnelled.employees().getFirst().attributes().getFirst().value(), "development");
    assertTrue(tunnelled.employees().get(2).attributes().isEmpty());
    assertNull(tunnelled.employees().get(3).name());
    assertNull(tunnelled.employees().get(3).age());
    assertNull(tunnelled.employees().get(3).attributes());

    // equal records are deduplicated, no matter how deep they are nested
    assertSame(tunnelled.employees().getFirst().attributes().getFirst(),
               tunnelled.employees().get(1).attributes().getFirst());

    // the whole object graph is a reference of its own
    assertSame(tunnel(employees, tripType), tunnelled);

    tunnel(new Employees(List.of()), tripType);
    tunnel(new Employees(null), tripType);
    tunnel(null, tripType);
  }

  /**
   * The same as {@link #testNestedRecords()}, but with arrays instead of lists.<br>
   * Notice that the array components make the records fall back to identity equals,
   * hence the graph must be asserted component by component.
   */
  @Test
  void testNestedRecordsWithArrays() {
    TripType<EmployeesArray> tripType = TripFactory.getInstance().getType(EmployeesArray.class);

    EmployeesArray.Attribute shared = new EmployeesArray.Attribute("department", "development");
    EmployeesArray employees = new EmployeesArray(
      new EmployeesArray.Employee[] {
        new EmployeesArray.Employee("kerrin", 29,
                                    new EmployeesArray.Attribute[] {
                                      shared, new EmployeesArray.Attribute("room", "1.02")}),
        new EmployeesArray.Employee("kathrin", 59,
                                    // an equal but distinct attribute of another employee
                                    new EmployeesArray.Attribute[] {
                                      new EmployeesArray.Attribute("department", "development")}),
        new EmployeesArray.Employee("hugo", 42, new EmployeesArray.Attribute[0]),
        new EmployeesArray.Employee(null, null, null),
        null});

    EmployeesArray tunnelled = tunnelNoEquals(employees, tripType);
    assertNotSame(tunnelled, employees);
    assertEquals(tunnelled.employees().getClass().getComponentType(), EmployeesArray.Employee.class);
    assertEquals(tunnelled.employees().length, 5);
    assertEquals(tunnelled.employees()[0].name(), "kerrin");
    assertEquals(tunnelled.employees()[0].age(), Integer.valueOf(29));
    assertEquals(tunnelled.employees()[0].attributes(), employees.employees()[0].attributes());   // assertEquals is aware of arrays!
    assertEquals(tunnelled.employees()[0].attributes().getClass().getComponentType(), EmployeesArray.Attribute.class);
    assertEquals(tunnelled.employees()[2].attributes().length, 0);
    assertNull(tunnelled.employees()[3].name());
    assertNull(tunnelled.employees()[3].age());
    assertNull(tunnelled.employees()[3].attributes());
    assertNull(tunnelled.employees()[4]);

    // equal records are deduplicated, no matter how deep they are nested
    assertSame(tunnelled.employees()[0].attributes()[0], tunnelled.employees()[1].attributes()[0]);

    // the whole object graph is a reference of its own
    assertSame(tunnelNoEquals(employees, tripType), tunnelled);

    EmployeesArray empty = tunnelNoEquals(new EmployeesArray(new EmployeesArray.Employee[0]), tripType);
    assertEquals(empty.employees().length, 0);
    assertNull(tunnelNoEquals(new EmployeesArray(null), tripType).employees());
    assertNull(tunnel(null, tripType));
  }

  @Test
  void testControlledDeduplicatableRecord() {
    TripType<ControlledDeduplicatableRecord> tripType = TripFactory.getInstance().getType(ControlledDeduplicatableRecord.class);

    ControlledDeduplicatableRecord record1 = new ControlledDeduplicatableRecord("blah", true);
    ControlledDeduplicatableRecord record2 = new ControlledDeduplicatableRecord("blah", true);
    ControlledDeduplicatableRecord tunneled1 = tunnel(record1, tripType);
    ControlledDeduplicatableRecord tunneled2 = tunnel(record2, tripType);
    assertSame(tunneled1, tunneled2);

    record1 = new ControlledDeduplicatableRecord("blah", false);
    record2 = new ControlledDeduplicatableRecord("blah", false);
    tunneled1 = tunnel(record1, tripType);
    tunneled2 = tunnel(record2, tripType);
    assertNotSame(tunneled1, tunneled2);
  }

  @Test
  void testNotReferencable() {
    TripType<NotReferencableClass> tripType = TripFactory.getInstance().getType(NotReferencableClass.class);
    NotReferencableClass obj = new NotReferencableClass();
    assertNotSame(tunnel(obj, tripType), tunnel(obj, tripType));
  }

  @Test
  void testArray() {
    TestClass[] array = new TestClass[]{new TestClass(), new TestSubClass()};
    TripType<TestClass[]> tripType = TripFactory.getInstance().getType(array.getClass());
    TestClass[] tunneledArray = tunnel(array, tripType);
    assertNotSame(tunneledArray, array);
    assertSame(tunnel(array, tripType), tunneledArray);
    assertEquals(tunneledArray.getClass().getComponentType(), array.getClass().getComponentType());
  }

  @Test
  void testObjectArray() {
    Object[] array = new Object[]{new TestClass(), new TestSubClass()};
    TripType<Object[]> tripType = TripFactory.getInstance().getType(array.getClass());
    Object[] tunneledArray = tunnel(array, tripType);
    assertNotSame(tunneledArray, array);
    assertSame(tunnel(array, tripType), tunneledArray);
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testClassArray() {
    Class[] array = new Class[]{TestClass.class, TestSubClass.class, TestRecord.class, Integer.TYPE, Long[].class, Class[][].class};
    TripType<Class[]> tripType = TripFactory.getInstance().getType(array.getClass());
    Class[] tunneledArray = tunnel(array, tripType);
    assertNotSame(tunneledArray, array);
    assertSame(tunnel(array, tripType), tunneledArray);
  }

  @Test
  void testObjectWithArray() {
    TestClassWithArray withArray = new TestClassWithArray();
    withArray.childs = new TestSubClass[] {new TestSubClass(), null}; // childs is TestClass[] !
    TripType<TestClassWithArray> withType = TripFactory.getInstance().getType(withArray.getClass());
    TestClassWithArray tunnelledWithArray = tunnel(withArray, withType);
    assertEquals(tunnelledWithArray.childs.getClass().getComponentType(), withArray.childs.getClass().getComponentType());
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testMixedArray() {
    String[] strArray = new String[] {"one", "two", "three"};   // for reference test
    List list = new ArrayList<>();
    list.add("one");
    list.add(1);
    list.add(new TestClass());
    list.add(new TestSubClass());
    list.add(new TestRecord("kerrin", 29, false, null, null, strArray));
    TestInvocationHandler ih = new TestInvocationHandler(new TestProxyTarget());
    TestProxyInterface proxy = (TestProxyInterface) Proxy.newProxyInstance(
      ih.getClass().getClassLoader(), new Class[]{TestProxyInterface.class}, ih);
    proxy.setName("test");
    list.add(proxy);
    list.add(String.class);
    list.add(new BMoney(10000, 2));
    list.add(TestEnum.RED);
    list.add(new ArrayList<>(List.of("one", "two", "three")));  // immutable tests see below
    list.add(new Timestamp(true));
    list.add(strArray);
    Object[] array = list.toArray();

    TripType<Object[]> tripType = TripFactory.getInstance().getType(array.getClass());
    Object[] tunneledArray = tunnel(array, tripType);
    assertNotSame(tunneledArray, array);
    assertSame(tunneledArray[tunneledArray.length - 1], ((TestRecord) tunneledArray[4]).someObject());
    assertSame(tunnel(array, tripType), tunneledArray);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void testProxy() {
    TestInvocationHandler ih = new TestInvocationHandler(new TestProxyTarget());
    TestProxyInterface proxy = (TestProxyInterface) Proxy.newProxyInstance(
      ih.getClass().getClassLoader(), new Class[]{TestProxyInterface.class}, ih);
    proxy.setName("test");
    assertEquals(proxy.getName(), "test");

    TripType<TestProxyInterface> type = TripFactory.getInstance().getType(proxy.getClass());
    assertEquals(type.getCategory(), TripTypeCategory.PROXY);
    TestProxyInterface tunneledProxy = tunnel(proxy, type);
    assertEquals(tunneledProxy.getName(), "test");
    assertNotSame(tunneledProxy, proxy);
    assertSame(tunnel(proxy, type), tunneledProxy);

    TestClass object = new TestClass();
    object.someObject = proxy;
    TripType<TestClass> objectType = TripFactory.getInstance().getType(object.getClass());
    TestClass tunneledObject = tunnel(object, objectType);
    assertNotSame(proxy, tunneledObject.someObject);
    assertSame(tunneledObject.someObject, tunnel(object, objectType).someObject);
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testCollections() {
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    list.add("tree");
    TripType<List> type = TripFactory.getInstance().getType(list.getClass());
    assertTrue(type instanceof CollectionTripType);
    List<String> tunneledList = tunnel(list, type);
    assertNotSame(tunneledList, list);
    assertSame(tunneledList, tunnel(list, type));
    assertNull(tunnel(null, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testSelfReferencingCollection() {
    List<Object> list = new ArrayList<>();
    list.add("element");
    list.add(list);
    TripType<List> type = TripFactory.getInstance().getType(list.getClass());
    List tunneledList = tunnelNoEquals((List) list, type);
    assertEquals(tunneledList.size(), 2);
    assertEquals(tunneledList.get(0), "element");
    assertSame(tunneledList.get(1), tunneledList);

    Map<String, Object> map = new HashMap<>();
    map.put("self", map);
    TripType<Map> mapType = TripFactory.getInstance().getType(map.getClass());
    Map tunneledMap = tunnelNoEquals((Map) map, mapType);
    assertSame(tunneledMap.get("self"), tunneledMap);
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testWrappedCollections() {
    List<String> unmodifiableList = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("one", null, "two")));
    TripType<List> listType = TripFactory.getInstance().getType(unmodifiableList.getClass());
    List tunneledList = tunnel((List) unmodifiableList, listType);
    assertEquals(tunneledList.getClass(), unmodifiableList.getClass());
    try {
      tunneledList.add("three");
      fail("should be unmodifiable");
    }
    catch (UnsupportedOperationException ok) {
      // expected
    }

    List<String> asList = Arrays.asList("one", null, "two");
    tunnel((List) asList, TripFactory.getInstance().getType(asList.getClass()));

    List<String> emptyList = Collections.emptyList();
    assertSame(tunnel((List) emptyList, TripFactory.getInstance().getType(emptyList.getClass())),
               Collections.emptyList());

    Set<String> emptySet = Collections.emptySet();
    assertSame(tunnel((Set) emptySet, TripFactory.getInstance().getType(emptySet.getClass())),
               Collections.emptySet());

    List<String> singletonList = Collections.singletonList("solo");
    tunnel((List) singletonList, TripFactory.getInstance().getType(singletonList.getClass()));

    Set<String> singleton = Collections.singleton("solo");
    tunnel((Set) singleton, TripFactory.getInstance().getType(singleton.getClass()));

    Set<String> unmodifiableSet = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("one", "two")));
    tunnel((Set) unmodifiableSet, TripFactory.getInstance().getType(unmodifiableSet.getClass()));

    // Collections.unmodifiableCollection provides no content-based equals
    Collection<String> unmodifiableCollection = Collections.unmodifiableCollection(List.of("one", "two"));
    Collection tunneledCollection = tunnelNoEquals((Collection) unmodifiableCollection,
                                                   TripFactory.getInstance().getType(unmodifiableCollection.getClass()));
    assertEquals(new ArrayList<>(tunneledCollection), List.of("one", "two"));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testWrappedMaps() {
    Map<String, Integer> unmodifiableMap = Collections.unmodifiableMap(new HashMap<>(Map.of("one", 1, "two", 2)));
    TripType<Map> mapType = TripFactory.getInstance().getType(unmodifiableMap.getClass());
    Map tunneledMap = tunnel((Map) unmodifiableMap, mapType);
    assertEquals(tunneledMap.getClass(), unmodifiableMap.getClass());
    try {
      tunneledMap.put("three", 3);
      fail("should be unmodifiable");
    }
    catch (UnsupportedOperationException ok) {
      // expected
    }

    Map<String, Integer> emptyMap = Collections.emptyMap();
    assertSame(tunnel((Map) emptyMap, TripFactory.getInstance().getType(emptyMap.getClass())),
               Collections.emptyMap());

    Map<String, Integer> singletonMap = Collections.singletonMap("solo", 1);
    tunnel((Map) singletonMap, TripFactory.getInstance().getType(singletonMap.getClass()));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testEnumSet() {
    EnumSet<TestEnum> set = EnumSet.of(TestEnum.RED, TestEnum.BLUE);
    TripType<EnumSet> type = TripFactory.getInstance().getType(set.getClass());
    EnumSet tunneledSet = tunnel((EnumSet) set, type);
    assertTrue(tunneledSet.contains(TestEnum.RED));

    EnumSet<TestEnum> empty = EnumSet.noneOf(TestEnum.class);
    EnumSet tunneledEmpty = tunnel((EnumSet) empty, type);
    assertTrue(tunneledEmpty.isEmpty());
    tunneledEmpty.add(TestEnum.GREEN);    // proves the element type survived
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testEnumMap() {
    EnumMap<TestEnum, String> map = new EnumMap<>(TestEnum.class);
    map.put(TestEnum.RED, "red");
    map.put(TestEnum.BLUE, null);
    TripType<EnumMap> type = TripFactory.getInstance().getType(map.getClass());
    EnumMap tunneledMap = tunnel((EnumMap) map, type);
    assertEquals(tunneledMap.get(TestEnum.RED), "red");

    EnumMap<TestEnum, String> empty = new EnumMap<>(TestEnum.class);
    EnumMap tunneledEmpty = tunnel((EnumMap) empty, type);
    assertTrue(tunneledEmpty.isEmpty());
    tunneledEmpty.put(TestEnum.GREEN, "green");   // proves the key type survived
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testImmutableCollections() {
    List<String> list = List.of("one", "two", "tree");
    TripType<Collection> type = TripFactory.getInstance().getType(list.getClass());
    assertTrue(type instanceof ImmutableCollectionTripType);
    Collection<String> tunneledList = tunnel(list, type);
    assertNotSame(tunneledList, list);
    assertSame(tunneledList, tunnel(list, type));

    Set<String> set = Set.of("one", "two", "tree");
    type = TripFactory.getInstance().getType(set.getClass());
    assertTrue(type instanceof ImmutableCollectionTripType);
    Collection<String> tunneledSet = tunnel(set, type);
    assertNotSame(tunneledSet, set);
    assertSame(tunneledSet, tunnel(set, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testMixedCollections() {
    List list = new ArrayList<>();
    list.add(new ArrayList<>());
    list.add("one");
    list.add(1);
    list.add(new TestClass());
    list.add(new TestSubClass());
    list.add(new TestRecord("kerrin", 29, false, null, null, new Class[] {TestClass.class, Integer.TYPE}));
    TestInvocationHandler ih = new TestInvocationHandler(new TestProxyTarget());
    TestProxyInterface proxy = (TestProxyInterface) Proxy.newProxyInstance(
      ih.getClass().getClassLoader(), new Class[]{TestProxyInterface.class}, ih);
    proxy.setName("test");
    list.add(proxy);
    list.add(String.class);
    list.add(new BMoney(10000, 2));
    list.add(TestEnum.RED);
    list.add(new ArrayList<>(List.of("one", "two", "three"))); // immutable tests see below
    list.add(new Timestamp(true));

    TripType<List> type = TripFactory.getInstance().getType(list.getClass());
    List tunneledList = tunnel(list, type);
    assertNotSame(tunneledList, list);
    assertSame(tunneledList, tunnel(list, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testCollectionWithComponentProvider() {
    TrackedList<String> list = new TrackedArrayList<>();
    list.add("one");
    list.add(null);
    list.add("two");
    list.add("three");
    list.add("four");
    list.add("five");
    list.remove("three");

    TripType<TrackedList> type = TripFactory.getInstance().getType(list.getClass());
    TrackedList tunneledList = tunnel(list, type);
    assertNotSame(tunneledList, list);
    assertEquals(tunneledList.getRemovedObjects().size(), 1);
    assertEquals(tunneledList.getRemovedObjects().iterator().next(), "three");
    assertSame(tunneledList, tunnel(list, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testCopyOnWriteCollection() {
    ImmutableArrayList<String> immutableList = new ImmutableArrayList<>();
    immutableList.add("one");
    immutableList.add(null);
    immutableList.add("two");
    immutableList.add("three");
    immutableList.add("four");
    immutableList.add("five");
    immutableList.setImmutable(true);
    CopyOnWriteCollection<String> list = new CopyOnWriteList<>(immutableList);
    list.remove("three");
    TripType<CopyOnWriteCollection> type = TripFactory.getInstance().getType(list.getClass());
    CopyOnWriteCollection tunneledList = tunnel(list, type);
    assertNotSame(tunneledList, list);
    assertEquals(tunneledList.size(), 5);
    assertSame(tunneledList, tunnel(list, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testTreeSet() {
    TreeSet<String> set = new TreeSet<>();
    set.add("one");
    set.add("two");
    set.add("tree");
    assertNull(set.comparator());
    TripType<TreeSet> type = TripFactory.getInstance().getType(set.getClass());
    TreeSet<String> tunneledSet = tunnel(set, type);
    assertNotSame(tunneledSet, set);
    assertNull(tunnel(null, type));

    // this does NOT work, since the comparator is a non-static inner
    // class of TripTypeTest, i.e., the comparator's constructor
    // expects a TripTypeTest instance as an argument.
//    set = new TreeSet<>(new Comparator<>() {
//      @Override
//      public int compare(String o1, String o2) {
//        return o1.compareTo(o2);
//      }
//    });
    set = new TreeSet<>(STRING_COMPARATOR);   // this works!
    set.add("four");
    set.add("five");
    set.add("six");
    assertNotNull(set.comparator());
    tunneledSet = tunnel(set, type);
    assertNotNull(tunneledSet.comparator());
  }

  private static final Comparator<String> STRING_COMPARATOR = new Comparator<>() {
    @Override
    public int compare(String o1, String o2) {
      return o1.compareTo(o2);
    }
  };

  // this would work as well!
//  private static class StringComparator implements Comparator<String> {
//    @Override
//    public int compare(String o1, String o2) {
//      return o1.compareTo(o2);
//    }
//  }
//
//  private static final StringComparator STRING_COMPARATOR = new StringComparator();

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testMap() {
    Map<String, Integer> map = new HashMap<>();
    map.put("one", 1);
    map.put("two", 2);
    map.put("three", 3);
    TripType<Map> type = TripFactory.getInstance().getType(map.getClass());
    Map<String, Integer> tunneledMap = tunnel(map, type);
    assertNotSame(tunneledMap, map);
    assertSame(tunnel(map, type), tunneledMap);
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testMixedMap() {
    Map map = new HashMap();
    map.put(0, new HashMap<>());
    map.put(1, "one");
    map.put(2, 1);
    map.put(3, new TestClass());
    map.put(4, new TestSubClass());
    map.put(5, new TestRecord("kerrin", 29, false, null, null, new Class[] {TestClass.class, Integer.TYPE}));
    TestInvocationHandler ih = new TestInvocationHandler(new TestProxyTarget());
    TestProxyInterface proxy = (TestProxyInterface) Proxy.newProxyInstance(
      ih.getClass().getClassLoader(), new Class[]{TestProxyInterface.class}, ih);
    proxy.setName("test");
    map.put(6, proxy);
    map.put(7, String.class);
    map.put(8, new BMoney(10000, 2));
    map.put(9, TestEnum.RED);
    map.put(10, new ArrayList<>(List.of("one", "two", "three"))); // immutable tests see below
    map.put(11, new Timestamp(true));

    TripType<Map> type = TripFactory.getInstance().getType(map.getClass());
    Map tunneledMap = tunnel(map, type);
    assertNotSame(tunneledMap, map);
    assertSame(tunneledMap, tunnel(map, type));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testTreeMap() {
    TreeMap<String, Integer> map = new TreeMap<>(STRING_COMPARATOR);
    map.put("one", 1);
    map.put("two", 2);
    map.put("three", 3);
    assertNotNull(map.comparator());
    TripType<TreeMap> type = TripFactory.getInstance().getType(map.getClass());
    TreeMap<String, Integer> tunneledMap = tunnel(map, type);
    assertNotSame(tunneledMap, map);
    assertSame(tunnel(map, type), tunneledMap);
    assertNotNull(tunneledMap.comparator());
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testImmutableMap() {
    Map<String, Integer> map = Map.of("one", 1, "two", 2, "three", 3);
    TripType<Map> type = TripFactory.getInstance().getType(map.getClass());
    Map<String, Integer> tunneledMap = tunnel(map, type);
    assertNotSame(tunneledMap, map);
    assertSame(tunnel(map, type), tunneledMap);
  }

  /**
   * {@link Properties} is a plain JDK map: all its fields are declared in {@code java.util},
   * so none of them is serializable (see {@code MapTripType#isFieldSerializable}) and the
   * entries are all that travels.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testProperties() {
    Properties properties = new Properties();
    properties.setProperty("user", "hugo");
    properties.setProperty("empty", "");
    properties.put("count", 42);    // Hashtable accepts non-string values

    TripType<Properties> type = TripFactory.getInstance().getType(Properties.class);
    Properties tunneled = tunnel(properties, type);
    assertNotSame(tunneled, properties);
    assertEquals(tunneled.getProperty("user"), "hugo");
    assertEquals(tunneled.getProperty("empty"), "");
    assertEquals(tunneled.get("count"), 42);
    assertEquals(tunneled.stringPropertyNames(), Set.of("user", "empty"));
    assertSame(tunnel(properties, type), tunneled);

    assertTrue(tunnel(new Properties(), type).isEmpty());
    assertNull(tunnel(null, type));

    // known limitation: the defaults live in a java.util field and are therefore not transported.
    // Use ChainedProperties instead, see testChainedProperties().
    Properties defaults = new Properties();
    defaults.setProperty("inherited", "value");
    Properties withDefaults = new Properties(defaults);
    withDefaults.setProperty("own", "value");
    assertEquals(withDefaults.getProperty("inherited"), "value");
    Properties tunneledWithDefaults = tunnel(withDefaults, type);
    assertEquals(tunneledWithDefaults.getProperty("own"), "value");
    assertNull(tunneledWithDefaults.getProperty("inherited"));
  }

  /**
   * {@link ChainedProperties} exposes the defaults of a {@link Properties} through a
   * {@code @CanonicalGetter} and takes them back through a {@code @CanonicalConstructor}.
   * The map is thus created from its components, which precede the entries in the stream.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testChainedProperties() {
    ChainedProperties root = new ChainedProperties();
    root.setProperty("level", "root");
    root.setProperty("fromRoot", "yes");
    ChainedProperties middle = new ChainedProperties(root);
    middle.setProperty("level", "middle");
    middle.setProperty("fromMiddle", "yes");
    ChainedProperties leaf = new ChainedProperties(middle);
    leaf.setProperty("level", "leaf");
    leaf.put("count", 42);    // Hashtable accepts non-string values

    TripType<ChainedProperties> type = TripFactory.getInstance().getType(ChainedProperties.class);
    ChainedProperties tunneled = tunnel(leaf, type);
    assertNotSame(tunneled, leaf);
    // the whole chain arrived
    assertEquals(tunneled.getProperty("level"), "leaf");
    assertEquals(tunneled.getProperty("fromMiddle"), "yes");
    assertEquals(tunneled.getProperty("fromRoot"), "yes");
    assertEquals(tunneled.get("count"), 42);
    assertEquals(tunneled.stringPropertyNames(), Set.of("level", "fromMiddle", "fromRoot"));
    // ... including its structure
    assertNotNull(tunneled.getDefaults());
    assertEquals(tunneled.getDefaults().getProperty("level"), "middle");
    assertNotNull(tunneled.getDefaults().getDefaults());
    assertEquals(tunneled.getDefaults().getDefaults().getProperty("level"), "root");
    assertNull(tunneled.getDefaults().getDefaults().getDefaults());
    assertSame(tunnel(leaf, type), tunneled);

    // without defaults
    ChainedProperties plain = new ChainedProperties();
    plain.setProperty("own", "value");
    ChainedProperties tunneledPlain = tunnel(plain, type);
    assertNull(tunneledPlain.getDefaults());
    assertEquals(tunneledPlain.getProperty("own"), "value");
    assertTrue(tunnel(new ChainedProperties(), type).isEmpty());
    assertNull(tunnel(null, type));

    // shared defaults must arrive as one object, not as two copies
    ChainedProperties shared = new ChainedProperties();
    shared.setProperty("shared", "value");
    List<ChainedProperties> chains = new ArrayList<>();
    chains.add(new ChainedProperties(shared));
    chains.add(new ChainedProperties(shared));
    TripType<List> listType = TripFactory.getInstance().getType(chains.getClass());
    List<ChainedProperties> tunneledChains = tunnel(chains, (TripType) listType);
    assertEquals(tunneledChains.getFirst().getProperty("shared"), "value");
    assertSame(tunneledChains.getFirst().getDefaults(), tunneledChains.get(1).getDefaults());
  }

  /**
   * A collection providing neither a sized nor a no-args constructor, but a canonical one.<br>
   * {@code name} becomes a component of its own, {@code capacity} is transient and is
   * transported by its {@code @CanonicalGetter}.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testCanonicalCollection() {
    CanonicalList<Object> list = new CanonicalList<>("the name", 4711);
    list.add("one");
    list.add(null);
    list.add("two");
    list.add(list);   // the element back-references the collection itself

    TripType<CanonicalList> type = TripFactory.getInstance().getType(CanonicalList.class);
    CanonicalList<Object> tunneled = tunnelNoEquals(list, (TripType) type);
    assertNotSame(tunneled, list);
    assertEquals(tunneled.getName(), "the name");
    assertEquals(tunneled.getCapacity(), 4711);
    assertEquals(tunneled.size(), 4);
    assertEquals(tunneled.get(0), "one");
    assertNull(tunneled.get(1));
    assertEquals(tunneled.get(2), "two");
    assertSame(tunneled.get(3), tunneled);

    // the canonical defaults must survive as well
    CanonicalList<Object> empty = new CanonicalList<>(null, 0);
    CanonicalList<Object> tunneledEmpty = tunnelNoEquals(empty, (TripType) type);
    assertNull(tunneledEmpty.getName());
    assertEquals(tunneledEmpty.getCapacity(), 0);
    assertTrue(tunneledEmpty.isEmpty());
  }

  /**
   * {@link EncryptedProperties} is a JDK map with fields of its own.<br>
   * The entries are serialized by the {@link org.tentackle.trip.types.MapTripType}, the fields
   * as its components. Notice that {@link java.util.Hashtable#equals} compares the entries only,
   * so the fields must be asserted explicitly.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testEncryptedProperties() {
    EncryptedProperties properties = new EncryptedProperties();
    properties.setName("backend.properties");
    properties.setProperty("user", "hugo");
    // no @Service(Cryptor.class) configured in this module, hence the encrypted value is set verbatim
    properties.setProperty("password", "~c29tZSBzZWNyZXQ=");
    properties.setProperty("escaped", "\\~not encrypted");
    properties.setProperty("MixedCase", "value");

    TripType<EncryptedProperties> type = TripFactory.getInstance().getType(EncryptedProperties.class);
    EncryptedProperties tunneled = tunnel(properties, type);
    assertNotSame(tunneled, properties);
    assertEquals(tunneled.getName(), "backend.properties");
    assertNull(tunneled.getKeyPrefix());
    // the encrypted value must arrive untouched, i.e. neither decrypted nor re-encrypted
    assertEquals(tunneled.getPropertyBlunt("password"), "~c29tZSBzZWNyZXQ=");
    assertEquals(tunneled.getProperty("user"), "hugo");
    assertEquals(tunneled.getProperty("escaped"), "~not encrypted");
    // the key case map is transient and must be rebuilt lazily on the receiving side
    assertEquals(tunneled.getPropertyIgnoreCase("mixedcase"), "value");
    assertSame(tunnel(properties, type), tunneled);

    // the key prefix must survive as well and still translate the keys
    EncryptedProperties prefixed = new EncryptedProperties();
    prefixed.setKeyPrefix("org.tentackle.");
    prefixed.setProperty("user", "hugo");
    assertNotNull(prefixed.getPropertyBlunt("user"));
    EncryptedProperties tunneledPrefixed = tunnel(prefixed, type);
    assertEquals(tunneledPrefixed.getKeyPrefix(), "org.tentackle.");
    assertEquals(tunneledPrefixed.getProperty("user"), "hugo");
    assertEquals(tunneledPrefixed.get("org.tentackle.user"), "hugo");
  }

  @Test
  void testException() {
    try {
      new TestExceptionGenerator().createWrappedNPE();
      fail("shouldn't work!");
    }
    catch (RuntimeException exception) {
      TripType<RuntimeException> type = TripFactory.getInstance().getType(exception.getClass());
      RuntimeException tunneledException = tunnelPhenotypic(exception, type);
      assertNotSame(tunneledException, exception);
      assertEquals(tunneledException.getStackTrace(), exception.getStackTrace());
      assertEquals(tunneledException.getCause().getStackTrace(), exception.getCause().getStackTrace());
    }
  }

  @Test
  void testThrowableWithoutMessageCauseConstructor() {
    // NullPointerException has neither a (String, Throwable) nor a (Throwable) constructor;
    // the message must survive nevertheless, and the cause must be applied via initCause()
    NullPointerException npe = new NullPointerException("npe");
    npe.initCause(new IllegalStateException("inner"));
    TripType<NullPointerException> tripType = TripFactory.getInstance().getType(NullPointerException.class);
    NullPointerException tunnelled = tunnelPhenotypic(npe, tripType);
    assertEquals(tunnelled.getMessage(), "npe");
    assertNotNull(tunnelled.getCause());
    assertEquals(tunnelled.getCause().getMessage(), "inner");
  }

  @Test
  void testSuppressedExceptionsDropped() {
    // suppressed exceptions are deliberately not serialized (see ThrowableComponentProvider)
    IllegalStateException exception = new IllegalStateException("boom", new NullPointerException("npe"));
    exception.addSuppressed(new RuntimeException("suppressed"));
    TripType<IllegalStateException> tripType = TripFactory.getInstance().getType(IllegalStateException.class);
    IllegalStateException tunnelled = tunnelPhenotypic(exception, tripType);
    assertEquals(tunnelled.getMessage(), "boom");
    assertNotNull(tunnelled.getCause());
    assertEquals(tunnelled.getCause().getMessage(), "npe");
    assertEquals(tunnelled.getSuppressed().length, 0);
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void testLargeReferenceTables() {
    // more than 65535 distinct referenced objects: exercises the 2- and 3-byte
    // reference id encodings (REFERENCE2/REFERENCE3) on both stream ends
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < 70000; i++) {
      TestClass obj = new TestClass();
      obj.name = "obj" + i;
      obj.age = i;
      list.add(obj);
    }
    list.addAll(new ArrayList<>(list));   // same instances again -> written as references
    TripType<List> type = TripFactory.getInstance().getType(list.getClass());
    List tunneled = tunnel((List) list, type);
    assertSame(tunneled.get(0), tunneled.get(70000));
    assertSame(tunneled.get(300), tunneled.get(70300));
    assertSame(tunneled.get(69999), tunneled.get(139999));
  }

  @Test
  void testJOS() {
    Object ugly = new TestJOSClass();
    TripType<Object> type = TripFactory.getInstance().getType(ugly.getClass());
    Object tunneledUgly = tunnel(ugly, type);
    assertNotSame(tunneledUgly, ugly);
    assertSame(tunneledUgly, tunnel(ugly, type));

    TripType<TestClass> tripType = TripFactory.getInstance().getType(TestClass.class);
    TestClass obj = new TestClass();
    obj.someObject = ugly;
    TestClass tunnelled = tunnel(obj, tripType);
    assertSame(tunnelled.someObject, tunnel(obj, tripType).someObject);
  }

  @Test
  void testNoNoArgs() {
    NoNoArgsInstantiatable object = new NoNoArgsInstantiatable("test", 40);
    TripType<NoNoArgsInstantiatable> tripType = TripFactory.getInstance().getType(object.getClass());
    NoNoArgsInstantiatable tunneled = tunnel(object, tripType);
    assertNotSame(tunneled, object);
    assertSame(tunneled, tunnel(object, tripType));
  }


  private class InnerClass implements Runnable, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String text;

    private InnerClass() {
      this.text = "test";
    }

    @Override
    public String toString() {
      return text;
    }

    @Override
    public void run() {
      System.out.println(text);
    }
  }

  @Test
  void testInnerClassViaJOS() {
    TestJOSClass obj = new TestJOSClass();
    obj.someObject = new InnerClass();
    TripType<TestJOSClass> nestedType = TripFactory.getInstance().getType(obj.getClass());
    TestJOSClass tunnelled = tunnelNoEquals(obj, nestedType);
    assertTrue(tunnelled.someObject instanceof InnerClass);
    assertEquals(tunnelled.someObject.toString(), obj.someObject.toString());
  }

  @Test
  void testLambda() {
    Supplier<String> lambda = (Supplier<String> & Serializable) () -> "some string";
    TripType<TestClass> tripType = TripFactory.getInstance().getType(TestClass.class);
    TestClass obj = new TestClass();
    obj.someObject = lambda;
    TestClass tunnelled = tunnelNoEquals(obj, tripType);
    assertEquals(lambda.get(), ((Supplier<?>) tunnelled.someObject).get());
  }

  @Test
  void testInnerClassCanonical() {
    TripType<TestClass> tripType = TripFactory.getInstance().getType(TestClass.class);
    TestClass obj = new TestClass();    // new reference!
    obj.someObject = new InnerClassCanonical("test");
    TestClass tunnelled = tunnelNoEquals(obj, tripType);
    assertTrue(tunnelled.someObject instanceof Serializable);
  }

  private class InnerClassCanonical implements Runnable, Serializable {
    // must be Serializable even if serialized via TRIP since otherwise
    // the field "this$0" won't be created by the compiler!
    @Serial
    private static final long serialVersionUID = 1L;

    @CanonicalField(ordinal = 0)
    private final String text;

    @CanonicalConstructor
    private InnerClassCanonical(String text) {
      this.text = text;
    }

    @Override
    public void run() {
      System.out.println("hello world!");
    }
  }

  @Test
  void testPrepareSerialization() {
    TripType<PreparedSerializableClass> tripType = TripFactory.getInstance().getType(PreparedSerializableClass.class);
    PreparedSerializableClass obj = new PreparedSerializableClass();
    PreparedSerializableClass tunnelled = tunnel(obj, tripType);
    assertEquals(tunnelled.getName(), "Test");
  }

  @Test
  void testValidateSerialization() {
    TripType<ValidateSerializableClass> tripType = TripFactory.getInstance().getType(ValidateSerializableClass.class);
    ValidateSerializableClass obj = new ValidateSerializableClass();
    ValidateSerializableClass tunnelled = tunnel(obj, tripType);
    assertEquals(tunnelled.getResult(), "validated");
  }

  @Test
  void testTripInstantiationException() {
    TripInstantiationException exception =
      new TripInstantiationException(TestClass.class, "cannot instantiate", new IllegalStateException("inner"));
    TripType<TripInstantiationException> tripType = TripFactory.getInstance().getType(TripInstantiationException.class);
    TripInstantiationException tunnelled = tunnelPhenotypic(exception, tripType);
    assertEquals(tunnelled.getClazz(), TestClass.class);
    assertEquals(tunnelled.getMessage(), "cannot instantiate");
    assertNotNull(tunnelled.getCause());
    assertEquals(tunnelled.getCause().getMessage(), "inner");
  }

  @Test
  void testTripNoSuchDelegateException() {
    UUID delegateId = UUID.randomUUID();
    TripNoSuchDelegateException exception = new TripNoSuchDelegateException(delegateId);
    TripType<TripNoSuchDelegateException> tripType = TripFactory.getInstance().getType(TripNoSuchDelegateException.class);
    TripNoSuchDelegateException tunnelled = tunnelPhenotypic(exception, tripType);
    assertEquals(tunnelled.getDelegateId(), delegateId);
    assertEquals(tunnelled.getMessage(), exception.getMessage());
  }

  @Test
  void testTripStreamClosedException() {
    TripStreamClosedException exception = new TripStreamClosedException("stream closed", new IOException("io"));
    TripType<TripStreamClosedException> tripType = TripFactory.getInstance().getType(TripStreamClosedException.class);
    TripStreamClosedException tunnelled = tunnelPhenotypic(exception, tripType);
    assertEquals(tunnelled.getMessage(), "stream closed");
    assertNotNull(tunnelled.getCause());
    assertEquals(tunnelled.getCause().getMessage(), "io");
  }

  @Test
  void testTripError() {
    TripError error = new TripError(DefaultOpCode.NO_SUCH_DELEGATE, "no delegate");
    TripType<TripError> tripType = TripFactory.getInstance().getType(TripError.class);
    TripError tunnelled = tunnelPhenotypic(error, tripType);
    assertEquals(tunnelled.getOpCode(), DefaultOpCode.NO_SUCH_DELEGATE);
    assertTrue(tunnelled.isNoSuchDelegate());
    assertEquals(tunnelled.getMessage(), error.getMessage());
  }


  private <T> T tunnel(T value, TripType<T> tripType) {
    T tunneledValue = tunnelValue(value, tripType);
    assertEquals(tunneledValue, value);   // assertEquals is aware of arrays!
    return tunneledValue;
  }

  private <T> T tunnelNoEquals(T value, TripType<T> tripType) {
    return tunnelValue(value, tripType);
  }

  private <T> T tunnelPhenotypic(T value, TripType<T> tripType) {
    T tunneledValue = tunnelValue(value, tripType);
    assertEquals(tunneledValue.toString(), value.toString());
    return tunneledValue;
  }

  private <T> void tunnelNullPrimitive(TripType<T> tripType) {
    try {
      tunnelValue(null, tripType);
      fail("shouldn't work!");
    }
    catch (TripRuntimeException tx) {
      assertTrue(tx.getMessage().contains(" primitive "));
    }
  }

  private void tunnel(String value) {
    TripType<String> tripType = TripFactory.getInstance().getType(String.class);
    assertEquals(tunnelValue(value, tripType), value);
  }

  /**
   * Tunnels a value written by one trip type and read back by another, which allows simulating
   * a sender whose class definition differs from the receiver's.
   *
   * @param value the value to write
   * @param writer the trip type to write with
   * @param reader the trip type to read with
   * @return the value read back
   * @param <T> the type
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <T> T tunnelValue(T value, TripType<T> writer, TripType<T> reader) {
    try {
      // each case is a message of its own, or the value would be sent as a back-reference
      transceiver.clear();
      writer.write(value, transceiver.getStream());
      transceiver.echo();
      OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
      return reader.read(transceiver.getStream(), opCode);
    }
    finally {
      transceiver.reset();
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <T> T tunnelValue(T value, TripType<T> tripType) {
    try {
      tripType.write(value, transceiver.getStream());
      transceiver.echo();
      Deserializer deserializer = transceiver.getStream().getDeserializer();
      OpCode opCode = deserializer.deserializeOpCode();
      if (tripType instanceof PredefinedArray tripArrayType) {
        return (T) tripArrayType.readWithType(transceiver.getStream(), opCode);
      }
      return tripType.read(transceiver.getStream(), opCode);
    }
    finally {
      transceiver.reset();
    }
  }

}
