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

package org.tentackle.misc;

import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;


interface ObjectUtilitiesHolder {
  ObjectUtilities INSTANCE = ServiceFactory.createService(ObjectUtilities.class, ObjectUtilities.class);
}


/**
 * Object utility methods.
 * <p>
 * Mainly used by {@link CompoundValue} to convert strings to objects.<br>
 * Applications may extend and replace it via the &#64;{@link Service} annotation to support application-specific types.
 * See {@link #setConverter(Class, Function)}.
 */
@Service(ObjectUtilities.class)   // defaults to self
public class ObjectUtilities {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static ObjectUtilities getInstance() {
    return ObjectUtilitiesHolder.INSTANCE;
  }


  private final Map<Class<?>, Function<Object,?>> converterMap = new HashMap<>();


  /**
   * Creates the utilities with converters for the standard types:
   * strings, numbers, booleans, characters, and the date and time types of {@code java.util},
   * {@code java.sql}, {@code java.time} and tentackle.
   */
  public ObjectUtilities() {
    setConverter(String.class, this::toString);
    setConverter(DMoney.class, this::toDMoney);
    setConverter(BMoney.class, this::toBMoney);
    setConverter(BigDecimal.class, this::toBigDecimal);
    setConverter(BigInteger.class, this::toBigInteger);
    setConverter(Double.class, this::toDouble);
    setConverter(Double.TYPE, this::toDouble);
    setConverter(Float.class, this::toFloat);
    setConverter(Float.TYPE, this::toFloat);
    setConverter(Long.class, this::toLong);
    setConverter(Long.TYPE, this::toLong);
    setConverter(Integer.class, this::toInteger);
    setConverter(Integer.TYPE, this::toInteger);
    setConverter(Short.class, this::toShort);
    setConverter(Short.TYPE, this::toShort);
    setConverter(Byte.class, this::toByte);
    setConverter(Byte.TYPE, this::toByte);
    setConverter(Character.class, this::toCharacter);
    setConverter(Character.TYPE, this::toCharacter);
    setConverter(Boolean.class, this::toBoolean);
    setConverter(Boolean.TYPE, this::toBoolean);
    setConverter(java.sql.Timestamp.class, this::toSqlTimestamp);
    setConverter(org.tentackle.common.Timestamp.class, this::toTimestamp);
    setConverter(java.sql.Time.class, this::toSqlTime);
    setConverter(org.tentackle.common.Time.class, this::toTime);
    setConverter(java.util.Date.class, this::toUtilDate);
    setConverter(java.sql.Date.class, this::toSqlDate);
    setConverter(org.tentackle.common.Date.class, this::toDate);
    setConverter(LocalDateTime.class, this::toLocalDateTime);
    setConverter(OffsetDateTime.class, this::toOffsetDateTime);
    setConverter(ZonedDateTime.class, this::toZonedDateTime);
    setConverter(Instant.class, this::toInstant);
    setConverter(LocalDate.class, this::toLocalDate);
    setConverter(LocalTime.class, this::toLocalTime);
    setConverter(OffsetTime.class, this::toOffsetTime);
  }


