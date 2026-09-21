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

import org.testng.annotations.Test;

import org.tentackle.misc.TimeKeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StatementHistoryCompressor}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatementHistoryCompressorTest {

  @Test
  public void testNoRepeatsLeavesOutputUnchanged() {
    List<StatementHistory> history = List.of(
        historyOf(null, "SELECT 1", null),
        historyOf(null, "SELECT 2", null),
        historyOf(null, "SELECT 3", null)
    );
    String result = StatementHistoryCompressor.compress(history).toString();
    assertFalse(result.contains(">>>>>>"), result);
    assertTrue(result.contains("SELECT 1"), result);
    assertTrue(result.contains("SELECT 2"), result);
    assertTrue(result.contains("SELECT 3"), result);
  }

  @Test
  public void testSingleStatementRepeatedIsCompressed() {
    List<StatementHistory> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(historyOf(null, "SELECT * FROM foo WHERE id=?", Map.of(1, "42")));
    }
    String result = StatementHistoryCompressor.compress(history).toString();
    assertTrue(result.contains(">>>>>> 10 times:"), result);
    assertTrue(result.contains("<<<<<<"), result);
    // the statement must appear exactly once within the compressed block
    assertEquals(countOccurrences(result, "SELECT * FROM foo"), 1, result);
  }

  @Test
  public void testMultiStatementBlockRepeated() {
    List<StatementHistory> history = new ArrayList<>();
    history.add(historyOf(null, "SELECT * FROM calloff WHERE id=?", Map.of(1, "8787899123")));
    for (int i = 0; i < 5; i++) {
      history.add(historyOf(null, "SELECT * FROM shipmentfile WHERE id=?", Map.of(1, "18907555737101")));
      history.add(historyOf(null, "SELECT * FROM shipmentreference WHERE shipmentfile_id=?", Map.of(1, "18907555737101")));
      history.add(historyOf(null, "SELECT * FROM shipment WHERE id=?", Map.of(1, "18907555238101")));
    }
    history.add(historyOf(null, "SELECT * FROM trailing WHERE id=?", Map.of(1, "1")));

    String result = StatementHistoryCompressor.compress(history).toString();

    assertTrue(result.contains(">>>>>> 5 times:"), result);
    assertTrue(result.contains("<<<<<<"), result);
    assertTrue(result.contains("calloff"), result);
    assertTrue(result.contains("trailing"), result);
    // calloff + 3 block statements (shown once) + trailing = 5 statement lines, not 1 + 15 + 1
    assertEquals(statementLineCount(result), 5, result);
  }

  @Test
  public void testShortRepeatNotWorthCompressingIsLeftAsIs() {
    List<StatementHistory> history = List.of(
        historyOf(null, "SELECT 1", null),
        historyOf(null, "SELECT 1", null)
    );
    String result = StatementHistoryCompressor.compress(history).toString();
    assertFalse(result.contains(">>>>>>"), result);
    assertEquals(countOccurrences(result, "SELECT 1"), 2, result);
  }

  @Test
  public void testDifferingParametersAreNotTreatedAsRepeat() {
    List<StatementHistory> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(historyOf(null, "SELECT * FROM foo WHERE id=?", Map.of(1, "id-" + i)));
    }
    String result = StatementHistoryCompressor.compress(history).toString();
    assertFalse(result.contains(">>>>>>"), result);
    assertEquals(countOccurrences(result, "SELECT * FROM foo"), 10, result);
  }

  @Test
  public void testDifferingTxNameAreNotTreatedAsRepeat() {
    // every txName is distinct -> no two consecutive statements are ever equal, no repeat possible
    List<StatementHistory> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(historyOf("tx" + i, "SELECT * FROM foo WHERE id=?", Map.of(1, "42")));
    }
    String result = StatementHistoryCompressor.compress(history).toString();
    assertFalse(result.contains(">>>>>>"), result);
    assertEquals(statementLineCount(result), 10, result);
  }

  @Test
  public void testAlternatingTxNameFormsARepeatingBlock() {
    // tx1/tx2 alternating is itself a genuine period-2 repeating pattern and must be compressed
    List<StatementHistory> history = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      history.add(historyOf(i % 2 == 0 ? "tx1" : "tx2", "SELECT * FROM foo WHERE id=?", Map.of(1, "42")));
    }
    String result = StatementHistoryCompressor.compress(history).toString();
    assertTrue(result.contains(">>>>>> 5 times:"), result);
    assertEquals(statementLineCount(result), 2, result);
  }

  private static StatementHistory historyOf(String txName, String sql, Map<Integer,Object> parameters) {
    return new StatementHistory(txName, System.currentTimeMillis(), new TimeKeeper(0), sql, parameters, null);
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int index = 0;
    while ((index = haystack.indexOf(needle, index)) != -1) {
      count++;
      index += needle.length();
    }
    return count;
  }

  /**
   * Counts the statement lines in a compressed dump, i.e. all lines except the loop markers.
   */
  private static long statementLineCount(String result) {
    return java.util.Arrays.stream(result.split("\n"))
        .filter(line -> !line.isBlank() && !line.startsWith(">>>>>>") && !line.startsWith("<<<<<<"))
        .count();
  }

}
