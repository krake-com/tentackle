/*
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

package org.tentackle.dbms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.tentackle.common.Compare;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.StatisticsResult;
import org.tentackle.misc.Duration;

/**
 * Statement statistics collector.
 *
 * @author harald
 */
public class StatementStatistics implements Comparable<StatementStatistics> {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(StatementStatistics.class);

  // instance counter
  private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

  // statistics collector
  private static final Map<StatementStatistics,StatementStatistics> STAT_MAP = new HashMap<>();

  // the time statistics began in epochal milliseconds.
  private static long since;


  /**
   * If true the statistics are per statement and parameters, else only per statement.
   */
  public static boolean with_parameters;


  /**
   * Collects the statistics from a statement history.
   *
   * @param history the history to collect
   */
  public static synchronized void collect(StatementHistory history) {
    StatementStatistics key = new StatementStatistics(history.getSql(), history.getTxName(), with_parameters ? history.getParameters() : null);
    StatementStatistics value = STAT_MAP.get(key);
    if (value == null) {
      value = key;
      STAT_MAP.put(key, value);
    }
    value.countInvocation(history.getDuration());
  }


  /**
   * Gets the current statistics.
   *
   * @param clear true if clear
   * @return the statement statistics sorted by sql and parameters
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
   * @param logger the logger
   * @param level the logging level
   * @param clear true if clear after log
   */
  public static void log(Logger logger, Level level, boolean clear) {
    try {
      if (logger.isLoggable(level)) {
        StringBuilder buf = new StringBuilder("SQL Statement Statistics:");
        for (StatementStatistics stat : getStatistics(clear)) {
          buf.append("\n    ");
          buf.append(stat);
        }
        logger.log(level, buf.toString(), null);
      }
    }
    catch (RuntimeException rex) {
      LOGGER.severe("cannot log SQL statistics", rex);
    }
  }


  // ----------------- end static section ---------------------------------


  private final int instanceNumber;               // the unique instance number
  private final String sql;                       // the SQL statement
  private final String txName;                    // transaction name
  private final Map<Integer,Object> parameters;   // execution parameters
  private final StatisticsResult statResult;      // statistics result


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
    this.statResult = new StatisticsResult();
  }


  /**
   * Count the invocation of a statement.
   *
   * @param duration the execution time in milliseconds
   */
  public void countInvocation(Duration duration) {
    if (duration != null && duration.isValid()) {
      statResult.count(duration);
    }
  }


  /**
   * Gets the collected statistics result.
   *
   * @return the statistic result
   */
  public StatisticsResult getStatistics() {
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
   * @return the txname
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
    if ((this.sql == null) ? (other.sql != null) : !this.sql.equals(other.sql)) {
      return false;
    }
    if ((this.txName == null) ? (other.txName != null) : !this.txName.equals(other.txName)) {
      return false;
    }
    return !(this.parameters != other.parameters &&
             (this.parameters == null || !this.parameters.equals(other.parameters)));
  }


  @Override
  public int compareTo(StatementStatistics o) {
    int rv = Compare.compare(sql, o.sql);
    if (rv == 0) {
      /**
       * Because statistics are only created for unequal parameters
       * it is sufficient to compare the instanceNumber
       */
      rv = instanceNumber - o.instanceNumber;
    }
    return rv;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(">SQL-Stats: ");
    buf.append(statResult.getMinDuration().millisToString()).
        append(' ').
        append(statResult.getMaxDuration().millisToString()).
        append(' ').
        append(statResult.getTotalDuration().millisToString()).
        append(" ms / ").
        append(statResult.getCount()).
        append(" x ").
        append(sql == null ? "<null>" : sql);
    if (parameters != null && !parameters.isEmpty()) {
      for (Integer pos: new TreeSet<>(parameters.keySet())) {
        buf.append(", ").
            append(pos).
            append("='");
        Object value = parameters.get(pos);
        buf.append(value == null ? "<null>" : value.toString()).
            append("'");
      }
    }
    if (txName != null) {
      buf.append(" in [").
          append(txName).
          append(']');
    }
    return buf.toString();
  }

}
