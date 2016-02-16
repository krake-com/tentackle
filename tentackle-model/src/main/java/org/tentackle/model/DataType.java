/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.tentackle.common.BMoney;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.common.DMoney;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.sql.SqlType;



/**
 * The data type.
 *
 * @author harald
 */
public enum DataType {

  /** A {@link String}. */
  STRING("String", false, false, false, false, false, SqlType.VARCHAR),

  /** A {@link java.sql.Date}. */
  DATE("Date", false, true, false, true, false, SqlType.DATE),

  /** A {@link java.sql.Time}. */
  TIME("Time", false, true, false, true, false, SqlType.TIME),

  /** A {@link java.sql.Timestamp}. */
  TIMESTAMP("Timestamp", false, true, false, true, false, SqlType.TIMESTAMP),

  /** A {@link org.tentackle.common.Binary} (tentackle type). */
  BINARY("Binary", false, true, false, false, false, SqlType.LONGVARBINARY),

  /** A {@link BigDecimal}. */
  BIGDECIMAL("BigDecimal", false, false, true, false, false, SqlType.DECIMAL),

  /** A {@link org.tentackle.common.BMoney} (tentackle type). */
  BMONEY("BMoney", false, false, true, false, false, SqlType.DOUBLE, SqlType.INTEGER),

  /** A {@link org.tentackle.common.DMoney} (tentackle type). */
  DMONEY("DMoney", false, false, true, false, false, SqlType.DECIMAL, SqlType.INTEGER),

  /** A {@link Character}. */
  CHARACTER("Character", false, false, false, false, false, SqlType.CHAR),

  /** A char (primitive). */
  CHARACTER_PRIMITIVE("char", true, false, false, false, false, SqlType.CHAR),

  /** A {@link Boolean}. */
  BOOLEAN("Boolean", false, false, false, false, true, SqlType.BIT),

  /** A boolean (primitive). */
  BOOLEAN_PRIMITIVE("boolean", true, false, false, false, true, SqlType.BIT),

  /** A {@link Byte}. */
  BYTE("Byte", false, false, true, false, false, SqlType.TINYINT),

  /** A byte (primitive). */
  BYTE_PRIMITIVE("byte", true, false, false, false, false, SqlType.TINYINT),

  /** A {@link Short}. */
  SHORT("Short", false, false, true, false, false, SqlType.SMALLINT),

  /** A short (primitive). */
  SHORT_PRIMITIVE("short", true, false, true, false, false, SqlType.SMALLINT),

  /** A {@link Integer}. */
  INTEGER("Integer", false, false, true, false, false, SqlType.INTEGER),

  /** An int (primitive). */
  INTEGER_PRIMITIVE("int", true, false, true, false, false, SqlType.INTEGER),

  /** A {@link Long}. */
  LONG("Long", false, false, true, false, false, SqlType.BIGINT),

  /** A long (primitive). */
  LONG_PRIMITIVE("long", true, false, true, false, false, SqlType.BIGINT),

  /** A {@link Float}. */
  FLOAT("Float", false, false, true, false, false, SqlType.FLOAT),

  /** A float (primitive). */
  FLOAT_PRIMITIVE("float", true, false, true, false, false, SqlType.FLOAT),

  /** A {@link Double}. */
  DOUBLE("Double", false, false, true, false, false, SqlType.DOUBLE),

  /** A double (primitive). */
  DOUBLE_PRIMITIVE("double", true, false, true, false, false, SqlType.DOUBLE),

  /**
   * Application specific type.<br>
   * Such types are immutable because they just convert data between internal
   * and external representation. In most cases this is an enum.
   */
  APPLICATION("<application>", false, false, false, false, false, SqlType.JAVA_OBJECT);



  /**
   * Pair of SqlType and its name postfix.
   */
  public static class SqlTypeWithPostfix {

    private final SqlType sqlType;
    private final String postfix;

    /**
     * Creates an sqltype with postfix string.
     *
     * @param sqlType the sqltype
     * @param postfix the postfix
     */
    public SqlTypeWithPostfix(SqlType sqlType, String postfix) {
      this.sqlType = sqlType;
      this.postfix = postfix;
    }

    /**
     * Gets the sqltype.
     *
     * @return the sql type
     */
    public SqlType getSqlType() {
      return sqlType;
    }

    /**
     * Gets the postfix.
     *
     * @return the postfix
     */
    public String getPostfix() {
      return postfix;
    }
  }


