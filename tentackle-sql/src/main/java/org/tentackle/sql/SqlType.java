/*
 * Tentackle - http://www.tentackle.org.
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

/**
 * The sql types used by tentackle.
 * <p>
 * Defines a typesafe subset of {@link Types}.
 *
 * @author harald
 */
public enum SqlType {

  /** String. */
  VARCHAR(Types.VARCHAR, false, false, ""),

  /** Date. */
  DATE(Types.DATE, false, false, new Date(0)),

  /** Time. */
  TIME(Types.TIME, false, false, new Time(0)),

  /** Timestamp. */
  TIMESTAMP(Types.TIMESTAMP, false, false, new Timestamp(0)),

  /** Binary. */
  LONGVARBINARY(Types.LONGVARBINARY, false, false, null),

  /** BigDecimal and DMoney. */
  DECIMAL(Types.DECIMAL, true, true, new BigDecimal(0)),

  /** Character and char. */
  CHAR(Types.CHAR, false, false, ' '),

  /** Boolean and boolean. */
  BIT(Types.BIT, false, false, false),

  /** Byte and byte. */
  TINYINT(Types.TINYINT, true, false, (byte) 0),

  /** Short and short. */
  SMALLINT(Types.SMALLINT, true, false, (short) 0),

  /** Integer and int. */
  INTEGER(Types.INTEGER, true, false, 0),

  /** Long and long. */
  BIGINT(Types.BIGINT, true, false, 0L),

  /** Float and float. */
  FLOAT(Types.FLOAT, true, true, 0.0f),

  /** Double and double. */
  DOUBLE(Types.DOUBLE, true, true, 0.0d),

  /** Application specific. */
  JAVA_OBJECT(Types.JAVA_OBJECT, false, false, null);




  private final int sqlType;          // the JDBC sql type
  private final boolean numeric;      // true if numeric type
  private final boolean fractional;   // true if fractional numeric type
  private final Object defaultValue;  // the default value

  /**
   * Creates an sql type.
   *
   * @param sqlType the java sql type
   */
  SqlType(int sqlType, boolean numeric, boolean fractional, Object defaultValue) {
    this.sqlType = sqlType;
    this.numeric = numeric;
    this.fractional = fractional;
    this.defaultValue = defaultValue;
  }


  /**
   * Gets the java SQL type.
   *
   * @return the sql type
   */
  public int getSqlType() {
    return sqlType;
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

}