  /**
   * Converts a value to the given type.
   * <p>
   * The conversion is tried in the following order:
   * <ol>
   *   <li>no conversion if the value is already of the given type</li>
   *   <li>a converter registered via {@link #setConverter(Class, Function)}</li>
   *   <li>enums by name. Lowercase camel case names are converted to uppercase with underscores,
   *   for example {@code oneTwo} to {@code ONE_TWO}</li>
   *   <li>classes by name</li>
   *   <li>a public static {@code valueOf} method of the given type, see {@link #valueOf(Class, Object)}</li>
   * </ol>
   *
   * @param <T> the type
   * @param clazz the type
   * @param value the value
   * @return the converted value, null if value is null
   * @throws IllegalArgumentException if the conversion is not supported or failed
   * @throws RuntimeException the exception thrown by the converter, for example a {@link java.time.DateTimeException}
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T> T convert(Class<T> clazz, Object value) {

    if (value == null) {
      return null;
    }

    Objects.requireNonNull(clazz, "clazz");

    final Object object;

    if (clazz.isAssignableFrom(value.getClass())) {
      object = value;   // no conversion necessary
    }
    else {
      Function<Object,T> converter = (Function<Object, T>) converterMap.get(clazz);
      if (converter != null) {
        object = converter.apply(value);
      }
      else if (clazz.isEnum()) {
        String name = value.toString();
        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
          // lowercase camelcase -> convert oneTwo to ONE_TWO
          name = StringHelper.camelCaseToDelimited(name, "_").toUpperCase(Locale.ROOT);
        }
        object = Enum.valueOf((Class<Enum>) clazz, name);
      }
      else if (clazz == Class.class) {
        try {
          String className = value.toString();
          object = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        }
        catch (ClassNotFoundException exception) {
          throw new IllegalArgumentException(exception);
        }
      }
      else {
        try {
          object = valueOf(clazz, value);
        }
        catch (IllegalArgumentException e) {
          // ignore and throw generic exception instead
          throw new IllegalArgumentException("unsupported conversion: " + value.getClass().getName() + " -> " + clazz.getName());
        }
      }
    }

    return (T) object;
  }


  /**
   * Invokes a matching valueOf method on the given class for a given value.<br>
   * The method must be public and static and accept the value's class or one of its superclasses.
   *
   * @param clazz the class providing the valueOf method
   * @param value the value to pass to the valueOf method, usually a string
   * @param <T> the returned type
   * @return the created value
   * @throws IllegalArgumentException if no matching method found
   * @throws TentackleRuntimeException if the invocation failed
   */
  @SuppressWarnings("unchecked")
  public <T> T valueOf(Class<T> clazz, Object value) {
    Class<?> valueType = value.getClass();
    Method valueOfMethod = null;
    while (valueType != null) {
      try {
        valueOfMethod = clazz.getDeclaredMethod("valueOf", valueType);
        if (Modifier.isStatic(valueOfMethod.getModifiers()) && Modifier.isPublic(valueOfMethod.getModifiers())) {
          break;
        }
        else {
          valueOfMethod = null;
        }
      }
      catch (NoSuchMethodException exception) {
        // try superclass
        valueType = valueType.getSuperclass();
      }
    }
    if (valueOfMethod == null) {
      throw new IllegalArgumentException("no matching public static " + clazz.getName() + "#valueOf method for " + value.getClass());
    }

    try {
      return (T) valueOfMethod.invoke(null, value);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new TentackleRuntimeException(clazz.getName() + "#valueOf failed for " + value.getClass(), e);
    }
  }


  /**
   * Checks whether a given number is zero.<br>
   * The scale of decimal numbers does not matter.
   *
   * @param number the number
   * @return true if the number is zero, false if not zero or null
   */
  public boolean isZero(Number number) {
    return switch (number) {
      case null -> false;
      case BMoney bMoney -> bMoney.isZero();
      case BigInteger _ -> number.equals(BigInteger.ZERO);
      case BigDecimal bigDecimal -> bigDecimal.signum() == 0;    // equals() would compare the scale as well
      case Double v -> v == 0.0d;
      case Float v -> v == 0.0f;
      default -> number.longValue() == 0;
    };
  }



  /**
   * Sets a converter for a given class.<br>
   * Replaces an existing converter for the same class.
   *
   * @param clazz the class to convert to
   * @param converter the converter function
   * @param <T> the destination type
   */
  protected <T> void setConverter(Class<T> clazz, Function<Object,T> converter) {
    converterMap.put(clazz, converter);
  }



  // ------------------ converters ----------------------------

  private String toString(Object value) {
    return value.toString();
  }

  private DMoney toDMoney(Object value) {
    if (value instanceof BigDecimal) {
      return new DMoney((BigDecimal) value);
    }
    if (value instanceof Number) {
      return new DMoney(((Number) value).doubleValue(), LocaleProvider.getInstance().getCurrency().getDefaultFractionDigits());
    }
    else {
      String str = value.toString();
      return new DMoney(str, FormatHelper.determineScale(str));
    }
  }

  private BMoney toBMoney(Object value) {
    if (value instanceof BigDecimal) {
      return new BMoney((BigDecimal) value);
    }
    if (value instanceof Number) {
      return new BMoney(((Number) value).doubleValue(), LocaleProvider.getInstance().getCurrency().getDefaultFractionDigits());
    }
    String str = value.toString();
    return new BMoney(str, FormatHelper.determineScale(str));
  }

