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

import org.tentackle.common.Timestamp;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.io.ReconnectedException;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.MappedDiagnosticContext;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendVersionInfo;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * A JDBC connection managed by the {@link ConnectionManager}.
 * <p>
 * The connection provides some additional features such as a prepared statement cache
 * and translates {@link SQLException}s to {@link PersistenceException}s.
 * The {@link ConnectionManager} is responsible for attaching and detaching
 * the connection to a session.
 *
 * @author harald
 */
public class ManagedConnection {

  /**
   * Minimum statement history size to feed through the compressor.
   */
  private static final int MIN_STATEMENT_HISTORY_COMPRESSION = 256;


  /**
   * Minimum milliseconds a connection remains attached before being logged.<br>
   * 0 to disable, -1 to log every detach().
   * <p>
   * For analysis and debugging purposes only.
   */
  private static long logMinAttachMillis;

  /**
   * Flag to turn on statement statistics. (default is turned off)
   */
  private static boolean collectStatistics;

  /**
   * Optional periodical log of statistics in milliseconds. (default is off)
   * <br>
   * If turned on, the statistics will be logged and cleared every logStatisticsMillis milliseconds.
   */
  private static long logStatisticsMillis;

  /**
   * Optional filter to log only statements for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   */
  private static Pattern mdcFilter;

  /**
   * Optionally, enable statement checks for resultSetType and resultSetConcurrency.
   */
  private static boolean checkStatementResultSetParameters;

  /**
   * Logging level to log nested selects.<br>
   * When enabled, selects running in parallel on the same connection will be logged.
   * This usually means that from within a loop processing a result set, a method is invoked,
   * that in turn triggers another select (which may be perfectly ok or an unwanted side effect).
   */
  private static Level logLevelForParallelOpenResultSets;

  /**
   * Adds detected and logged parallel result sets to the {@link StatementTracer}.
   */
  private static boolean traceParallelOpenResultSets;

  /**
   * Optional limit for the number of statements per transaction.<br>
   * Useful to detect wild running transactions eating up all memory and blowing up the backend.<br>
   * The default is 0 (disabled).
   */
  private static int maxStatementsPerTransaction;


  /**
   * Gets the minimum milliseconds a connection remains attached before being logged.
   *
   * @return the minimum milliseconds
   */
  public static long getLogMinAttachMillis() {
    return logMinAttachMillis;
  }

  /**
   * Sets the minimum milliseconds a connection remains attached before being logged.
   *
   * @param millis milliseconds, 0 to disable, -1 to log every detach()
   */
  public static void setLogMinAttachMillis(long millis) {
    logMinAttachMillis = millis;
  }

  /**
   * Returns whether to collect statement statistics.
   *
   * @return true if statistics enabled
   */
  public static boolean isCollectingStatistics() {
    return collectStatistics;
  }

  /**
   * Sets a flag whether to collect statement statistics.
   *
   * @param collect true to count invocations and execution times
   */
  public static void setCollectingStatistics(boolean collect) {
    collectStatistics = collect;
  }

  /**
   * Gets the optional periodical log of statistics in milliseconds.
   *
   * @return the period in milliseconds, 0 if disabled (default)
   */
  public static long getLogStatisticsMillis() {
    return logStatisticsMillis;
  }

  /**
   * Sets the optional periodical log of statistics in milliseconds.<br>
   * If turned on, the statistics will be logged and cleared every logStatisticsMillis milliseconds.
   *
   * @param millis the period in milliseconds, 0 to disable
   */
  public static void setLogStatisticsMillis(long millis) {
    ManagedConnection.logStatisticsMillis = millis;
  }

  /**
   * Gets the MDC filter.
   *
   * @return the filter, null if none
   */
  public static Pattern getMdcFilter() {
    return mdcFilter;
  }

  /**
   * Sets the optional filter to log only statements for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   *
   * @param filter the filter, null to clear
   */
  public static void setMdcFilter(Pattern filter) {
    mdcFilter = filter;
  }

  /**
   * Returns whether to check that the requested result type and concurrency matches the prepared statement reused.
   *
   * @return true if the check is enabled
   */
  public static boolean isCheckingStatementResultSetParameters() {
    return checkStatementResultSetParameters;
  }

  /**
   * Sets whether to check that the requested result type and concurrency matches the prepared statement reused.
   *
   * @param check true to enable check
   */
  public static void setCheckingStatementResultSetParameters(boolean check) {
    checkStatementResultSetParameters = check;
  }

  /**
   * Gets the logging level to log nested selects.
   *
   * @return the level, null if disabled
   */
  public static Level getLogLevelForParallelOpenResultSets() {
    return logLevelForParallelOpenResultSets;
  }

