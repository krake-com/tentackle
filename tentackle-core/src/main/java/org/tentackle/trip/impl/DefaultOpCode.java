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

package org.tentackle.trip.impl;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArrayType;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.types.BigDecimalTripType;
import org.tentackle.trip.types.BinaryTripType;
import org.tentackle.trip.types.BooleanTripType;
import org.tentackle.trip.types.ByteTripType;
import org.tentackle.trip.types.CharacterTripType;
import org.tentackle.trip.types.ClassTripType;
import org.tentackle.trip.types.DateTripType;
import org.tentackle.trip.types.DoubleTripType;
import org.tentackle.trip.types.FloatTripType;
import org.tentackle.trip.types.I18NTextTripType;
import org.tentackle.trip.types.InstantTripType;
import org.tentackle.trip.types.IntegerTripType;
import org.tentackle.trip.types.JOSTripType;
import org.tentackle.trip.types.LambdaTripType;
import org.tentackle.trip.types.LocalDateTimeTripType;
import org.tentackle.trip.types.LocalDateTripType;
import org.tentackle.trip.types.LocalTimeTripType;
import org.tentackle.trip.types.LocaleTripType;
import org.tentackle.trip.types.LongTripType;
import org.tentackle.trip.types.OffsetDateTimeTripType;
import org.tentackle.trip.types.OffsetTimeTripType;
import org.tentackle.trip.types.ShortTripType;
import org.tentackle.trip.types.StackTraceElementTripType;
import org.tentackle.trip.types.StringTripType;
import org.tentackle.trip.types.TimeTripType;
import org.tentackle.trip.types.TimeZoneTripType;
import org.tentackle.trip.types.TimestampTripType;
import org.tentackle.trip.types.UUIDTripType;
import org.tentackle.trip.types.ZonedDateTimeTripType;

/**
 * Predefined operation codes.
 */
public enum DefaultOpCode implements OpCode {

  /** null value. */
  NULL(0x00),


  /** boolean value. */
  BOOLEAN(0x01, OpCodeCategory.FIXED_TYPE, new BooleanTripType()),

  /** byte value. */
  BYTE(0x02, OpCodeCategory.FIXED_TYPE, new ByteTripType()),

  /** short value. */
  SHORT(0x03, OpCodeCategory.FIXED_TYPE, new ShortTripType()),

  /** small short value (fits in 1 byte). */
  SHORT1(0x04, OpCodeCategory.FIXED_TYPE, new ShortTripType()),

  /** integer value (4 bytes). */
  INTEGER(0x05, OpCodeCategory.FIXED_TYPE, new IntegerTripType()),

  /** small integer value (fits in 1 byte). */
  INTEGER1(0x06, OpCodeCategory.FIXED_TYPE, new IntegerTripType()),

  /** small integer value (fits in 2 bytes). */
  INTEGER2(0x07, OpCodeCategory.FIXED_TYPE, new IntegerTripType()),

  /** small integer value (fits in 3 bytes). */
  INTEGER3(0x08, OpCodeCategory.FIXED_TYPE, new IntegerTripType()),

