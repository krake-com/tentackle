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
import org.tentackle.misc.TimeKeeper;

/**
 * Statistics result for SQL statements.<br>
 * Optionally extends {@link StatisticsResult} by fetch duration for the result set and result set size.
 */
public class StatementStatisticsResult extends StatisticsResult {

  private StatisticsResult fetchResult;     // != null if result set involved
  private int minRows = Integer.MAX_VALUE;  // minimum number of returned rows
  private int maxRows = Integer.MIN_VALUE;  // maximum number of returned rows
  private long totalRows;                   // total number of returned rows


  /**
   * Creates a statement statistics result.
   */
  public StatementStatisticsResult() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Counts the fetch-statistics.
   *
   * @param duration the duration fetching the result from the database
   * @param rows the number of rows fetched
   */
  public void countFetch(TimeKeeper duration, int rows) {
    if (fetchResult == null) {
      fetchResult = new StatisticsResult();
    }
    fetchResult.count(duration);

    if (rows < minRows) {
      minRows = rows;
    }
    if (rows > maxRows) {
      maxRows = rows;
    }
    totalRows += rows;
  }

  /**
   * Returns whether the statement involved fetching from a result set.
   *
   * @return true if fetch statistics are valid
   */
  public boolean isFetchResultValid() {
    return fetchResult != null;
  }

  /**
   * Returns the result for fetching the result set from the database.
   *
   * @return the fetch statistics, null if no result set involved
   */
  public StatisticsResult getFetchResult() {
    return fetchResult;
  }

  /**
   * Gets the minimum number of returned rows.
   *
   * @return the row count, {@link Integer#MAX_VALUE} if no result set involved
   */
  public int getMinRows() {
    return minRows;
  }

  /**
   * Gets the maximum number of returned rows.
   *
   * @return the row count, {@link Integer#MIN_VALUE} if no result set involved
   */
  public int getMaxRows() {
    return maxRows;
  }

  /**
   * Gets the total number of returned rows.
   *
   * @return the total number of rows fetched
   */
  public long getTotalRows() {
    return totalRows;
  }

  @Override
  public String toString() {
    if (isValid()) {
      StringBuilder buf = new StringBuilder();
      buf.append(getMinDuration().millisToString()).append(' ')
         .append(getMaxDuration().millisToString()).append(' ')
         .append(getTotalDuration().millisToString());
      if (isFetchResultValid()) {
        buf.append(' ')
           .append(minRows).append(' ')
           .append(maxRows).append(' ')
           .append(totalRows).append(' ')
           .append(fetchResult.getMinDuration().millisToString()).append(' ')
           .append(fetchResult.getMaxDuration().millisToString()).append(' ')
           .append(fetchResult.getTotalDuration().millisToString());
      }
      buf.append(" ms / ").append(getCount());
      return buf.toString();
    }
    return "<invalid>";
  }

}
