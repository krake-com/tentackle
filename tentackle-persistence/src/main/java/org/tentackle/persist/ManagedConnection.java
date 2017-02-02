/**
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


package org.tentackle.persist;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.MappedDiagnosticContext;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.persist.rmi.RemoteDbSessionImpl;
import org.tentackle.sql.Backend;

/**
 * A jdbc connection managed by the ConnectionManager.<br>
 *
 * The connection provides some additional features
 * such as a prepared statement cache
 * and translates SQLExceptions to DbRuntimeExceptions.
 * The ConnectionManager is responsible to attach and detach
 * the connection to a session.
 *
 * @author harald
 */
public class ManagedConnection {

  /**
   * We keep an internal set of all ManagedConnections via WeakReferences, so the
   * ManagedConnections still will be finalized when closed.
   * The set is used to figure out which Db is attached to what connection
   * for debugging purposes.
   */
  private static final Set<WeakReference<ManagedConnection>> MANAGED_CONNECTIONS =
          Collections.newSetFromMap(new ConcurrentHashMap<>());


  /**
   * Gets a list of all managed open connections.
   *
   * @return the set of connections
   */
  public static Collection<ManagedConnection> getManagedConnections() {
    List<ManagedConnection> mgcons = new ArrayList<>();
    for (Iterator<WeakReference<ManagedConnection>> iter = MANAGED_CONNECTIONS.iterator(); iter.hasNext(); ) {
      ManagedConnection mgcon = iter.next().get();
      if (mgcon != null && !mgcon.isClosed()) {
        mgcons.add(mgcon);
      }
      else  {
        iter.remove();
      }
    }
    return mgcons;
  }

  /**
   * Minimum milliseconds a connection remains attached before being logged.<br>
   * 0 to disable, -1 to log every detach.
   * <p>
   * For analysis and debugging purposes only.
   */
  public static long logMinAttachMillis;

  /**
   * Flag to turn on statement statistics. (default is turned off)
   */
  public static boolean collectStatistics;

  /**
   * Optional periodical log of statistics in milliseconds. (default is off)
   * <br>
   * If turned on, the statistics will be logged and cleared every logStatisticsMillis milliseconds.
   */
  public static long logStatisticsMillis;

  /**
   * Optional filter to log only statements for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   */
  public static Pattern mdcFilter;

  /**
   * Optionally enable statement checks for resultSetType and resultSetConcurrency.
   */
  public static boolean checkStatementResultSetParameters;