  private final String javaType;                          // the java type
  private final boolean primitive;                        // true if java type is primitive
  private final boolean mutable;                          // true if mutable object
  private final boolean numeric;                          // true if numeric type
  private final boolean dateOrTime;                       // true if date and/or time
  private final boolean bool;                             // true if boolean or Boolean
  private final SqlType[] sqlTypes;                       // the sql type(s)
  private final SqlTypeWithPostfix[] sqlTypesWithPostfix; // ... and as pairs with their postfixes




  /**
   * Creates a type.
   *
   * @param javaType the java type
   * @param primitive true if java type is primitive
   * @param mutable true if type is a mutable java object
   * @param numeric true if numeric type
   * @param dateOrTime true if data and/or time
   * @param sqlTypes the sql type(s)
   */
  DataType(String javaType, boolean primitive, boolean mutable, boolean numeric, boolean dateOrTime, boolean bool, SqlType... sqlTypes) {
    this.javaType = javaType;
    this.primitive = primitive;
    this.mutable = mutable;
    this.numeric = numeric;
    this.dateOrTime = dateOrTime;
    this.bool = bool;
    this.sqlTypes = sqlTypes;
    this.sqlTypesWithPostfix = new SqlTypeWithPostfix[sqlTypes.length];
    for (int i=0; i < sqlTypes.length; i++) {
      sqlTypesWithPostfix[i] = new SqlTypeWithPostfix(sqlTypes[i], i == 0 ? "" : ("_" + (i+1)));
    }
  }


  /**
   * Returns whether type is a primitive.
   *
   * @return true if primitive
   */
  public boolean isPrimitive() {
    return primitive;
  }


  /**
   * Returns whether type is a mutable java object.<br>
   * Mutable objects may change their state.
   *
   * @return true if mutable
   */
  public boolean isMutable() {
    return mutable;
  }


  /**
   * Returns whether this is a numeric type.
   *
   * @return true if numeric
   */
  public boolean isNumeric() {
    return numeric;
  }


  /**
   * Returns whether this is a date and/or time type.
   *
   * @return true if date or time type
   */
  public boolean isDateOrTime() {
    return dateOrTime;
  }


  /**
   * Returns whether this is a boolean or Boolean type.
   *
   * @return true if bool
   */
  public boolean isBool() {
    return bool;
  }

  /**
   * Gets the sql type.<br>
   * Notice that a tentackle type may be mapped to
   * more than one column for multi-column types such as BMoney.
   *
   * @return the sql type(s)
   */
  public SqlType[] getSqlTypes() {
    return sqlTypes;
  }

  /**
   * Gets the SqlTypes along with their column name postfixes.
   * <p>
   * The first postfix is always the empty string. The second one
   * is "_2", third "_3" and so on. This is a tentackle convention for
   * multi-column types such as BMoney.
   *
   * @return the sq; types and their postfixes
   */
  public SqlTypeWithPostfix[] getSqlTypesWithPostfix() {
    return sqlTypesWithPostfix;
  }


  /**
   * True if this is a multicolumn type.
   *
   * @return true if multicolumn
   */
  public boolean isMultiColumn() {
    return sqlTypes.length > 1;
  }


  /**
   * Returns whether this is a numeric type with the scale stored in a second column.
   * <p>
   * BMoney and Dmoney for example.
   *
   * @return true if scale in second column
   */
  public boolean isScaleInSecondColumn() {
    return numeric && sqlTypes.length == 2 && sqlTypes[0].isFractional() && sqlTypes[1].isNumeric() && !sqlTypes[1].isFractional();
  }


  @Override
  public String toString() {
    return javaType;
  }


  /**
   * Converts to a Java primitive.
   *
   * @return the primitive type, null if no primitive for given type
   */
  public DataType toPrimitive() {
    switch (this) {
      case BOOLEAN:
        return BOOLEAN_PRIMITIVE;
      case CHARACTER:
        return CHARACTER_PRIMITIVE;
      case BYTE:
        return BYTE_PRIMITIVE;
      case SHORT:
        return SHORT_PRIMITIVE;
      case INTEGER:
        return INTEGER_PRIMITIVE;
      case LONG:
        return LONG_PRIMITIVE;
      case FLOAT:
        return FLOAT_PRIMITIVE;
      case DOUBLE:
        return DOUBLE_PRIMITIVE;
      default:
        return null;
    }
  }


  /**
   * Converts a primitive to a Java class.
   *
   * @return the non-primitive type, null if this is not a primitive
   */
  public DataType toNonPrimitive() {
    switch (this) {
      case BOOLEAN_PRIMITIVE:
        return BOOLEAN;
      case CHARACTER_PRIMITIVE:
        return CHARACTER;
      case BYTE_PRIMITIVE:
        return BYTE;
      case SHORT_PRIMITIVE:
        return SHORT;
      case INTEGER_PRIMITIVE:
        return INTEGER;
      case LONG_PRIMITIVE:
        return LONG;
      case FLOAT_PRIMITIVE:
        return FLOAT;
      case DOUBLE_PRIMITIVE:
        return DOUBLE;
      default:
        return null;
    }
  }