  /**
   * Sets the logging level to log nested selects.<br>
   * If enabled, selects running in parallel on the same connection will be logged.
   * This usually means that from within a loop processing a result set, a method is invoked,
   * that in turn triggers another select.
   *
   * @param level the logging level, null to disable
   */
  public static void setLogLevelForParallelOpenResultSets(Level level) {
    logLevelForParallelOpenResultSets = level;
  }

  /**
   * Returns whether detected (and logged) parallel result sets should automatically be added to the {@link StatementTracer}.
   *
   * @return true to add, false is default
   */
  public static boolean isTraceParallelOpenResultSets() {
    return traceParallelOpenResultSets;
  }

  /**
   * Sets whether detected (and logged) parallel result sets should automatically be added to the {@link StatementTracer}.<br>
   * Enabling requires {@link #setLogLevelForParallelOpenResultSets(Level)} to be a valid loggable level.
   *
   * @param trace true to add, false is default
   */
  public static void setTraceParallelOpenResultSets(boolean trace) {
    traceParallelOpenResultSets = trace;
  }

  /**
   * Sets the limit for the number of statements per transaction.<br>
   * Useful to detect wild running transactions eating up all memory and blowing up the backend.<br>
   * The default is 0 (disabled).
   *
   * @param max the max. number of statements within a transaction.
   */
  public static void setMaxStatementsPerTransaction(int max) {
    maxStatementsPerTransaction = max;
  }

  /**
   * Gets the statement limit per transaction.
   *
   * @return number of statements, 0 if unlimited (default)
   */
  public static int getMaxStatementsPerTransaction() {
    return maxStatementsPerTransaction;
  }


  private static final Logger LOGGER = Logger.get(ManagedConnection.class);

  private static final AtomicLong INSTANCE_COUNTER = new AtomicLong();

  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources to be cleaned up.<br>
   * Notice that ManagedConnections are referenced by their ConnectionManager.
   * As a consequence, they will be cleaned up when the ConnectionManager is cleaned up.
   */
  private static class Resources implements Runnable {
    private volatile Connection connection; // the wrapped connection, null if closed
    private String name;                    // the name of the managed connection
    private volatile boolean dead;          // the connection is dead (comlink error detected)
    private ManagedConnection me;           // != null if programmatic cleanup

    Resources(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void run() {
      try {
        Connection c = connection;
        if (c != null) {
          if (me == null) {
            // application lost the reference and did not close it before
            LOGGER.warning("closing unreferenced connection {0}", name);
          }
          try {
            c.close();
          }
          catch (SQLException | RuntimeException sx) {
            if (!dead) {
              LOGGER.severe("low level close failed for " + name + " -> marked closed", sx);
            }
          }
          finally {
            connection = null;
          }
        }
      }
      finally {
        me = null;
      }
    }
  }


  private final Resources resources;                      // resources to be cleaned up
  private final Cleanable cleanable;                      // to clean up the connection
  private final ConnectionManager manager;                // the manager that created this connection
  private final Backend backend;                          // the backend
  private final BackendVersionInfo backendVersionInfo;    // the backend's database version
  private final String backendId;                         // a unique ID to identify the connection at the backend
  private final long instanceNumber;                      // unique instance number (for logging only)
  private final long establishedSince;                    // connection established establishedSince... (epochal [ms])
  private final int defaultTxIsolation;                   // the initial/default transaction isolation level
  private final boolean defaultReadOnly;                  // the initial/default read-only mode
  private int txIsolation;                                // the current transaction isolation level
  private boolean readOnly;                               // the current read-only mode
  private volatile Db db;                                 // currently attached Db, null = free connection
  private long expireAt;                                  // connection shutdown at (epochal [ms]), 0 = forever
  private volatile long attachedSince;                    // attached since when...
  private volatile long detachedSince;                    // detached since when...
  private volatile long lastVerified;                     // last connection verification
  private int index;                                      // connection index given by connection manager, -1 not used
  private int attachCount;                                // 0 = not attached, else number of times db is attached
  private int maxCountForClearWarnings;                   // trigger when to clearWarning() on a connection (0 = disabled)
  private int counterForClearWarnings;                    // current counter
  private StatementHistory currentStatement;              // statement history while not in transaction
  private long connectionInactivityTimeoutMs;             // optional database connection inactivity timeout
  private boolean connectionKeepAliveEnabled;             // optional periodical fake-select to prevent premature close of idle connection

  private final List<StatementHistory> statementHistory;                        // statement history while in transaction
  private final Map<StatementKey,PreparedStatementWrapper> preparedStatements;  // all prepared statements for this connection (stmtId:statement)
  private final Set<WeakReference<ResultSetWrapper>> openResultSets;            // pending open result sets (thread safe bec. of WeakRefs)
  private final Set<WeakReference<StatementWrapper>> runningStatements;         // running statements (thread safe bec. of WeakRefs)