  /**
   * Logging level to log nested selects.<br>
   * When enabled, selects running in parallel on the same connection will be logged.
   * This is usually means, that from within a loop processing a result set, a method is invoked,
   * that in turn triggers another select.
   */
  public static Level logLevelForParallelOpenResultSets;


  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedConnection.class);

  private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();


  private final ConnectionManager manager;                // the manager that created this connection
  private final Backend backend;                          // the backend
  private Connection connection;                          // the wrapped connection, null if closed
  private final String backendId;                         // a unique ID to identify the connection at the backend
  private final long instanceNumber;                      // unique instance number (for logging only)
  private Db db;                                          // currently attached Db, null = free connection
  private long establishedSince;                          // connection established establishedSince... (epochal [ms])
  private long expireAt;                                  // connection shutdown at (epochal [ms]), 0 = forever
  private long attachedSince;                             // attached since when...
  private long detachedSince;                             // detached since when...
  private int index;                                      // connection index given by connection manager, -1 not used
  private int attachCount;                                // 0 = not attached, else number of times db is attached
  private int maxCountForClearWarnings;                   // trigger when to clearWarning() on a connection (0 = disabled)
  private int counterForClearWarnings;                    // current counter
  private boolean dead;                                   // connection is dead (comlink error detected)
  private StatementHistory currentStatement;              // statement history while not in transaction

  private final List<StatementHistory> statementHistory;                        // statement history while in transaction
  private final Map<StatementKey,PreparedStatementWrapper> preparedStatements;  // all prepared statements for this connection (stmtId:statement)
  private final Set<WeakReference<ResultSetWrapper>> openResultSets;            // pending open result sets (thread safe bec. of WeakRefs)
  private final Set<WeakReference<StatementWrapper>> runningStatements;         // running statements (thread safe bec. of WeakRefs)


  /**
   * Creates a managed connection.
   *
   * @param manager the connection manager
   * @param backend the backend
   * @param connection the low level JDBC connection
   */
  public ManagedConnection(ConnectionManager manager, Backend backend, Connection connection) {

    if (connection == null) {
      throw new IllegalArgumentException("connection is null");
    }

    this.manager = manager;
    this.backend = backend;
    this.connection = connection;

    backendId = backend.getBackendId(connection);
    instanceNumber = INSTANCE_COUNTER.incrementAndGet();
    MANAGED_CONNECTIONS.add(new WeakReference<>(this));

    establishedSince = System.currentTimeMillis();
    detachedSince = establishedSince;
    index = -1;
    statementHistory = new ArrayList<>();
    preparedStatements = new ConcurrentHashMap<>();
    // use a threadsafe set due to concurrent cleanup of weakreferences in getOpenResultSets and cancelRunningStatements
    openResultSets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    runningStatements = Collections.newSetFromMap(new ConcurrentHashMap<>());
  }


  /**
   * Gets the connection manager
   *
   * @return the manager
   */
  public ConnectionManager getManager() {
    return manager;
  }


  /**
   * Gets the low level connection.
   *
   * @return the physical connection
   */
  public Connection getConnection() {
    return connection;
  }


  /**
   * Gets the epochal time when this connection was established.
   *
   * @return the time establishedSince in ms
   */
  public long getEstablishedSince() {
    return establishedSince;
  }


  /**
   * Sets the epochal time when this connection should be closed, if unused.
   *
   * @param expireAt the time in [ms], 0 = forever
   */
  public void setExpireAt(long expireAt) {
    this.expireAt = expireAt;
  }

  /**
   * Gets the epochal time when this connection should be closed, if unused.
   *
   * @return the time in [ms], 0 = forever
   */
  public long getExpireAt() {
    return expireAt;
  }


  /**
   * Gets the epochal time when this connection was attached.
   *
   * @return the epochal time of last attach, 0 if not attached
   */
  public long getAttachedSince() {
    return attachedSince;
  }


  /**
   * Gets the epochal time when this connection was detached.
   * <p>
   * Note that newly created conntections get their detach time initialized
   * from the current system time.
   *
   * @return the epochal time of last detach, 0 if attached
   */
  public long getDetachedSince() {
    return detachedSince;
  }


  /**
   * Sets the connection index.
   * Connection managers use that to manage connection lists.
   *
   * @param index the connection index
   */
  public void setIndex(int index) {
    this.index = index;
  }

  /**
   * Gets the connection index.
   *
   * @return the connection index.
   */
  public int getIndex() {
    return index;
  }


  /**
   * Marks a connection being dead.
   * <p>
   * Marking connections dead allows connection managers like
   * {@link MpxConnectionManager} to re-open connections before
   * being attached next time. Notice that not all connection managers
   * honour the dead-flag (makes only sense in servers, anyway).
   *
   * @param dead true if marked dead, false not dead
   */
  public void setDead(boolean dead) {
    this.dead = dead;
  }

  /**
   * Returns whether connection is marked dead
   * @return true if dead
   */
  public boolean isDead() {
    return dead;
  }


  /**
   * Checks whether connection is still valid.<p>
   *
   * Implemented via a "SELECT 1" query.
   * If the check fails the connection is marked dead.
   *
   * @return true if connection still valid, false if invalid
   */
  public boolean verifyConnection() {
    try (Statement stmt = connection.createStatement()) {
      stmt.executeQuery("SELECT 1");
      return true;
    }
    catch (SQLException ex) {
      setDead(true);
      return false;
    }
  }


  /**
   * Attaches a connection to a session.
   * Connections must be attached before it can be used by statements
   * or starting a tx.
   * The method must only be invoked by a connection manager!
   *
   * @param db the logical Db to attach
   */
  public void attachSession(Db db) {

    if (db == null) {
      throw new IllegalArgumentException("db is null");
    }

    if (this.db != null) {
      if (this.db == db) {
        if (db.getConnection() != this) {
          throw new PersistenceException(db, "db lost current connection " + this + ", count=" + attachCount);
        }
        attachCount++;
      }
      else  {
        throw new PersistenceException(db, "connection " + this + " already attached to " + this.db);
      }
    }
    else  {
      if (attachCount != 0) {
        throw new PersistenceException(db, "attach count of unattached connection " + this + " is not 0, but " + attachCount);
      }
      this.db = db;
      db.setConnection(this);
      attachCount = 1;
      attachedSince = System.currentTimeMillis();
      detachedSince = 0;
    }
    LOGGER.finer("{0} attached to {1}, count={2}", db, this, attachCount);
  }


  /**
   * Checks whether a Db is attached to this connection.
   *
   * @return true if attached, else false.
   */
  public boolean isAttached() {
    return db != null;
  }


  /**
   * Returns whether a transaction is running.
   *
   * @return true if connection is attached to a Db in transaction
   */
  public boolean isTxRunning() {
    return isAttached() && db.isTxRunning();
  }


  /**
   * Detaches a session from a connection.
   * Connections must be detached before they can be used by another Db.
   * The method must only be invoked by a connection manager!
   *
   * @param db the db to detach
   */
  public void detachSession(Db db) {
    if (db == null) {
      throw new IllegalArgumentException("db is null");
    }
    if (this.db != db) {
      throw new PersistenceException(db, "connection " + this + " not attached to " + db + " (instead attached to " + this.db + ")");
    }
    LOGGER.finer("{0} detached from {1}, count={2}", db, this, attachCount);

    attachCount--;

    if (attachCount == 0) {
      detachedSince = System.currentTimeMillis();
      if (logMinAttachMillis != 0 && detachedSince - attachedSince > logMinAttachMillis) {
        if (isMDCValid()) {
          LOGGER.warning("attached for " + (detachedSince - attachedSince) + "ms: " + toDiagnosticString());
        }
      }
      else if (LOGGER.isFineLoggable() && isMDCValid()) {
        LOGGER.fine(toDiagnosticString());
      }
      this.db = null;
      db.setConnection(null);
      attachedSince = 0;

      if (collectStatistics) {
        if (logStatisticsMillis > 0 && detachedSince - StatementStatistics.getSince() > logStatisticsMillis) {
          // log and clear statistics
          StatementStatistics.log(LOGGER, Logger.Level.INFO, true);
        }
        // collect all statements
        if (isMDCValid()) {
          for (StatementHistory history: statementHistory) {
            StatementStatistics.collect(history);
          }
          if (currentStatement != null) {
            StatementStatistics.collect(currentStatement);
          }
        }
      }

      statementHistory.clear();
      currentStatement = null;
    }
    else {
      if (db.getConnection() != this) {
        throw new PersistenceException(db, "db lost current connection " + this + ", count=" + attachCount++);
      }
      if (attachCount < 0) {
        throw new PersistenceException(db, "connection " + this + " detached too often, attachcount = " + attachCount++);
      }
    }
  }


  /**
   * Gets tha attached session.
   *
   * @return the session, null if not attached
   */
  public Db getSession() {
    return db;
  }


  /**
   * Gets the unique instance number.
   *
   * @return the instance number
   */
  public long getInstanceNumber() {
    return instanceNumber;
  }


  /**
   * Gets the backend id.
   *
   * @return the unique id
   */
  public String getBackendId() {
    return backendId;
  }


  /**
   * Gets the connection name.
   *
   * @return the unique name
   */
  public String getName() {
    StringBuilder buf = new StringBuilder();
    buf.append(instanceNumber);
    if (backendId != null) {
      buf.append('/');
      buf.append(backendId);
    }
    return buf.toString();
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    Connection con = connection;  // local variable because could be closed between test and use
    buf.append(con == null ? "<closed connection>" : con.toString());
    buf.append('[').append(getName());
    if (index >= 0) {
      buf.append('/').append(index);
    }
    if (attachCount > 0) {
      buf.append(", attached ").append(attachCount).append(" since ").append(new Timestamp(attachedSince));
    }
    if (dead) {
      buf.append(", dead");
    }
    buf.append(']');
    return buf.toString();
  }


  /**
   * Checks whether the mapped diagnostic context is valid for statistics logging.
   *
   * @return true if loggable
   * @see #mdcFilter
   */
  protected boolean isMDCValid() {
    if (mdcFilter == null) {
      return true;
    }
    MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
    return mdc != null && mdc.matchesPattern(mdcFilter);
  }


  /**
   * Logs the execution start of a statement during a transaction.<br>
   * The execution start is the current epochal time.
   * The execution end must be set via {@link StatementHistory#end()}
   *
   * @param statement the statement
   * @return the history with an execution start of now and execution end not set, never null
   */
  StatementHistory logStatementHistory(StatementWrapper statement) {
    StatementHistory history = new StatementHistory(statement);
    if (isTxRunning()) {
      statementHistory.add(history);
    }
    else  {
      currentStatement = history;
    }
    return history;
  }


  /**
   * Gets the statement history.<br>
   *
   * @return the statements executed during last attachment
   */
  public List<StatementHistory> getStatementHistory() {
    return statementHistory;
  }


  /**
   * Adds a pending result set.
   *
   * @param rs the result set
   */
  public void addResultSet(ResultSetWrapper rs) {
    openResultSets.add(new WeakReference<>(rs));
    if (logLevelForParallelOpenResultSets != null && openResultSets.size() > 1 && LOGGER.isLoggable(logLevelForParallelOpenResultSets)) {
      Collection<ResultSetWrapper> parallelResultStatements = getOpenResultSets();
      if (!parallelResultStatements.isEmpty()) {
        int count = 0;
        StringBuilder buf = new StringBuilder();
        buf.append("parallel queries running on ").append(this).append(":");
        for (ResultSetWrapper prs: parallelResultStatements) {
          StatementWrapper stmt = prs.getStatement();
          if (stmt != null && !stmt.isParallelOk()) {
            buf.append('\n').append(prs);
            count++;
          }
        }
        if (count > 1) {
          LOGGER.log(logLevelForParallelOpenResultSets, buf.toString(), null);
        }
      }
    }
  }

  /**
   * Removes a pending result set.
   *
   * @param rs the result set
   */
  public void removeResultSet(ResultSetWrapper rs) {
    for (Iterator<WeakReference<ResultSetWrapper>> iter = openResultSets.iterator(); iter.hasNext(); ) {
      WeakReference<ResultSetWrapper> ref = iter.next();
      ResultSetWrapper refRs = ref.get();
      // if closed or unreferenced or the db to remove
      if (refRs == null || refRs == rs) {   // == is okay here
        iter.remove();
      }
    }
  }

  /**
   * Gets all open result sets for this connection.
   *
   * @return the result sets, never null
   */
  public Collection<ResultSetWrapper> getOpenResultSets() {
    List<ResultSetWrapper> rsList = new ArrayList<>();
    for (Iterator<WeakReference<ResultSetWrapper>> iter = openResultSets.iterator(); iter.hasNext(); ) {
      WeakReference<ResultSetWrapper> ref = iter.next();
      ResultSetWrapper refRs = ref.get();
      if (refRs != null) {
        rsList.add(refRs);
      }
      else  {
        iter.remove();
      }
    }
    return rsList;
  }


  /**
   * Creates a diagnostic string.
   *
   * @return the connection's status info
   */
  public String toDiagnosticString() {
    StringBuilder buf = new StringBuilder(toString());
    if (dead) {
      buf.append(" *** DEAD *** ");
    }
    buf.append(": ");
    buf.append(manager);
    buf.append('[');
    buf.append(index);
    buf.append("] valid ");
    buf.append(new Date(establishedSince));
    buf.append(" - ");
    if (expireAt != 0) {
      buf.append(new Date(expireAt));
    }
    else  {
      buf.append(" <unlimited>");
    }
    Db lDb = db;    // may be closed in the meantime...
    if (lDb != null) {
      buf.append("\n    -> ");
      buf.append(lDb.getName());
      if (lDb.getTxName() != null) {
        buf.append("/");
        buf.append(lDb.getTxName());
      }
      Thread ownerThread = lDb.getOwnerThread();
      if (ownerThread != null) {
        buf.append(", ");
        buf.append(lDb.getOwnerThread());
      }
      for (RemoteDbSessionImpl session: RemoteDbSessionImpl.getOpenSessions()) {
        if (session.getSession() == lDb) {
          // this is my session!
          buf.append(", ");
          buf.append(session);
        }
      }
    }

    try {
      // the following code may fail if statement history is being
      // modified in parallel. As this will only happen in rare cases
      // such as via StackdumpHelper, we don't synchronize on statementHistory
      // to avoid cost and locks.
      if (!statementHistory.isEmpty() || currentStatement != null) {
        buf.append("\n\n    Statements:");
        for (StatementHistory stmt: statementHistory) {
          buf.append("\n    ");
          buf.append(stmt);
        }
        StatementHistory stmt = currentStatement;
        if (stmt != null) {
          buf.append("\n    ");
          buf.append(stmt);
        }
        buf.append("\n");
      }
    }
    catch (ConcurrentModificationException cmx) {
      buf.append("\n*** connection still in use by other thread(s) -> statement history aborted ***\n");
    }
    catch (RuntimeException rex) {
      LOGGER.warning("cannot dump statement history", rex);
    }

    try {
      // dump all open result sets (threadsafe)
      Collection<ResultSetWrapper> rsList = getOpenResultSets();
      if (!rsList.isEmpty()) {
        buf.append("\n\n    Resultsets:");
        for (ResultSetWrapper rs: rsList) {
          buf.append("\n    ");
          buf.append(rs);
        }
        buf.append("\n");
      }
    }
    catch (RuntimeException rex) {
      LOGGER.warning("cannot dump open result sets", rex);
    }

    return buf.toString();
  }

  /**
   * asserts that a connection is attached
   */
  private void assertAttached() {
    if (db == null) {
      throw new PersistenceException("connection " + this + " not attached to any Db");
    }
  }



  /**
   * Sets the autocommit feature.
   *
   * @param autoCommit true to enable autocomit, false to disable.
   */
  public void setAutoCommit(boolean autoCommit) {
    assertAttached();
    try {
      connection.setAutoCommit(autoCommit);
    }
    catch (SQLException ex) {
      throw new PersistenceException(db, "setting autocommit failed", ex);
    }
  }


  /**
   * Gets the autocommit value.
   *
   * @return the autocommit value.
   */
  public boolean getAutoCommit() {
    try {
      return connection.getAutoCommit();
    }
    catch (SQLException ex) {
      throw new PersistenceException(db, "getting autocommit failed", ex);
    }
  }


  /**
   * Performs a commit.
   */
  public void commit() {
    assertAttached();
    try {
      connection.commit();
    }
    catch (SQLException ex) {
      throw new PersistenceException(db, "commit failed", ex);
    }
  }


  /**
   * Performs a rollback.
   *
   * @param withLog true if log via INFO
   */
  public void rollback(boolean withLog) {
    assertAttached();
    try {
      if (withLog && LOGGER.isInfoLoggable()) {
        LOGGER.info(toDiagnosticString());
      }
      connection.rollback();
      // unmark all statements marked ready
      unmarkPreparedStatements();
    }
    catch (SQLException ex) {
      throw new PersistenceException(db, "rollback failed: " + toDiagnosticString(), ex);
    }
  }


  /**
   * Remembers a statement as running.
   *
   * @param statement the statement
   */
  public void addRunningStatement(StatementWrapper statement) {
    runningStatements.add(new WeakReference<>(statement));
  }

  /**
   * Removes a statement from the running statements.
   *
   * @param statement the statement
   */
  public void removeRunningStatement(StatementWrapper statement) {
    // usually there is no more than one running statement at a time, so the loop is fast
    for (Iterator<WeakReference<StatementWrapper>> iter = runningStatements.iterator(); iter.hasNext(); ) {
      WeakReference<StatementWrapper> ref = iter.next();
      StatementWrapper stmt = ref.get();
      if (stmt == null || stmt == statement) {
        iter.remove();
      }
    }
  }


  /**
   * Cancels the(all) currently running statement(s).<br>
   * May be invoked from any thread.
   */
  public void cancelRunningStatements() {
    for (Iterator<WeakReference<StatementWrapper>> iter = runningStatements.iterator(); iter.hasNext(); ) {
      WeakReference<StatementWrapper> ref = iter.next();
      StatementWrapper stmt = ref.get();
      if (stmt != null) {
        if (stmt.isRunning()) {
          try {
            stmt.cancel();
          }
          catch (RuntimeException ex) {
            // just log
            LOGGER.warning("canceling statement failed: " + stmt, ex);
          }
        }
      }
      iter.remove();
    }
  }



  /**
   * Unmark all prepared statements that are marked ready.
   */
  public void unmarkPreparedStatements() {
    // unmark all statements marked ready
    for (PreparedStatementWrapper stmt: preparedStatements.values()) {
      if (stmt != null && stmt.isMarkedReady()) {
        try {
          stmt.consume();
        }
        catch (RuntimeException ex) {
          // just log (if not cancelled)
          if (!stmt.isCancelled()) {
            LOGGER.warning("unmarking statement failed: " + stmt, ex);
          }
        }
      }
    }
  }



  /**
   * Reads all warnings, logs and clears them.
   */
  public void logAndClearWarnings() {
    try {
      // log connection warning
      SQLWarning warning = connection.getWarnings();
      while (warning != null) {
        LOGGER.warning(warning.getMessage());
        warning = warning.getNextWarning();
      }
      connection.clearWarnings();  // release memory used by warnings
      // log prepared statement warnings
      for (PreparedStatementWrapper stmt: preparedStatements.values()) {
        if (!stmt.isClosed()) { // if not closed
          warning = stmt.getStatement().getWarnings();
          while (warning != null) {
            LOGGER.warning(warning.getMessage());
            warning = warning.getNextWarning();
          }
          stmt.getStatement().clearWarnings();
        }
      }
      // as this statement is called when there are no more running statements
      // we ignore the unprepared running statements.
    }
    catch (Exception e) {
      throw new PersistenceException (db, "reading warnings failed", e);
    }
  }



  /**
   * Sets the countForClearWarnings trigger.
   *
   * @param max the maxcount, 0 = app must eat the warnings
   */
  public void setMaxCountForClearWarnings(int max) {
    maxCountForClearWarnings = max;
  }

  /**
   * Gets the current setting for clearWarnings() trigger.
   *
   * @return the maxcount, 0 = app must eat the warnings
   */
  public int getMaxCountForClearWarnings()  {
    return maxCountForClearWarnings;
  }



  /**
   * Increments a counter and empties the warnings on the connection
   * and all prepared statements if a trigger value is reached.
   * This is necessary for apps that don't
   * clearWarnings() on their own, otherwise filling up memory might occur.
   */
  public void countForClearWarnings() {
    if (maxCountForClearWarnings > 0) {
      counterForClearWarnings++;
      if (counterForClearWarnings >= maxCountForClearWarnings)  {
        logAndClearWarnings();
        counterForClearWarnings = 0;
      }
    }
  }



  /**
   * Forces a detach of the db connection.
   * The method is used from connection managers only, hence package scope.
   */
  void forceDetached() {
    if (isAttached()) {
      // database is still attached
      LOGGER.warning("forcing detach of connection " + this + " attached to " + db);
      // close pending statements
      closePreparedStatements(true);
      if (!isClosed()) {
        try {
          if (!getAutoCommit()) {
            // in transaction!!! rollback
            LOGGER.severe("rolling back pending transaction for " + db);
            rollback(true);
          }
        }
        catch (PersistenceException pex) {
          LOGGER.severe("connection is broken", pex);
          closeImpl();
        }
      }
      if (db != null) {
        // if not already detached via closePreparedStatements(true)
        db.setConnection(null);
        db = null;
      }
      attachCount = 0;
      statementHistory.clear();
      currentStatement = null;
      openResultSets.clear();
    }
    // else already detached
  }


  /**
   * Closes the connection.<br>
   * Closing an already closed connection is allowed.
   */
  public void close() {
    if (!isClosed()) {
      try {
        // rollback before close to prevent an implicit commit (JDBC implementation specific behaviour)
        if (!connection.getAutoCommit())  {
          LOGGER.warning("closing connection while in transaction:\n" + toDiagnosticString());
          connection.rollback();
        }
      }
      catch (SQLException ex) {
        LOGGER.warning("low level rollback failed: " + ex.getMessage());
      }

      logAndClearWarnings();
      forceDetached();
      closePreparedStatements(false);

      closeImpl();
    }
  }


  /**
   * Closes the connection.
   */
  private void closeImpl() {
    try {
      connection.close();
    }
    catch (SQLException ex) {
      LOGGER.warning("low level close failed: " + ex.getMessage() + " -> " + this + " marked closed!");
    }
    finally {
      connection = null;          // to GC
      getManagedConnections();    // remove unreferenced or closed entries
    }
  }


  /**
   * Gets the connection's closed state.
   *
   * @return true if connection is closed
   */
  public boolean isClosed() {
    return connection == null;
  }



  @Override
  protected void finalize() throws Throwable {
    try {
      if (!isClosed()) {
        LOGGER.warning("closing unreferenced open connection: " + this);
        close();
      }
    }
    catch (Exception ex) {
      try {
        LOGGER.warning("closing unreferenced connection '" + this + "' failed in finalizer", ex);
      }
      catch (Exception ex2) {
        // don't stop finalization if just the logging failed
      }
    }
    finally {
      super.finalize();
    }
  }




  /**
   * Closes prepared statements.
   *
   * @param onlyMarkedReady true if close only pending statements, false if all
   */
  public void closePreparedStatements(boolean onlyMarkedReady) {
    // close all statements
    for (Iterator<PreparedStatementWrapper> iter = preparedStatements.values().iterator(); iter.hasNext(); ) {
      PreparedStatementWrapper stmt = iter.next();
      if (!stmt.isClosed() &&   // if not already closed
          (!onlyMarkedReady || stmt.isMarkedReady())) { // or all or only pending
        try {
          stmt.close();
        }
        catch (RuntimeException ex) {
          // just log, we need to close really all
          LOGGER.warning("closing statement failed: " + stmt, ex);
        }
      }
      if (onlyMarkedReady) {
        iter.remove();
      }
    }
    if (!onlyMarkedReady) {
      preparedStatements.clear();
    }
  }


  /**
   * Removes a closed prepared statement.
   *
   * @param st the statement
   */
  public void removePreparedStatement(PreparedStatementWrapper st) {
    if (!st.isClosed()) {
      throw new PersistenceException(db, "statement still open: " + st);
    }
    StatementKey key = st.getStatementKey();
    if (key != null) {
      // if not a one-shot
      preparedStatements.remove(st.getStatementKey());
    }
  }


  /**
   * Creates a non-prepared statement.<br>
   *
   * One-shot statements (i.e. non-prepared statements) must attach the db as soon as they
   * are instantiated. The db is detached after executeUpdate or after executeQuery when
   * its result set is closed.
   *
   * @param resultSetType a result set type; one of
   *        <code>ResultSet.TYPE_FORWARD_ONLY</code>,
   *        <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
   *        <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
   * @param resultSetConcurrency a concurrency type; one of
   *        <code>ResultSet.CONCUR_READ_ONLY</code> or
   *        <code>ResultSet.CONCUR_UPDATABLE</code>
   * @return a new <code>Statement</code> object that will generate
   *         <code>ResultSet</code> objects with the given type and
   *         concurrency
   */
  public StatementWrapper createStatement (int resultSetType, int resultSetConcurrency) {
    try {
      Statement stmt = connection.createStatement(resultSetType, resultSetConcurrency);
      return new StatementWrapper(this, stmt);
    }
    catch (SQLException ex) {
      throw new PersistenceException(db, "creating statement failed for " + this, ex);
    }
  }


  /**
   * Creates a prepared statement.
   *
   * @param statementKey the statement key, null if one-shot
   * @param sql the sql code
   * @param resultSetType the result set type
   * @param resultSetConcurrency the result set concurrency
   * @return the statement
   */
  public PreparedStatementWrapper createPreparedStatement(StatementKey statementKey, String sql, int resultSetType, int resultSetConcurrency) {
    try {
      sql = backend.optimizeSql(sql);
      return new PreparedStatementWrapper(
              this,
              connection.prepareStatement(sql, resultSetType, resultSetConcurrency),
              statementKey,
              sql);
    }
    catch (SQLException e) {
      throw new PersistenceException(db, "creating prepared statement failed", e);
    }
  }


  /**
   * Gets a prepared statement.<br>
   *
   * The statement will be reused if already prepared.
   * Else it will be prepared according to the statement definition in BackendStatement.
   * <p>
   * Notice: this method will be invoked from within a synchronized block from a Db-instance,
   * so there's no need for special multi-threading handling.
   *
   * @param statementKey the unique statement key
   * @param alwaysPrepare true if always do a physical prepare
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   * @param sqlSupplier the SQL supplier
   * @return the prepared statement for this connection
   */
  public PreparedStatementWrapper getPreparedStatement (StatementKey statementKey, boolean alwaysPrepare,
                                                        int resultSetType, int resultSetConcurrency, Supplier<String> sqlSupplier)  {

    if (statementKey == null) {
      throw new PersistenceException("statement key required");
    }
    assertAttached();

    PreparedStatementWrapper prepStmt = preparedStatements.get(statementKey);

    if (prepStmt == null || prepStmt.isClosed() || alwaysPrepare) {
      // we need to prepare it
      prepStmt = createPreparedStatement(statementKey, sqlSupplier.get(), resultSetType, resultSetConcurrency);
      preparedStatements.put(statementKey, prepStmt);
      LOGGER.finer("statement {0} prepared on {1}", prepStmt, this);
    }
    else  {
      // already created: check that parameters are the same
      if (checkStatementResultSetParameters) {
        checkStatement(prepStmt, resultSetType, resultSetConcurrency);
      }
      LOGGER.finer("statement {0} re-used on {1}", prepStmt, this);
    }

    return prepStmt;
  }


  /**
   * Check that requested resulttype and concurrency matches the prepared statement to reuse.
   *
   * @param prepStmt the prepared statement
   * @param resultSetType the requested result set type
   * @param resultSetConcurrency the requested result set concurrency
   */
  private void checkStatement(PreparedStatementWrapper prepStmt, int resultSetType, int resultSetConcurrency) {
    Statement stmt = prepStmt.getStatement();
    try {
      if (stmt.getResultSetType() != resultSetType) {
        throw new PersistenceException("wrong requested resultset type: " + resultSetType + " != statement: " +
                stmt.getResultSetType() + " for '" + prepStmt.getSql() + "'");
      }
      if (stmt.getResultSetConcurrency() != resultSetConcurrency) {
        throw new PersistenceException("wrong requested resultset concurrency: " + resultSetConcurrency + " != statement: " +
                stmt.getResultSetConcurrency() + " for '" + prepStmt.getSql() + "'");
      }
    }
    catch (SQLException sqx) {
      throw new PersistenceException(
              "cannot determine prepared statement configuration for '" + prepStmt.getSql() + "'", sqx);
    }
  }

}
