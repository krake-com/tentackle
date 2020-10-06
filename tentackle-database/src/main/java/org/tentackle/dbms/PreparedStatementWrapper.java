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



package org.tentackle.dbms;

import org.tentackle.common.BMoney;
import org.tentackle.common.Binary;
import org.tentackle.common.DMoney;
import org.tentackle.common.DateHelper;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.DataType;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;



/**
 * A wrapper for prepared statements.<br>
 * Will catch and report SQLExceptions and
 * keep track of being used only once after {@link Db#getPreparedStatement}.
 *
 * @author harald
 */
public class PreparedStatementWrapper extends StatementWrapper implements BackendPreparedStatement {


  private final StatementKey statementKey;  // the statement key, if not a one-shot statement
  private int columnOffset;                 // offset to add to column index, default is 0
  private Map<Integer,Object> parameters;   // execution parameters


  /**
   * Creates a wrapper for a prepared statement.
   *
   * @param con the connection
   * @param stmt the jdbc statement
   * @param statementKey the statement key if not a one-shot
   * @param sql the original sql string that created the stmt
   */
  public PreparedStatementWrapper(ManagedConnection con, PreparedStatement stmt, StatementKey statementKey, String sql) {

    super(con, stmt);

    this.statementKey = statementKey;
    this.sql = sql;

    Db db = getAttachedSession();
    // if not a one-shot statement
    if (db.getFetchSize() != 0) {
      // set fixed fetchsize (0 = use drivers default)
      setFetchSize(db.getFetchSize());
    }

    if (db.getMaxRows() != 0) {
      // set maximum rows, 0 = no limit
      setMaxRows(db.getMaxRows());
    }

    parameters = new HashMap<>();
  }


  @Override
  public String toString() {
    if (statementKey != null && statementKey.getStatementId() != null) {
      return statementKey + super.toString();
    }
    return "<prepared>" + super.toString();
  }


  @Override
  public void close() {
    super.close();
    con.removePreparedStatement(this);
  }


  /**
   * Gets the wrapped prepared statement.
   *
   * @return the prepared statement, null if closed
   */
  @Override
  public PreparedStatement getStatement()  {
    return (PreparedStatement) stmt;
  }


  /**
   * Gets the statement key.
   *
   * @return the key, null if one-shot
   */
  public StatementKey getStatementKey() {
    return statementKey;
  }


  /**
   * Sets the column offset.
   * Useful for eager loading or joining in general.
   *
   * @param columnOffset (default is 0)
   */
  public void setColumnOffset(int columnOffset) {
    this.columnOffset = columnOffset;
  }

  /**
   * Gets the column offset.
   *
   * @return the current columnOffset
   */
  public int getColumnOffset()  {
    return columnOffset;
  }

  /**
   * Gets the effective position.<br>
   * This the p + columnOffset.
   *
   * @param p the sql position
   * @return the effective position
   */
  protected int effectivePosition(int p) {
    return columnOffset + p;
  }


  /**
   * Sets the parameter to be remembered for diagnostics.
   *
   * @param p the effective sql position
   * @param value the value of the parameter
   */
  protected void rememberParameter(int p, Object value) {
    parameters.put(p, value);
  }

  @Override
  protected void detachSession() {
    super.detachSession();
    // new map because old reference is held in StatementHistory
    parameters = new HashMap<>();
  }


