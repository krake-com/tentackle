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

import org.tentackle.log.StatisticsResult;

/**
 * Statistics result for transactions.<br>
 * Extends {@link StatisticsResult} by the number of statements and rollbacks.
 */
public class TransactionStatisticsResult extends StatisticsResult {

  private long rollbackCount;                     // number of transactions rolled back

  private long minStatements = Long.MAX_VALUE;    // minimum number of statements
  private long maxStatements = Long.MIN_VALUE;    // maximum number of statements
  private long totalStatements;                   // total number of statements


  /**
   * Creates a transaction statistics result.
   */
  public TransactionStatisticsResult() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Counts a rollback.
   */
  public void countRollback() {
    rollbackCount++;
  }

  /**
   * Gets the number of rollbacks.<br>
   * {@link #getCount()} - {@code getRollbackCount()} gives the number of commits.
   *
   * @return the number of rollbacks
   */
  public long getRollbackCount() {
    return rollbackCount;
  }

  /**
   * Counts the statement statistics.
   *
   * @param statements the number of statements executed
   */
  public void countStatements(int statements) {
    if (statements < minStatements) {
      minStatements = statements;
    }
    if (statements > maxStatements) {
      maxStatements = statements;
    }
    totalStatements += statements;
  }

  /**
   * Gets the minimum number of statements.
   *
   * @return the statement count, {@link Long#MAX_VALUE} if no transaction counted so far
   */
  public long getMinStatements() {
    return minStatements;
  }

  /**
   * Gets the maximum number of statements.
   *
   * @return the statement count, {@link Long#MIN_VALUE} if no transaction counted so far
   */
  public long getMaxStatements() {
    return maxStatements;
  }

  /**
   * Gets the total number of statements.
   *
   * @return the total number of statements
   */
  public long getTotalStatements() {
    return totalStatements;
  }

  @Override
  public String toString() {
    if (isValid()) {
      return getMinDuration().millisToString() + ' ' +
             getMaxDuration().millisToString() + ' ' +
             getTotalDuration().millisToString() + ' ' +
             minStatements + ' ' +
             maxStatements + ' ' +
             totalStatements +
             " ms:st / " + rollbackCount + " r " + getCount();
    }
    return "<invalid>";
  }

}
