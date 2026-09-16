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
import org.tentackle.common.Date;
import org.tentackle.common.DateHelper;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.datatypes.ZonedDateTimeType;
import org.tentackle.common.TBinary;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper for {@link ResultSet}s.
 *
 * @author harald
 */
public class ResultSetWrapper implements AutoCloseable {

  /**
   * Enables warning logging if result sets are longer open than given milliseconds.<br>
   * 0 to disable (default).<br>
   * Useful to detect unexpected database write locks or programming errors when result sets are kept open
   * for a long time (consuming an attached connection), for example, due to expensive processing while reading the result set.
   */
  public static long warnOpenMillis = 0L;


  private static final Logger LOGGER = Logger.get(ResultSetWrapper.class);

  /**
   * Column name suffix for 2nd column in double-column predefined types.
   *
   * @see DataType#getColumnSuffix(org.tentackle.sql.Backend, int)
   */
  private static final String COL2_SUFFIX = "_2";

  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources and state to be cleaned up.
   */
  private static class Resources implements Runnable {

    private final StatementHistory history;         // statement history to log duration and number of rows fetched
    private final TimeKeeper duration;              // duration until closed
    private StatementWrapper stmt;                  // the statement wrapper
    private ResultSet rs;                           // the wrapped result set, null if closed
    private Db db;                                  // db from statement
    private Backend backend;                        // the backend
    private ResultSetSkipBlock skipBlock;           // current skip block
    private boolean commitOnCloseVoucherValid;      // true if commitOnCloseVoucher has been set
    private long commitOnCloseVoucher;              // != 0 if issue a commit() before close()
    private List<ResultSetSkipBlock> skipBlocks;    // list of skip blocks (null if not in skip mode)
    private boolean closeStatementOnClose;          // true = close the statement after the resultset is closed
    private ResultSetWrapper me;                    // != null if normal close, else cleanup
    private int rowCount;                           // number of rows fetched

    Resources(StatementWrapper stmt, StatementHistory history, ResultSet rs) {
      this.stmt = stmt;
      this.history = history;
      this.rs = rs;
      db = stmt.getSession();
      if (db == null) {
        stmt.consume();
        throw new PersistenceException("connection already closed for " + this);
      }
      backend = db.getBackend();
      // enable statement-auto-closing if the statement is a non-prepared statement or a prepared one-shot
      closeStatementOnClose = !(stmt instanceof PreparedStatementWrapper) ||
                              ((PreparedStatementWrapper) stmt).getStatementKey() == null;
      duration = new TimeKeeper();
    }

    @Override
    public void run() {
      try {
        final ResultSet rset = rs;    // local copy in case of race-condition
        if (rset != null) {
          try {
            final StatementWrapper st = stmt;
            if (st != null && !st.isClosed()) {
              st.unmarkReady();
              if (me == null) {
                LOGGER.warning("closing unreferenced result set wrapper for {0}", st);
              }
              else {
                // removes me from stmt.close (see below)
                st.forgetResultSetWrapper(me);    // st ref to me is a weak ref, so no harm if not done in cleanup
              }
              final boolean voucherValid = commitOnCloseVoucherValid;
              final Db session = db;
              if (voucherValid && session != null) {
                session.commit(commitOnCloseVoucher);
                commitOnCloseVoucher = 0;
                commitOnCloseVoucherValid = false;
              }
            }

            rset.close();   // this is ok even if already closed (according to the API)

            history.applyFetch(duration.end(), rowCount);

            if (warnOpenMillis > 0) {
              double millis = duration.millis();
              if (millis > warnOpenMillis) {
                if (me != null) {
                  // log with stacktrace of closing caller
                  LOGGER.warning("result set wrapper " + me + " open for " + millis + "ms", new PersistenceException(db, "late closing"));
                }
                else {
                  // closed by cleanup (stacktrace makes no sense)
                  LOGGER.warning("unreferenced result set wrapper open for {0}ms, statement {1}", millis, st);
                }
              }
            }
          }
          catch (SQLException | RuntimeException ex) {
            if (me != null) {
              LOGGER.severe("closing result set wrapper " + me + " failed", ex);
            }
            else {
              LOGGER.severe("closing unreferenced result set wrapper failed for statement " + stmt, ex);
            }
          }
          finally {
            db = null;
            rs = null;
            backend = null;
            skipBlock = null;
            skipBlocks = null;
            final StatementWrapper st = stmt;
            if (st != null) {
              st.detachSession();
              if (closeStatementOnClose) {
                st.close();
              }
              stmt = null;
            }
          }
        }
      }
      finally {
        me = null;
      }
    }
  }


  private final Resources resources;                // resources to be cleaned up
  private final Cleanable cleanable;                // to clean up the result set
  private int columnOffset;                         // offset to add to the column index (e.g., for eager loading / joins)
  private int columnCount;                          // number of columns returned, -1 if unknown
  private ResultSetMetaData metaData;               // the metadata, null if not retrieved yet
  private int skipBlockIndex;                       // the current skip block
  private boolean cursorMovedInSkipMode;            // true if the cursor moved the first time while in skip mode
  private Map<Object,ResultSetSection> sections;    // the configured column sections
  private ResultSetSection currentSection;          // current section, null if none


  /**
   * Creates a wrapper for the given result set.
   *
   * @param stmt is the prepared statement wrapper that created this result set
   * @param history the statement history
   * @param rs the original result-set
   */
  public ResultSetWrapper (StatementWrapper stmt, StatementHistory history, ResultSet rs) {
    resources = new Resources(stmt, history, rs);
    columnCount = -1;
    cleanable = CLEANER.register(this, resources);
  }


  /**
   * Gets the session associated with the result set.
   *
   * @return the db
   */
  public Db getSession() {
    return resources.db;
  }

  /**
   * Gets the statement.
   *
   * @return the statement being executed for this result set
   */
  public StatementWrapper getStatement() {
    return resources.stmt;
  }

  @Override
  public String toString() {
    StatementWrapper stmt = resources.stmt;
    return stmt == null ? "<closed result set>" : stmt.toString();
  }


  /**
   * Gets the metadata.
   *
   * @return the metadata
   */
  public ResultSetMetaData getMetaData() {
    if (metaData == null) {
      try {
        metaData = resources.rs.getMetaData();
      }
      catch (SQLException sqx) {
        throw new PersistenceException(resources.db, sqx);
      }
    }
    return metaData;
  }


  /**
   * Closes the resultset.
   */
  @Override
  public void close () {
    resources.me = this;      // programmatic close, not a cleanup
    cleanable.clean();
  }


  /**
   * Determines whether the result set is closed.
   *
   * @return true if closed
   */
  public boolean isClosed() {
    return resources.rs == null;
  }


  /**
   * Set whether to commit() before close() or not.<br>
   * Some dbms (e.g., Postgres) cannot use cursors outside a transaction.
   * In this case a transaction must be started before executeQuery()
   * (see PreparedStatementWrapper and Query)
   * and closed when the resultset (or the cursor) is closed.
   * The default is not to commit on close.
   *
   * @param commitOnCloseVoucherValid true if commitOnCloseVoucher is valid
   * @param commitOnCloseVoucher the voucher to commit, 0 if none
   */
  public void setCommitOnCloseVoucher(boolean commitOnCloseVoucherValid, long commitOnCloseVoucher) {
    resources.commitOnCloseVoucherValid = commitOnCloseVoucherValid;
    resources.commitOnCloseVoucher = commitOnCloseVoucher;
  }


