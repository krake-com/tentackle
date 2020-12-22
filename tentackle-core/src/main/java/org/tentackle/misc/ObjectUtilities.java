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
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;

interface ObjectUtilitiesHolder {
  ObjectUtilities INSTANCE = ServiceFactory.createService(ObjectUtilities.class);
}

/**
 * Object utility methods.
 * <p>
 * Mainly used by {@link CompoundValue}.<br>
 * Applications should extend and replace via {@link Service} annotation for application-specific types.
 */
@Service(ObjectUtilities.class)   // defaults to self
public class ObjectUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ObjectUtilities getInstance() {
    return ObjectUtilitiesHolder.INSTANCE;
  }


  private final Map<Class<?>, Function<Object,?>> converterMap = new HashMap<>();


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
    setConverter(LocalDate.class, this::toLocalDate);
    setConverter(LocalTime.class, this::toLocalTime);
  }


  /**
   * Converts value to the given type.
   * <p>
   * Throws {@link IllegalArgumentException} if unsupported type or conversion failed.
   *
   * @param <T> the type
   * @param clazz the type
   * @param value the value
   * @return the converted value, null if value is null
   */
  @SuppressWarnings("unchecked")
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
          name = StringHelper.camelCaseToDelimited(name, "_").toUpperCase();
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
   * Invokes a matching valueOf method on the given class for a given value.
   * <p>
   * Throws {@link IllegalArgumentException} if no matching method found.<br>
   * Throws {@link TentackleRuntimeException} if invocation failed.
   *
   * @param clazz the class providing the valueOf method
   * @param value the value to pass to the valueOf method, usually a string
   * @param <T> the returned type
   * @return the created value
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
   * Checks whether a given number is zero.
   *
   * @param number the number
   * @return true if number is zero
   */
  public boolean isZero(Number number) {
    boolean zero;
    if (number instanceof BMoney) {
      zero = ((BMoney) number).isZero();
    }
    else if (number instanceof BigInteger) {
      zero = number.equals(BigInteger.ZERO);
    }
    else if (number instanceof BigDecimal) {
      zero = number.equals(BigDecimal.ZERO);
    }
    else if (number instanceof Double) {
      zero = ((Double) number) == 0.0d;
    }
    else if (number instanceof Float) {
      zero = ((Float) number) == 0.0f;
    }
    else {
      zero = number.longValue() == 0;
    }
    return zero;
  }



  /**
   * Sets a converter for a given class.
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
      return new DMoney(((Number) value).doubleValue(), Currency.getInstance(LocaleProvider.getInstance().getLocale()).getDefaultFractionDigits());
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
      return new BMoney(((Number) value).doubleValue(), Currency.getInstance(LocaleProvider.getInstance().getLocale()).getDefaultFractionDigits());
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
    if (value instanceof java.util.Date) {
      return new java.sql.Timestamp(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalDateTime) {
      return java.sql.Timestamp.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
    }
    try {
      return FormatHelper.parseTimestamp(value.toString());   // tentackle Timestamp extends sql Timestamp
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private org.tentackle.common.Timestamp toTimestamp(Object value) {
    if (value instanceof java.util.Date) {
      return new org.tentackle.common.Timestamp(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalDateTime) {
      return new org.tentackle.common.Timestamp(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
    try {
      return FormatHelper.parseTimestamp(value.toString());
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private java.sql.Time toSqlTime(Object value) {
    if (value instanceof java.util.Date) {
      return new java.sql.Time(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalTime) {
      return new java.sql.Time(((LocalTime) value).toSecondOfDay() * 1000L - TimeZone.getDefault().getRawOffset());
    }
    try {
      return FormatHelper.parseTime(value.toString());   // tentackle Time extends sql Time
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private org.tentackle.common.Time toTime(Object value) {
    if (value instanceof java.util.Date) {
      return new org.tentackle.common.Time(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalTime) {
      return new org.tentackle.common.Time(((LocalTime) value).toSecondOfDay() * 1000L - TimeZone.getDefault().getRawOffset());
    }
    try {
      return FormatHelper.parseTime(value.toString());
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private java.util.Date toUtilDate(Object value) {
    if (value instanceof LocalDate) {
      return new java.util.Date(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
    try {
      return FormatHelper.parseDate(value.toString());   // tentackle Date extends sql Date which extends util Date
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private java.sql.Date toSqlDate(Object value) {
    if (value instanceof java.util.Date) {
      return new java.sql.Date(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalDate) {
      return new java.sql.Date(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
    try {
      return FormatHelper.parseDate(value.toString());   // tentackle Date extends sql Date
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private org.tentackle.common.Date toDate(Object value) {
    if (value instanceof java.util.Date) {
      return new org.tentackle.common.Date(((java.util.Date) value).getTime());
    }
    if (value instanceof LocalDate) {
      return new org.tentackle.common.Date(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
    try {
      return FormatHelper.parseDate(value.toString());
    }
    catch (ParseException px) {
      throw new IllegalArgumentException(px);
    }
  }

  private LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof java.sql.Timestamp) {
      return LocalDateTime.ofInstant(((java.sql.Timestamp) value).toInstant(), ZoneId.systemDefault());
    }
    return FormatHelper.parseLocalDateTime(value.toString());
  }

  private LocalDate toLocalDate(Object value) {
    if (value instanceof java.util.Date) {
      return LocalDate.ofInstant(Instant.ofEpochMilli(((java.util.Date) value).getTime()), ZoneId.systemDefault());
    }
    if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).toLocalDate();
    }
    return FormatHelper.parseLocalDate(value.toString());
  }

  private LocalTime toLocalTime(Object value) {
    if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).toLocalTime();
    }
    if (value instanceof java.sql.Time) {
      return LocalTime.ofInstant(Instant.ofEpochMilli(((java.sql.Time) value).getTime()), ZoneId.systemDefault());
    }
    return FormatHelper.parseLocalTime(value.toString());
  }
}
