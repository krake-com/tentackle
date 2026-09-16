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

package org.tentackle.sql;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;

/**
 * Just an interface to decouple dependency from PreparedStatementWrapper.
 *
 * @author harald
 */
public interface BackendPreparedStatement {

  /**
   * Sets the object into the statement via its {@link DataType}.<br>
   * This method is provided for application-specific types.
   *
   * @param dataType the datatype
   * @param p the positional index, starting at 1
   * @param object the object of given datatype
   * @param mapNull true if map null values
   * @param size the optional size from the model
   * @param <T> the type
   * @return the number of columns set
   */
  <T> int set(DataType<T> dataType, int p, T object, boolean mapNull, Integer size);

  /**
   * Sets a column of an object into a prepared statement via its {@link DataType}.<br>
   * This method is provided for application-specific types.
   *
   * @param dataType the datatype
   * @param p the positional index, starting at 1
   * @param object the object of given datatype
   * @param index the column index
   * @param mapNull true if map null values
   * @param size the optional size from the model
   * @param <T> the type
   */
  <T> void set(DataType<T> dataType, int p, T object, int index, boolean mapNull, Integer size);

  /**
   * Generic setter for objects via known {@link SqlType}.
   * @param sqlType the sql type
   * @param p the positional index, starting at 1
   * @param object the object of given sql type
   */
  void set(SqlType sqlType, int p, Object object);

  /**
   * Sets an array parameter.<br>
   * Used for array operators such as IN, ANY or ALL.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param type the type of the array's elements, or the inner type if elements implement Convertible
   * @param columnIndex the column index for multi-column {@link DataType}s, -1 if type is a Convertible, ignored otherwise
   * @param elements the array elements
   * @param operator the array operator
   */
  void setArray(int p, Class<?> type, int columnIndex, Collection<?> elements, String operator);    // no <T> because illegal if Convertible

  /**
   * Sets the designated parameter to the given Java <code>Boolean</code> value.
   * The driver converts this
   * to an SQL <code>BIT</code> or <code>BOOLEAN</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value, null if the value should be set to SQL NULL
   */
  void setBoolean(int p, Boolean b);

  /**
   * Sets the designated parameter to the given Java <code>boolean</code> value.
   * The driver converts this
   * to an SQL <code>BIT</code> or <code>BOOLEAN</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value
   */
  void setBoolean(int p, boolean b);

  /**
   * Sets the designated parameter to the given Java <code>Byte</code> value.
   * The driver converts this
   * to an SQL <code>TINYINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value, null if the value should be set to SQL NULL
   */
  void setByte(int p, Byte b);

  /**
   * Sets the designated parameter to the given Java <code>byte</code> value.
   * The driver converts this
   * to an SQL <code>TINYINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value
   */
  void setByte(int p, byte b);

  /**
   * Sets the designated parameter to the given Java <code>char</code> value.
   * The driver converts this
   * to an SQL <code>VARCHAR</code>
   * when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param c the parameter value
   */
  void setChar(int p, char c);

  /**
   * Sets the designated parameter to the given Java <code>Character</code> value.
   * The driver converts this
   * to an SQL <code>VARCHAR</code>
   * when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param c the parameter value, null if the value should be set to SQL NULL
   */
  void setCharacter(int p, Character c);