  /**
   * Creates a managed connection.
   *
   * @param manager the connection manager
   * @param backend the backend
   * @param backendVersionInfo the backend's database version
   * @param connection the low-level JDBC connection
   */
  public ManagedConnection(ConnectionManager manager, Backend backend, BackendVersionInfo backendVersionInfo, Connection connection) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.backend = Objects.requireNonNull(backend, "backend");
    this.backendVersionInfo = Objects.requireNonNull(backendVersionInfo, "backendVersionInfo");

    backendId = backend.getBackendId(Objects.requireNonNull(connection, "connection"));
    instanceNumber = INSTANCE_COUNTER.incrementAndGet();
    establishedSince = System.currentTimeMillis();
    detachedSince = establishedSince;
    index = -1;
    statementHistory = Collections.synchronizedList(new ArrayList<>());   // may be cleared by forceDetached() from another thread
    preparedStatements = new ConcurrentHashMap<>();
    // use a thread-safe set due to concurrent cleanup of weak references in getOpenResultSets and cancelRunningStatements
    openResultSets = ConcurrentHashMap.newKeySet();
    runningStatements = ConcurrentHashMap.newKeySet();

    resources = new Resources(connection);
    resources.name = toString();    // cannot be a constructor arg because of chicken egg
    cleanable = CLEANER.register(this, resources);

    defaultTxIsolation = txIsolation = getTransactionIsolation();
    defaultReadOnly = readOnly = readConnectionReadOnly();

    ManagedConnectionMonitor.getInstance().registerManagedConnection(this);
  }


