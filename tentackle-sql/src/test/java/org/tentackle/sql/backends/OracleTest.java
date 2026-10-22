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

import org.tentackle.sql.SqlType;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.OracleColumnMetaData;
import org.tentackle.sql.metadata.OracleIndexColumnMetaData;
import org.tentackle.sql.metadata.OracleIndexColumnMetaData.ColumnExpression;
import org.tentackle.sql.metadata.TableMetaData;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;

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


  @Test
  public void testColumnDefault() {
    Oracle oracle = new Oracle();

    // "serial NUMBER(19) DEFAULT 1 NOT NULL" is reported as "1 " because oracle stores
    // the default as the verbatim source text, up to the token following the default value
    OracleColumnMetaData column = createColumnMetaData(oracle, "1 ");
    Assert.assertEquals(column.getDefaultValue(), "1");
    Assert.assertTrue(oracle.isDefaultEqual(column, SqlType.BIGINT, 1L),
                      "the trailing blank must not be mistaken for a changed default");

    // the single blank oracle uses instead of the empty string keeps its quotes
    Assert.assertEquals(createColumnMetaData(oracle, "' ' ").getDefaultValue(), "' '");

    // no default at all
    Assert.assertNull(createColumnMetaData(oracle, null).getDefaultValue());
    Assert.assertNull(createColumnMetaData(oracle, "   ").getDefaultValue());

    // the NULL literals still drop the default, even with trailing whitespace
    Assert.assertNull(createColumnMetaData(oracle, "NULL ").getDefaultValue());
    Assert.assertNull(createColumnMetaData(oracle, "(null)\n").getDefaultValue());
  }


  @Test
  public void testMappedColumnDefault() {
    Oracle oracle = new Oracle();

    // oracle cannot store the empty string: the DDL says DEFAULT ' ' and oracle reports "' '"
    OracleColumnMetaData column = createColumnMetaData(oracle, "' '");
    Assert.assertTrue(oracle.isDefaultEqual(column, SqlType.VARCHAR, ""),
                      "the empty string must match the single blank oracle stores for it");
    // an explicitly declared single blank is the very same default
    Assert.assertTrue(oracle.isDefaultEqual(column, SqlType.VARCHAR, " "));
    Assert.assertFalse(oracle.isDefaultEqual(column, SqlType.VARCHAR, "x"));

    // a non-empty string is unaffected by the mapping
    Assert.assertTrue(oracle.isDefaultEqual(createColumnMetaData(oracle, "'abc'"), SqlType.VARCHAR, "abc"));

    // booleans are rendered as 1/0 by valueToLiteral
    Assert.assertTrue(oracle.isDefaultEqual(createColumnMetaData(oracle, "1 "), SqlType.BIT, true),
                      "TRUE must match the 1 oracle stores for it");
    Assert.assertTrue(oracle.isDefaultEqual(createColumnMetaData(oracle, "0 "), SqlType.BIT, false));
    Assert.assertFalse(oracle.isDefaultEqual(createColumnMetaData(oracle, "0"), SqlType.BIT, true));

    // no default at all
    Assert.assertTrue(oracle.isDefaultEqual(createColumnMetaData(oracle, null), SqlType.VARCHAR, null));
    Assert.assertFalse(oracle.isDefaultEqual(createColumnMetaData(oracle, null), SqlType.VARCHAR, ""));
  }


  /**
   * Creates the column metadata for a NUMBER(19) NOT NULL column with a given default.
   *
   * @param backend the oracle backend
   * @param defaultValue the default value as reported by {@code getColumns}, null if none
   * @return the column metadata
   */
  static OracleColumnMetaData createColumnMetaData(Oracle backend, String defaultValue) {
    TableMetaData tableMetaData = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    OracleColumnMetaData column = (OracleColumnMetaData) backend.createColumnMetaData(tableMetaData);
    try {
      column.setupColumnFromMetaData(columnResultSet(defaultValue));
    }
    catch (Exception ex) {
      throw new AssertionError("cannot set up the column metadata", ex);
    }
    return column;
  }

  /**
   * Creates a result set as returned by {@link DatabaseMetaData#getColumns} for a single column.
   *
   * @param defaultValue the default value, null if none
   * @return the result set
   */
  private static ResultSet columnResultSet(String defaultValue) {
    return (ResultSet) Proxy.newProxyInstance(
        OracleTest.class.getClassLoader(), new Class<?>[] { ResultSet.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getString" -> switch ((String) args[0]) {
            case "COLUMN_NAME" -> "SERIAL";
            case "TYPE_NAME" -> "NUMBER";
            case "COLUMN_DEF" -> defaultValue;
            default -> null;
          };
          case "getInt" -> switch ((String) args[0]) {
            case "COLUMN_SIZE" -> 19;
            case "NULLABLE" -> DatabaseMetaData.columnNoNulls;
            case "DATA_TYPE" -> Types.BIGINT;
            default -> 0;
          };
          case "getShort" -> (short) 0;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "ResultSet[serial]";
          default -> null;
        });
  }

}
