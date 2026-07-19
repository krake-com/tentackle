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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ScriptRunnerResult} record.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScriptRunnerResultTest {

  @Test
  public void testUpdateCountResult() {
    ScriptRunnerResult result = new ScriptRunnerResult("UPDATE t SET x=1", 0, "", 5, 3);
    assertFalse(result.isResultSet());
    assertEquals(result.getUpdateCount(), 3);
    assertEquals(result.resultToString(), "3");
    assertEquals(result.sql(), "UPDATE t SET x=1");
    assertEquals(result.offset(), 0);
    assertEquals(result.millis(), 5L);
    assertEquals(result.columnCount(), 0);
  }

  @Test
  public void testWarningsOnlyResult() {
    ScriptRunnerResult result = new ScriptRunnerResult("some warning");
    assertEquals(result.warnings(), "some warning");
    assertFalse(result.isResultSet());
    assertEquals(result.getUpdateCount(), -1);   // no data
  }

  @Test
  public void testResultSet() {
    ScriptRunnerResult result = new ScriptRunnerResult(
        "SELECT a, b FROM t", 0, "", 1, 2,
        new int[] { 0, 0 }, new String[] { "A", "B" },
        "a1", "b1", "a2", "b2");
    assertTrue(result.isResultSet());
    assertEquals(result.columnCount(), 2);
    assertEquals(result.getUpdateCount(), -1);   // it's a result set, not an update

    String rendered = result.resultToString();
    assertTrue(rendered.contains("A"), rendered);
    assertTrue(rendered.contains("B"), rendered);
    assertTrue(rendered.contains("a1"), rendered);
    assertTrue(rendered.contains("b2"), rendered);
  }

  @Test
  public void testToStringContainsSqlAndWarnings() {
    ScriptRunnerResult result = new ScriptRunnerResult("UPDATE t SET x=1", 0, "careful!", 5, 3);
    String s = result.toString();
    assertTrue(s.contains("UPDATE t SET x=1"), s);
    assertTrue(s.contains("Warnings: careful!"), s);
  }

  @Test
  public void testToStringWithoutWarnings() {
    ScriptRunnerResult result = new ScriptRunnerResult("UPDATE t SET x=1", 0, "", 5, 3);
    assertFalse(result.toString().contains("Warnings:"));
  }
}
