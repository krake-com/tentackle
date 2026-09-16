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

import org.tentackle.sql.datatypes.UUIDType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.function.Function;

/**
 * The SQL types used when talking to the database backend.
 * <p>
 * Defines a typesafe subset of {@link Types}.
 *
 * @author harald
 */
public enum SqlType {

  /** String. */
  VARCHAR("", SqlType::removeSingleQuotes),

  /** Date. */
  DATE(new Date(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Date.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Date(0);   // parsing failed
    }
  }),

  /** Time. */
  TIME(new Time(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Time.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Time(0);   // parsing failed
    }
  }),

  /** Timestamp. */
  TIMESTAMP(new Timestamp(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return Timestamp.valueOf(t);
    }
    catch (RuntimeException rex) {
      return new Timestamp(0);   // parsing failed
    }
  }),

  /** Arbitrary data usually stored as BLOB. */
  BLOB(null, _ -> null),  // cannot be converted

  /** Large Strings usually stored as CLOB. */
  CLOB("", SqlType::removeSingleQuotes),

  /** XML content, usually stored in a native XML column or emulated as CLOB. */
  SQLXML("", SqlType::removeSingleQuotes),

  /** JSON content, usually stored in a native JSON column or emulated as CLOB. */
  JSON("", SqlType::removeSingleQuotes),

  /** BigDecimal and DMoney. */
  DECIMAL(new BigDecimal(0), t -> {
    t = removeSingleQuotes(t);
    try {
      return BigDecimal.valueOf(Double.parseDouble(t));
    }
    catch (RuntimeException rex) {
      return new BigDecimal(0);   // parsing failed
    }
  }),

  /** Character and char. */
  CHAR(' ', t -> {
    t = removeSingleQuotes(t);
    try {
      return t.isEmpty() ? ' ' : t.charAt(0);
    }
    catch (RuntimeException rex) {
      return ' ';   // parsing failed
    }
  }),

  /** Boolean and boolean. */
  BIT(false, t -> {
    t = removeSingleQuotes(t);
    try {
      return Boolean.valueOf(t);
    }
    catch (RuntimeException rex) {
      return false;   // parsing failed
    }
  }),

  /** Byte and byte. */
  TINYINT((byte) 0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Byte.valueOf(t);
    }
    catch (RuntimeException rex) {
      return (byte) 0;   // parsing failed
    }
  }),

  /** Short and short. */
  SMALLINT((short) 0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Short.valueOf(t);
    }
    catch (RuntimeException rex) {
      return (short) 0;   // parsing failed
    }
  }),

  /** Integer and int. */
  INTEGER(0, t -> {
    t = removeSingleQuotes(t);
    try {
      return Integer.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0;   // parsing failed
    }
  }),

  /** Long and long. */
  BIGINT(0L, t -> {
    t = removeSingleQuotes(t);
    try {
      return Long.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0L;   // parsing failed
    }
  }),

  /** Float and float. */
  FLOAT(0.0f, t -> {
    t = removeSingleQuotes(t);
    try {
      return Float.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0.0f;   // parsing failed
    }
  }),

  /** Double and double. */
  DOUBLE(0.0d, t -> {
    t = removeSingleQuotes(t);
    try {
      return Double.valueOf(t);
    }
    catch (RuntimeException rex) {
      return 0.0d;   // parsing failed
    }
  }),

  /** UUID. */
  UUID(UUIDType.NIL, t -> {
    t = removeSingleQuotes(t);
    try {
      return java.util.UUID.fromString(t);
    }
    catch (RuntimeException rex) {
      return UUIDType.NIL;   // parsing failed
    }
  }),

  /** Application-specific. */
  JAVA_OBJECT(null, _ -> null);    // conversion not possible


  /**
   * Removes optional single quotes if returned from meta-data.
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


  private final Object defaultValue;                // the default value
  private final Function<String,Object> parser;     // to parse JDBC metadata

  /**
   * Creates an SQL type.
   *
   * @param defaultValue the default value
   * @param parser the parser to parse JDBC metadata
   */
  SqlType(Object defaultValue, Function<String,Object> parser) {
    this.defaultValue = defaultValue;
    this.parser = parser;
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
