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

import org.tentackle.common.Compare;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.TimeKeeper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Statement statistics collector.
 *
 * @author harald
 */
public class StatementStatistics implements Comparable<StatementStatistics> {

  private static final Logger LOGGER = Logger.get(StatementStatistics.class);

  private static final String LOG_TAG = "    >SQL-Stats: ";

  // instance counter
  private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

  // statistics collector
  private static final Map<StatementStatistics,StatementStatistics> STAT_MAP = new HashMap<>();

  // the time statistics began in epochal milliseconds.
  private static long since;


  /**
   * If true, the statistics are per statement and parameters, else only per statement.
   */
  private static boolean withParameters;


  /**
   * Sets whether statistics should be separated according to the statement parameters.
   *
   * @param withParams true if with parameter stats
   */
  public static void setWithParameters(boolean withParams) {
    withParameters = withParams;
  }

  /**
   * Returns whether statistics should be separated according to the statement parameters.
   *
   * @return rue if with parameter stats
   */
  public static boolean isWithParameters() {
    return withParameters;
  }


  /**
   * Collects the statistics from a statement history.
   *
   * @param history the history to collect
   */
  public static synchronized void collect(StatementHistory history) {
    List<Map<Integer, Object>> batchParameters = history.getBatchParameters();
    if (withParameters) {
      if (batchParameters != null && !batchParameters.isEmpty()) {    // size must be > 0!
        TimeKeeper singleDuration = history.getExecutionDuration().clone().divide(batchParameters.size());
        for (Map<Integer, Object> parameters : batchParameters) {
          collect(new StatementStatistics(history.getSql(), history.getTxName(), parameters), singleDuration);
        }
      }
      else {
        collect(new StatementStatistics(history.getSql(), history.getTxName(), history.getParameters()), history);
      }
    }
    else {
      if (batchParameters != null && !batchParameters.isEmpty()) {
        collect(new StatementStatistics(history.getSql(), history.getTxName(), null), history.getExecutionDuration(), batchParameters.size());
      }
      else {
        collect(new StatementStatistics(history.getSql(), history.getTxName(), null), history);
      }
    }
  }

  private static void collect(StatementStatistics key, TimeKeeper timeKeeper, int count) {
    STAT_MAP.computeIfAbsent(key, k -> k).countInvocation(timeKeeper, count);
  }

  private static void collect(StatementStatistics key, TimeKeeper timeKeeper) {
    STAT_MAP.computeIfAbsent(key, k -> k).countInvocation(timeKeeper);
  }

  private static void collect(StatementStatistics key, StatementHistory history) {
    STAT_MAP.computeIfAbsent(key, k -> k).countInvocation(history);
  }


  /**
   * Gets the current statistics.
   *
   * @param clear true if clear
   * @return the statement statistics sorted by SQL and parameters
   */
  public static synchronized Set<StatementStatistics> getStatistics(boolean clear) {
    Set<StatementStatistics> stats = new TreeSet<>(STAT_MAP.values());
    if (clear) {
      clearStatistics();
    }
    return stats;
  }


  /**
   * Clears the counters.
   */
  public static synchronized void clearStatistics() {
    STAT_MAP.clear();
    since = System.currentTimeMillis();
  }


  /**
   * Gets the time when counting started.
   *
   * @return the epochal time in milliseconds
   */
  public static synchronized long getSince() {
    if (since == 0) {
      since = System.currentTimeMillis();   // invoked first time?
    }
    return since;
  }


  /**
   * Logs the statics.
   *
   * @param level the logging level
   * @param clear true if clear after log
   */
  public static void log(Level level, boolean clear) {
    try {
      LOGGER.log(level, null, () -> {
        StringBuilder buf = new StringBuilder();
        buf.append("SQL Statement Statistics").append(LOG_TAG).append(FormatHelper.formatLocalDateTime(LocalDateTime.now()));
        for (StatementStatistics stat : getStatistics(clear)) {
          buf.append('\n').append(stat);
        }
        if (clear) {
          buf.append("\n    (cleared)");
        }
        return buf.toString();
      });
    }
    catch (RuntimeException rex) {
      LOGGER.severe("cannot log SQL statistics", rex);
    }
  }


