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

package org.tentackle.sql;

import java.io.Serial;
import java.util.List;

/**
 * Runtime exception thrown when a {@link ScriptRunner} encounters an error.
 *
 * @author harald
 */
public class ScriptRunnerException extends BackendException {

  @Serial
  private static final long serialVersionUID = 1L;

  /** the SQL code that failed. */
  private final String sql;

  /** the successful results collected so far. */
  @SuppressWarnings("serial")
  private final List<ScriptRunnerResult> results;

  /**
   * Creates a new ScriptRunnerException.
   *
   * @param message the detail message
   * @param sql the SQL code that failed
   * @param results the successful results collected so far
   * @param cause the cause
   */
  public ScriptRunnerException(String message, String sql, List<ScriptRunnerResult> results, Throwable cause) {
    super(message, cause);
    this.sql = sql;
    this.results = results;
  }

  /**
   * Constructor used by TRIP deserialization.<br>
   * The SQL code and the results are not transferred and remain null.
   *
   * @param message the detail message
   * @param cause the cause
   */
  protected ScriptRunnerException(String message, Throwable cause) {
    super(message, cause);
    sql = null;
    results = null;
  }


  /**
   * Gets the SQL code that failed.
   *
   * @return the SQL code, null if unknown
   */
  public String getSql() {
    return sql;
  }

  /**
   * Gets the results collected so far.
   *
   * @return the results, null if unknown
   */
  public List<ScriptRunnerResult> getResults() {
    return results;
  }

}