  /**
   * Clears the current parameter values immediately.
   *
   * @see PreparedStatement#clearParameters()
   */
  public void clearParameters() {
    try {
      getStatement().clearParameters();
      parameters.clear();
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Gets the parameter at position.
   *
   * @param p the sql position
   * @return the object, null if no such position or object is null
   */
  public Object getParameter(int p) {
    return parameters.get(effectivePosition(p));
  }

  /**
   * Gets the parameter map.
   *
   * @return the parameters
   */
  public Map<Integer,Object> getParameters() {
    return parameters;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden because sql-string isn't used.
   */
  @Override
  protected int executeUpdateImpl(String sql) throws SQLException {
    return getStatement().executeUpdate();
  }


  /**
   * Executes the update.
   *
   * @return the row count
   */
  public int executeUpdate() {
    return super.executeUpdate(null);
  }

  /**
   * Method must not be invoked on a {@link PreparedStatementWrapper}.
   *
   * @param sql must be null, any non-null results in a {@link PersistenceException}
   * @return the row count
   */
  @Override
  public int executeUpdate(String sql) {
    if (sql != null) {
      throw new PersistenceException(getSession(), "invocation not allowed for prepared statements");
    }
    return super.executeUpdate(null);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden because sql-string isn't used.
   */
  @Override
  protected ResultSet executeQueryImpl(String sql) throws SQLException {
    return getStatement().executeQuery();
  }



  /**
   * Executes the query.
   *
   * @param withinTx is true if start a transaction for this query.
   *
   * @return the result set as a ResultSetWrapper
   */
  public ResultSetWrapper executeQuery (boolean withinTx) {
    return super.executeQuery(null, withinTx);
  }


  /**
   * Executes the query.
   *
   * @return the result set as a ResultSetWrapper
   */
  public ResultSetWrapper executeQuery () {
    return executeQuery(false);
  }




  // ----------------------------------- the setters -----------------------------------


  @Override
  public void setNull (int p, int type) {
    try {
      int ep = effectivePosition(p);
      getStatement().setNull (ep, type);
      rememberParameter(ep, null);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public <T> int set(DataType<T> dataType, int p, T object, boolean mapNull, Integer size) {
    try {
      Object[] values = dataType.set(con.getBackend(), getStatement(), p, object, mapNull, size);
      for (int i=0; i < values.length; i++) {
        rememberParameter(p + i, values[i]);
      }
      return values.length;
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setString (int p, String s, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      if (s == null) {
        if (mapNull)  {
          s = con.getBackend().getEmptyString();
          getStatement().setString(ep, s);
        }
        else  {
          getStatement().setNull(ep, Types.VARCHAR);
        }
      }
      else {
        getStatement().setString (ep, s);
      }
      rememberParameter(ep, s);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setString (int p, String s) {
    setString(p, s, false);
  }

  @Override
  public void setLargeString(int p, String s, boolean mapNull) {
    if (con.getBackend().isClobSupported()) {
      try {
        int ep = effectivePosition(p);
        if (s == null && mapNull) {
          s = con.getBackend().getEmptyString();
        }
        if (s != null) {
          Clob clob = con.getConnection().createClob();
          clob.setString(1, s);
          getStatement().setClob(p, clob);
        }
        else {
          getStatement().setNull(ep, Types.CLOB);
        }
        rememberParameter(ep, s);
      }
      catch (SQLException e)  {
        throw new PersistenceException(getSession(), this.toString(), e);
      }
    }
    else {
      setString(p, s, mapNull);
    }
  }

  @Override
  public void setLargeString(int p, String s) {
    setLargeString(p, s, false);
  }

  @Override
  public void setBoolean (int p, boolean b) {
    try {
      int ep = effectivePosition(p);
      getStatement().setBoolean (ep, b);
      rememberParameter(ep, b);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }


  @Override
  public void setBoolean (int p, Boolean b) {
    if (b == null) {
      setNull(p, Types.BIT);
    }
    else {
      setBoolean(p, b.booleanValue());
    }
  }


  @Override
  public void setByte (int p, byte b) {
    try {
      int ep = effectivePosition(p);
      getStatement().setByte (ep, b);
      rememberParameter(ep, b);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setByte (int p, Byte b) {
    if (b == null) {
      setNull(p, Types.TINYINT);
    }
    else {
      setByte(p, b.byteValue());
    }
  }


  @Override
  public void setChar (int p, char c) {
    try {
      int ep = effectivePosition(p);
      getStatement().setString(ep, String.valueOf(c));
      rememberParameter(ep, c);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setCharacter (int p, Character c, boolean mapNull) {
    if (c == null) {
      if (mapNull)  {
        setChar(p, ' ');
      }
      else  {
        setNull(p, Types.CHAR);
      }
    }
    else {
      setChar(p, c);
    }
  }

  @Override
  public void setCharacter (int p, Character c) {
    setCharacter(p, c, false);
  }


  @Override
  public void setShort (int p, short s) {
    try {
      int ep = effectivePosition(p);
      getStatement().setShort (ep, s);
      rememberParameter(ep, s);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setShort (int p, Short s) {
    if (s == null) {
      setNull(p, Types.SMALLINT);
    }
    else {
      setShort(p, s.shortValue());
    }
  }


  @Override
  public void setInt (int p, int i) {
    try {
      int ep = effectivePosition(p);
      getStatement().setInt (ep, i);
      rememberParameter(ep, i);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setInteger (int p, Integer i) {
    if (i == null) {
      setNull(p, Types.INTEGER);
    }
    else {
      setInt(p, i);
    }
  }


  @Override
  public void setLocalDate(int p, LocalDate d, Calendar timezone, boolean mapNull) {
    setDate(p, d == null ? null : Date.valueOf(d), timezone, mapNull);
  }

  @Override
  public void setLocalDate(int p, LocalDate d) {
    setLocalDate(p, d, null, false);
  }

  @Override
  public void setLocalDate(int p, LocalDate d, boolean mapNull) {
    setLocalDate(p, d, null, mapNull);
  }

  @Override
  public void setLocalDate(int p, LocalDate d, Calendar timezone) {
    setLocalDate(p, d, timezone, false);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts) {
    setLocalDateTime(p, ts, null, false);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts, boolean mapNull) {
    setLocalDateTime(p, ts, null, mapNull);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts, Calendar timezone) {
    setLocalDateTime(p, ts, timezone, false);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts, Calendar timezone, boolean mapNull) {
    setTimestamp(p, ts == null ? null : Timestamp.valueOf(ts), timezone, mapNull);
  }

  @Override
  public void setLocalTime(int p, LocalTime t) {
    setLocalTime(p, t, null);
  }

  @Override
  public void setLocalTime(int p, LocalTime t, Calendar timezone) {
    setTime(p, t == null ? null : Time.valueOf(t), timezone);
  }


  @Override
  public void setLong (int p, long l) {
    if (con.getBackend().needSetLongWorkaround()) {
      /*
       * long is translated to varchar in update statements by the Ingres JDBC-driver for some obscure reason.
       * Insert statements, however, work with long.
       * So, we simply translate it here to int (which is not ok, but there's no other workaround so far)
       */
      setInt (p, (int) l);
    }
    else  {
      try {
        int ep = effectivePosition(p);
        getStatement().setLong(ep, l);
        rememberParameter(ep, l);
      }
      catch (SQLException e)  {
        throw new PersistenceException(getSession(), this.toString(), e);
      }
    }
  }

  @Override
  public void setLong (int p, Long l) {
    if (l == null) {
      setNull(p, Types.BIGINT);
    }
    else {
      setLong(p, l.longValue());
    }
  }


  @Override
  public void setFloat (int p, float f) {
    try {
      int ep = effectivePosition(p);
      getStatement().setFloat (ep, f);
      rememberParameter(ep, f);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setFloat (int p, Float f) {
    if (f == null) {
      setNull(p, Types.FLOAT);
    }
    else {
      setFloat(p, f.floatValue());
    }
  }


  @Override
  public void setDouble (int p, double d) {
    try {
      int ep = effectivePosition(p);
      getStatement().setDouble (ep, d);
      rememberParameter(ep, d);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setDouble (int p, Double d) {
    if (d == null) {
      setNull(p, Types.DOUBLE);
    }
    else {
      setDouble(p, d.doubleValue());
    }
  }


  @Override
  public void setBigDecimal (int p, BigDecimal d) {
    if (d == null) {
      setNull(p, Types.DECIMAL);
    }
    else  {
      try {
        int ep = effectivePosition(p);
        getStatement().setBigDecimal(ep, d);
        rememberParameter(ep, d);
      }
      catch (SQLException e)  {
        throw new PersistenceException(getSession(), this.toString(), e);
      }
    }
  }


  /**
   * Sets the designated parameter to a <code>BMoney</code> value.<br>
   * A BMoney will not be stored as a single field but as two fields:
   * <ol>
   * <li>a double representing the value</li>
   * <li>an int representing the scale</li>
   * </ol>
   * This is due to most DBMS can't store arbitrary scaled decimals in
   * a single column, i.e. all values in the column must have the same scale.
   *
   * @param p the sql position
   * @param m the money value, null to set SQL NULL
   * @see #setDMoney(int, DMoney)
   */
  public void setBMoney (int p, BMoney m) {
    try {
      int ep = effectivePosition(p);
      if (m == null) {
        getStatement().setNull(ep, Types.DOUBLE);
        getStatement().setNull(ep + 1, Types.SMALLINT);
        rememberParameter(ep, null);
        rememberParameter(ep + 1, null);
      }
      else {
        double d = m.doubleValue();
        short s = (short) m.scale();
        getStatement().setDouble(ep, d);    // set the value
        getStatement().setShort(ep + 1, s);   // set the scale
        rememberParameter(ep, d);
        rememberParameter(ep + 1, s);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }


  /**
   * Sets the designated parameter to a <code>DMoney</code> value.<br>
   * A DMoney will not be stored as a single field but as two fields:
   * <ol>
   * <li>a BigDecimal with a scale of 0 representing the value</li>
   * <li>an int representing the scale</li>
   * </ol>
   * This is due to most DBMS can't store arbitrary scaled decimals in
   * a single column, i.e. all values in the column must have the same scale.
   *
   * @param p the sql position
   * @param m the money value, null to set SQL NULL
   * @see #setBMoney(int, BMoney)
   */
  public void setDMoney (int p, DMoney m) {
    try {
      int ep = effectivePosition(p);
      if (m == null) {
        getStatement().setNull(ep, Types.DECIMAL);
        getStatement().setNull(ep + 1, Types.SMALLINT);
        rememberParameter(ep, null);
        rememberParameter(ep + 1, null);
      }
      else {
        short s = (short) m.scale();
        BigDecimal d = m.movePointRight(s);
        getStatement().setBigDecimal(ep, d);  // set the value
        getStatement().setShort(ep + 1, s);     // set the scale
        rememberParameter(ep, d);
        rememberParameter(ep + 1, s);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }


  @Override
  public void setDate(int p, Date d, Calendar timezone, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      if (d == null && mapNull) {
        d = DateHelper.MIN_DATE;
      }
      if (d == null) {
        getStatement().setNull(ep, Types.DATE);
      }
      else  {
        if (timezone == null) {
          getStatement().setDate(ep, d);
        }
        else  {
          getStatement().setDate(ep, d, timezone);
        }
      }
      rememberParameter(ep, d);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setDate(int p, Date d, Calendar timezone) {
    setDate(p, d, timezone, false);
  }

  @Override
  public void setDate(int p, Date d, boolean mapNull) {
    setDate(p, d, null, mapNull);
  }

  @Override
  public void setDate(int p, Date d)  {
    setDate(p, d, null, false);
  }


  @Override
  public void setTimestamp(int p, Timestamp ts, Calendar timezone, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      if (ts == null && mapNull) {
        ts = DateHelper.MIN_TIMESTAMP;
      }
      if (ts == null) {
        getStatement().setNull(ep, Types.TIMESTAMP);
      }
      else  {
        if (timezone == null) {
          getStatement().setTimestamp(ep, ts);
        }
        else  {
          getStatement().setTimestamp(ep, ts, timezone);
        }
      }
      rememberParameter(ep, ts);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setTimestamp(int p, Timestamp ts, Calendar timezone) {
    setTimestamp(p, ts, timezone, false);
  }

  @Override
  public void setTimestamp(int p, Timestamp ts, boolean mapNull) {
    setTimestamp(p, ts, null, mapNull);
  }

  @Override
  public void setTimestamp(int p, Timestamp ts) {
    setTimestamp(p, ts, null, false);
  }


  @Override
  public void setTime(int p, Time t, Calendar timezone) {
    try {
      int ep = effectivePosition(p);
      if (t == null) {
        getStatement().setNull(ep, Types.TIME);
      }
      else {
        if (timezone == null) {
          getStatement().setTime(ep, t);
        }
        else  {
          getStatement().setTime(ep, t, timezone);
        }
      }
      rememberParameter(ep, t);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setTime(int p, Time t) {
    setTime(p, t, null);
  }


  /**
   * Sets the designated parameter to the given {@link Binary} value.
   * will
   * The driver converts this to an SQL <code>BLOB</code> value when it sends it to the
   * database.
   * The implementation translates the Binary into an Inputstream and invokes
   * {@link PreparedStatement#setBlob(int, java.io.InputStream, long)}.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value, null if the value should be set to SQL NULL
   */
  public void setBinary(int p, Binary<?> b) {
    try {
      int ep = effectivePosition(p);
      if (b == null || b.getLength() == 0) {
        // "empty" binaries are treated as null
        b = null;
        getStatement().setNull(ep, Types.LONGVARBINARY);
      }
      else {
        getStatement().setBlob(ep, b.getInputStream(), b.getLength());
      }
      rememberParameter(ep, b);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

}