  /** long value (8 bytes). */
  LONG(0x09, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 1 byte). */
  LONG1(0x0A, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 2 bytes). */
  LONG2(0x0B, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 3 bytes). */
  LONG3(0x0C, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 4 bytes). */
  LONG4(0x0D, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 5 bytes). */
  LONG5(0x0E, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 6 bytes). */
  LONG6(0x0F, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** small long value (fits in 7 bytes). */
  LONG7(0x10, OpCodeCategory.FIXED_TYPE, new LongTripType()),

  /** float value. */
  FLOAT(0x11, OpCodeCategory.FIXED_TYPE, new FloatTripType()),

  /** double value. */
  DOUBLE(0x12, OpCodeCategory.FIXED_TYPE, new DoubleTripType()),

  /** small double value (fits in 4 bytes). */
  DOUBLE4(0x13, OpCodeCategory.FIXED_TYPE, new DoubleTripType()),

  /** character value. */
  CHAR(0x14, OpCodeCategory.FIXED_TYPE, new CharacterTripType()),

  /** small character value (fits in 1 byte). */
  CHAR1(0x15, OpCodeCategory.FIXED_TYPE, new CharacterTripType()),

  /** String value. */
  STRING(0x16, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** short String (length &lt; 256). */
  STRING1(0x17, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** short String (length &lt; 65,536). */
  STRING2(0x18, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** referenced String value. */
  STRING_REF(0x19, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** referenced String (length &lt; 256). */
  STRING1_REF(0x1A, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** referenced String (length &lt; 65,536). */
  STRING2_REF(0x1B, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** empty String. */
  STRING_EMPTY(0x1C, OpCodeCategory.FIXED_TYPE, new StringTripType()),

  /** java.lang.StackTraceElement. */
  STACKTRACE_ELEMENT(0x1D, OpCodeCategory.FIXED_TYPE, new StackTraceElementTripType()),

  /** java.util.Date, java.sql.Date and org.tentackle.common.Date. */
  DATE(0x1E, OpCodeCategory.FIXED_TYPE, new DateTripType()),

  /** java.sql.Time and org.tentackle.common.Time. */
  TIME(0x1F, OpCodeCategory.FIXED_TYPE, new TimeTripType()),

  /** java.sql.Timestamp and org.tentackle.common.Timestamp. */
  TIMESTAMP(0x20, OpCodeCategory.FIXED_TYPE, new TimestampTripType()),

  /** java.math.BigDecimal, org.tentackle.common.BMoney and org.tentackle.common.DMoney. */
  BIGDECIMAL(0x21, OpCodeCategory.FIXED_TYPE, new BigDecimalTripType()),

  /** java.time.LocalDate. */
  LOCALDATE(0x22, OpCodeCategory.FIXED_TYPE, new LocalDateTripType()),

  /** java.time.LocalTime. */
  LOCALTIME(0x23, OpCodeCategory.FIXED_TYPE, new LocalTimeTripType()),

  /** java.time.LocalDateTime. */
  LOCALDATETIME(0x24, OpCodeCategory.FIXED_TYPE, new LocalDateTimeTripType()),

  /** java.time.ZonedDateTime. */
  ZONEDDATETIME(0x25, OpCodeCategory.FIXED_TYPE, new ZonedDateTimeTripType()),

  /** java.time.OffsetDateTime. */
  OFFSETDATETIME(0x26, OpCodeCategory.FIXED_TYPE, new OffsetDateTimeTripType()),

  /** java.time.OffsetTime. */
  OFFSETTIME(0x27, OpCodeCategory.FIXED_TYPE, new OffsetTimeTripType()),

  /** java.time.Instant. */
  INSTANT(0x28, OpCodeCategory.FIXED_TYPE, new InstantTripType()),

  /** java.util.UUID. */
  UUID(0x29, OpCodeCategory.FIXED_TYPE, new UUIDTripType()),

  /** binary data. */
  BINARY(0x2A, OpCodeCategory.FIXED_TYPE, new BinaryTripType()),

  /** org.tentackle.common.I18NText. */
  I18NTEXT(0x2B, OpCodeCategory.FIXED_TYPE, new I18NTextTripType()),

  /** java.util.Locale. */
  LOCALE(0x2C, OpCodeCategory.FIXED_TYPE, new LocaleTripType()),

  /** java.util.TimeZone. */
  TIMEZONE(0x2D, OpCodeCategory.FIXED_TYPE, new TimeZoneTripType()),


  /** any other object class. */
  OBJECT(0x38, OpCodeCategory.DICTIONARY_BASED),

  /** record class. */
  RECORD(0x39, OpCodeCategory.DICTIONARY_BASED),

  /** enum class. */
  ENUM(0x3A, OpCodeCategory.DICTIONARY_BASED),

  /** proxy class. */
  PROXY(0x3B, OpCodeCategory.DICTIONARY_BASED),

  /** any other clazz. */
  CLASS(0x3C, OpCodeCategory.FIXED_TYPE, new ClassTripType()),

  /** exception with stacktrace. */
  THROWABLE(0x3E, OpCodeCategory.DICTIONARY_BASED),


  /** n-dimensional array for all arrays w/o a predefined trip type. */
  ARRAY(0x40, OpCodeCategory.DICTIONARY_BASED),

  /** 1-dimensional array with a predefined trip type. */
  ARRAYN(0x41, OpCodeCategory.PREDEFINED_ARRAY),

  /** 1-dimensional small array (length &lt; 256) with a predefined trip type. */
  ARRAYN1(0x42, OpCodeCategory.PREDEFINED_ARRAY),

  /** 1-dimensional small array (length &lt; 65,536) with a predefined trip type. */
  ARRAYN2(0x43, OpCodeCategory.PREDEFINED_ARRAY),

  /** block of booleans within the array. */
  BOOLN(0x44),

  /** block of bytes within the array. */
  BYTEN(0x45),

  /** block of small characters within the array. */
  CHARN(0x46),

  /** block of small shorts within the array. */
  SHORTN(0x47),

  /** skip the block with default values within the array. */
  SKIP(0x48),

  /** small skip block (length &lt; 256). */
  SKIP1(0x49),

  /** small skip block (length &lt; 65536). */
  SKIP2(0x4A),


  /** collections (list, set). */
  COLLECTION(0x50, OpCodeCategory.DICTIONARY_BASED),

  /** maps. */
  MAP(0x51, OpCodeCategory.DICTIONARY_BASED),

  /** immutable collections (List.of(), Set.of()). */
  IMMUTABLE_COLLECTION(0x52, OpCodeCategory.DICTIONARY_BASED),

  /** immutable maps (Map.of()). */
  IMMUTABLE_MAP(0x53, OpCodeCategory.DICTIONARY_BASED),


  /** new object back reference (4 bytes). */
  REFERENCE(0x58, OpCodeCategory.REFERENCE),

  /** new small object back reference (fits in 1 byte). */
  REFERENCE1(0x59, OpCodeCategory.REFERENCE),

  /** new small object back reference (fits in 2 bytes). */
  REFERENCE2(0x5A, OpCodeCategory.REFERENCE),

  /** new small object back reference (fits in 3 bytes). */
  REFERENCE3(0x5B, OpCodeCategory.REFERENCE),

  /** known object back reference (4 bytes). */
  REFERENCE_SENT(0x5C, OpCodeCategory.REFERENCE),

  /** known small object back reference (fits in 1 byte). */
  REFERENCE1_SENT(0x5D, OpCodeCategory.REFERENCE),

  /** known small object back reference (fits in 2 bytes). */
  REFERENCE2_SENT(0x5E, OpCodeCategory.REFERENCE),

  /** known small object back reference (fits in 3 bytes). */
  REFERENCE3_SENT(0x5F, OpCodeCategory.REFERENCE),


  /** dictionary entry. */
  DICTIONARY(0x60),

  /** ordinal value (4 bytes). */
  DICTIONARY_ORDINAL(0x61, OpCodeCategory.DICTIONARY_ORDINAL),

  /** small ordinal value (fits in 1 byte). */
  DICTIONARY_ORDINAL1(0x62, OpCodeCategory.DICTIONARY_ORDINAL),

  /** small ordinal value (fits in 2 bytes). */
  DICTIONARY_ORDINAL2(0x63, OpCodeCategory.DICTIONARY_ORDINAL),

  /** small ordinal value (fits in 3 bytes). */
  DICTIONARY_ORDINAL3(0x64, OpCodeCategory.DICTIONARY_ORDINAL),


  /** ordinal value (4 bytes). */
  COMPONENT_ORDINAL(0x65, OpCodeCategory.COMPONENT_ORDINAL),

  /** small ordinal value (fits in 1 byte). */
  COMPONENT_ORDINAL1(0x66, OpCodeCategory.COMPONENT_ORDINAL),

  /** small ordinal value (fits in 2 bytes). */
  COMPONENT_ORDINAL2(0x67, OpCodeCategory.COMPONENT_ORDINAL),

  /** small ordinal value (fits in 3 bytes). */
  COMPONENT_ORDINAL3(0x68, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip one ordinal value. */
  COMPONENT_ORDINAL_SKIP1(0x69, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip two ordinal values. */
  COMPONENT_ORDINAL_SKIP2(0x6A, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip three ordinal values. */
  COMPONENT_ORDINAL_SKIP3(0x6B, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip four ordinal values. */
  COMPONENT_ORDINAL_SKIP4(0x6C, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip five ordinal values. */
  COMPONENT_ORDINAL_SKIP5(0x6D, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip six ordinal values. */
  COMPONENT_ORDINAL_SKIP6(0x6E, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip seven ordinal values. */
  COMPONENT_ORDINAL_SKIP7(0x6F, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip eight ordinal values. */
  COMPONENT_ORDINAL_SKIP8(0x70, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip nine ordinal values. */
  COMPONENT_ORDINAL_SKIP9(0x71, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip ten ordinal values. */
  COMPONENT_ORDINAL_SKIP10(0x72, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip eleven ordinal values. */
  COMPONENT_ORDINAL_SKIP11(0x73, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip twelve ordinal values. */
  COMPONENT_ORDINAL_SKIP12(0x74, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip thirteen ordinal values. */
  COMPONENT_ORDINAL_SKIP13(0x75, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip fourteen ordinal values. */
  COMPONENT_ORDINAL_SKIP14(0x76, OpCodeCategory.COMPONENT_ORDINAL),

  /** skip fifteen ordinal values. */
  COMPONENT_ORDINAL_SKIP15(0x77, OpCodeCategory.COMPONENT_ORDINAL),


  /** JOS wrapper. */
  JOS(0x78, OpCodeCategory.FIXED_TYPE, new JOSTripType()),

  /** lambda wrapper. */
  LAMBDA(0x79, OpCodeCategory.FIXED_TYPE, new LambdaTripType()),


  /** no such delegate UUID registered. */
  NO_SUCH_DELEGATE(0x7A, OpCodeCategory.ERROR),

  /** client side serialization error. */
  CLIENT_SERIALIZATION_ERROR(0x7B, OpCodeCategory.ERROR),

  /** client side deserialization error. */
  CLIENT_DESERIALIZATION_ERROR(0x7C, OpCodeCategory.ERROR),

  /** server side serialization error. */
  SERVER_SERIALIZATION_ERROR(0x7D, OpCodeCategory.ERROR),

  /** server side deserialization error. */
  SERVER_DESERIALIZATION_ERROR(0x7E, OpCodeCategory.ERROR),


  /** end marker. */
  END(0x7F);



  private final int code;
  private final OpCodeCategory category;
  private final TripType<?> fixedType;

  /**
   * Opcode enum.
   *
   * @param code the unique numeric code
   * @param category the optional category, null if not applicable
   * @param fixedType the optional fixed type, null if not applicable
   */
  DefaultOpCode(int code, OpCodeCategory category, TripType<?> fixedType) {
    this.code = code;
    this.category = category;
    this.fixedType = fixedType;
  }

  DefaultOpCode(int code, OpCodeCategory category) {
    this(code, category, null);
  }

  DefaultOpCode(int code) {
    this(code, null, null);
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public Integer toExternal() {
    return code;
  }

  @Override
  public boolean isReference() {
    return category == OpCodeCategory.REFERENCE;
  }

  @Override
  public boolean isComponentOrdinal() {
    return category == OpCodeCategory.COMPONENT_ORDINAL;
  }

  @Override
  public boolean isDictionaryOrdinal() {
    return category == OpCodeCategory.DICTIONARY_ORDINAL;
  }

  @Override
  public boolean isDictionaryBased() {
    return category == OpCodeCategory.DICTIONARY_BASED;
  }

  @Override
  public boolean isPredefinedArray() {
    return category == OpCodeCategory.PREDEFINED_ARRAY;
  }

  @Override
  public boolean isError() {
    return category == OpCodeCategory.ERROR;
  }

  @Override
  public TripType<?> getPredefinedArrayType(PredefinedArrayType arrayType) {
    if (!isPredefinedArray()) {
      throw new TripRuntimeException(this + " is not a predefined array");
    }
    return TripFactory.getInstance().getType(arrayType.getArrayClass());
  }

  @Override
  public boolean isFixedType() {
    return category == OpCodeCategory.FIXED_TYPE;
  }

  @Override
  public TripType<?> getFixedType() {
    if (fixedType == null) {
      throw new TripRuntimeException(this + " is not a fixed type");
    }
    return fixedType;
  }

  /**
   * Gets the OpCode from its byte value.
   *
   * @param code the op code
   * @return the OpCode, never null
   * @throws TripRuntimeException if invalid code
   */
  public static DefaultOpCode valueOf(int code) {
    DefaultOpCode opCode = CODES[code];
    if (opCode == null) {
      throw new TripRuntimeException("invalid op code: " + String.format("0x%02X", code));
    }
    return opCode;
  }



  private static final DefaultOpCode[] CODES = createArray();

  private static DefaultOpCode[] createArray() {
    DefaultOpCode[] codes = new DefaultOpCode[256];
    for (DefaultOpCode opCode : values()) {
      codes[opCode.code] = opCode;
    }
    return codes;
  }

}
