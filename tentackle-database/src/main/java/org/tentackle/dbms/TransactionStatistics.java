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

import org.tentackle.log.Logger;
import org.tentackle.log.StatisticsResult;
import org.tentackle.misc.FormatHelper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction statistics collector.
 *
 * @author harald
 */
public class TransactionStatistics {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = Logger.get(TransactionStatistics.class);

  /**
   * Statistics map {@code txname:result}
   */
  private final Map<String, TransactionStatisticsResult> stats;

  /**
   * Creates a new statistics object.
   */
  public TransactionStatistics() {
    this.stats = new HashMap<>();
  }

  /**
   * Counts the invocation of a transaction.
   *
   * @param transaction the transaction
   * @param rolledBack true if transaction was rolled back, false if committed
   */
  public synchronized void countTransaction(DbTransaction transaction, boolean rolledBack) {
    TransactionStatisticsResult result = stats.computeIfAbsent(transaction.getTxName(),
                                                               _ -> new TransactionStatisticsResult());
    result.count(transaction.getTimeKeeper().end());
    if (rolledBack) {
      result.countRollback();
    }
    // count statements (with pessimistic null-checks, should never be null, but...)
    Db session = transaction.getSession();
    if (session != null) {
      ManagedConnection connection = session.getConnection();
      if (connection != null) {
        List<StatementHistory> statementHistory = connection.getStatementHistory();
        if (statementHistory != null) {
          result.countStatements(statementHistory.size());
        }
      }
    }
  }

  /**
   * Logs the statistics.
   *
   * @param title the title, null if default
   * @param level the logging level
   * @param tag   intro logging tag
   * @param clear true if clear statistics after dump
   */
  public synchronized void logStatistics(String title, Logger.Level level, String tag, boolean clear) {
    if (!stats.isEmpty()) {
      if (LOGGER.isLoggable(level)) {
        try {
          StringBuilder buf = new StringBuilder();
          if (title == null) {
            title = "Transaction Statistics";
          }
          buf.append(title).append(tag).append(FormatHelper.formatLocalDateTime(LocalDateTime.now()));
          for (Map.Entry<String, TransactionStatisticsResult> entry : stats.entrySet()) {
            StatisticsResult result = entry.getValue();
            if (result.isValid()) {
              String txName = entry.getKey();
              buf.append('\n').append(tag)
                 .append(result).append(" x ")
                 .append(txName);
            }
          }
          if (clear) {
            buf.append("\n    (cleared)");
          }
          LOGGER.log(level, buf.toString());
        }
        catch (RuntimeException rex) {
          LOGGER.severe("cannot log transaction statistics", rex);
        }
      }
      if (clear) {
        stats.clear();
      }
    }
  }

}
