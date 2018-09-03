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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeSet;
import org.tentackle.log.Logger;
import org.tentackle.misc.Duration;

/**
 * History of statement execution.
 *
 * @author harald
 */
public class StatementHistory {

  /**
   * Minimum milliseconds a statement needs to execute before being logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  public static long logMinDurationMillis;

  /**
   * Format for execution starting time.
   */
  private static final DateFormat START_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

  private static final Logger LOGGER = Logger.get(StatementHistory.class);


  private final String txName;                    // transaction name if tx was running, else null
  private final long executionStart;              // start of execution in epochal milliseconds
  private final Duration duration;                // duration in nanoseconds
  private final String sql;                       // the SQL statement
  private final Map<Integer,Object> parameters;   // execution parameters if prepared statement, else null

  /**
    * Creates a statement execution history.
    *
    * @param statement the executed statement
   * @param sql optional SQL string if statement does not provide it
    */
  public StatementHistory(StatementWrapper statement, String sql) {
    this.parameters = statement instanceof PreparedStatementWrapper ? ((PreparedStatementWrapper) statement).getParameters() : null;
    this.txName = statement.getAttachedSession().getTxName();
    this.executionStart = System.currentTimeMillis();
    this.duration = new Duration();
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
    duration.end();
    if (logMinDurationMillis > 0 && duration.millis() > logMinDurationMillis) {
      LOGGER.warning(">>> duration >>> " + toString());
    }
    LOGGER.finer(() -> toString());
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
   * Gets the duration.
   *
   * @return the duration, never null
   */
  public Duration getDuration() {
    return duration;
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
   * Gets the sql string.
   *
   * @return the sql string
   */
  public String getSql() {
    return sql;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    synchronized(START_TIME_FORMAT) {   // formatters are not thread-safe :(
      buf.append(START_TIME_FORMAT.format(new Date(getExecutionStart())));
    }
    buf.append('(');
    if (duration.isValid()) {
      buf.append(duration.millisToString());
      buf.append("ms): ");
    }
    else  {
      buf.append("running): ");
    }
    buf.append(sql);
    if (parameters != null && !parameters.isEmpty()) {
      for (Integer pos: new TreeSet<>(parameters.keySet())) {
        buf.append(", ");
        buf.append(pos);
        buf.append("='");
        Object value = parameters.get(pos);
        buf.append(value == null ? "<null>" : value.toString());
        buf.append("'");
      }
    }
    if (txName != null) {
      buf.append(" in [").append(txName).append(']');
    }
    return buf.toString();
  }

}