  /**
   * Returns whether the commitOnCloseVoucher is valid.
   *
   * @return true if perform a commit on close
   */
  public boolean isCommitOnCloseVoucherValid() {
    return resources.commitOnCloseVoucherValid;
  }

  /**
   * Gets the commit-on-close voucher.
   *
   * @return the commit on close transaction voucher
   */
  public long getCommitOnCloseVoucher()  {
    if (!isCommitOnCloseVoucherValid()) {
      throw new PersistenceException("commitOnCloseVoucher is not valid");
    }
    return resources.commitOnCloseVoucher;
  }



  /**
   * Set the statement to be closed when the result set is closed.<br>
   * By default, the statement remains open.
   *
   * @param closeStatementOnClose true to close the statement on close
   */
  public void setCloseStatementOnclose(boolean closeStatementOnClose) {
    resources.closeStatementOnClose = closeStatementOnClose;
  }

  /**
   * Gets the statement-on-close flag.
   *
   * @return true to close the statement on close
   */
  public boolean isCloseStatementOnclose()  {
    return resources.closeStatementOnClose;
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
   * Gets the number of columns in the resultset.
   *
   * @return the number of columns
   */
  public int getColumnCount() {
    if (columnCount == -1) {
      try {
        columnCount = getMetaData().getColumnCount();
      }
      catch (SQLException sqx) {
        throw new PersistenceException(resources.db, sqx);
      }
    }
    return columnCount;
  }


  /**
   * Gets the column name.
   *
   * @param column the first column is 1, the second is 2, ...
   * @return the column name
   */
  public String getColumnName(int column) {
    try {
      return getMetaData().getColumnName(column);
    }
    catch (SQLException sqx) {
      throw new PersistenceException(resources.db, sqx);
    }
  }


  /**
   * Returns whether the result set is in skipmode.
   *
   * @return true if in skipmode
   */
  public boolean isInSkipMode() {
    return resources.skipBlocks != null;
  }

  /**
   * Maps the given column name to its column index.
   * <p>
   * If the result set is in skip mode, the first column with that name is found
   * after having skipped some columns according to the last invocation of {@link #skip()}.
   * Otherwise, the standard JDBC implementation is used.
   *
   * @param name the column name
   * @return the index in the result set
   */
  public int findColumn(String name) {
    if (isInSkipMode()) {    // skipmode
      int ndx = resources.skipBlock.findColumn(name);
      return ndx - columnOffset;
    }

    // else standard mode
    try {
      return resources.rs.findColumn(name) - columnOffset;
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Skips the given number of columns in the current skip block.<br>
   * This is usually the column count of an entity.
   *
   * @param columnCount the number of columns
   */
  public void skipColumns(int columnCount) {
    assertInSkipMode();
    resources.skipBlock.applyColumnIndex(resources.skipBlock.getSkippedColumns() + columnCount);
  }

  /**
   * Starts or restarts skip mode.
   * <p>
   * Skipmode allows multiple columns with the same name belonging to different entities.
   * For example, in a joined SELECT of two unrelated tables (i.e., no MULTI-inheritance),
   * at least the columns id and serial will appear twice. With skip mode enabled,
   * the column configuration allows skipping already configured columns (hence the name skip mode).
   * <p>
   * Skipmode is implemented by changing the way {@link #findColumn(java.lang.String)} maps names to column indexes.
   * All other methods are unaffected.
   *
   * <pre>
   * rs.startSkip();
   * int foo_a = rs.findColumn("a");
   * int foo_b = rs.findColumn("b");
   * ...
   * rs.skip();                         // skips to the next occurrences
   * int bar_a = rs.findColumn("a");    // will find the second column named "a"
   * int bar_b = rs.findColumn("b");    // will find the second column named "b"
   * ...
   * rs.skip();             // for the third, etc...
   * </pre>
   *
   * Invoking startSkip again will revert to the first skip block.
   * <p>
   * For performance reasons the skip blocks are reused as soon as the cursor is moved,
   * so the following is allowed:
   * <pre>
   * while (rs.next()) {
   *   rs.startSkip();
   *   po1 = po1.readFromResultSetWrapper(rs);
   *   rs.skip();
   *   po2 = po2.readFromResultSetWrapper(rs);
   * }
   * </pre>
   *
   * For multi-inherited entities it is required to apply the column count as well since
   * not all sections may be retrieved depending on the classids found in the result set.
   * So the code above changes to:
   * <pre>
   * while (rs.next()) {
   *   rs.startSkip();
   *   po1 = po1.readFromResultSetWrapper(rs);
   *   rs.skip(po1.getColumnCount());
   *   po2 = po2.readFromResultSetWrapper(rs);
   * }
   * </pre>
   *
   */
  public void startSkip() {
    if (cursorMovedInSkipMode) {
      assertInSkipMode();
      // just rewind
      resources.skipBlock = resources.skipBlocks.getFirst();
      skipBlockIndex = 0;
    }
    else  {
      // build first skipblock
      resources.skipBlock = new ResultSetSkipBlock(this, 0);
      resources.skipBlocks = new ArrayList<>();
      resources.skipBlocks.add(resources.skipBlock);
      cursorMovedInSkipMode = false;
    }
  }

  /**
   * Ends skip mode.
   */
  public void endSkip() {
    resources.skipBlocks = null;
    resources.skipBlock = null;
  }

  /**
   * Returns the currently configured skip blocks.
   * <P>
   * A skip block is a map of column names to column indexes.
   * The first skip block contains all columns of the result set.
   * If a column name appears more than once in the result set,
   * the _first_ column index is used.
   * <p>
   * Subsequent blocks contain all columns not configured by findColumn
   * from the previous block.
   *
   * @return the skip blocks
   */
  public List<ResultSetSkipBlock> getSkipBlocks() {
    assertInSkipMode();
    return resources.skipBlocks;
  }

  /**
   * Gets the index to the current skip block.
   *
   * @return the current index, 0 if first
   */
  public int getSkipBlockIndex() {
    assertInSkipMode();
    return skipBlockIndex;
  }

  /**
   * Skips to the next logical entity.
   * @see #startSkip()
   * @throws PersistenceException if not in skipmode
   */
  public void skip() {
    assertInSkipMode();
    if (cursorMovedInSkipMode) {
      // re-use old skip blocks
      skipBlockIndex++;
      if (skipBlockIndex >= resources.skipBlocks.size()) {
        throw new PersistenceException(resources.db, "only " + resources.skipBlocks.size() + " skip blocks used so far in " + this);
      }
      resources.skipBlock = resources.skipBlocks.get(skipBlockIndex);
    }
    else  {
      int skippedColumns = resources.skipBlock == null ? 0 : resources.skipBlock.getMaxColumnIndex();
      resources.skipBlock = new ResultSetSkipBlock(this, skippedColumns);
      resources.skipBlocks.add(resources.skipBlock);
    }
  }

  /**
   * Performs a {@link #skipColumns} followed by a {@link #skip} in one method call.<br>
   * Provided to save typing only.
   *
   * @param columnCount the columns to skip
   */
  public void skip(int columnCount) {
    skipColumns(columnCount);
    skip();
  }


  /**
   * Configures a section within this resultset for retrieval.
   * <p>
   * A section is a logical group of columns within the result set.<br>
   * There must be at least one section activated to retrieve columns
   * by section configuration instead of the traditional way by position or by name.<br>
   * A section may span the whole result-set, or there may be more than one section,
   * for example, one for each joined table. It's up to the application.<br>
   * If {@code configureSection} returns true, a new section was created and
   * the columns of this section must be configured once via {@link #configureColumn} in
   * the order of their retrieval.<br>
   * Otherwise, the section is already configured.<br>
   * This is the preferred way to retrieve values from a result-set since
   * it is the best compromise between speed and flexibility with respect to
   * the order of the returned columns.<p>
   *
   * Example:
   * <pre>
   *  if (rs.activateSection(CLASSVARIABLES)) {
   *    rs.configureColumn(CN_OBJECTCLASS);
   *    rs.configureColumn(CN_OBJECTID);
   *    ...
   *  }
   *  objectClass = rs.getString();
   *  objectId = rs.getLong();
   *  contextObjectId = rs.getLong();
   *  ...
   * </pre>
   *
   * @param key the unique section key, usually the class variables
   * @return true if columns of the new section must be configured
   * @see #configureColumn(java.lang.String)
   */
  public boolean configureSection(Object key) {
    if (sections == null) {
      sections = new HashMap<>();
    }
    currentSection = sections.get(key);
    if (currentSection == null) {
      currentSection = new ResultSetSection(this, key);
      sections.put(key, currentSection);
      return true;
    }
    currentSection.resetColumnIndex();
    return false;
  }


  /**
   * Configures a column of the current section for automatic retrieval.<br>
   * The method can only be used for predefined types!
   *
   * @param columnName the column name
   */
  public void configureColumn(String columnName) {
    assertValidSection();
    currentSection.configureColumn(columnName);
  }

  /**
   * Configures all columns for the given name and datatype.<br>
   * The method is mainly provided for backend-specific datatypes but can be used for any datatype.
   *
   * @param dataType the datatype
   * @param columnName the column name (basename from the model)
   */
  public void configureColumns(DataType<?> dataType, String columnName) {
    assertValidSection();
    int columnCount = dataType.getColumnCount(resources.backend);
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      currentSection.configureColumn(columnName + dataType.getColumnSuffix(resources.backend, columnIndex).orElse(""));
    }
  }


  /**
   * Gives the JDBC driver a hint as to the number of rows that should
   * be fetched from the database when more rows are needed for this
   * <code>ResultSet</code> object.
   * If the fetch size specified is zero, the JDBC driver
   * ignores the value and is free to make its own best guess as to what
   * the fetch size should be.  The default value is set by the
   * <code>Statement</code> object
   * that created the result set.  The fetch size may be changed at any time.
   *
   * @param rows the number of rows to fetch
   * @see #getFetchSize
   */
  public void setFetchSize(int rows) {
    try {
      resources.rs.setFetchSize(rows);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the fetch size for this
   * <code>ResultSet</code> object.
   *
   * @return the current fetch size for this <code>ResultSet</code> object
   * @see #setFetchSize
   */
  public int getFetchSize() {
    try {
      return resources.rs.getFetchSize();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }

  }



  /**
   * Gives a hint as to the direction in which the rows in this
   * <code>ResultSet</code> object will be processed.
   * The initial value is determined by the
   * <code>Statement</code> object
   * that produced this <code>ResultSet</code> object.
   * The fetch direction may be changed at any time.
   *
   * @param direction an <code>int</code> specifying the suggested
   *        fetch direction; one of <code>ResultSet.FETCH_FORWARD</code>,
   *        <code>ResultSet.FETCH_REVERSE</code>, or
   *        <code>ResultSet.FETCH_UNKNOWN</code>
   * @see StatementWrapper#setFetchDirection
   * @see #getFetchDirection
   */
  public void setFetchDirection(int direction) {
    try {
      resources.rs.setFetchDirection(direction);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the fetch direction for this
   * <code>ResultSet</code> object.
   *
   * @return the current fetch direction for this <code>ResultSet</code> object
   * @see #setFetchDirection
   */
  public int getFetchDirection() {
    try {
      return resources.rs.getFetchDirection();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor forward one row from its current position.<br>
   * A <code>ResultSet</code> cursor is initially positioned
   * before the first row; the first call to the method
   * <code>next</code> makes the first row the current row; the
   * second call makes the second row the current row, and so on.
   * <p>
   * When a call to the <code>next</code> method returns <code>false</code>,
   * the cursor is positioned after the last row. Any
   * invocation of a <code>ResultSet</code> method which requires a
   * current row will result in a <code>SQLException</code> being thrown.
   * If the result set type is <code>TYPE_FORWARD_ONLY</code>, it is vendor specified
   * whether their JDBC driver implementation will return <code>false</code> or
   * throw an <code>SQLException</code> on a further call to <code>next</code>.
   * <p>
   * If an input stream is open for the current row, a call
   * to the method <code>next</code> will
   * implicitly close it. A <code>ResultSet</code> object's
   * warning chain is cleared when a new row is read.
   *
   * @return <code>true</code> if the new current row is valid;
   * <code>false</code> if there are no more rows
   */
  public boolean next()  {
    try {
      clearSection();
      boolean valid = resources.rs.next();
      if (valid) {
        resources.rowCount++;
      }
      return valid;
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the previous row in this
   * <code>ResultSet</code> object.
   *<p>
   * When a call to the <code>previous</code> method returns <code>false</code>,
   * the cursor is positioned before the first row.  Any invocation of a
   * <code>ResultSet</code> method which requires a current row will result in a
   * <code>SQLException</code> being thrown.
   *<p>
   * If an input stream is open for the current row, a call to the method
   * <code>previous</code> will implicitly close it.  A <code>ResultSet</code>
   *  object's warning change is cleared when a new row is read.
   *
   * @return <code>true</code> if the cursor is now positioned on a valid row;
   * <code>false</code> if the cursor is positioned before the first row
   */
  public boolean previous ()  {
    try {
      clearSection();
      return resources.rs.previous();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the first row in
   * this <code>ResultSet</code> object.
   *
   * @return <code>true</code> if the cursor is on a valid row;
   * <code>false</code> if there are no rows in the result set
   */
  public boolean first ()  {
    try {
      clearSection();
      return resources.rs.first();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the last row in
   * this <code>ResultSet</code> object.
   *
   * @return <code>true</code> if the cursor is on a valid row;
   * <code>false</code> if there are no rows in the result set
   */
  public boolean last ()  {
    try {
      clearSection();
      return resources.rs.last();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the front of
   * this <code>ResultSet</code> object, just before the
   * first row. This method has no effect if the result set contains no rows.
   */
  public void beforeFirst()  {
    try {
      clearSection();
      resources.rs.beforeFirst();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the end of
   * this <code>ResultSet</code> object, just after the
   * last row. This method has no effect if the result set contains no rows.
   */
  public void afterLast()  {
    try {
      clearSection();
      resources.rs.afterLast();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves whether the cursor is before the first row in
   * this <code>ResultSet</code> object.
   * <p>
   * <strong>Note:</strong>Support for the <code>isBeforeFirst</code> method
   * is optional for <code>ResultSet</code>s with a result
   * set type of <code>TYPE_FORWARD_ONLY</code>
   *
   * @return <code>true</code> if the cursor is before the first row;
   * <code>false</code> if the cursor is at any other position or the
   * result set contains no rows
   */
  public boolean isBeforeFirst()  {
    try {
      return resources.rs.isBeforeFirst();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves whether the cursor is after the last row in
   * this <code>ResultSet</code> object.
   * <p>
   * <strong>Note:</strong>Support for the <code>isAfterLast</code> method
   * is optional for <code>ResultSet</code>s with a result
   * set type of <code>TYPE_FORWARD_ONLY</code>
   *
   * @return <code>true</code> if the cursor is after the last row;
   * <code>false</code> if the cursor is at any other position or the
   * result set contains no rows
   */
  public boolean isAfterLast()  {
    try {
      return resources.rs.isAfterLast();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the current row number.<br>
   * The first row is number 1, the second number 2, and so on.
   * <p>
   * <strong>Note:</strong>Support for the <code>getRow</code> method
   * is optional for <code>ResultSet</code>s with a result
   * set type of <code>TYPE_FORWARD_ONLY</code>
   *
   * @return the current row number; <code>0</code> if there is no current row
   */
  public int getRow ()  {
    try {
      return resources.rs.getRow();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor to the given row number in
   * this <code>ResultSet</code> object.
   *
   * <p>If the row number is positive, the cursor moves to
   * the given row number with respect to the
   * beginning of the result set.  The first row is row 1, the second
   * is row 2, and so on.
   *
   * <p>If the given row number is negative, the cursor moves to
   * an absolute row position with respect to
   * the end of the result set.  For example, calling the method
   * <code>absolute(-1)</code> positions the
   * cursor on the last row; calling the method <code>absolute(-2)</code>
   * moves the cursor to the next-to-last row, and so on.
   *
   * <p>An attempt to position the cursor beyond the first/last row in
   * the result set leaves the cursor before the first row or after
   * the last row.
   *
   * <p><B>Note:</B> Calling <code>absolute(1)</code> is the same
   * as calling <code>first()</code>. Calling <code>absolute(-1)</code>
   * is the same as calling <code>last()</code>.
   *
   * @param row the number of the row to which the cursor should move.
   *        A positive number indicates the row number counting from the
   *        beginning of the result set; a negative number indicates the
   *        row number counting from the end of the result set
   * @return <code>true</code> if the cursor is moved to a position in this
   * <code>ResultSet</code> object;
   * <code>false</code> if the cursor is before the first row or after the
   * last row
   */
  public boolean absolute (int row)  {
    try {
      clearSection();
      return resources.rs.absolute(row);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Moves the cursor a relative number of rows, either positive or negative.
   * Attempting to move beyond the first/last row in the
   * result set positions the cursor before/after
   * the first/last row. Calling <code>relative(0)</code> is valid, but does
   * not change the cursor position.
   *
   * <p>Note: Calling the method <code>relative(1)</code>
   * is identical to calling the method <code>next()</code> and
   * calling the method <code>relative(-1)</code> is identical
   * to calling the method <code>previous()</code>.
   *
   * @param rows an <code>int</code> specifying the number of rows to
   *        move from the current row; a positive number moves the cursor
   *        forward; a negative number moves the cursor backward
   * @return <code>true</code> if the cursor is on a row;
   *         <code>false</code> otherwise
   */
  public boolean relative (int rows)  {
    try {
      clearSection();
      return resources.rs.relative(rows);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Reports whether
   * the last column read had a value of SQL <code>NULL</code>.
   * Note that you must first call one of the getter methods
   * on a column to try to read its value and then call
   * the method <code>wasNull</code> to see if the value read was
   * SQL <code>NULL</code>.
   *
   * @return <code>true</code> if the last column value read was SQL
   *         <code>NULL</code> and <code>false</code> otherwise
   */
  public boolean wasNull() {
    try {
      return resources.rs.wasNull();
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves a list of DTOs from this resultset.<br>
   * Intended for projection queries, i.e. queries delivering just data, not PDOs.
   * Such queries are usually built by the {@link org.tentackle.dbms.dsl.DSL},
   * which provides {@code toList} as a terminal operation.
   *
   * @param clazz the DTO class
   * @param <T> the DTO type
   * @return the list of objects
   * @see DbUtilities#resultSetToList(ResultSetWrapper, Class)
   * @see org.tentackle.dbms.dsl.Select#toList(Db, Class)
   */
  public <T> List<T> toList(Class<T> clazz) {
    return DbUtilities.getInstance().resultSetToList(this, clazz);
  }



  // ---------------------- get values by column label ---------------------------


  /**
   * Retrieves the value of the designated column in the current row.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Object getObject (String name)  {
    try {
      return resources.rs.getObject(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getString (String name, boolean mapNull)  {
    try {
      String str = resources.rs.getString (name);
      if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
        return null;
      }
      return str;
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getString (String name) {
    return getString(name, false);
  }


  /**
   * Gets a large string.<br>
   * The backend usually stores such strings in a CLOB.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getLargeString (String name, boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isClobSupported()) {
        Clob clob = resources.rs.getClob(name);
        String str;
        if (clob != null) {
          str = clob.getSubString(1, (int) clob.length());
          clob.free();
        }
        else {
          str = null;
        }
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(name, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getLargeString (String name) {
    return getLargeString(name, false);
  }

  /**
   * Gets a string bridged through the JDBC {@code SQLXML} interface.<br>
   * The backend usually stores such strings in a native XML column, or emulates it as CLOB/VARCHAR.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getSqlxml (String name, boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isSqlxmlSupported()) {
        SQLXML sqlxml = resources.rs.getSQLXML(name);
        String str = null;
        if (sqlxml != null) {
          str = sqlxml.getString();
          sqlxml.free();
        }
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(name, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getSqlxml(java.lang.String, boolean)
   */
  public String getSqlxml (String name) {
    return getSqlxml(name, false);
  }

  /**
   * Gets a string holding a JSON document.<br>
   * The backend usually stores such strings in a native JSON column, or emulates it as CLOB/VARCHAR.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull if empty strings should be treated as null values, ignored for a native JSON column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getJson (String name, boolean mapNull)  {
    try {
      Backend backend = resources.stmt.getConnection().getBackend();
      if (backend.isJsonSupported()) {
        return backend.getJson(resources.rs, resources.rs.findColumn(name));   // the hook is position-based
      }
      else {
        return getString(name, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Gets a string holding a JSON document.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getJson(java.lang.String, boolean)
   */
  public String getJson (String name) {
    return getJson(name, false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>false</code>
   */
  public boolean getBoolean (String name) {
    try {
      return resources.rs.getBoolean(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Boolean getABoolean (String name)  {
    boolean value = getBoolean(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>float</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public float getFloat (String name) {
    try {
      return resources.rs.getFloat(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Float</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Float getAFloat (String name)  {
    float value = getFloat(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>double</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public double getDouble (String name) {
    try {
      return resources.rs.getDouble(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Double</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Double getADouble (String name)  {
    double value = getDouble(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>java.math.BigDecimal</code> with full precision.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BigDecimal getBigDecimal (String name) {
    try {
      return resources.rs.getBigDecimal(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>BMoney</code> with full precision.<br>
   * Notice that BMoney fields use two fields:
   * one for the value and
   * one for the scale (= name + "_2")
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BMoney getBMoney (String name) {
    double value = getDouble(name);
    return wasNull() ? null : new BMoney (value, getInt(name + COL2_SUFFIX));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>DMoney</code> with full precision.<br>
   * Notice that DMoney fields use two fields:
   * one for the value and
   * one for the scale (= name + "_2")
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public DMoney getDMoney (String name) {
    BigDecimal decimal = getBigDecimal(name);
    return wasNull() ? null : new DMoney(decimal.movePointLeft(getInt(name + COL2_SUFFIX)));
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>byte</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public byte getByte (String name) {
    try {
      return resources.rs.getByte(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


/**
 * Retrieves the value of the designated column in the current row
 * of this <code>ResultSet</code> object as
 * a <code>Byte</code> in the Java programming language.
 *
 * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
 * @return the column value; if the value is SQL <code>NULL</code>, the
 * value returned is <code>null</code>
 */
  public Byte getAByte (String name)  {
    byte value = getByte(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>char</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code> or the empty string, the
   * value returned is <code>0</code>
   */
  public char getChar (String name) {
    try {
      String val = resources.rs.getString(name);
      return val == null || val.isEmpty() ? 0 : val.charAt(0);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *        If the SQL AS clause was not specified, then the label is the name of the column.
   * @param mapNull if blanks should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   */
  public Character getCharacter (String name, boolean mapNull)  {
    char value = getChar(name);
    return wasNull() ? null : (mapNull && value == ' ' ? null : value);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *        If the SQL AS clause was not specified, then the label is the name of the column.
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   */
  public Character getCharacter (String name)  {
    return getCharacter(name, false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>short</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public short getShort (String name) {
    try {
      return resources.rs.getShort(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Short</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Short getAShort (String name)  {
    short value = getShort(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>int</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public int getInt (String name) {
    try {
      return resources.rs.getInt(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Integer</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Integer getInteger (String name)  {
    int value = getInt(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>long</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public long getLong (String name)  {
    try {
      return resources.rs.getLong(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Long</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Long getALong (String name)  {
    long value = getLong(name);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *  If the SQL AS clause was not specified, then the label is the name of the column
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(String name, Calendar timezone, boolean mapNull)  {
    try {
      java.sql.Date date = timezone == null ?
              resources.rs.getDate(name) : resources.rs.getDate(name, timezone);
      if (date == null ||
          (mapNull && date.equals(DateHelper.MIN_DATE)))  {
        // mindate is translated back to null
        return null;
      }
      return Date.createFrozen(date.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate(String name, boolean mapNull)  {
    Date date = getDate(name, mapNull);
    return date == null ? null : date.toLocalDate();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *  If the SQL AS clause was not specified, then the label is the name of the column
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(String name, Calendar timezone) {
    return getDate(name, timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *  If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(String name, boolean mapNull)  {
    return getDate(name, null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(String name)  {
    return getDate(name, null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate(String name)  {
    return getLocalDate(name, false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(String name, Calendar timezone, boolean mapNull)  {
    try {
      java.sql.Timestamp ts = timezone == null ?
              resources.rs.getTimestamp(name) : resources.rs.getTimestamp(name, timezone);
      if (ts == null ||
          (mapNull && ts.equals(DateHelper.MIN_TIMESTAMP)))  {
        // mintimestamp is translated back to null
        return null;
      }
      return Timestamp.createFrozen(ts.getTime(), ts.getNanos());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime(String name, boolean mapNull)  {
    Timestamp ts = getTimestamp(name, mapNull);
    return ts == null ? null : ts.toLocalDateTime();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 0/0 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant(String name, boolean mapNull)  {
    long seconds = getLong(name);
    int nanos = getInt(name + COL2_SUFFIX);
    if (wasNull() ||
        mapNull && seconds == 0 && nanos == 0) {
      return null;
    }
    return Instant.ofEpochSecond(seconds, nanos);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 00:00:00.000+00:00 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime(String name, boolean mapNull)  {
    Timestamp timestamp = getTimestamp(name);
    int offset = getInt(name + COL2_SUFFIX);
    if (wasNull() ||
        mapNull && timestamp.getTime() == 0 && offset == 0) {
      return null;
    }
    return OffsetDateTime.of(timestamp.toLocalDateTime().plusSeconds(offset), ZoneOffset.ofTotalSeconds(offset));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 00:00:00.000 GMT should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime(String name, boolean mapNull)  {
    Timestamp timestamp = getTimestamp(name);
    String zoneId = getString(name + COL2_SUFFIX);
    if (wasNull() ||
        mapNull && timestamp.getTime() == 0 && ZonedDateTimeType.isGMT(zoneId)) {
      return null;
    }
    return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.of(zoneId));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(String name, Calendar timezone) {
    return getTimestamp(name, timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(String name, boolean mapNull) {
    return getTimestamp(name, null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(String name)  {
    return getTimestamp(name, null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime(String name)  {
    return getLocalDateTime(name, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant(String name)  {
    return getInstant(name, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime(String name)  {
    return getOffsetDateTime(name, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime(String name)  {
    return getZonedDateTime(name, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime(String name, Calendar timezone)  {
    try {
      // no minTime (makes no sense, see PreparedStatementWrapper)
      java.sql.Time time = timezone == null ?
              resources.rs.getTime(name) : resources.rs.getTime(name, timezone);
      return time == null ? null : Time.createFrozen(time.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime(String name)  {
    return getTime(name, null);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalTime getLocalTime(String name)  {
    Time time = getTime(name);
    return time == null ? null : time.toLocalTime();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetTime</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.
   * If the SQL AS clause was not specified, then the label is the name of the column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetTime getOffsetTime(String name)  {
    Time time = getTime(name);
    return time == null ? null : OffsetTime.of(time.toLocalTime(), ZoneOffset.ofTotalSeconds(getInt(name + COL2_SUFFIX)));
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link Binary}.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *        If the SQL AS clause was not specified, then the label is the name of the column
   * @param bufSize the initial buffer size for the Binary
   * @return the binary read from db
   */
  public <T extends Serializable> Binary<T> getBinary(String name, int bufSize)  {
    try {
      return Binary.create(resources.rs.getBinaryStream(name), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link TBinary}.
   *
   * @param name the label for the column specified with the SQL AS clause.
   *        If the SQL AS clause was not specified, then the label is the name of the column
   * @param bufSize the initial buffer size for the TBinary
   * @return the binary read from db
   */
  public <T> TBinary<T> getTBinary(String name, int bufSize)  {
    try {
      return TBinary.create(resources.rs.getBinaryStream(name), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>int</code> in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
   * @param type Class representing the Java data type
   * @return the column value, null if SQL value is null
   * @param <T> the object's type
   */
  public <T> T getObject(String name, Class<T> type) {
    try {
      return resources.rs.getObject(name, type);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this {@code ResultSet} object as an {@code Array} object
   * in the Java programming language.
   *
   * @param name the label for the column specified with the SQL AS clause
   * @return the column value; if the value is SQL <code>NULL</code>, the
   *         value returned is <code>null</code>
   */
  public Array getArray(String name) {
    try {
      return resources.rs.getArray(name);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  // ------------------- getter via positions -----------------------------

  /**
   * Gets the object for given {@link DataType}.<br>
   * This method is provided for application-specific types.
   *
   * @param dataType the datatype
   * @param pos the column positions
   * @param mapNull true if unmap null-values
   * @param size the optional size from the model
   * @param <T> the type
   * @return the object (can be null)
   */
  public <T> T get(DataType<T> dataType, int[] pos, boolean mapNull, Integer size) {
    if (pos.length != dataType.getColumnCount(resources.backend)) {
      throw new PersistenceException("position array length does not match column count for " + dataType +
                                     ", expected " + dataType.getColumnCount(resources.backend) + ", actual " + pos.length);
    }
    if (columnOffset != 0) {
      int[] oPos = new int[pos.length];
      for (int i=0; i < pos.length; i++) {
        oPos[i] = pos[i] + columnOffset;
      }
      pos = oPos;
    }
    try {
      return dataType.get(resources.db.getBackend(), resources.rs, pos, mapNull, size);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Object getObject (int pos)  {
    try {
      return resources.rs.getObject (pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getString (int pos, boolean mapNull)  {
    try {
      String str = resources.rs.getString (pos + columnOffset);
      if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
        return null;
      }
      return str;
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getString (int pos) {
    return getString(pos, false);
  }


  /**
   * Retrieves a String from a CLOB.
   *
   * @param pos the parameter index in the result set
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getLargeString (int pos, boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isClobSupported()) {
        Clob clob = resources.rs.getClob(pos + columnOffset);
        String str = clob == null ? null : clob.getSubString(1, (int) clob.length());
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(pos, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves a String from a CLOB.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getLargeString (int pos) {
    return getLargeString(pos, false);
  }


  /**
   * Gets a string bridged through the JDBC {@code SQLXML} interface.
   *
   * @param pos the parameter index in the result set
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getSqlxml (int pos, boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isSqlxmlSupported()) {
        SQLXML sqlxml = resources.rs.getSQLXML(pos + columnOffset);
        String str = null;
        if (sqlxml != null) {
          str = sqlxml.getString();
          sqlxml.free();
        }
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(pos, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Gets a string bridged through the JDBC {@code SQLXML} interface.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getSqlxml(java.lang.String, boolean)
   */
  public String getSqlxml (int pos) {
    return getSqlxml(pos, false);
  }


  /**
   * Gets a string holding a JSON document.
   *
   * @param pos the parameter index in the result set
   * @param mapNull if empty strings should be treated as null values, ignored for a native JSON column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getJson (int pos, boolean mapNull)  {
    try {
      Backend backend = resources.stmt.getConnection().getBackend();
      if (backend.isJsonSupported()) {
        return backend.getJson(resources.rs, pos + columnOffset);
      }
      else {
        return getString(pos, mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Gets a string holding a JSON document.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getJson(java.lang.String, boolean)
   */
  public String getJson (int pos) {
    return getJson(pos, false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>false</code>
   */
  public boolean getBoolean (int pos) {
    try {
      return resources.rs.getBoolean(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Boolean getABoolean (int pos)  {
    boolean value = getBoolean(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>float</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public float getFloat (int pos) {
    try {
      return resources.rs.getFloat(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Float</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Float getAFloat (int pos)  {
    float value = getFloat(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>double</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public double getDouble (int pos) {
    try {
      return resources.rs.getDouble(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Double</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Double getADouble (int pos)  {
    double value = getDouble(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>java.math.BigDecimal</code> with full precision.
   *
   * @param pos the parameter index in the result set
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BigDecimal getBigDecimal (int pos) {
    try {
      return resources.rs.getBigDecimal(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>BMoney</code> with full precision.<br>
   * Notice that BMoney fields use two fields:
   * one for the value and
   * one for the scale (= name + "P")
   *
   * @param pos the parameter index for the amount in the result set
   * @param ppos the parameter index for the scale in the result set
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BMoney getBMoney (int pos, int ppos) {
    double value = getDouble(pos);
    return wasNull() ? null : new BMoney (value, getInt(ppos));
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>DMoney</code> with full precision.<br>
   * Notice that DMoney fields use two fields:
   * one for the value and
   * one for the scale (= name + "P")
   *
   * @param pos the parameter index for the amount in the result set
   * @param ppos the parameter index for the scale in the result set
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public DMoney getDMoney (int pos, int ppos) {
    BigDecimal value = getBigDecimal(pos);
    return wasNull() ? null : new DMoney (value.movePointLeft(getInt(ppos)));
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>byte</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public byte getByte (int pos) {
    try {
      return resources.rs.getByte(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Byte</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Byte getAByte (int pos)  {
    byte value = getByte(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>char</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code> or the empty string, the
   * value returned is <code>0</code>
   */
  public char getChar (int pos) {
    try {
      String val = resources.rs.getString(pos + columnOffset);
      return val == null || val.isEmpty() ? 0 : val.charAt(0);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull if blanks should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   *
   */
  public Character getCharacter (int pos, boolean mapNull)  {
    char value = getChar(pos);
    return wasNull() ? null : (mapNull && value == ' ' ? null : value);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   *
   */
  public Character getCharacter (int pos) {
    return getCharacter(pos, false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>short</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public short getShort (int pos) {
    try {
      return resources.rs.getShort(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Short</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Short getAShort (int pos)  {
    short value = getShort(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>int</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public int getInt (int pos) {
    try {
      return resources.rs.getInt(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Integer</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Integer getInteger (int pos)  {
    int value = getInt(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>long</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public long getLong (int pos)  {
    try {
      return resources.rs.getLong(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Long</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Long getALong (int pos)  {
    long value = getLong(pos);
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(int pos, Calendar timezone, boolean mapNull)  {
    try {
      java.sql.Date date = timezone == null ?
              resources.rs.getDate(pos + columnOffset) : resources.rs.getDate(pos + columnOffset, timezone);
      if (date == null ||
          (mapNull && date.equals(DateHelper.MIN_DATE)))  {
        // mindate is translated back to null
        return null;
      }
      return Date.createFrozen(date.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate(int pos, boolean mapNull)  {
    Date date = getDate(pos, mapNull);
    return date == null ? null : date.toLocalDate();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(int pos, boolean mapNull)  {
    return getDate(pos, null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(int pos, Calendar timezone) {
    return getDate(pos, timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(int pos)  {
    return getDate(pos, null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate(int pos)  {
    return getLocalDate(pos, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(int pos, Calendar timezone, boolean mapNull)  {
    try {
      java.sql.Timestamp ts = timezone == null ?
              resources.rs.getTimestamp(pos + columnOffset) : resources.rs.getTimestamp(pos + columnOffset, timezone);
      if (ts == null ||
          (mapNull && ts.equals(DateHelper.MIN_TIMESTAMP)))  {
        // mindate is translated back to null
        return null;
      }
      return Timestamp.createFrozen(ts.getTime(), ts.getNanos());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime(int pos, boolean mapNull)  {
    Timestamp ts = getTimestamp(pos, mapNull);
    return ts == null ? null : ts.toLocalDateTime();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @param pos the parameter index for the seconds column
   * @param npos the parameter index for the nanoseconds column
   * @param mapNull true if 0/0 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant(int pos, int npos, boolean mapNull)  {
    long seconds = getLong(pos);
    int nanos = getInt(npos);
    if (wasNull() ||
        mapNull && seconds == 0 && nanos == 0) {
      return null;
    }
    return Instant.ofEpochSecond(seconds, nanos);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index for the timestamp column
   * @param opos the parameter index for the offset integer column
   * @param mapNull true if 1.1.1970 00:00:00.000+00:00 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime(int pos, int opos, boolean mapNull)  {
    Timestamp timestamp = getTimestamp(pos);
    int offset = getInt(opos);
    if (wasNull() ||
        mapNull && timestamp.getTime() == 0 && offset == 0) {
      return null;
    }
    return OffsetDateTime.of(timestamp.toLocalDateTime().plusSeconds(offset), ZoneOffset.ofTotalSeconds(offset));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index for the timestamp column
   * @param zpos the parameter index for the timezone varchar column
   * @param mapNull true if 1.1.1970 00:00:00.000 GMT should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime(int pos, int zpos, boolean mapNull)  {
    Timestamp timestamp = getTimestamp(pos);
    String zoneId = getString(zpos);
    if (wasNull() ||
        mapNull && timestamp.getTime() == 0 && ZonedDateTimeType.isGMT(zoneId)) {
      return null;
    }
    return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.of(zoneId));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(int pos, boolean mapNull)  {
    return getTimestamp(pos, null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(int pos, Calendar timezone)  {
    return getTimestamp(pos, timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(int pos)  {
    return getTimestamp(pos, null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime(int pos)  {
    return getLocalDateTime(pos, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @param pos the parameter index for the seconds column
   * @param npos the parameter index for the nanoseconds column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant(int pos, int npos)  {
    return getInstant(pos, npos, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index for the timestamp column
   * @param opos the parameter index for the offset integer column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime(int pos, int opos)  {
    return getOffsetDateTime(pos, opos, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @param pos the parameter index for the timestamp column
   * @param zpos the parameter index for the timezone varchar column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime(int pos, int zpos)  {
    return getZonedDateTime(pos, zpos, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime(int pos, Calendar timezone)  {
    try {
      // no mapNull possible
      java.sql.Time time = timezone == null ?
              resources.rs.getTime(pos + columnOffset) : resources.rs.getTime(pos + columnOffset, timezone);
      return time == null ? null : Time.createFrozen(time.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime(int pos)  {
    return getTime(pos, null);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalTime</code> in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalTime getLocalTime(int pos)  {
    Time time = getTime(pos);
    return time == null ? null : time.toLocalTime();
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetTime</code> in the Java programming language.
   *
   * @param pos the parameter index for the time in the result set
   * @param opos the parameter index for the offset in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetTime getOffsetTime(int pos, int opos)  {
    Time time = getTime(pos);
    return wasNull() ? null : OffsetTime.of(time.toLocalTime(), ZoneOffset.ofTotalSeconds(getInt(opos)));
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link Binary}.
   *
   * @param pos the parameter index in the result set
   * @param bufSize the initial buffer size for the Binary
   * @return the binary read from db
   */
  public <T extends Serializable> Binary<T> getBinary(int pos, int bufSize)  {
    try {
      return Binary.create(resources.rs.getBinaryStream(pos + columnOffset), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link TBinary}.
   *
   * @param pos the parameter index in the result set
   * @param bufSize the initial buffer size for the TBinary
   * @return the binary read from db
   */
  public <T> TBinary<T> getTBinary(int pos, int bufSize)  {
    try {
      return TBinary.create(resources.rs.getBinaryStream(pos + columnOffset), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Object</code> of given type <code>T</code>.
   *
   * @param pos the parameter index in the result set
   * @param type Class representing the Java data type
   * @return the column value, null if SQL value is null
   * @param <T> the object's type
   */
  public <T> T getObject(int pos, Class<T> type) {
    try {
      return resources.rs.getObject(pos + columnOffset, type);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this {@code ResultSet} object as an {@code Array} object
   * in the Java programming language.
   *
   * @param pos the parameter index in the result set
   * @return the column value; if the value is SQL <code>NULL</code>, the
   *         value returned is <code>null</code>
   */
  public Array getArray(int pos) {
    try {
      return resources.rs.getArray(pos + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  // ------------------- getter with automatic column index -----------------------------

  /**
   * Gets the object for given {@link DataType}.<br>
   * This method is provided for application-specific types.
   *
   * @param dataType the datatype
   * @param mapNull true if unmap null-values
   * @param size the optional size from the model
   * @param <T> the type
   * @return the object (can be null)
   */
  public <T> T get(DataType<T> dataType, boolean mapNull, Integer size) {
    int[] pos = new int[dataType.getColumnCount(resources.backend)];
    for (int i=0; i < pos.length; i++) {
      pos[i] = nextConfiguredIndex();
    }
    return get(dataType, pos, mapNull, size);
  }


  /**
   * Retrieves the value of the configured column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getString (boolean mapNull)  {
    try {
      String str = resources.rs.getString (nextConfiguredIndex() + columnOffset);
      if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
        return null;
      }
      return str;
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the configured column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>String</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getString () {
    return getString(nextConfiguredIndex(), false);
  }


  /**
   * Retrieves a String from a CLOB column.
   *
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getLargeString (boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isClobSupported()) {
        Clob clob = resources.rs.getClob(nextConfiguredIndex() + columnOffset);
        String str = clob == null ? null : clob.getSubString(1, (int) clob.length());
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves a String from a CLOB column.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getString(java.lang.String, boolean)
   */
  public String getLargeString () {
    return getLargeString(nextConfiguredIndex(), false);
  }


  /**
   * Gets a string bridged through the JDBC {@code SQLXML} interface.
   *
   * @param mapNull if empty strings should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getSqlxml (boolean mapNull)  {
    try {
      if (resources.stmt.getConnection().getBackend().isSqlxmlSupported()) {
        SQLXML sqlxml = resources.rs.getSQLXML(nextConfiguredIndex() + columnOffset);
        String str = null;
        if (sqlxml != null) {
          str = sqlxml.getString();
          sqlxml.free();
        }
        if (mapNull && str != null && str.equals(resources.db.getBackend().getEmptyString())) {
          return null;
        }
        return str;
      }
      else {
        return getString(mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Gets a string bridged through the JDBC {@code SQLXML} interface.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getSqlxml(java.lang.String, boolean)
   */
  public String getSqlxml () {
    return getSqlxml(nextConfiguredIndex(), false);
  }


  /**
   * Gets a string holding a JSON document.
   *
   * @param mapNull if empty strings should be treated as null values, ignored for a native JSON column
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public String getJson (boolean mapNull)  {
    try {
      Backend backend = resources.stmt.getConnection().getBackend();
      if (backend.isJsonSupported()) {
        return backend.getJson(resources.rs, nextConfiguredIndex() + columnOffset);
      }
      else {
        return getString(mapNull);
      }
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Gets a string holding a JSON document.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   * @see #getJson(java.lang.String, boolean)
   */
  public String getJson () {
    return getJson(nextConfiguredIndex(), false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>false</code>
   */
  public boolean getBoolean () {
    try {
      return resources.rs.getBoolean(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Boolean</code> in the Java programming language.
   *
   * <P>If the designated column has a datatype of CHAR or VARCHAR
   * and contains a "0" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 0, a value of <code>false</code> is returned.  If the designated column has a datatype
   * of CHAR or VARCHAR
   * and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER, or BIGINT
   * and contains a 1, a value of <code>true</code> is returned.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Boolean getABoolean ()  {
    boolean value = getBoolean(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>float</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public float getFloat () {
    try {
      return resources.rs.getFloat(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Float</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Float getAFloat ()  {
    float value = getFloat(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>double</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public double getDouble () {
    try {
      return resources.rs.getDouble(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Double</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Double getADouble ()  {
    double value = getDouble(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>java.math.BigDecimal</code> with full precision.
   *
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BigDecimal getBigDecimal () {
    try {
      return resources.rs.getBigDecimal(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>BMoney</code> with full precision.<br>
   * Notice that BMoney fields use two columns!
   *
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public BMoney getBMoney () {
    return getBMoney(nextConfiguredIndex(), nextConfiguredIndex());
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a
   * <code>DMoney</code> with full precision.<br>
   * Notice that DMoney fields use two columns!
   *
   * @return the column value (full precision);
   * if the value is SQL <code>NULL</code>, the value returned is
   * <code>null</code> in the Java programming language.
   */
  public DMoney getDMoney () {
    return getDMoney(nextConfiguredIndex(), nextConfiguredIndex());
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>byte</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public byte getByte () {
    try {
      return resources.rs.getByte(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Byte</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Byte getAByte ()  {
    byte value = getByte(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>char</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code> or the empty string, the
   * value returned is <code>0</code>
   */
  public char getChar () {
    try {
      String val = resources.rs.getString(nextConfiguredIndex() + columnOffset);
      return val == null || val.isEmpty() ? 0 : val.charAt(0);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @param mapNull if blanks should be treated as null values
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   *
   */
  public Character getCharacter (boolean mapNull)  {
    char value = getChar(nextConfiguredIndex());
    return wasNull() ? null : (mapNull && value == ' ' ? null : value);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Character</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>. If the value is the empty string the returned
   * value is <code>new Character(0)</code>.
   *
   */
  public Character getCharacter () {
    return getCharacter(nextConfiguredIndex(), false);
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>short</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public short getShort () {
    try {
      return resources.rs.getShort(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Short</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Short getAShort ()  {
    short value = getShort(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>int</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public int getInt () {
    try {
      return resources.rs.getInt(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Integer</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Integer getInteger ()  {
    int value = getInt(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>long</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>0</code>
   */
  public long getLong ()  {
    try {
      return resources.rs.getLong(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>Long</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Long getALong ()  {
    long value = getLong(nextConfiguredIndex());
    return wasNull() ? null : value;
  }


  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(Calendar timezone, boolean mapNull)  {
    try {
      int pos = nextConfiguredIndex();
      java.sql.Date date = timezone == null ?
              resources.rs.getDate(pos + columnOffset) : resources.rs.getDate(pos + columnOffset, timezone);
      if (date == null ||
          (mapNull && date.equals(DateHelper.MIN_DATE)))  {
        // mindate is translated back to null
        return null;
      }
      return Date.createFrozen(date.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(boolean mapNull)  {
    return getDate(nextConfiguredIndex(), null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate(boolean mapNull)  {
    return getLocalDate(nextConfiguredIndex(), mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate(Calendar timezone) {
    return getDate(nextConfiguredIndex(), timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Date</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Date getDate()  {
    return getDate(nextConfiguredIndex(), null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDate</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDate getLocalDate()  {
    return getLocalDate(nextConfiguredIndex(), false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param timezone the calendar providing the timezone configuration
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(Calendar timezone, boolean mapNull)  {
    try {
      int pos = nextConfiguredIndex();
      java.sql.Timestamp ts = timezone == null ?
              resources.rs.getTimestamp(pos + columnOffset) : resources.rs.getTimestamp(pos + columnOffset, timezone);
      if (ts == null ||
          (mapNull && ts.equals(DateHelper.MIN_TIMESTAMP)))  {
        // mindate is translated back to null
        return null;
      }
      return Timestamp.createFrozen(ts.getTime(), ts.getNanos());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(boolean mapNull)  {
    return getTimestamp(nextConfiguredIndex(), null, mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 00:00:00.000 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime(boolean mapNull)  {
    return getLocalDateTime(nextConfiguredIndex(), mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @param mapNull true if 0/0 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant(boolean mapNull)  {
    return getInstant(nextConfiguredIndex(), nextConfiguredIndex(), mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 00:00:00.000+00:00 should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime(boolean mapNull)  {
    return getOffsetDateTime(nextConfiguredIndex(), nextConfiguredIndex(), mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @param mapNull true if 1.1.1970 00:00:00.000 GMT should be mapped to null
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime(boolean mapNull)  {
    return getZonedDateTime(nextConfiguredIndex(), nextConfiguredIndex(), mapNull);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp(Calendar timezone)  {
    return getTimestamp(nextConfiguredIndex(), timezone, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Timestamp</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Timestamp getTimestamp()  {
    return getTimestamp(nextConfiguredIndex(), null, false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalDateTime</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalDateTime getLocalDateTime()  {
    return getLocalDateTime(nextConfiguredIndex(), false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.Instant</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Instant getInstant()  {
    return getInstant(nextConfiguredIndex(), nextConfiguredIndex(), false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetDateTime</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetDateTime getOffsetDateTime()  {
    return getOffsetDateTime(nextConfiguredIndex(), nextConfiguredIndex(), false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.ZonedDateTime</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public ZonedDateTime getZonedDateTime()  {
    return getZonedDateTime(nextConfiguredIndex(), nextConfiguredIndex(), false);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @param timezone the calendar providing the timezone configuration
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime(Calendar timezone)  {
    try {
      // no mapNull possible
      int pos = nextConfiguredIndex();
      java.sql.Time time = timezone == null ?
              resources.rs.getTime(pos + columnOffset) : resources.rs.getTime(pos + columnOffset, timezone);
      return time == null ? null : Time.createFrozen(time.getTime());
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.sql.Time</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public Time getTime()  {
    return getTime(nextConfiguredIndex(), null);
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.LocalTime</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public LocalTime getLocalTime()  {
    return getLocalTime(nextConfiguredIndex());
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * a <code>java.time.OffsetTime</code> in the Java programming language.
   *
   * @return the column value; if the value is SQL <code>NULL</code>, the
   * value returned is <code>null</code>
   */
  public OffsetTime getOffsetTime()  {
    return getOffsetTime(nextConfiguredIndex(), nextConfiguredIndex());
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link Binary}.
   *
   * @param bufSize the initial buffer size for the Binary
   * @return the binary read from db
   */
  public <T extends Serializable> Binary<T> getBinary(int bufSize)  {
    try {
      return Binary.create(resources.rs.getBinaryStream(nextConfiguredIndex() + columnOffset), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as a {@link TBinary}.
   *
   * @param bufSize the initial buffer size for the TBinary
   * @return the binary read from db
   */
  public <T> TBinary<T> getTBinary(int bufSize)  {
    try {
      return TBinary.create(resources.rs.getBinaryStream(nextConfiguredIndex() + columnOffset), bufSize, true);
    }
    catch (SQLException | IOException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Object</code> of given type <code>T</code>.
   *
   * @return the column value, null if SQL value is null
   */
  public Object getObject() {
    try {
      return resources.rs.getObject(nextConfiguredIndex() + columnOffset);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }

  /**
   * Retrieves the value of the designated column in the current row
   * of this <code>ResultSet</code> object as
   * an <code>Object</code> of given type <code>T</code>.
   *
   * @param type Class representing the Java data type
   * @return the column value, null if SQL value is null
   * @param <T> the object's type
   */
  public <T> T getObject(Class<T> type) {
    try {
      return resources.rs.getObject(nextConfiguredIndex() + columnOffset, type);
    }
    catch (SQLException e)  {
      throw new PersistenceException(resources.db, e);
    }
  }



  // ----------------------------------- private methods ----------------------------------------------

  /**
   * Clears the configured index and sets the flag that cursor was moved.
   */
  private void clearSection() {
    if (resources.rs == null) {
      throw new PersistenceException("result set already closed");
    }
    currentSection = null;
    if (isInSkipMode()) {
      cursorMovedInSkipMode = true;
    }
  }

  /**
   * Gets the next configured column index.
   *
   * @return the next column index
   */
  private int nextConfiguredIndex() {
    assertValidSection();
    return currentSection.nextColumnIndex();
  }

  /**
   * Asserts that the current section is valid.
   */
  private void assertValidSection() {
    if (currentSection == null) {
      throw new PersistenceException(resources.db, "no current section configured for automatic indexed retrieval");
    }
  }

  /**
   * Asserts that the result set is in skip mode.
   */
  private void assertInSkipMode() {
    if (!isInSkipMode()) {
      throw new PersistenceException(resources.db, "result set is not in skip mode");
    }
  }

}