  // ----------------- end static section ---------------------------------


  private final int instanceNumber;                     // the unique instance number
  private final String sql;                             // the SQL statement
  private final String txName;                          // transaction name
  private final Map<Integer,Object> parameters;         // execution parameters
  private final StatementStatisticsResult statResult;   // statistics result


  /**
   * Create a statistics collector.
   *
   * @param sql the SQL statement
   * @param txName the transaction name
   * @param parameters execution parameters
   */
  public StatementStatistics(String sql, String txName, Map<Integer, Object> parameters) {
    this.instanceNumber = INSTANCE_COUNT.incrementAndGet();
    this.sql = sql;
    this.txName = txName;
    this.parameters = parameters;
    this.statResult = new StatementStatisticsResult();
  }


  /**
   * Count invocations and duration of a statement.
   *
   * @param count the number of invocations
   * @param duration the execution time in milliseconds
   */
  public void countInvocation(TimeKeeper duration, int count) {
    if (duration != null && duration.isValid()) {
      statResult.count(duration, count);
    }
  }

  /**
   * Count a statement execution duration.
   *
   * @param duration the statement duration
   */
  public void countInvocation(TimeKeeper duration) {
    if (duration != null && duration.isValid()) {
      statResult.count(duration);
    }
  }

  /**
   * Counts an invocation with optional fetch statistics.
   *
   * @param history the statement
   */
  public void countInvocation(StatementHistory history) {
    TimeKeeper duration = history.getExecutionDuration();
    if (duration != null && duration.isValid()) {
      statResult.count(duration);
      TimeKeeper fetchDuration = history.getFetchDuration();
      if (fetchDuration != null) {    // if query
        statResult.countFetch(fetchDuration, history.getFetchCount());
      }
    }
  }


  /**
   * Gets the collected statistics result.
   *
   * @return the statistic result
   */
  public StatementStatisticsResult getStatistics() {
    return statResult;
  }


  /**
   * Gets the parameters.
   *
   * @return the parameters
   */
  public Map<Integer, Object> getParameters() {
    return parameters;
  }


  /**
   * Gets the SQL string.
   *
   * @return the SQL string
   */
  public String getSql() {
    return sql;
  }


  /**
   * Gets the transaction name.
   *
   * @return the transaction name
   */
  public String getTxName() {
    return txName;
  }


  @Override
  public int hashCode() {
    int hash = 5;
    hash = 79 * hash + (this.sql != null ? this.sql.hashCode() : 0);
    hash = 79 * hash + (this.txName != null ? this.txName.hashCode() : 0);
    hash = 79 * hash + (this.parameters != null ? this.parameters.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final StatementStatistics other = (StatementStatistics) obj;
    if (!Objects.equals(this.sql, other.sql)) {
      return false;
    }
    if (!Objects.equals(this.txName, other.txName)) {
      return false;
    }
    return Objects.equals(this.parameters, other.parameters);
  }

  @Override
  public int compareTo(StatementStatistics o) {
    int rv = Compare.compare(sql, o.sql);
    if (rv == 0) {
      /*
       * Because statistics are only created for unequal parameters.
       * It is sufficient to compare the instanceNumber
       */
      rv = Integer.compare(instanceNumber, o.instanceNumber);
    }
    return rv;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (statResult.isValid()) {
      buf.append(LOG_TAG).append(statResult).append(" x ");
      if (sql == null) {
        buf.append("<null>");
      }
      else {
        buf.append(sql);
      }

      if (parameters != null && !parameters.isEmpty()) {
        for (Integer pos : new TreeSet<>(parameters.keySet())) {
          buf.append(", ").append(pos).append("='");
          Object value = parameters.get(pos);
          if (value == null) {
            buf.append("<null>");
          }
          else {
            buf.append(value);
          }
          buf.append("'");
        }
      }

      if (txName != null) {
        buf.append(" [").append(txName).append(']');
      }
    }
    return buf.toString();
  }

}
