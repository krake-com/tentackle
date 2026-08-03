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
import org.tentackle.log.Logger;
import org.tentackle.misc.TimeKeeper;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * History of statement execution.
 *
 * @author harald
 */
public class StatementHistory {

  private static long logMinDurationMillis;

  /**
   * Sets the duration of statements to be logged as a warning.
   *
   * @param logMinDurationMillis the duration in milliseconds, 0 to disable (default)
   */
  public static void setLogMinDurationMillis(long logMinDurationMillis) {
    StatementHistory.logMinDurationMillis = logMinDurationMillis;
  }

  /**
   * Gets the duration of statements to be logged as a warning.
   *
   * @return the duration in milliseconds
   */
  public static long getLogMinDurationMillis() {
    return logMinDurationMillis;
  }

  private static final Logger LOGGER = Logger.get(StatementHistory.class);


  private final String txName;                                // transaction name if tx was running, else null
  private final long executionStart;                          // start of execution in epochal milliseconds
  private final TimeKeeper executionDuration;                 // SQL statement execution duration in nanoseconds
  private final String sql;                                   // the SQL statement
  private final Map<Integer,Object> parameters;               // execution parameters if prepared statement, else null
  private final List<Map<Integer,Object>> batchParameters;    // batched execution parameters
  private TimeKeeper fetchDuration;                           // query only: duration to fetch the results from the result set
  private int fetchCount;                                     // query only: number of rows fetched

  /**
    * Creates a statement execution history.
    *
    * @param statement the executed statement
   * @param sql optional SQL string if statement does not provide it
    */
  public StatementHistory(StatementWrapper statement, String sql) {
    this.parameters = statement instanceof PreparedStatementWrapper ? ((PreparedStatementWrapper) statement).getParameters() : null;
    this.batchParameters = statement instanceof PreparedStatementWrapper ? ((PreparedStatementWrapper) statement).getBatchParameters() : null;
    this.txName = statement.getAttachedSession().getTxName();
    this.executionStart = System.currentTimeMillis();
    this.executionDuration = new TimeKeeper();
    if (sql != null) {
      this.sql = sql;
    }
    else {
      this.sql = statement.getSql();
    }
  }


  /**
   * Sets the execution end.
   */
  public void end() {
    executionDuration.end();
    if (logMinDurationMillis > 0 && executionDuration.millis() > logMinDurationMillis) {
      LOGGER.warning(">>> duration >>> " + this);
    }
    else {
      LOGGER.finer(this::toString);
    }
  }

  /**
   * Gets the start of execution in epochal milliseconds.
   *
   * @return the start
   */
  public long getExecutionStart() {
    return executionStart;
  }

  /**
   * Gets the duration of the statement execution.
   *
   * @return the duration, never null
   */
  public TimeKeeper getExecutionDuration() {
    return executionDuration;
  }

  /**
   * Applies the fetch duration and number of rows fetched if the statement is a query.
   *
   * @param fetchDuration the fetch duration
   * @param fetchCount the number of rows fetched
   */
  public void applyFetch(TimeKeeper fetchDuration, int fetchCount) {
    this.fetchDuration = fetchDuration;
    this.fetchCount = fetchCount;
  }

  /**
   * Gets the duration to fetch the result from a query statement.
   *
   * @return the duration, null if not a query
   */
  public TimeKeeper getFetchDuration() {
    return fetchDuration;
  }

  /**
   * Gets the number of rows fetched from the result set.
   *
   * @return the fetched rows (only valid if the statement was a query)
   * @see #getFetchDuration()
   */
  public int getFetchCount() {
    return fetchCount;
  }

  /**
   * Gets the transaction name.
   *
   * @return the tx name, null if none or no transaction
   */
  public String getTxName() {
    return txName;
  }

  /**
   * Gets the parameter map.
   *
   * @return the parameters, null if not a prepared statement
   */
  public Map<Integer,Object> getParameters() {
    return parameters;
  }

  /**
   * Gets the batched parameter map.
   *
   * @return the batched parameter, null if not batched or not a prepared statement
   */
  public List<Map<Integer, Object>> getBatchParameters() {
    return batchParameters;
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
    buf.append(new Timestamp(getExecutionStart()));
    buf.append('(');
    if (executionDuration.isValid()) {
      buf.append(executionDuration.millisToString());
      buf.append("ms");
      if (fetchDuration != null) {
        buf.append('/').append(fetchDuration.millisToString()).append("ms/").append(fetchCount).append('r');
      }
      buf.append("): ");
    }
    else  {
      buf.append("running): ");
    }
    buf.append(sql);
    if (batchParameters != null) {
      int batchNo = 0;
      for (Map<Integer,Object> parameters : batchParameters) {
        parametersToString(buf, parameters);
        buf.append(" [").append(batchNo++).append(']');
      }
    }
    else if (parameters != null) {
      parametersToString(buf, parameters);
    }
    if (txName != null) {
      buf.append(" [").append(txName).append(']');
    }
    return buf.toString();
  }

  private void parametersToString(StringBuilder buf, Map<Integer,Object> parameters) {
    for (Integer pos: new TreeSet<>(parameters.keySet())) {
      buf.append(", ");
      buf.append(pos);
      buf.append("='");
      Object value = parameters.get(pos);
      buf.append(value == null ? "<null>" : value.toString());
      buf.append("'");
    }
  }

}