  /**
   * Sets the designated parameter to the given Java <code>Character</code> value.
   * The driver converts this
   * to an SQL <code>VARCHAR</code>
   * when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param c the parameter value, null if the value should be set to SQL NULL
   * @param mapNull true if null values should be mapped to BLANK, else SQL NULL
   */
  void setCharacter(int p, Character c, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.sql.Date</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   * The driver converts this
   * to an SQL <code>DATE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value, null if the value should be set to SQL NULL
   */
  void setDate(int p, Date d);

  /**
   * Sets the designated parameter to the given <code>java.sql.Date</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   * The driver converts this
   * to an SQL <code>DATE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value
   * @param mapNull to map null values to 1.1.1970 (epochal time zero), else SQL NULL
   */
  void setDate(int p, Date d, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.sql.Date</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   * The driver converts this
   * to an SQL <code>DATE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value
   * @param timezone the calendar providing the timezone configuration
   */
  void setDate(int p, Date d, Calendar timezone);

  /**
   * Sets the designated parameter to the given <code>java.sql.Date</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   * The driver converts this
   * to an SQL <code>DATE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull to map null values to 1.1.1970 (epochal time zero), else SQL NULL
   */
  void setDate(int p, Date d, Calendar timezone, boolean mapNull);

  /**
   * Sets the designated parameter to the given Java <code>Double</code> value.
   * The driver converts this
   * to an SQL <code>DOUBLE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value, null if the value should be set to SQL NULL
   */
  void setDouble(int p, Double d);

  /**
   * Sets the designated parameter to the given Java <code>double</code> value.
   * The driver converts this
   * to an SQL <code>DOUBLE</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value
   */
  void setDouble(int p, double d);

  /**
   * Sets the designated parameter to the given Java <code>Float</code> value.
   * The driver converts this
   * to an SQL <code>REAL</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param f the parameter value, null if the value should be set to SQL NULL
   */
  void setFloat(int p, Float f);

  /**
   * Sets the designated parameter to the given Java <code>float</code> value.
   * The driver converts this
   * to an SQL <code>REAL</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param f the parameter value
   */
  void setFloat(int p, float f);

  /**
   * Sets the designated parameter to the given Java <code>int</code> value.
   * The driver converts this
   * to an SQL <code>INTEGER</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param i the parameter value
   */
  void setInt(int p, int i);

  /**
   * Sets the designated parameter to the given Java <code>Integer</code> value.
   * The driver converts this
   * to an SQL <code>INTEGER</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param i the parameter value, null if the value should be set to SQL NULL
   */
  void setInteger(int p, Integer i);

  /**
   * Sets the designated parameter to the given <code>java.time.LocalDate</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value, null if the value should be set to SQL NULL
   */
  void setLocalDate(int p, LocalDate d);

  /**
   * Sets the designated parameter to the given <code>java.time.LocalDate</code> value
   * using the default time zone of the virtual machine that is running
   * the application.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value
   * @param mapNull to map null values to 1.1.1970 (epochal time zero), else SQL NULL
   */
  void setLocalDate(int p, LocalDate d, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.time.LocalDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value, null if the value should be set to SQL NULL
   */
  void setLocalDateTime(int p, LocalDateTime ts);

  /**
   * Sets the designated parameter to the given <code>java.time.LocalDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 (epochal time zero),
   * else SQL NULL
   */
  void setLocalDateTime(int p, LocalDateTime ts, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.time.OffsetDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value, null if the value should be set to SQL NULL
   */
  void setOffsetDateTime(int p, OffsetDateTime ts);

  /**
   * Sets the designated parameter to the given <code>java.time.OffsetDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 (epochal time zero),
   * else SQL NULL
   */
  void setOffsetDateTime(int p, OffsetDateTime ts, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.time.ZonedDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value, null if the value should be set to SQL NULL
   */
  void setZonedDateTime(int p, ZonedDateTime ts);

  /**
   * Sets the designated parameter to the given <code>java.time.ZonedDateTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 GMT (epochal time zero),
   * else SQL NULL
   */
  void setZonedDateTime(int p, ZonedDateTime ts, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.time.Instant</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value, null if the value should be set to SQL NULL
   */
  void setInstant(int p, Instant ts);

  /**
   * Sets the designated parameter to the given <code>java.time.Instant</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 (epochal time zero),
   * else SQL NULL
   */
  void setInstant(int p, Instant ts, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.time.LocalTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param t the parameter value, null if the value should be set to SQL NULL
   */
  void setLocalTime(int p, LocalTime t);

  /**
   * Sets the designated parameter to the given <code>java.time.OffsetTime</code> value.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param t the parameter value, null if the value should be set to SQL NULL
   */
  void setOffsetTime(int p, OffsetTime t);

  /**
   * Sets the designated parameter to the given Java <code>Long</code> value.
   * The driver converts this
   * to an SQL <code>BIGINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param l the parameter value, null if the value should be set to SQL NULL
   */
  void setLong(int p, Long l);

  /**
   * Sets the designated parameter to the given Java <code>long</code> value.
   * The driver converts this
   * to an SQL <code>BIGINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param l the parameter value
   */
  void setLong(int p, long l);

  /**
   * Sets the designated parameter to SQL <code>NULL</code>.
   *
   * @param pos the first parameter is 1, the second is 2, ...
   * @param type the SQL type code defined in <code>java.sql.Types</code>
   */
  void setNull(int pos, int type);

  /**
   * Sets the designated parameter to the given Java <code>Short</code> value.
   * The driver converts this
   * to an SQL <code>SMALLINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value, null if the value should be set to SQL NULL
   */
  void setShort(int p, Short s);

  /**
   * Sets the designated parameter to the given Java <code>short</code> value.
   * The driver converts this
   * to an SQL <code>SMALLINT</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value
   */
  void setShort(int p, short s);

  /**
   * Sets the designated parameter to the given Java <code>String</code> value.
   * The driver converts this
   * to an SQL <code>VARCHAR</code> or <code>LONGVARCHAR</code> value
   * (depending on the argument's
   * size relative to the driver's limits on <code>VARCHAR</code> values)
   * when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value, null if the value should be set to SQL NULL
   */
  void setString(int p, String s);

  /**
   * Sets the designated parameter to the given Java <code>String</code> value.
   * The driver converts this
   * to an SQL <code>VARCHAR</code> or <code>LONGVARCHAR</code> value
   * (depending on the argument's
   * size relative to the driver's limits on <code>VARCHAR</code> values)
   * when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value
   * @param mapNull true if null values should be mapped to the empty string, else SQL NULL
   */
  void setString(int p, String s, boolean mapNull);

  /**
   * Sets a very large string.<br>
   * This is usually converted to SQL <code>CLOB</code>.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value
   * @param mapNull true if null values should be mapped to the empty string, else SQL NULL
   */
  void setLargeString(int p, String s, boolean mapNull);

  /**
   * Sets a very large string.<br>
   * This is usually converted to SQL <code>CLOB</code>.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value, null if the value should be set to SQL NULL
   */
  void setLargeString(int p, String s);

  /**
   * Sets a string bridged through the JDBC {@code SQLXML} interface.<br>
   * This is usually converted to a native XML column, or emulated as <code>CLOB</code>/<code>VARCHAR</code>.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value
   * @param mapNull true if null values should be mapped to the empty string, else SQL NULL
   */
  void setSqlxml(int p, String s, boolean mapNull);

  /**
   * Sets a string bridged through the JDBC {@code SQLXML} interface.<br>
   * This is usually converted to a native XML column, or emulated as <code>CLOB</code>/<code>VARCHAR</code>.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param s the parameter value, null if the value should be set to SQL NULL
   */
  void setSqlxml(int p, String s);

  /**
   * Sets the designated parameter to the given <code>java.sql.Time</code> value.
   * The driver converts this
   * to an SQL <code>TIME</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param t the parameter value, null if the value should be set to SQL NULL
   */
  void setTime(int p, Time t);

  /**
   * Sets the designated parameter to the given <code>java.sql.Time</code> value.
   * The driver converts this
   * to an SQL <code>TIME</code> value when it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param t the parameter value, null if the value should be set to SQL NULL
   * @param timezone the calendar providing the timezone configuration
   */
  void setTime(int p, Time t, Calendar timezone);

  /**
   * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value.
   * The driver
   * converts this to an SQL <code>TIMESTAMP</code> value when it sends it to the
   * database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value, null if the value should be set to SQL NULL
   */
  void setTimestamp(int p, Timestamp ts);

  /**
   * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value.
   * The driver
   * converts this to an SQL <code>TIMESTAMP</code> value when it sends it to the
   * database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 (epochal time zero),
   * else SQL NULL
   */
  void setTimestamp(int p, Timestamp ts, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value.
   * The driver
   * converts this to an SQL <code>TIMESTAMP</code> value when it sends it to the
   * database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param timezone the calendar providing the timezone configuration
   * else SQL NULL
   */
  void setTimestamp(int p, Timestamp ts, Calendar timezone);

  /**
   * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value.
   * The driver
   * converts this to an SQL <code>TIMESTAMP</code> value when it sends it to the
   * database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param ts the parameter value
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull to map null values to 1.1.1970 00:00:00.000 (epochal time zero),
   * else SQL NULL
   */
  void setTimestamp(int p, Timestamp ts, Calendar timezone, boolean mapNull);

  /**
   * Sets the designated parameter to the given <code>java.math.BigDecimal</code> value.
   * The driver converts this to an SQL <code>NUMERIC</code> value when
   * it sends it to the database.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param d the parameter value, null if the value should be set to SQL NULL
   */
  void setBigDecimal(int p, BigDecimal d);

}
