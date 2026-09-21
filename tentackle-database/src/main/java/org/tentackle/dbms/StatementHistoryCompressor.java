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

import java.util.List;
import java.util.Objects;

/**
 * Compresses immediately repeating runs of {@link StatementHistory} entries.
 * <p>
 * Application code stuck in a loop can re-execute the very same sequence of statements
 * hundreds of thousands of times, which would otherwise blow up a diagnostic dump.
 * This detects the smallest contiguous block of statements that repeats immediately and
 * prints it only once, followed by the number of repetitions.
 *
 * @author harald
 */
final class StatementHistoryCompressor {

  /**
   * Upper bound for the length of a repeating block to search for.<br>
   * Bounds the search cost per position; a repeating unit longer than this is not searched for.
   */
  private static final int MAX_PERIOD = 64;


  private StatementHistoryCompressor() {
  }

  /**
   * Creates a compressed StringBuilder from the statement history.
   *
   * @param history the history, in execution order
   * @return the builder
   */
  static StringBuilder compress(List<StatementHistory> history) {
    StringBuilder buf = new StringBuilder();
    int n = history.size();
    int i = 0;
    while (i < n) {
      int maxPeriod = Math.min(MAX_PERIOD, (n - i) / 2);
      int period = 0;
      int count = 0;
      for (int p = 1; p <= maxPeriod; p++) {
        int c = countRepeats(history, i, p);
        if (c >= 2 && (long) c * p > p + 2) {
          period = p;
          count = c;
          break;
        }
      }
      if (period > 0) {
        buf.append("\n>>>>>> ").append(count).append(" times:");
        for (int k = 0; k < period; k++) {
          buf.append("\n    ").append(history.get(i + k));
        }
        buf.append("\n<<<<<<");
        i += period * count;
      }
      else {
        buf.append("\n    ").append(history.get(i));
        i++;
      }
    }
    return buf;
  }

  /**
   * Counts how many times the block of the given period, starting at the given index, repeats.
   *
   * @param history the history
   * @param start the start index of the block
   * @param period the block length
   * @return the number of consecutive occurrences, always &gt;= 1
   */
  private static int countRepeats(List<StatementHistory> history, int start, int period) {
    int n = history.size();
    int count = 1;
    while (start + count * period + period <= n && blockEquals(history, start, start + count * period, period)) {
      count++;
    }
    return count;
  }

  private static boolean blockEquals(List<StatementHistory> history, int a, int b, int period) {
    for (int k = 0; k < period; k++) {
      if (!isSameStatement(history.get(a + k), history.get(b + k))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSameStatement(StatementHistory s1, StatementHistory s2) {
    return Objects.equals(s1.getTxName(), s2.getTxName()) &&
           Objects.equals(s1.getSql(), s2.getSql()) &&
           Objects.equals(s1.getParameters(), s2.getParameters()) &&
           Objects.equals(s1.getBatchParameters(), s2.getBatchParameters());
  }

}
