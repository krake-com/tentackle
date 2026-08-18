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

import org.tentackle.log.Logger;
import org.tentackle.session.ConstraintException;
import org.tentackle.session.PersistenceException;

import java.lang.ref.WeakReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A wrapper for sql statements.<br>
 * Will catch and report SQLExceptions and
 * keep track of being used only once after {@link Db#createStatement()}.
 *
 * @author harald
 */
public class StatementWrapper implements AutoCloseable {

  private static final Logger LOGGER = Logger.get(StatementWrapper.class);


  /** the managed connection. */
  protected final ManagedConnection con;

  /** the sql statement, null if closed. */
  protected Statement stmt;

  /** the SQL string. */
  protected String sql;

  /** flag if the statement is marked ready for being consumed. */
  protected boolean ready;

  /** flag if the statement is currently running. */
  protected boolean running;

  /** flag if the statement has been canceled. */
  protected boolean canceled;

  /** flag to tell that parallel execution should not be logged. */
  protected boolean parallelOk;

  /** optional stacktrace that invoked the last query or update. */
  protected String stackTrace;

  /** the pending open result set. */
  protected WeakReference<ResultSetWrapper> openResultSet;


  /**
   * Creates a wrapper for a sql statement.
   *
   * @param con the connection
   * @param stmt the SQL statement
   */
  public StatementWrapper (ManagedConnection con, Statement stmt) {
    this.con  = con;
    this.stmt = stmt;
  }


  /**
   * Gets the connection.
   *
   * @return the connection
   */
  public ManagedConnection getConnection() {
    return con;
  }

  /**
   * Gets the wrapped statement.
   *
   * @return the statement, null if closed
   */
  public Statement getStatement()  {
    return stmt;
  }

  /**
   * Returns whether parallel execution on the same connection should be logged.
   *
   * @see ManagedConnection#getLogLevelForParallelOpenResultSets()
   * @return true if parallel is select ok
   */
  public boolean isParallelOk() {
    return parallelOk;
  }

  /**
   * Sets whether parallel execution on the same connection should be logged.<br>
   * Multiple open result sets on a single connection usually mean that
   * other selects are submitted while processing the result set. Under rare conditions
   * this might be ok, but usually it's not.
   *
   * @param parallelOk true if parallel select is ok
   */
  public void setParallelOk(boolean parallelOk) {
    this.parallelOk = parallelOk;
  }

  /**
   * Gets the optional stacktrace that invoked the last query or update.
   *
   * @return the stacktrace as a string, null if none (default)
   */
  public String getStackTrace() {
    return stackTrace;
  }

  /**
   * Sets an optional stacktrace that invoked the last query or update.<br>
   * If set, the stacktrace will be logged in {@link #toString()}.
   *
   * @param stackTrace the stacktrace as a string, null to clear
   */
  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  /**
   * Gets the currently attached session.
   *
   * @return the db, null if statement not attached
   */
  public Db getSession() {
    Db db = con.getSession();
    if (db == null && !con.isDead()) {
      LOGGER.warning("statement " + this + " not attached to connection " + con);
    }
    return db;
  }

  /**
   * Same as {@link #getSession()} but nullsafe.<br>
   * Throws PersistenceException if statement no more attached to the connection.
   *
   * @return the session, never null
   */
  public Db getAttachedSession() {
    Db db = con.getSession();
    if (db == null) {
      throw new PersistenceException("connection " + con + " already detached from statement " + this);
    }
    return db;
  }

  /**
   * Marks the statement to be ready for being consumed by a {@link Db} attached
   * to a {@link ConnectionManager}.<br>
   * This is an additional measure to enforce the programming rule that
   * a statement is being used only once after {@link Db#createStatement()}
   * (for non-prepared statements) or after {@link Db#getPreparedStatement}
   * for prepared statements.<p>
   * If a statement is marked ready more than once, i.e., an open result
   * exists (which would be closed according to the JDBC specs), a PersistenceException is thrown.
   * The specs in {@link java.sql.Statement} say:
   * <pre>
   * By default, only one ResultSet object per Statement object can be open at the same time.
   * Therefore, if the reading of one ResultSet object is interleaved with the reading of another,
   * each must have been generated by different Statement objects.
   * All execution methods in the Statement interface implicitly close a statement's current
   * ResultSet object if an open one exists.
   * </pre>
   * Without this additional measure a "ResultSet closed" exception will be thrown by the
   * JDBC-driver on the next usage of the first result set, and you wouldn't have any clue which
   * result set forced the closing.
   */
  public synchronized void markReady() {
    if (isMarkedReady()) {
      String msg = "statement " + this + " marked ready and not consumed yet";
      Db session = con.getSession();
      forceDetached();
      throw new PersistenceException(session, msg);
    }
    ready = true;
    canceled = false;
  }

  /**
   * Un-marks (consumes) this statement previously marked ready.
   */
  public synchronized void unmarkReady() {
    if (!isClosed() && !isMarkedReady()) {
      String msg = "statement " + this + " already consumed";
      Db session = con.getSession();
      forceDetached();
      throw new PersistenceException(session, msg);
    }
    ready = false;
    setRunning(false);
  }

  /**
   * Returns whether this statement is marked ready.<br>
   * Used to determine unused pending statements in servers when closing
   * dead sessions.
   * @return true if pending
   */
  public synchronized boolean isMarkedReady() {
    return ready;
  }

  /**
   * Consume a statement without executing it.<br>
   * The method is invoked if a rollback is performed before consuming the statement.
   */
  public void consume() {
    LOGGER.fine("statement {0} consumed", this);
    unmarkReady(); // check for being marked ready and mark consumed
    detachSession();
  }

  /**
   * Gets the SQL string.
   *
   * @return the SQL string
   */
  public String getSql() {
    return sql;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("{ ");
    if (sql != null) {
      buf.append(sql);
    }
    else if (stmt != null) {
      buf.append(stmt);
    }
    else {
      return "<closed statement>";
    }
    buf.append(" }");
    if (stmt == null) {
      buf.append(" (closed)");
    }
    if (stackTrace != null) {
      buf.append(", last invocation:").append(stackTrace);
    }
    return buf.toString();
  }

  /**
   * Executes the given SQL statement.
   *
   * @param sql an sql-statement
   * @return the row count
   */
  public int executeUpdate(String sql) {
    if (sql != null) {
      getSession().executeBatch();    // not a batchable statement -> flush batch
    }
    LOGGER.finer("execute update {0} = {1}", this, sql);
    try {
      con.countForClearWarnings();
      assertOpen();
      assertNotReadOnly();
      unmarkReady(); // check for being marked ready and mark it consumed again
      Db db = getAttachedSession();
      db.setAlive(true);
      setRunning(true);
      StatementHistory history = con.logStatementHistory(this, sql);
      int count = executeUpdateImpl(sql);
      history.end();
      return count;
    }
    catch (SQLException e)  {
      Db db = getAttachedSession();
      boolean dead = con.checkForDeadLink(e);
      if (!dead && db.getBackend().isConstraintException(e)) {
        throw new ConstraintException(db, this.toString(), e);
      }
      throw con.createFromSqlException(this.toString(), e);
    }
    finally {
      setRunning(false);
      detachSession();
    }
  }

  /**
   * Adds the given SQL command to the current list of commands.
   *
   * @param sql the SQL statement
   */
  public void addBatch(String sql) {
    LOGGER.finer("add batch {0} = {1}", this, sql);
    try {
      assertOpen();
      if (this.sql == null) {
        this.sql = sql;
      }
      else {
        this.sql += "\n" + sql;
      }
      stmt.addBatch(sql);
    }
    catch (SQLException e)  {
      throw con.createFromSqlException(this.toString(), e);
    }
  }

  /**
   * Empties this {@code Statement} object's current list of batched SQL commands.
   */
  public void clearBatch() {
    LOGGER.finer("clear batch {0}", this);
    try {
      assertOpen();
      sql = null;
      stmt.clearBatch();
    }
    catch (SQLException e)  {
      throw con.createFromSqlException(this.toString(), e);
    }
  }

  /**
   * Submits a batch of commands.
   *
   * @param finish true if this is the last invocation
   * @return an array of update counts containing one element for each command in the batch
   */
  public int[] executeBatch(boolean finish) {
    LOGGER.finer("execute batch {0} = {1}", this, sql);
    try {
      con.countForClearWarnings();
      assertOpen();
      assertNotReadOnly();
      unmarkReady(); // check for being marked ready and mark it consumed again
      Db db = getAttachedSession();
      db.setAlive(true);
      setRunning(true);
      StatementHistory history = con.logStatementHistory(this, sql);
      int[] count = stmt.executeBatch();
      history.end();
      return count;
    }
    catch (SQLException e)  {
      Db db = getAttachedSession();
      boolean dead = con.checkForDeadLink(e);
      if (!dead && db.getBackend().isConstraintException(e)) {
        throw new ConstraintException(db, this.toString(), e);
      }
      throw con.createFromSqlException(this.toString(), e);
    }
    finally {
      setRunning(false);
      if (finish) {
        detachSession();
      }
      else {
        markReady();
      }
    }
  }

  /**
   * Executes a query.
   *
   * @param sql is the query SQL string
   * @param withinTx is true if start a transaction for this query.
   *
   * @return the result set as a ResultSetWrapper
   */
  public ResultSetWrapper executeQuery (String sql, boolean withinTx) {

    getSession().executeBatch();    // not a batchable statement -> flush batch

    LOGGER.finer("execute query {0} = {1}", this, sql);
    long txVoucher = 0;

    try {
      con.countForClearWarnings();
      assertOpen();
      Db db = getAttachedSession();
      db.setAlive(true);
      setRunning(true);   // will be cleared when the result set is closed
      if (withinTx) {
        txVoucher = db.begin("executeQuery");
        // returns true if autocommit on, i.e., we were not within a transaction, and begin() started a new one.
      }
      StatementHistory history = con.logStatementHistory(this, sql);
      ResultSetWrapper resultSet = createResultSetWrapper(history, executeQueryImpl(sql));
      history.end();
      if (withinTx) {
        resultSet.setCommitOnCloseVoucher(true, txVoucher);  // commit tx on closing the resultset
      }
      return resultSet;
    }
    catch (SQLException e)  {
      Db db = getAttachedSession();
      boolean dead = con.checkForDeadLink(e);
      if (withinTx && !dead) {
        try {
          db.rollback(txVoucher);
        }
        catch (RuntimeException rex) {
          // just log
          LOGGER.severe("statement failed", e);
        }
      }
      else  {
        // dead or not within a transaction: application cannot roll back -> force cleanup
        try {
          db.forceDetached();
        }
        catch (RuntimeException rex) {
          // just log
          LOGGER.severe("detach failed", e);
        }
      }
      throw con.createFromSqlException(this.toString(), e);
    }
  }

  /**
   * Executes a query.
   *
   * @param sql the query SQL string
   * @return the result set as a ResultSetWrapper
   */
  public ResultSetWrapper executeQuery (String sql) {
    return executeQuery(sql, false);
  }

  /**
   * Returns whether the statement is being executed.<br>
   * May be invoked from any thread.
   *
   * @return true if the statement is running
   */
  public synchronized boolean isRunning() {
    return running;
  }

  /**
   * Cancels a statement.
   */
  public synchronized void cancel() {
    try {
      if (stmt != null) {
        LOGGER.info("canceling statement: {0}", this);
        stmt.cancel();
      }
      setRunning(false);
      canceled = true;
    }
    catch (SQLException e) {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Returns whether this statement has been canceled.<br>
   * May be invoked from any thread.
   *
   * @return true if canceled
   */
  public synchronized boolean isCanceled() {
    return canceled;
  }

  /**
   * Closes this statement.
   */
  @Override
  public void close ()  {
    final Statement st = stmt;    // local copy in case of race-condition
    if (st != null) {
      try {
        if (isMarkedReady()) {
          LOGGER.warning("statement " + this + " not consumed -> cleanup");
          ready = false;
        }
        st.close();   // closing an already closed statement is ok
      }
      catch (SQLException sqx)  {
        throw new PersistenceException(getSession(), toString(), sqx);
      }
      finally {
        stmt = null;  // this marks it closed!
        stackTrace = null;
        final WeakReference<ResultSetWrapper> ref = openResultSet;    // local copy in case of race-condition
        if (ref != null) {
          ResultSetWrapper rs = ref.get();
          if (rs != null) {
            try {
              rs.close();
            }
            catch (RuntimeException rex) {
              LOGGER.severe("closing result set failed for statement " + this, rex);
            }
          }
          openResultSet = null;
        }
      }
    }
  }

  /**
   * Determines whether this statement is closed.
   *
   * @return true if statement closed
   */
  public boolean isClosed() {
    return stmt == null;
  }

  /**
   * Gives the JDBC driver a hint as to the number of rows that should
   * be fetched from the database when more rows are needed for
   * <code>ResultSet</code> objects generated by this <code>Statement</code>.
   * If the value specified is zero, then the hint is ignored.
   * The default value is zero.
   *
   * @param rows the number of rows to fetch
   * @see #getFetchSize
   */
  public void setFetchSize(int rows)  {
    assertOpen();
    try {
      stmt.setFetchSize(rows);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Retrieves the number of result set rows that is the default
   * fetch size for <code>ResultSet</code> objects
   * generated from this <code>Statement</code> object.
   * If this <code>Statement</code> object has not set
   * a fetch size by calling the method <code>setFetchSize</code>,
   * the return value is implementation-specific.
   *
   * @return the default fetch size for result sets generated
   *          from this <code>Statement</code> object
   * @see #setFetchSize
   */
  public int getFetchSize() {
    assertOpen();
    try {
      return stmt.getFetchSize();
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Sets the limit for the maximum number of rows that any
   * <code>ResultSet</code> object generated by this <code>Statement</code>
   * object can contain to the given number.
   * If the limit is exceeded, the excess
   * rows are silently dropped.
   *
   * @param max the new max rows limit; zero means there is no limit
   * @see #getMaxRows
   */
  public void setMaxRows(int max)  {
    assertOpen();
    try {
      stmt.setMaxRows(max);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Retrieves the maximum number of rows that a
   * <code>ResultSet</code> object produced by this
   * <code>Statement</code> object can contain.  If this limit is exceeded,
   * the excess rows are silently dropped.
   *
   * @return the current maximum number of rows for a <code>ResultSet</code>
   *         object produced by this <code>Statement</code> object;
   *         zero means there is no limit
   * @see #setMaxRows
   */
  public int getMaxRows() {
    assertOpen();
    try {
      return stmt.getMaxRows();
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Gives the driver a hint as to the direction in which
   * rows will be processed in <code>ResultSet</code>
   * objects created using this <code>Statement</code> object.  The
   * default value is <code>ResultSet.FETCH_FORWARD</code>.
   * <P>
   * Note that this method sets the default fetch direction for
   * result sets generated by this <code>Statement</code> object.
   * Each result set has its own methods for getting and setting
   * its own fetch direction.
   *
   * @param direction the initial direction for processing rows
   * @see #getFetchDirection
   */
  public void setFetchDirection(int direction) {
    assertOpen();
    try {
      stmt.setFetchDirection(direction);
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }

  /**
   * Retrieves the direction for fetching rows from
   * database tables that is the default for result sets
   * generated from this <code>Statement</code> object.
   * If this <code>Statement</code> object has not set
   * a fetch direction by calling the method <code>setFetchDirection</code>,
   * the return value is implementation-specific.
   *
   * @return the default fetch direction for result sets generated
   *          from this <code>Statement</code> object
   * @see #setFetchDirection
   */
  public int getFetchDirection() {
    assertOpen();
    try {
      return stmt.getFetchDirection();
    }
    catch (SQLException e)  {
      throw new PersistenceException(getSession(), this.toString(), e);
    }
  }


  /**
   * Detach the session from the connection.<br>
   * Statements detach the session on executeUpdate or
   * on close() in the {@link ResultSetWrapper} after executeQuery.
   */
  protected void detachSession() {
    Db db = con.getSession();
    if (db != null) {
      // if not already cleaned up
      db.detach();
    }
  }

  /**
   * Implementation of executeUpdate.<br>
   *
   * @param sql the SQL string
   * @return the number of affected rows
   * @throws SQLException if the update failed
   */
  protected int executeUpdateImpl(String sql) throws SQLException {
    this.sql = sql;
    StatementTracer.getInstance().trace(this);
    return stmt.executeUpdate(sql);
  }

  /**
   * Implementation of executeQuery.<br>
   *
   * @param sql the SQL string
   * @return the result set
   * @throws SQLException if the query failed
   */
  protected ResultSet executeQueryImpl(String sql) throws SQLException {
    this.sql = sql;
    StatementTracer.getInstance().trace(this);
    return stmt.executeQuery(sql);
  }

  /**
   * Creates a result set wrapper from a result set.
   *
   * @param history the statement history
   * @param jdbcResultSet the low-level jdbc result set
   * @return the wrapper
   */
  protected ResultSetWrapper createResultSetWrapper(StatementHistory history, ResultSet jdbcResultSet) {
    ResultSetWrapper rs = new ResultSetWrapper(this, history, jdbcResultSet);
    con.addResultSet(rs);
    openResultSet = new WeakReference<>(rs);
    return rs;
  }

  /**
   * Forgets about the given result set.
   *
   * @param rs the result set wrapper
   */
  protected void forgetResultSetWrapper(ResultSetWrapper rs) {
    con.removeResultSet(rs);
    openResultSet = null;
  }

  /**
   * Sets the running flag of this statement.<br>
   * Updates the running statements in {@link ManagedConnection}.
   *
   * @param running true if running, false if finished
   */
  protected synchronized void setRunning(boolean running) {
    this.running = running;
    if (running) {
      con.addRunningStatement(this);
    }
    else  {
      con.removeRunningStatement(this);
    }
  }

  /**
   * Asserts the statement is open.
   */
  protected void assertOpen() {
    if (stmt == null) {
      throw new PersistenceException(getSession(), "statement already closed");
    }
  }

  /**
   * Asserts db is writable.
   */
  protected void assertNotReadOnly() {
    Db db = con.getSession();
    if (db != null && db.isReadOnly()) {
      throw new PersistenceException(db, "is read-only");
    }
  }

  /**
   * Detaches the Db after a severe error.
   * <p>
   * Invoked to clean up any pending statements, rollback the transaction (if any)
   * and detach the db after a severe error.
   * This is a cleanup measure in case the application does not handle
   * exceptions properly.
   */
  protected void forceDetached() {
    try {
      Db db = con.getSession();
      if (db != null) {
        db.forceDetached();
      }
    }
    catch (RuntimeException rex) {
      LOGGER.severe("emergency detach failed", rex);
    }
  }

}