  private BigDecimal toBigDecimal(Object value) {
    String str = value.toString();
    return new BMoney(str, FormatHelper.determineScale(str));   // BMoney is a BigDecimal
  }

  private BigInteger toBigInteger(Object value) {
    if (value instanceof BigDecimal) {
      return ((BigDecimal) value).toBigInteger();
    }
    if (value instanceof Number) {
      return BigInteger.valueOf(((Number) value).longValue());
    }
    return new BigInteger(value.toString());
  }

  private Double toDouble(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return Double.parseDouble(value.toString());
  }

  private Float toFloat(Object value) {
    if (value instanceof Number) {
      return  ((Number) value).floatValue();
    }
    return Float.parseFloat(value.toString());
  }

  private Long toLong(Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return Long.parseLong(value.toString());
  }

  private Integer toInteger(Object value) {
    if (value instanceof Number) {
      return  ((Number) value).intValue();
    }
    return Integer.parseInt(value.toString());
  }

  private Short toShort(Object value) {
    if (value instanceof Number) {
      return  ((Number) value).shortValue();
    }
    return Short.parseShort(value.toString());
  }

  private Byte toByte(Object value) {
    if (value instanceof Number) {
      return  ((Number) value).byteValue();
    }
    return Byte.parseByte(value.toString());
  }

  private Character toCharacter(Object value) {
    String s = value.toString();
    return s.isEmpty() ? 0 : s.charAt(0);
  }

  private Boolean toBoolean(Object value) {
    return Boolean.valueOf(value.toString());
  }

  private java.sql.Timestamp toSqlTimestamp(Object value) {
    if (value instanceof java.util.Date date) {
      return new java.sql.Timestamp(date.getTime());
    }
    if (value instanceof LocalDate localDate) {
      return java.sql.Timestamp.valueOf(LocalDateTime.of(localDate, LocalTime.of(0, 0)));
    }
    if (value instanceof LocalDateTime localDateTime) {
      return java.sql.Timestamp.valueOf(localDateTime);
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return java.sql.Timestamp.valueOf(offsetDateTime.toLocalDateTime());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return java.sql.Timestamp.valueOf(zonedDateTime.toLocalDateTime());
    }
    if (value instanceof Instant instant) {
      return new java.sql.Timestamp(instant.toEpochMilli());
    }
    return FormatHelper.parseTimestamp(value.toString());   // tentackle Timestamp extends sql Timestamp
  }

  private org.tentackle.common.Timestamp toTimestamp(Object value) {
    return new org.tentackle.common.Timestamp(toSqlTimestamp(value).getTime());
  }

  private java.sql.Time toSqlTime(Object value) {
    if (value instanceof java.util.Date date) {
      return new java.sql.Time(date.getTime());
    }
    if (value instanceof LocalTime localTime) {
      return java.sql.Time.valueOf(localTime);
    }
    if (value instanceof OffsetTime offsetTime) {
      return java.sql.Time.valueOf(offsetTime.toLocalTime());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return java.sql.Time.valueOf(localDateTime.toLocalTime());
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return java.sql.Time.valueOf(offsetDateTime.toLocalTime());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return java.sql.Time.valueOf(zonedDateTime.toLocalTime());
    }
    if (value instanceof Instant instant) {
      return new java.sql.Time(instant.toEpochMilli());
    }
    return FormatHelper.parseTime(value.toString());   // tentackle Time extends sql Time
  }

  private org.tentackle.common.Time toTime(Object value) {
    return new org.tentackle.common.Time(toSqlTime(value).getTime());
  }

  private java.util.Date toUtilDate(Object value) {
    return new java.util.Date(toSqlDate(value).getTime());
  }

  private java.sql.Date toSqlDate(Object value) {
    if (value instanceof java.util.Date date) {
      return new java.sql.Date(date.getTime());
    }
    if (value instanceof LocalDate localDate) {
      return java.sql.Date.valueOf(localDate);
    }
    if (value instanceof LocalDateTime localDateTime) {
      return java.sql.Date.valueOf(localDateTime.toLocalDate());
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return java.sql.Date.valueOf(offsetDateTime.toLocalDate());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return java.sql.Date.valueOf(zonedDateTime.toLocalDate());
    }
    if (value instanceof Instant instant) {
      return new java.sql.Date(instant.toEpochMilli());
    }
    return FormatHelper.parseDate(value.toString());   // tentackle Date extends sql Date
  }

