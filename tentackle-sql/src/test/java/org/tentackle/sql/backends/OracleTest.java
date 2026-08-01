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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.sql.metadata.OracleIndexColumnMetaData;
import org.tentackle.sql.metadata.OracleIndexColumnMetaData.ColumnExpression;

/**
 * Tests for the oracle backend.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class OracleTest {

  @Test
  public void testCreateIndex() {
    Oracle oracle = new Oracle();

    // plain index: unchanged
    Assert.assertEquals(oracle.sqlCreateIndex("foo", "foo_code", true, null, "code"),
                        "CREATE UNIQUE INDEX foo_code ON foo (code);\n");

    // filtered index: emulated by a function-based index
    Assert.assertEquals(oracle.sqlCreateIndex("foo", "foo_code", true, "type = 0", "code"),
                        "CREATE UNIQUE INDEX foo_code ON foo (CASE WHEN (type = 0) THEN code END);\n");

    // all columns must be wrapped, else non-matching rows would not get an all-NULL key
    Assert.assertEquals(oracle.sqlCreateIndex("foo", "foo_code", false, "type = 0", "code", "name"),
                        "CREATE INDEX foo_code ON foo (CASE WHEN (type = 0) THEN code END, " +
                        "CASE WHEN (type = 0) THEN name END);\n");

    // descending and function-based columns
    Assert.assertEquals(oracle.sqlCreateIndex("foo", "foo_code", false, "type = 0", "-code", "UPPER(name)"),
                        "CREATE INDEX foo_code ON foo (CASE WHEN (type = 0) THEN code END DESC, " +
                        "CASE WHEN (type = 0) THEN UPPER(name) END);\n");
  }


  @Test
  public void testIndexCapabilities() {
    Oracle oracle = new Oracle();
    Assert.assertTrue(oracle.isFilteredIndexSupported());
    Assert.assertTrue(oracle.isFunctionBasedIndexSupported());

    // the deprecated backend for oracle 8 - 11 is left untouched
    Oracle8 oracle8 = new Oracle8();
    Assert.assertFalse(oracle8.isFilteredIndexSupported());
    Assert.assertFalse(oracle8.isFunctionBasedIndexSupported());
  }


  @Test
  public void testParseColumnExpression() {
    // filtered index, as rendered by oracle in ALL_IND_EXPRESSIONS
    ColumnExpression expression = OracleIndexColumnMetaData.parseColumnExpression("CASE WHEN (\"TYPE\"=0) THEN \"CODE\" END");
    Assert.assertEquals(expression.filterCondition(), "(TYPE=0)");
    Assert.assertNull(expression.functionName());
    Assert.assertEquals(expression.columnName(), "code");

    // without the redundant parenthesis and with a string literal
    expression = OracleIndexColumnMetaData.parseColumnExpression("CASE WHEN \"STATUS\"='A' THEN \"CODE\" END");
    Assert.assertEquals(expression.filterCondition(), "STATUS='A'");
    Assert.assertNull(expression.functionName());
    Assert.assertEquals(expression.columnName(), "code");

    // a function within the condition must not confuse the keyword scanner
    expression = OracleIndexColumnMetaData.parseColumnExpression("CASE WHEN (NVL(\"TYPE\",0)=0) THEN \"CODE\" END");
    Assert.assertEquals(expression.filterCondition(), "(NVL(TYPE,0)=0)");
    Assert.assertEquals(expression.columnName(), "code");

    // filtered and function-based
    expression = OracleIndexColumnMetaData.parseColumnExpression("CASE WHEN (\"TYPE\"=0) THEN UPPER(\"NAME\") END");
    Assert.assertEquals(expression.filterCondition(), "(TYPE=0)");
    Assert.assertEquals(expression.functionName(), "UPPER");
    Assert.assertEquals(expression.columnName(), "name");

    // plain function-based index
    expression = OracleIndexColumnMetaData.parseColumnExpression("UPPER(\"NAME\")");
    Assert.assertNull(expression.filterCondition());
    Assert.assertEquals(expression.functionName(), "UPPER");
    Assert.assertEquals(expression.columnName(), "name");

    // descending index: oracle creates a function-based index holding just the column
    expression = OracleIndexColumnMetaData.parseColumnExpression("\"CODE\"");
    Assert.assertNull(expression.filterCondition());
    Assert.assertNull(expression.functionName());
    Assert.assertEquals(expression.columnName(), "code");

    // a column named CASEID must not be mistaken for a CASE-expression
    expression = OracleIndexColumnMetaData.parseColumnExpression("CASEID");
    Assert.assertNull(expression.filterCondition());
    Assert.assertEquals(expression.columnName(), "caseid");
  }

}
