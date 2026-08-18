/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.sql.backends;

import org.testng.annotations.Test;

import org.tentackle.sql.Backend;

import static org.testng.Assert.assertEquals;

/**
 * Tests the reduction of where-clauses with skipped optional conditions.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReduceSqlTest {

  private final Backend backend = new Postgres();

  /**
   * Reduces and normalizes the whitespace to keep the expectations readable.
   *
   * @param sql the sql text
   * @return the reduced text
   */
  private String reduce(String sql) {
    return backend.reduceSql(sql).replaceAll(" +", " ").trim();
  }

  @Test
  public void testNothingToReduce() {
    // valid statements must not be touched at all
    for (String sql: new String[] {
            "* FROM blah WHERE 1=1 AND code=?",
            "* FROM blah WHERE 1=1 AND (code=? OR type=?)",
            "* FROM blah WHERE 1=1 AND NOT (code=? OR type=?)",
            "* FROM blah WHERE 1=1 AND id IN(?) AND name NOT LIKE ? AND processed IS NOT NULL",
            "* FROM blah WHERE 1=1 AND EXISTS (SELECT 1 FROM other AS o WHERE o.blah_id=id AND o.code=?)",
            "* FROM blah WHERE 1=1 AND note='a AND b' AND brand=? AND \"NOT\"=?"}) {
      assertEquals(backend.reduceSql(sql), sql);
    }
  }

  @Test
  public void testEmptyGroup() {
    // the example from the documentation
    assertEquals(reduce("code=? AND NOT ()"), "code=?");
    assertEquals(reduce("NOT () AND code=?"), "code=?");
    assertEquals(reduce("code=? AND ( )"), "code=?");
    assertEquals(reduce("code=? OR ()"), "code=?");
    // nested empty groups
    assertEquals(reduce("code=? AND (() OR ())"), "code=?");
    // everything skipped
    assertEquals(reduce("()"), "");
  }

  @Test
  public void testDanglingOperators() {
    // the operand in between was skipped: it takes its leading operator with it
    assertEquals(reduce("code=? AND OR type=?"), "code=? OR type=?");
    assertEquals(reduce("code=? OR AND type=?"), "code=? AND type=?");
    assertEquals(reduce("code=? AND AND type=?"), "code=? AND type=?");
    // first or last operand of a group skipped
    assertEquals(reduce("(AND code=?)"), "( code=?)");
    assertEquals(reduce("(code=? AND )"), "(code=? )");
    // the trailing AND of an EXISTS clause whose last condition was skipped
    assertEquals(reduce("EXISTS (SELECT 1 FROM other AS o WHERE o.blah_id=id AND )"),
                 "EXISTS (SELECT 1 FROM other AS o WHERE o.blah_id=id )");
    // NOT without an operand
    assertEquals(reduce("code=? AND NOT AND type=?"), "code=? AND type=?");
  }

  @Test
  public void testWholeExpressionSkipped() {
    assertEquals(reduce("* FROM blah WHERE 1=1 AND ()"), "* FROM blah WHERE 1=1");
    assertEquals(reduce("* FROM blah WHERE 1=1 AND (() OR ())"), "* FROM blah WHERE 1=1");
  }

  @Test
  public void testParenthesesOfOperandsKept() {
    // only parentheses that are an operand of a logical expression may be removed
    assertEquals(reduce("updated<now() AND ()"), "updated<now()");
    assertEquals(reduce("code=? AND id IN()"), "code=? AND id IN()");
  }

  @Test
  public void testQuotedTextIsNotInterpreted() {
    // parentheses and keywords within literals must be left alone
    assertEquals(reduce("code=? AND note='()' AND NOT ()"), "code=? AND note='()'");
    assertEquals(reduce("code=? AND note='it''s (a) AND b' AND ()"), "code=? AND note='it''s (a) AND b'");
  }

  @Test
  public void testLowercaseKeywordsAreNotInterpreted() {
    // same convention as optimizeSql: lowercase keeps the statement untouched
    assertEquals(backend.reduceSql("code=? and not ()"), "code=? and not ()");
  }

}