  private org.tentackle.common.Date toDate(Object value) {
    return new org.tentackle.common.Date(toSqlDate(value).getTime());
  }

  private LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    if (value instanceof LocalDate localDate) {
      return LocalDateTime.of(localDate, LocalTime.of(0, 0));
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toLocalDateTime();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toLocalDateTime();
    }
    if (value instanceof Instant instant) {
      return new java.sql.Timestamp(instant.toEpochMilli()).toLocalDateTime();
    }
    return FormatHelper.parseLocalDateTime(value.toString());
  }

  private OffsetDateTime toOffsetDateTime(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().atOffset(OffsetDateTime.now().getOffset());
    }
    if (value instanceof LocalDate localDate) {
      return OffsetDateTime.of(localDate, LocalTime.of(0, 0), OffsetDateTime.now().getOffset());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.atOffset(OffsetDateTime.now().getOffset());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toOffsetDateTime();
    }
    if (value instanceof Instant instant) {
      return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    return FormatHelper.parseOffsetDateTime(value.toString());
  }

  private ZonedDateTime toZonedDateTime(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.systemDefault());
    }
    if (value instanceof LocalDate localDate) {
      return ZonedDateTime.of(localDate, LocalTime.of(0, 0),  ZoneId.systemDefault());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return ZonedDateTime.of(offsetDateTime.toLocalDateTime(), ZoneId.systemDefault());
    }
    if (value instanceof Instant instant) {
      return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    return FormatHelper.parseZonedDateTime(value.toString());
  }

  private Instant toInstant(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return Instant.ofEpochMilli(timestamp.getTime());
    }
    if (value instanceof LocalDate localDate) {
      return LocalDateTime.of(localDate, LocalTime.of(0,0)).toInstant(OffsetDateTime.now().getOffset());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toInstant(OffsetDateTime.now().getOffset());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toInstant();
    }
    if (value instanceof TemporalAccessor temporalAccessor) {
      return Instant.from(temporalAccessor);
    }
    return Instant.ofEpochMilli(FormatHelper.parseTimestamp(value.toString()).getTime());
  }

  private LocalDate toLocalDate(Object value) {
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate();
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate();
    }
    if (value instanceof java.util.Date date) {
      return new java.sql.Date(date.getTime()).toLocalDate();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toLocalDate();
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toLocalDate();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toLocalDate();
    }
    if (value instanceof Instant instant) {
      return new java.sql.Date(instant.toEpochMilli()).toLocalDate();
    }
    return FormatHelper.parseLocalDate(value.toString());
  }

  private LocalTime toLocalTime(Object value) {
    if (value instanceof java.sql.Time time) {
      return time.toLocalTime();
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalTime();
    }
    if (value instanceof java.util.Date date) {
      return new java.sql.Timestamp(date.getTime()).toLocalDateTime().toLocalTime();
    }
    if (value instanceof OffsetTime offsetTime) {
      return offsetTime.toLocalTime();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toLocalTime();
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toLocalTime();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toLocalTime();
    }
    if (value instanceof Instant instant) {
      return new java.sql.Time(instant.toEpochMilli()).toLocalTime();
    }
    return FormatHelper.parseLocalTime(value.toString());
  }

  private OffsetTime toOffsetTime(Object value) {
    if (value instanceof java.sql.Time time) {
      return time.toLocalTime().atOffset(OffsetTime.now().getOffset());
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalTime().atOffset(OffsetTime.now().getOffset());
    }
    if (value instanceof java.util.Date date) {
      return new java.sql.Timestamp(date.getTime()).toLocalDateTime().toLocalTime().atOffset(OffsetTime.now().getOffset());
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toLocalTime().atOffset(OffsetTime.now().getOffset());
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toOffsetTime();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toOffsetDateTime().toOffsetTime();
    }
    if (value instanceof Instant instant) {
      return new java.sql.Time(instant.toEpochMilli()).toLocalTime().atOffset(OffsetTime.now().getOffset());
    }
    return FormatHelper.parseOffsetTime(value.toString());
  }
}
