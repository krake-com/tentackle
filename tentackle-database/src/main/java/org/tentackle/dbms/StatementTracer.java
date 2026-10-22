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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


interface StatementTracerHolder {
  StatementTracer INSTANCE = ServiceFactory.createService(StatementTracer.class, StatementTracer.class);
}


/**
 * On-demand SQL statement tracer.<br>
 * Primarily used to trace usage of certain SQL statements on demand at runtime.
 * <p>
 * The tracer is disabled by default for performance reasons.
 */
@Service(StatementTracer.class)   // defaults to self
public class StatementTracer {

  private static final Logger LOGGER = Logger.get(StatementTracer.class);

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static StatementTracer getInstance() {
    return StatementTracerHolder.INSTANCE;
  }


  private final Set<String> sqlSet = ConcurrentHashMap.newKeySet();           // SQL statements to trace
  private final Map<String, Pattern> regexSet = new ConcurrentHashMap<>();    // regexes for SQLs to trace
  private volatile boolean regexPresent;        // to speed up
  private volatile boolean enabled;             // tracer is disabled by default
  private volatile Logger.Level logLevel;       // log level, if the traced statements should be immediately logged

  /**
   * Creates the tracer.
   */
  public StatementTracer() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Returns whether the tracer is enabled.
   *
   * @return true if enabled, default is false
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the tracer enabled.<br>
   * Notice that disabling does not remove any SQLs or Patterns.
   *
   * @param enabled true if enabled
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets the log level to trace statements.
   *
   * @return the level, null if statements should not be logged (default)
   */
  public Logger.Level getLogLevel() {
    return logLevel;
  }

  /**
   * Sets the log level to trace statements.
   *
   * @param logLevel the level, null if statements should not be logged (default)
   */
  public void setLogLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
  }

  /**
   * Adds an SQL statement to the list of traced statements.
   *
   * @param sql the statement to add
   */
  public void addSql(String sql) {
    sqlSet.add(sql);
  }

  /**
   * Removes an SQL statement from the list of traced statements.
   *
   * @param sql the statement to remove
   */
  public void removeSql(String sql) {
    sqlSet.remove(sql);
  }

  /**
   * Adds a regular expression to the list of traced statements.
   *
   * @param regex the regular expression
   */
  public synchronized void addRegex(String regex) {
    regexSet.computeIfAbsent(regex, Pattern::compile);
    regexPresent = true;
  }

  /**
   * Removes a regular expression from the list of traced statements.
   *
   * @param regex the regular expression
   */
  public synchronized void removeRegex(String regex) {
    regexSet.remove(regex);
    regexPresent = !regexSet.isEmpty();
  }

  /**
   * Checks a statement whether it should be traced.<br>
   * If so, the stacktrace is remembered in the statement.<br>
   * If the optional logging level is set, the statement will be immediately logged.
   *
   * @param statement the statement
   * @see StatementWrapper#setStackTrace(String)
   */
  public void trace(StatementWrapper statement) {
    if (enabled && isTraceable(statement)) {
      Logger.Level level = logLevel;
      statement.setStackTrace("\n[" + Thread.currentThread() + "] @ " + new Timestamp() + ": " +
                              ExceptionHelper.getStackTraceAsString(new StatementTraceException(statement.getSession())));
      if (level != null) {
        LOGGER.log(level, statement.toString());
      }
    }
    else {
      statement.setStackTrace(null);
    }
  }

  /**
   * Returns whether the statement should be traced.
   *
   * @param statement the statement
   * @return true to trace, false don't trace
   */
  protected boolean isTraceable(StatementWrapper statement) {
    String sql = statement.getSql();
    return sqlSet.contains(sql) ||
           regexPresent && regexSet.values().stream().anyMatch(pattern -> pattern.matcher(sql).matches());
  }

}