  /**
   * Parses a string and converts to the value of this type.
   * <p>
   * Notice: the method doesn't use any locale, so the results are
   * always the same regardless of the JVM's locale.
   *
   * @param str the source string
   * @return the value
   */
  public Object parse(String str) {
    DataType type = this;
    if (type.isPrimitive()) {
      type = this.toNonPrimitive();
    }
    try {
      switch (type) {
        case STRING:
          return BasicStringHelper.parseString(str);
        case DATE:
          return parseDate(str);
        case TIME:
          return parseTime(str);
        case TIMESTAMP:
          return parseTimestamp(str);
        case BIGDECIMAL:
          return new BigDecimal(BasicStringHelper.parseString(str));
        case BMONEY:
          return new BMoney(new BigDecimal(BasicStringHelper.parseString(str)));
        case DMONEY:
          return new DMoney(new BigDecimal(BasicStringHelper.parseString(str)));
        case CHARACTER:
          String cstr = BasicStringHelper.parseString(str);
          if (cstr.length() != 1) {
            throw new TentackleRuntimeException("character must be of length 1: " + cstr);
          }
          return cstr.charAt(0);
        case BOOLEAN:
          String bstr = BasicStringHelper.parseString(str);
          switch (bstr.toLowerCase()) {
            case "true":
            case "t":
            case "1":
              return Boolean.TRUE;
            case "false":
            case "f":
            case "0":
              return Boolean.FALSE;
            default:
              throw new TentackleRuntimeException("invalid boolean value: " + bstr);
          }
        case BYTE:
          return Byte.valueOf(BasicStringHelper.parseString(str));
        case SHORT:
          return Short.valueOf(BasicStringHelper.parseString(str));
        case INTEGER:
          return Integer.valueOf(BasicStringHelper.parseString(str));
        case LONG:
          return Long.valueOf(BasicStringHelper.parseString(str));
        case FLOAT:
          return Float.valueOf(BasicStringHelper.parseString(str));
        case DOUBLE:
          return Double.valueOf(BasicStringHelper.parseString(str));
      }
    }
    catch (ParseException ex) {
      throw new TentackleRuntimeException("parsing failed: " + str, ex);
    }
    throw new TentackleRuntimeException("cannot parse type " + type);
  }



  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat(DATE_PATTERN);
  private static final String TIME_PATTERN = "HH:mm:ss";
  private static final DateFormat TIME_FORMAT = new SimpleDateFormat(TIME_PATTERN);
  private static final String TIMESTAMP_PATTERN = DATE_PATTERN + " " + TIME_PATTERN;
  private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(TIMESTAMP_PATTERN);
  private static final String MS_TIMESTAMP_PATTERN = TIMESTAMP_PATTERN + ".000";
  private static final DateFormat MS_TIMESTAMP_FORMAT = new SimpleDateFormat(MS_TIMESTAMP_PATTERN);


  private static Date parseDate(String str) throws ParseException {
    synchronized(DATE_FORMAT) {  // formatters are not thread-safe :(
      return new Date(DATE_FORMAT.parse(BasicStringHelper.parseString(str)).getTime());
    }
  }

  private static Time parseTime(String str) throws ParseException {
    synchronized(TIME_FORMAT) {
      return new Time(TIME_FORMAT.parse(BasicStringHelper.parseString(str)).getTime());
    }
  }

  private static Timestamp parseTimestamp(String str) throws ParseException {
    str = BasicStringHelper.parseString(str);
    if (str.length() > 3 && str.charAt(str.length() - 4) == '.') {
      synchronized(MS_TIMESTAMP_FORMAT) {
        return new Timestamp(MS_TIMESTAMP_FORMAT.parse(str).getTime());
      }
    }
    else  {
      synchronized(TIMESTAMP_FORMAT) {
        return new Timestamp(TIMESTAMP_FORMAT.parse(str).getTime());
      }
    }
  }


  /**
   * Creates a datatype from a java type string.
   *
   * @param javaType the java type
   * @return the datatype, null if no such type
   */
  public static DataType createFromJavaType(String javaType) {
    for (DataType dataType: DataType.values()) {
      if (dataType.javaType.equals(javaType)) {
        return dataType;
      }
    }
    return null;
  }

}
