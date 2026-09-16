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
import org.tentackle.misc.Convertible;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendPreparedStatement;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlType;
import org.tentackle.sql.datatypes.ZonedDateTimeType;
import org.tentackle.common.TBinary;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper for prepared statements.<br>
 * Will catch and report SQLExceptions and
 * keep track of being used only once after {@link Db#getPreparedStatement}.
 *
 * @author harald
 */
public class PreparedStatementWrapper extends StatementWrapper implements BackendPreparedStatement {

  private static final String INVOCATION_NOT_ALLOWED = "invocation not allowed for prepared statements";


  private final StatementKey statementKey;              // the statement key, if not a one-shot statement
  private int columnOffset;                             // offset to add to the column index, default is 0
  private Map<Integer,Object> parameters;               // execution parameters
  private boolean executionPending;                     // true if next setXXX(1, ...) will invoke addBatch
  private List<Map<Integer,Object>> batchParameters;    // batched execution parameters
  private WeakReference<DbBatch> batchRef;              // set if currently batched (weak since the object just vanishes after tx)


  /**
   * Creates a wrapper for a prepared statement.
   *
   * @param con the connection
   * @param stmt the jdbc statement
   * @param statementKey the statement key if not a one-shot
   * @param sql the original SQL string that created the stmt
   */
  public PreparedStatementWrapper(ManagedConnection con, PreparedStatement stmt, StatementKey statementKey, String sql) {

    super(con, stmt);

    this.statementKey = statementKey;
    this.sql = sql;

    Db db = getAttachedSession();
    // if not a one-shot statement
    if (db.getFetchSize() != 0) {
      // set fixed fetch size (0 = use drivers default)
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
   * The method is invoked immediately before setting the value into the statement, and only for that purpose!
   * This the p + columnOffset.
   *
   * @param p the SQL position
   * @return effective position
   */
  private int effectivePosition(int p) {
    int ep = columnOffset + p;
    if (ep == 1 && isBatched()) {
      if (executionPending) {
        addBatch();
      }
      else {
        executionPending = true;
      }
    }
    return ep;
  }


  /**
   * Sets the parameter to be remembered for diagnostics.
   *
   * @param p the effective SQL position
   * @param value the value of the parameter
   */
  protected void rememberParameter(int p, Object value) {
    parameters.put(p, value);
  }

  @Override
  protected void detachSession() {
    super.detachSession();
    // new map because the old reference is held in StatementHistory
    clearParametersImpl();
  }


  @Override
  public synchronized void markReady() {
    if (!isMarkedReady() || !isBatched()) {
      super.markReady();
    }
  }

  /**
   * Sets the statement batch if batched within the current transaction.
   *
   * @param batch the batch
   */
  protected void setBatch(DbBatch batch) {
    batchRef = new WeakReference<>(batch);
  }

  /**
   * Gets the statement batch.
   *
   * @return null if not batched
   */
  protected DbBatch getBatch() {
    DbBatch batch = null;
    if (batchRef != null) {
      batch = batchRef.get();
      if (batch == null) {
        batchRef = null;
      }
    }
    return batch;
  }

  /**
   * Returns whether the statement is batched.
   *
   * @return true if used in a batched transaction
   */
  protected boolean isBatched() {
    return getBatch() != null;
  }


  /**
   * Clears the current parameter values immediately.
   *
   * @see PreparedStatement#clearParameters()
   */
  public void clearParameters() {
    try {
      getStatement().clearParameters();
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
    finally {
      clearParametersImpl();
    }
  }


  private void clearParametersImpl() {
    parameters = new HashMap<>();
    batchParameters = null;
    executionPending = false;
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
   * Gets the parameter map for a batched statement.
   *
   * @return the parameter batch list, null if not batched
   */
  public List<Map<Integer, Object>> getBatchParameters() {
    return batchParameters;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden because sql-string isn't used.
   */
  @Override
  protected int executeUpdateImpl(String sql) throws SQLException {
    StatementTracer.getInstance().trace(this);
    return getStatement().executeUpdate();
  }


  /**
   * Executes the update.
   *
   * @return the row count
   */
  public int executeUpdate() {
    DbBatch batch = getBatch();
    if (batch != null) {
      batch.batch(this);
      return 1;                       // batched statements always affect a single row
    }
    getSession().executeBatch();      // not a batchable statement -> flush batch
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
      throw new PersistenceException(getSession(), INVOCATION_NOT_ALLOWED);
    }
    return super.executeUpdate(null);
  }


  /**
   * Method must not be invoked on a {@link PreparedStatementWrapper}.<br>
   * Always throws {@link PersistenceException}.
   *
   * @param sql ignored
   */
  @Override
  public void addBatch(String sql) {
    throw new PersistenceException(getSession(), INVOCATION_NOT_ALLOWED);
  }

  /**
   * Adds a set of parameters to this {@code PreparedStatement}'s batch of commands.
   */
  public void addBatch() {
    try {
      assertOpen();
      if (batchParameters == null) {
        batchParameters = new ArrayList<>();
      }
      batchParameters.add(parameters);
      parameters = new HashMap<>();
      executionPending = true;
      getStatement().addBatch();
    }
    catch (SQLException e)  {
      throw con.createFromSqlException(this.toString(), e);
    }
  }

  @Override
  public void clearBatch() {
    try {
      super.clearBatch();
    }
    finally {
      clearParametersImpl();
    }
  }

  @Override
  public int[] executeBatch(boolean finish) {
    try {
      if (isExecutionPending()) {
        return super.executeBatch(finish);
      }
      else if (finish) {
        detachSession();
      }
      return new int[0];
    }
    finally {
      clearParametersImpl();
    }
  }


  /**
   * Returns whether parameters have been set so that execution is pending.
   *
   * @return true if pending, false otherwise
   */
  public boolean isExecutionPending() {
    return executionPending;
  }


  /**
   * Gets the number of pending addBatch invocations.
   *
   * @return 0 if nothing pending
   */
  public int getBatchCount() {
    return batchParameters != null ? batchParameters.size() : 0;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden because sql-string isn't used.
   */
  @Override
  protected ResultSet executeQueryImpl(String sql) throws SQLException {
    StatementTracer.getInstance().trace(this);
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
      int ep = effectivePosition(p);
      Object[] values = dataType.set(con.getBackend(), getStatement(), ep, object, mapNull, size);
      for (int i = 0; i < values.length; i++) {
        rememberParameter(ep + i, values[i]);
      }
      return values.length;
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public <T> void set(DataType<T> dataType, int p, T object, int index, boolean mapNull, Integer size) {
    try {
      int ep = effectivePosition(p);
      Object value = dataType.set(con.getBackend(), getStatement(), ep, object, index, mapNull, size);
      rememberParameter(ep, value);
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void set(SqlType sqlType, int p, Object object) {
    switch (sqlType) {
      case VARCHAR:
        setString(p, object == null ? null : object.toString());
        break;

      case DATE:
        setDate(p, (Date) object);
        break;

      case TIME:
        setTime(p, (Time) object);
        break;

      case TIMESTAMP:
        setTimestamp(p, (Timestamp) object);
        break;

      case BLOB:
        setBinary(p, (Binary<?>) object);
        break;

      case CLOB:
        setLargeString(p, object == null ? null : object.toString());
        break;

      case SQLXML:
        setSqlxml(p, object == null ? null : object.toString());
        break;

      case JSON:
        setJson(p, object == null ? null : object.toString());
        break;

      case DECIMAL:
        setBigDecimal(p, (BigDecimal) object);
        break;

      case CHAR:
        setCharacter(p, (Character) object);
        break;

      case BIT:
        setBoolean(p, (Boolean) object);
        break;

      case TINYINT:
        setByte(p, object == null ? null : ((Number) object).byteValue());
        break;

      case SMALLINT:
        setShort(p, object == null ? null : ((Number) object).shortValue());
        break;

      case INTEGER:
        setInteger(p, object == null ? null : ((Number) object).intValue());
        break;

      case BIGINT:
        setLong(p, object == null ? null : ((Number) object).longValue());
        break;

      case FLOAT:
        setFloat(p, object == null ? null : ((Number) object).floatValue());
        break;

      case DOUBLE:
        setDouble(p, object == null ? null : ((Number) object).doubleValue());
        break;

      default:
        throw new PersistenceException("cannot set application-specific types");
    }
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void setArray(int p, Class<?> type, int columnIndex, Collection<?> elements, String operator) {
    Collection arrayElements;
    DataType dataType = DataTypeFactory.getInstance().get(type);
    if (dataType == null || dataType.isColumnCountBackendSpecific()) {
      throw new PersistenceException("unsupported array type " + type.getName());
    }
    Backend backend = getSession().getBackend();
    if (dataType.getColumnCount(backend) == 1) {
      if (columnIndex < 0) {
        // convertible type
        arrayElements = new ArrayList<>();
        for (Object element : elements) {
          if (element instanceof Convertible) {
            arrayElements.add(((Convertible) element).toExternal());
          }
        }
      }
      else {
        arrayElements = elements;
      }
      columnIndex = 0;
    }
    else {
      arrayElements = new ArrayList<>();
      for (Object element : elements) {
        if (element != null) {
          arrayElements.add(dataType.getColumnValue(backend, columnIndex, element));
        }
      }
    }
    SqlType sqlType = dataType.getSqlType(con.getBackend(), columnIndex);
    int ep = effectivePosition(p);
    try {
      getConnection().getBackend().setArray(getStatement(), ep, sqlType, arrayElements, operator);
      rememberParameter(ep, arrayElements);
    }
    catch (SQLException e) {
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
          getStatement().setClob(ep, clob);
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
  public void setSqlxml(int p, String s, boolean mapNull) {
    if (con.getBackend().isSqlxmlSupported()) {
      try {
        int ep = effectivePosition(p);
        if (s == null && mapNull) {
          s = con.getBackend().getEmptyString();
        }
        if (s != null) {
          SQLXML sqlxml = con.getConnection().createSQLXML();
          sqlxml.setString(s);
          getStatement().setSQLXML(ep, sqlxml);
        }
        else {
          getStatement().setNull(ep, Types.SQLXML);
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
  public void setSqlxml(int p, String s) {
    setSqlxml(p, s, false);
  }

  @Override
  public void setJson(int p, String s, boolean mapNull) {
    if (con.getBackend().isJsonSupported()) {
      try {
        int ep = effectivePosition(p);
        // mapNull is not applicable to a native JSON column: the empty string is no JSON document
        con.getBackend().setJson(getStatement(), ep, s);
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
  public void setJson(int p, String s) {
    setJson(p, s, false);
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
  public void setLocalDate(int p, LocalDate d, boolean mapNull) {
    setDate(p, d == null ? null : Date.valueOf(d), mapNull);
  }

  @Override
  public void setLocalDate(int p, LocalDate d) {
    setLocalDate(p, d, false);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts) {
    setLocalDateTime(p, ts, false);
  }

  @Override
  public void setLocalDateTime(int p, LocalDateTime ts, boolean mapNull) {
    setTimestamp(p, ts == null ? null : Timestamp.valueOf(ts), mapNull);
  }

  @Override
  public void setOffsetDateTime(int p, OffsetDateTime ts) {
    setOffsetDateTime(p, ts, false);
  }

  @Override
  public void setOffsetDateTime(int p, OffsetDateTime ts, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      Timestamp timestamp;
      Integer offset;
      if (ts == null) {
        if (mapNull) {
          timestamp = DateHelper.MIN_TIMESTAMP;
          offset = 0;
        }
        else {
          timestamp = null;
          offset = null;
        }
      }
      else {
        offset = ts.getOffset().getTotalSeconds();
        timestamp = Timestamp.valueOf(ts.toLocalDateTime().minusSeconds(offset));
      }

      if (timestamp == null) {
        getStatement().setNull(ep, Types.TIMESTAMP);
        getStatement().setNull(ep + 1, Types.INTEGER);
      }
      else {
        getStatement().setTimestamp(ep, timestamp);
        getStatement().setInt(ep + 1, offset);
      }
      rememberParameter(ep, timestamp);
      rememberParameter(ep + 1, offset);
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setZonedDateTime(int p, ZonedDateTime ts) {
    setZonedDateTime(p, ts, false);
  }

  @Override
  public void setZonedDateTime(int p, ZonedDateTime ts, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      Timestamp timestamp;
      String zoneId;
      if (ts == null) {
        if (mapNull) {
          timestamp = DateHelper.MIN_TIMESTAMP;
          zoneId = ZonedDateTimeType.GMT;
        }
        else {
          timestamp = null;
          zoneId = null;
        }
      }
      else {
        timestamp = Timestamp.valueOf(ts.toLocalDateTime());
        zoneId = ts.getZone().getId();
      }

      if (timestamp == null) {
        getStatement().setNull(ep, Types.TIMESTAMP);
        getStatement().setNull(ep + 1, Types.VARCHAR);
      }
      else {
        getStatement().setTimestamp(ep, timestamp);
        getStatement().setString(ep + 1, zoneId);
      }
      rememberParameter(ep, timestamp);
      rememberParameter(ep + 1, zoneId);
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setInstant(int p, Instant ts) {
    setInstant(p, ts, false);
  }

  @Override
  public void setInstant(int p, Instant ts, boolean mapNull) {
    try {
      int ep = effectivePosition(p);
      Long seconds;
      int nanos;
      if (ts == null) {
        seconds = mapNull ? 0L : null;
        nanos = 0;
      }
      else {
        seconds = ts.getEpochSecond();
        nanos = ts.getNano();
      }

      if (seconds == null) {
        getStatement().setNull(ep, Types.BIGINT);
        getStatement().setNull(ep + 1, Types.INTEGER);
      }
      else {
        getStatement().setLong(ep, seconds);
        getStatement().setInt(ep + 1, nanos);
      }
      rememberParameter(ep, seconds);
      rememberParameter(ep + 1, nanos);
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setLocalTime(int p, LocalTime t) {
    setTime(p, t == null ? null : Time.valueOf(t));
  }

  @Override
  public void setOffsetTime(int p, OffsetTime t) {
    try {
      int ep = effectivePosition(p);
      if (t == null) {
        getStatement().setNull(ep, Types.TIME);
        getStatement().setNull(ep + 1, Types.INTEGER);
        rememberParameter(ep, null);
        rememberParameter(ep + 1, null);
      }
      else {
        Time time = Time.valueOf(t.toLocalTime());
        int offset = t.getOffset().getTotalSeconds();
        getStatement().setTime(ep, time);    // set the value
        getStatement().setInt(ep + 1, offset);   // set the scale
        rememberParameter(ep, time);
        rememberParameter(ep + 1, offset);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  @Override
  public void setLong (int p, long l) {
    try {
      int ep = effectivePosition(p);
      getStatement().setLong(ep, l);
      rememberParameter(ep, l);
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
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
   * a single column, i.e., all values in the column must have the same scale.
   *
   * @param p the SQL position
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
   * a single column, i.e., all values in the column must have the same scale.
   *
   * @param p the SQL position
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
   * The driver converts it to a <code>BLOB</code> value when it sends it to the
   * database.
   * The implementation translates the Binary into an InputStream and invokes
   * {@link PreparedStatement#setBinaryStream(int, java.io.InputStream, long)}.
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
        // notice: setBlob would create a large object on backends mapping BLOB to a plain binary
        // type such as BYTEA (postgres), BYTE (informix) or VARBINARY (mssql)
        getStatement().setBinaryStream(ep, b.getInputStream(), b.getLength());
      }
      rememberParameter(ep, b);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Sets the designated parameter to the given {@link TBinary} value.
   * The driver converts it to a <code>BLOB</code> value when it sends it to the
   * database.
   * The implementation translates the Binary into an InputStream and invokes
   * {@link PreparedStatement#setBinaryStream(int, java.io.InputStream, long)}.
   *
   * @param p the first parameter is 1, the second is 2, ...
   * @param b the parameter value, null if the value should be set to SQL NULL
   */
  public void setTBinary(int p, TBinary<?> b) {
    try {
      int ep = effectivePosition(p);
      if (b == null || b.getLength() == 0) {
        // "empty" binaries are treated as null
        b = null;
        getStatement().setNull(ep, Types.LONGVARBINARY);
      }
      else {
        // notice: setBlob would create a large object on backends mapping BLOB to a plain binary
        // type such as BYTEA (postgres), BYTE (informix) or VARBINARY (mssql)
        getStatement().setBinaryStream(ep, b.getInputStream(), b.getLength());
      }
      rememberParameter(ep, b);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

}
