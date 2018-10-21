/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.sql;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.function.Function;

/**
 * The sql types used by tentackle.
 * <p>
 * Defines a typesafe subset of {@link Types}.
 *
 * @author harald
 */
public enum SqlType {

  /** String. */
  VARCHAR(Types.VARCHAR, false, false, "", SqlType::removeSingleQuotes),

  /** Date. */
  DATE(Types.DATE, false, false, new Date(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Date.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Date(0);   // parsing failed
    }
  }),

  /** Time. */
  TIME(Types.TIME, false, false, new Time(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Time.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Time(0);   // parsing failed
    }
  }),

  /** Timestamp. */
  TIMESTAMP(Types.TIMESTAMP, false, false, new Timestamp(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Timestamp.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Timestamp(0);   // parsing failed
    }
  }),

  /** Binary. */
  LONGVARBINARY(Types.LONGVARBINARY, false, false, null, t -> null),  // cannot be converted

  /** BigDecimal and DMoney. */
  DECIMAL(Types.DECIMAL, true, true, new BigDecimal(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return BigDecimal.valueOf(Double.valueOf(t));
    }
    catch (RuntimeException rex) {
      return new BigDecimal(0);   // parsing failed
    }
  }),

  /** Character and char. */
  CHAR(Types.CHAR, false, false, ' ', t -> {
    t = removeSingleQuotes(t);
    try {
      return t.isEmpty() ? ' ' : t.charAt(0);
    }
    catch (RuntimeException rex) {
      return ' ';   // parsing failed
    }
  }),

  /** Boolean and boolean. */
  BIT(Types.BIT, false, false, false, t -> {
    t = removeSingleQuotes(t);
    try {
      return Boolean.valueOf(t);
    }
    catch (RuntimeException rex) {
      return false;   // parsing failed
    }
  }),

  /** Byte and byte. */
  TINYINT(Types.TINYINT, true, false, (byte) 0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Byte.valueOf(t);
    }
    catch (RuntimeException rex) {
      return (byte) 0;   // parsing failed
    }
  }),

  /** Short and short. */
  SMALLINT(Types.SMALLINT, true, false, (short) 0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Short.valueOf(t);
    }
    catch (RuntimeException rex) {
      return (short) 0;   // parsing failed
    }
  }),

  /** Integer and int. */
  INTEGER(Types.INTEGER, true, false, 0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Integer.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0;   // parsing failed
    }
  }),

  /** Long and long. */
  BIGINT(Types.BIGINT, true, false, 0L, t -> {
    t = removeSingleQuotes(t);
    try {
      return Long.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0L;   // parsing failed
    }
  }),

  /** Float and float. */
  FLOAT(Types.FLOAT, true, true, 0.0f, t -> {
    t = removeSingleQuotes(t);
    try {
      return Float.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0.0f;   // parsing failed
    }
  }),

  /** Double and double. */
  DOUBLE(Types.DOUBLE, true, true, 0.0d, t -> {
    t = removeSingleQuotes(t);
    try {
      return Double.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0.0d;   // parsing failed
    }
  }),

  /** Application specific. */
  JAVA_OBJECT(Types.JAVA_OBJECT, false, false, null, t -> null);    // conversion not possible (never returned from meta data)


  /**
   * Removes optional single quotes if returned from meta data.
   *
   * @param str the meta data string
   * @return the cleaned string
   */
  private static String removeSingleQuotes(String str) {
    if (str != null && str.length() >= 2 && str.startsWith("'") && str.endsWith("'")) {
      str = str.substring(1, str.length() - 1);
    }
    return str;
  }


  private final int jdbcType;                       // the JDBC sql type
  private final boolean numeric;                    // true if numeric type
  private final boolean fractional;                 // true if fractional numeric type
  private final Object defaultValue;                // the default value
  private final Function<String,Object> parser;     // to parse JDBC metadata

  /**
   * Creates an SQL type.
   *
   * @param jdbcType the JDBC type
   * @param numeric true if numeric
   * @param fractional true if fractional
   * @param defaultValue the default value
   * @param parser the parser to parse JDBC metadata (e.g. DEFAULTs)
   */
  SqlType(int jdbcType, boolean numeric, boolean fractional, Object defaultValue, Function<String,Object> parser) {
    this.jdbcType = jdbcType;
    this.numeric = numeric;
    this.fractional = fractional;
    this.defaultValue = defaultValue;
    this.parser = parser;
  }


  /**
   * Gets the java SQL type.
   *
   * @return the sql type
   */
  public int getJdbcType() {
    return jdbcType;
  }

  /**
   * Returns whether numeric type.
   *
   * @return true if numeric
   */
  public boolean isNumeric() {
    return numeric;
  }

  /**
   * Returns whether numeric fractional type.
   *
   * @return true if fractional
   */
  public boolean isFractional() {
    return fractional;
  }

  /**
   * Gets the default value.<br>
   *
   * @return the default, null if no default
   */
  public Object getDefaultValue() {
    return defaultValue;
  }

  /**
   * Parses the string and converts to a java-value.
   *
   * @param str the string usually returned from JDBC metadata
   * @return the java value
   */
  public Object parse(String str) {
    return parser.apply(str);
  }

}