  /**
   * Gets the connection manager.
   *
   * @return the manager
   */
  public ConnectionManager getManager() {
    return manager;
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Gets the version of the backend's database this connection is bound to.
   *
   * @return the backend version info
   */
  public BackendVersionInfo getBackendVersionInfo() {
    return backendVersionInfo;
  }

  /**
   * Gets the low-level connection.
   *
   * @return the physical connection, never null
   * @throws PersistenceException if the connection is closed
   */
  public Connection getConnection() {
    Connection c = resources.connection;
    if (c == null) {
      throw new PersistenceException("connection is closed");
    }
    return c;
  }

  /**
   * Gets the optional connection inactivity timeout.
   *
   * @return the timeout in [ms]
   */
  public long getConnectionInactivityTimeoutMs() {
    return connectionInactivityTimeoutMs;
  }

  /**
   * Sets the optional connection inactivity timeout.
   *
   * @param connectionInactivityTimeoutMs the timeout in [ms]
   */
  public void setConnectionInactivityTimeoutMs(long connectionInactivityTimeoutMs) {
    this.connectionInactivityTimeoutMs = connectionInactivityTimeoutMs;
  }

  /**
   * Returns whether fake selects are sent periodically to the backend.
   *
   * @return true if idle connections kept alive
   */
  public boolean isConnectionKeepAliveEnabled() {
    return connectionKeepAliveEnabled;
  }

  /**
   * Sets the optional flag to send fake selects periodically to the backend.
   *
   * @param connectionKeepAliveEnabled true to keep idle connections alive
   */
  public void setConnectionKeepAliveEnabled(boolean connectionKeepAliveEnabled) {
    this.connectionKeepAliveEnabled = connectionKeepAliveEnabled;
  }

  /**
   * Gets the epochal time in ms when this connection was verified.
   *
   * @return the last verification, 0 if never been verified at all
   */
  public long getLastVerified() {
    return lastVerified;
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
   * @return the epochal time of the last attach(), 0 if not attached
   */
  public long getAttachedSince() {
    return attachedSince;
  }

  /**
   * Gets the epochal time when this connection was detached.
   * <p>
   * Note that newly created connections get their detach-time initialized
   * from the current system time.
   *
   * @return the epochal time of the last detach(), 0 if attached
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
   * Marking connections dead allows connection managers to close and re-open
   * connections before being attached next time.
   *
   * @param dead true if marked dead, false not dead
   */
  public void setDead(boolean dead) {
    if (dead && !resources.dead) {
      LOGGER.severe("managed connection {0}: *** MARKED DEAD ***", this);
    }
    resources.dead = dead;
  }

  /**
   * Returns whether the connection is marked dead.
   *
   * @return true if dead
   */
  public boolean isDead() {
    return resources.dead;
  }

  /**
   * Returns whether timeout is enabled and the connection wasn't used for a long time and needs verification.
   *
   * @return true if {@link #verifyConnection()} should be invoked before use
   */
  public boolean isConnectionVerificationNecessary() {
    return isConnectionVerificationNecessary(System.currentTimeMillis());
  }

  /**
   * Returns whether timeout is enabled and the connection wasn't used for a long time and needs verification.
   *
   * @param currentTimeMillis the current epochal time in milliseconds
   * @return true if {@link #verifyConnection()} should be invoked before use
   */
  public boolean isConnectionVerificationNecessary(long currentTimeMillis) {
    return connectionInactivityTimeoutMs > 0 && detachedSince > 0 &&
           currentTimeMillis - detachedSince >= connectionInactivityTimeoutMs;
  }

  /**
   * Checks whether the connection is still valid.<p>
   * Usually implemented by the backend via a "SELECT 1" query.<br>
   * If the check fails, the connection is marked dead.
   *
   * @return true if connection still valid, false if invalid
   * @see Backend#getDummySelect()
   */
  public boolean verifyConnection() {
    try (Statement stmt = getConnection().createStatement()) {
      // Can be invoked at any time, even if another statement is currently running
      // on this connection (submitted from another thread) since JDBC drivers are thread-safe these days.
      // However, the invocation may be blocked until the other statement finishes.
      stmt.executeQuery(getBackend().getDummySelect());
      lastVerified = System.currentTimeMillis();
      return true;
    } // stmt.close closes the result set as well
    catch (RuntimeException | SQLException ex) {
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

    Objects.requireNonNull(db, "db");

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
   * Checks whether a session is attached to this connection.
   *
   * @return true if attached, else false.
   */
  public boolean isAttached() {
    return db != null;
  }

  /**
   * Returns whether a transaction is running.
   *
   * @return true if the connection is attached to a session in transaction
   */
  public boolean isTxRunning() {
    return isAttached() && db.isTxRunning();
  }

  /**
   * Returns whether the connection has expired.<br>
   * Expired connections should be closed and reopened.
   *
   * @return true if expired
   */
  public boolean isExpired() {
    return expireAt > 0 && expireAt < detachedSince;
  }

  /**
   * Detaches a session from a connection.
   * Connections must be detached before they can be used by another Db.
   * The method must only be invoked by a connection manager!
   *
   * @param db the db to detach
   */
  public void detachSession(Db db) {
    Objects.requireNonNull(db, "db");

    if (this.db != db) {
      throw new PersistenceException(db, "connection " + this + " not attached to " + db + " (instead attached to " + this.db + ")");
    }
    LOGGER.finer("{0} detached from {1}, count={2}", db, this, attachCount);

    attachCount--;

    if (attachCount == 0) {
      detachedSince = System.currentTimeMillis();
      if (logMinAttachMillis != 0 && detachedSince - attachedSince > logMinAttachMillis) {
        if (isMDCValid()) {
          LOGGER.warning("attached for {0}ms: {1}", () -> detachedSince - attachedSince, this::toDiagnosticString);
        }
      }
      else if (isMDCValid()) {
        LOGGER.fine(this::toDiagnosticString);
      }
      this.db = null;
      db.setConnection(null);
      attachedSince = 0;

      if (collectStatistics) {
        if (logStatisticsMillis > 0 && detachedSince - StatementStatistics.getSince() > logStatisticsMillis) {
          // log and clear statistics
          StatementStatistics.log(Logger.Level.INFO, true);
          DbTransactionFactory.getInstance().logStatistics(Logger.Level.INFO, true);
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

      restoreTransactionDefaults();
    }
    else {
      if (db.getConnection() != this) {
        throw new PersistenceException(db, "db lost current connection " + this + ", count=" + attachCount++);
      }
      if (attachCount < 0) {
        throw new PersistenceException(db, "connection " + this + " detached too often, attach count = " + attachCount++);
      }
    }
  }

  /**
   * Gets the attached session.
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
      buf.append('/').append(backendId);
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    Connection con = resources.connection;  // local variable because could be closed between test and use
    if (con == null) {
      buf.append("<closed connection>");
    }
    else {
      buf.append(con);
    }
    buf.append('[').append(getName());
    if (index >= 0) {
      buf.append('/').append(index);
    }
    buf.append(']');
    if (attachCount > 0) {
      buf.append(", attached ").append(attachCount).append(" since ").append(new Timestamp(attachedSince));
    }
    if (resources.dead) {
      buf.append(", *DEAD*");
    }
    return buf.toString();
  }

  /**
   * Gets the statement history.
   *
   * @return the statements executed during the last attachment
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
      Collection<ResultSetWrapper> connectionResultSets = getOpenResultSets();
      if (!connectionResultSets.isEmpty()) {
        List<ResultSetWrapper> resultSetsToLog = new ArrayList<>();
        for (ResultSetWrapper resultSet: connectionResultSets) {
          StatementWrapper statement = resultSet.getStatement();
          if (statement != null && !statement.isParallelOk()) {
            resultSetsToLog.add(resultSet);
          }
        }
        if (resultSetsToLog.size() > 1) {
          StringBuilder buf = new StringBuilder();
          buf.append("parallel queries running on ").append(this).append(':');
          for (ResultSetWrapper resultSet: resultSetsToLog) {
            buf.append('\n').append(resultSet);
            if (traceParallelOpenResultSets) {
              // enable tracer for all future invocations of this statement (if not done already)
              // this will log the invocation trace of the statement (see StatementWrapper.toString())
              StatementWrapper statement = resultSet.getStatement();
              if (statement != null) {
                String sql = statement.getSql();
                if (sql != null) {
                  StatementTracer tracer = StatementTracer.getInstance();
                  tracer.addSql(sql);
                  tracer.setEnabled(true);
                }
              }
            }
          }
          LOGGER.log(logLevelForParallelOpenResultSets, buf.toString());
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
      // if closed or unreferenced or the result set to remove
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
    buf.append(": ")
       .append(manager)
       .append('[')
       .append(index)
       .append("] valid ")
       .append(new Date(establishedSince))
       .append(" - ");
    if (expireAt != 0) {
      buf.append(new Date(expireAt));
    }
    else {
      buf.append(" <unlimited>");
    }
    if (readOnly != defaultReadOnly) {
      buf.append(", ").append(TransactionWritability.valueOf(!readOnly));
    }
    if (txIsolation != defaultTxIsolation) {
      buf.append(", ").append(TransactionIsolation.valueOf(txIsolation));
    }

    Db lDb = db;    // may be closed in the meantime...
    if (lDb != null) {
      buf.append("\n    -> ").append(lDb.getName());
      if (lDb.getTxName() != null) {
        buf.append('/').append(lDb.getTxName());
      }
      Thread ownerThread = lDb.getOwnerThread();
      if (ownerThread != null) {
        buf.append(", ").append(lDb.getOwnerThread());
      }
      for (RemoteDbSessionImpl session: RemoteDbSessionImpl.getOpenSessions()) {
        if (session.getSession() == lDb) {
          // this is my session!
          buf.append(", ").append(session);
        }
      }
    }

    try {
      if (!statementHistory.isEmpty() || currentStatement != null) {
        buf.append("\n\n    Statements:");
        try {
          buf.append(logStatementHistory(statementHistory, false));
        }
        catch (ConcurrentModificationException cmx) {
          buf.append(" (history may be incomplete because connection is still in use by other threads)");
          buf.append(logStatementHistory(new ArrayList<>(statementHistory), true));   // try on copy
        }
        StatementHistory stmt = currentStatement;
        if (stmt != null) {
          buf.append("\n    ");
          buf.append(stmt);
        }
        buf.append("\n");
      }
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
   * Gets the current transaction isolation level.
   *
   * @return the isolation level
   * @see Connection#getTransactionIsolation()
   */
  public int getTransactionIsolation() {
    try {
      return getConnection().getTransactionIsolation();
    }
    catch (SQLException sx) {
      throw createFromSqlException("getting transaction isolation level failed", sx);
    }
  }

  /**
   * Sets the transaction isolation level.
   *
   * @param txIsolation the isolation level
   * @see Connection#setTransactionIsolation(int)
   */
  public void setTransactionIsolation(int txIsolation) {
    try {
      getConnection().setTransactionIsolation(txIsolation);
      this.txIsolation = txIsolation;
    }
    catch (SQLException sx) {
      throw createFromSqlException("setting transaction isolation level to " + txIsolation + " failed", sx);
    }
  }

  /**
   * Gets the read-only mode.
   * <p>
   * Returns the mode this connection was last {@link #setReadOnly(boolean) put into}, not what
   * the driver reports. Drivers are free to treat {@link Connection#setReadOnly(boolean)} as a
   * hint: H2, for example, accepts it silently and keeps answering {@code false}, because its
   * {@link Connection#isReadOnly()} describes the database, not the session. Asking the driver
   * would therefore make {@link TransactionWritability#READ_ONLY} unenforceable on such a
   * backend and let a nested read-write transaction slip into a read-only one unnoticed.
   *
   * @return true if read-only
   * @see #readConnectionReadOnly()
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Gets the read-only mode as reported by the driver.
   *
   * @return true if read-only
   * @see Connection#isReadOnly()
   */
  private boolean readConnectionReadOnly() {
    try {
      return getConnection().isReadOnly();
    }
    catch (SQLException sx) {
      throw createFromSqlException("getting read-only mode failed", sx);
    }
  }

  /**
   * Sets the read-only mode.
   *
   * @param readOnly true if read-only
   * @see Connection#setReadOnly(boolean)
   */
  public void setReadOnly(boolean readOnly) {
    try {
      getConnection().setReadOnly(readOnly);
      this.readOnly = readOnly;
    }
    catch (SQLException sx) {
      throw createFromSqlException("setting read-only mode to " + readOnly + " failed", sx);
    }
  }

  /**
   * Sets the autocommit feature.
   * <p>
   * Restores the default transaction isolation level and read-only mode, if set to true,
   * and isolation or read-only mode was changed.
   *
   * @param autoCommit true to enable autocommit, false to disable.
   */
  public void setAutoCommit(boolean autoCommit) {
    assertAttached();
    try {
      getConnection().setAutoCommit(autoCommit);
      if (autoCommit) {
        restoreTransactionDefaults();
      }
    }
    catch (SQLException ex) {
      throw createFromSqlException("setting autocommit to " + autoCommit + " failed", ex);
    }
  }

  /**
   * Gets the autocommit value.
   *
   * @return the autocommit value.
   */
  public boolean getAutoCommit() {
    try {
      return getConnection().getAutoCommit();
    }
    catch (SQLException ex) {
      throw createFromSqlException("getting autocommit failed", ex);
    }
  }

  /**
   * Performs a commit.
   */
  public void commit() {
    assertAttached();
    try {
      getConnection().commit();
    }
    catch (SQLException ex) {
      throw createFromSqlException("commit failed", ex);
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
      if (withLog) {
        LOGGER.info(this::toDiagnosticString);
      }
      getConnection().rollback();
      // unmark all statements marked ready
      unmarkPreparedStatements();
    }
    catch (SQLException ex) {
      throw createFromSqlException("rollback failed: " + toDiagnosticString(), ex);
    }
  }

  /**
   * Creates an unnamed savepoint in the current transaction.
   *
   * @return the savepoint
   */
  public Savepoint setSavepoint() {
    assertAttached();
    try {
      return getConnection().setSavepoint();
    }
    catch (SQLException ex) {
      throw createFromSqlException("setting unnamed savepoint failed: " + toDiagnosticString(), ex);
    }
  }

  /**
   * Creates a savepoint with the given name in the current transaction.
   *
   * @param name the savepoint name
   *
   * @return the savepoint
   */
  public Savepoint setSavepoint(String name) {
    assertAttached();
    try {
      return getConnection().setSavepoint(name);
    }
    catch (SQLException ex) {
      throw createFromSqlException("setting savepoint '" + name + "' failed: " + toDiagnosticString(), ex);
    }
  }

  /**
   * Undoes all changes made after the given <code>Savepoint</code> object was set.
   *
   * @param savepoint the savepoint
   */
  public void rollback(Savepoint savepoint) {
    assertAttached();
    try {
      getConnection().rollback(savepoint);
    }
    catch (SQLException ex) {
      throw createFromSqlException("rollback of savepoint '" + savepoint + "' failed: " + toDiagnosticString(), ex);
    }
  }

  /**
   * Removes the specified <code>Savepoint</code> and subsequent <code>Savepoint</code> objects from the current
   * transaction.
   *
   * @param savepoint the savepoint to be removed
   */
  public void releaseSavepoint(Savepoint savepoint) {
    assertAttached();
    if (db.getBackend().isReleaseSavepointSupported()) {
      try {
        getConnection().releaseSavepoint(savepoint);
      }
      catch (SQLException ex) {
        throw createFromSqlException("release of savepoint '" + savepoint + "' failed: " + toDiagnosticString(), ex);
      }
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
   * Cancels all currently running statements.<br>
   * May be invoked from any thread.
   */
  public void cancelRunningStatements() {
    for (Iterator<WeakReference<StatementWrapper>> iter = runningStatements.iterator(); iter.hasNext(); ) {
      WeakReference<StatementWrapper> ref = iter.next();
      StatementWrapper stmt = ref.get();
      if (stmt != null && stmt.isRunning()) {
        try {
          stmt.cancel();
        }
        catch (RuntimeException ex) {
          // just log
          LOGGER.warning("canceling statement failed: " + stmt, ex);
        }
      }
      iter.remove();
    }
  }

  /**
   * Reads all warnings, logs, and clears them.
   */
  public void logAndClearWarnings() {
    try {
      // log connection warning
      SQLWarning warning = getConnection().getWarnings();
      while (warning != null) {
        LOGGER.warning(warning.getMessage());
        warning = warning.getNextWarning();
      }
      getConnection().clearWarnings();  // release memory used by warnings
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
      // as this statement is called when there are no more running statements,
      // we ignore the unprepared running statements.
    }
    catch (SQLException e) {
      if (!resources.dead) {
        throw createFromSqlException("reading warnings failed", e);
      }
    }
  }

  /**
   * Sets the countForClearWarnings trigger.
   *
   * @param max the maximum count, 0 = app must eat the warnings
   */
  public void setMaxCountForClearWarnings(int max) {
    maxCountForClearWarnings = max;
  }

  /**
   * Gets the current setting for clearWarnings() trigger.
   *
   * @return the maximum count, 0 = app must eat the warnings
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
   * Closes the connection.<br>
   * Closing an already closed connection is allowed.
   */
  public void close() {
    if (!isClosed()) {
      try {
        // rollback before close to prevent an implicit commit (JDBC implementation specific behavior)
        if (!getConnection().getAutoCommit())  {
          LOGGER.warning("closing connection while in transaction:\n{0}", this::toDiagnosticString);
          getConnection().rollback();
        }
      }
      catch (SQLException ex) {
        if (!resources.dead) {
          LOGGER.warning("low level rollback failed: {0}", ex.getMessage());
        }
      }

      logAndClearWarnings();
      forceDetached();
      closePreparedStatements(false);

      closeImpl();
    }
  }

  /**
   * Gets the connection's closed state.
   *
   * @return true if the connection is closed
   */
  public boolean isClosed() {
    return resources.connection == null;
  }

  /**
   * Closes prepared statements.
   *
   * @param onlyMarkedReady true if close only pending statements, false if all
   */
  public void closePreparedStatements(boolean onlyMarkedReady) {
    // close all statements
    for (PreparedStatementWrapper stmt : preparedStatements.values()) {
      if (!stmt.isClosed() &&   // if not already closed
          (!onlyMarkedReady || stmt.isMarkedReady())) { // or all or only pending
        try {
          stmt.close();
        }
        catch (RuntimeException ex) {
          // log that, we need to close them all
          if (!resources.dead) {
            LOGGER.warning("closing statement failed: " + stmt, ex);
          }
        }
      }
    }
    if (!onlyMarkedReady) {
      // drop whatever is left over, for example, statements that could not be closed
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
   * Creates a non-prepared statement.
   * <p>
   * One-shot statements (i.e., non-prepared statements) must attach the db as soon as they
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
      Statement stmt = getConnection().createStatement(resultSetType, resultSetConcurrency);
      return new StatementWrapper(this, stmt);
    }
    catch (SQLException ex) {
      throw createFromSqlException("creating statement failed for " + this, ex);
    }
  }

  /**
   * Creates a prepared statement.
   *
   * @param statementKey the statement key, null if one-shot
   * @param sql the SQL code
   * @param resultSetType the result set type
   * @param resultSetConcurrency the result set concurrency
   * @return the statement
   */
  public PreparedStatementWrapper createPreparedStatement(StatementKey statementKey, String sql,
                                                          int resultSetType, int resultSetConcurrency) {
    try {
      sql = backend.optimizeSql(sql);
      return new PreparedStatementWrapper(
              this,
              getConnection().prepareStatement(sql, resultSetType, resultSetConcurrency),
              statementKey,
              sql);
    }
    catch (SQLException e) {
      throw createFromSqlException("creating prepared statement failed", e);
    }
  }

  /**
   * Gets a prepared statement.
   * <p>
   * The statement will be reused if already prepared.
   * Otherwise, it will be prepared from the SQL code provided by the SQL supplier.
   *
   * @param statementKey the unique statement key
   * @param alwaysPrepare true if always do a physical preparing
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   * @param sqlSupplier the SQL supplier
   * @return the prepared statement for this connection
   */
  public PreparedStatementWrapper getPreparedStatement (StatementKey statementKey, boolean alwaysPrepare,
                                                        int resultSetType, int resultSetConcurrency,
                                                        SqlSupplier sqlSupplier)  {

    if (statementKey == null) {
      throw new PersistenceException("statement key required");
    }
    assertAttached();

    PreparedStatementWrapper prepStmt = preparedStatements.get(statementKey);

    if (prepStmt == null || prepStmt.isClosed() || alwaysPrepare) {
      if (prepStmt != null && !prepStmt.isClosed()) {
        /*
         * alwaysPrepare: the cached statement is replaced by a freshly prepared one.
         * It must be closed, else it would be leaked until the physical connection is closed.
         * Notice that closing removes it from the cache, so this must happen before the put below.
         */
        try {
          prepStmt.close();
        }
        catch (RuntimeException ex) {
          if (!resources.dead) {
            LOGGER.warning("closing replaced statement failed: " + prepStmt, ex);
          }
        }
      }
      prepStmt = createPreparedStatement(statementKey, sqlSupplier.get(backend).toString(), resultSetType, resultSetConcurrency);
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
   * Checks for a dead communications link.<br>
   * If the link is down, for example, because the database server closed it,
   * the connection is marked dead.
   *
   * @param ex the SQL exception
   * @return true if the connection is dead
   */
  public boolean checkForDeadLink(SQLException ex) {
    if (!isDead() && backend.isCommunicationLinkException(ex)) {
      // some severe comlink error, probably closed by server
      setDead(true);
    }
    return isDead();
  }

  /**
   * Creates a {@link PersistenceException} or {@link SessionClosedException} depending on the SQL error.<br>
   * If the session was reconnected, a {@link ReconnectedException} is thrown.
   *
   * @param message the message
   * @param sx the SQL exception
   * @return the persistence exception
   */
  public PersistenceException createFromSqlException(String message, SQLException sx) {
    PersistenceException persistenceException;
    if (checkForDeadLink(sx)) {
      persistenceException = new SessionClosedException(db, message, sx);
      Db ldb = db;
      if (ldb != null && ldb.optionallyReconnect()) {
        throw new ReconnectedException(persistenceException);
      }
    }
    else {
      persistenceException = new PersistenceException(db, message, sx);
    }
    return persistenceException;
  }


  /**
   * Logs the execution start of a statement.<br>
   * The execution start is the current epochal time.
   * The execution end must be set via {@link StatementHistory#end()}.
   * Within a transaction, the statement is appended to the statement history.
   * Otherwise, it replaces the current statement.
   *
   * @param statement the statement
   * @param sql optional SQL string if statement does not provide it
   * @return the history with an execution start of now and execution end not set, never null
   * @throws PersistenceException if the {@link #setMaxStatementsPerTransaction(int) statement limit} is exceeded
   */
  StatementHistory logStatementHistory(StatementWrapper statement, String sql) {
    StatementHistory history = new StatementHistory(statement, sql);
    if (isTxRunning()) {
      statementHistory.add(history);
      if (maxStatementsPerTransaction != 0 && statementHistory.size() > maxStatementsPerTransaction) {
        throw new PersistenceException(db, "transaction limit of statements exceeded (" + maxStatementsPerTransaction + ")");
      }
    }
    else  {
      currentStatement = history;
    }
    return history;
  }

  /**
   * Forces the connection to be detached from the session.<br>
   * The method is used from connection managers only, hence package scope.
   */
  void forceDetached() {
    if (isAttached()) {
      // the database is still attached
      LOGGER.warning("forcing detach of connection {0}, attached to {1}", this, db);
      cancelRunningStatements();
      // close pending statements
      closePreparedStatements(true);
      if (!isClosed()) {
        try {
          if (!getAutoCommit()) {
            // in transaction!!! rollback
            LOGGER.severe("rolling back pending transaction for {0}", db);
            rollback(true);
          }
        }
        catch (PersistenceException pex) {
          if (!resources.dead) {
            LOGGER.severe("connection is broken", pex);
          }
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
      restoreTransactionDefaults();
    }
    // else already detached
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
   * Cleans up after a rollback not initiated via {@link #rollback(boolean)}.<br>
   * Used when a global transaction is rolled back by the transaction manager.
   */
  void rolledBackExternally() {
    unmarkPreparedStatements();
  }

  /**
   * Unmarks all prepared statements that are marked ready.
   */
  private void unmarkPreparedStatements() {
    // unmark all statements marked ready
    for (PreparedStatementWrapper stmt: preparedStatements.values()) {
      if (stmt != null && stmt.isMarkedReady()) {
        try {
          stmt.consume();
        }
        catch (RuntimeException ex) {
          // just log (if not canceled)
          if (!stmt.isCanceled()) {
            LOGGER.warning("unmarking statement failed: " + stmt, ex);
          }
        }
      }
    }
  }

  /**
   * Closes the connection.
   */
  private void closeImpl() {
    resources.me = this;
    cleanable.clean();
  }

  /**
   * Creates a StringBuilder from the statement history, compressing immediately repeating runs.
   *
   * @param statementHistory the history
   * @param isCopy true if the history is already a copy
   * @return the builder
   */
  private StringBuilder logStatementHistory(List<StatementHistory> statementHistory, boolean isCopy) {
    if (statementHistory.size() < MIN_STATEMENT_HISTORY_COMPRESSION) {
      StringBuilder buf = new StringBuilder();
      for (StatementHistory stmt : statementHistory) {
        buf.append("\n    ").append(stmt);
      }
      return buf;
    }
    // large statement history, check for loops and compress
    return StatementHistoryCompressor.compress(isCopy ? statementHistory : new ArrayList<>(statementHistory));
  }

  /**
   * Asserts that a connection is attached.
   */
  private void assertAttached() {
    if (db == null) {
      throw new PersistenceException("connection " + this + " not attached to any Db");
    }
  }

  /**
   * Checks that the requested result type and concurrency match the prepared statement to reuse.
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
      throw createFromSqlException("cannot determine prepared statement configuration for '" + prepStmt.getSql() + "'", sqx);
    }
  }

  /**
   * Restores the transaction defaults.
   */
  private void restoreTransactionDefaults() {
    if (txIsolation != defaultTxIsolation) {
      setTransactionIsolation(defaultTxIsolation);
    }
    if (readOnly != defaultReadOnly) {
      setReadOnly(defaultReadOnly);
    }
  }

}
