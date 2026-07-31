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

package org.tentackle.persist.wurblet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.wurblet.WurbletArgumentExpression;

import static org.testng.Assert.assertEquals;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createEntity;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createWurblet;
import static org.tentackle.persist.wurblet.WurbletTestSupport.parseArgument;
import static org.tentackle.persist.wurblet.WurbletTestSupport.parseExpression;

/**
 * Tests for {@link WhereClauseGenerator}, which emits the java statements that build a WHERE
 * clause at runtime - one {@code sql.append(...)} per column, operator and parenthesis.
 * <p>
 * The generator is driven from the wurblet argument expression, so the tests parse the same
 * argument syntax an application writes in its {@code PdoSelectList} wurblet call and compare the
 * generated code line by line.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class WhereClauseGeneratorTest {

  private static final String INDENT = "        ";

  private Entity entity;
  private WhereClauseGenerator generator;

  @BeforeClass
  public void setUp() throws Exception {
    entity = createEntity();
    generator = new WhereClauseGenerator(createWurblet());
  }

  /**
   * Joins the expected {@code sql.append(...)} arguments into the generated code block.
   *
   * @param appends the append arguments, one per generated line
   * @return the expected code
   */
  private static String code(String... appends) {
    StringBuilder buf = new StringBuilder();
    for (String append : appends) {
      buf.append(INDENT).append("sql.append(").append(append).append(");\n");
    }
    return buf.toString();
  }

  /**
   * Encloses the expected code in the if-statement generated for an optional condition.
   *
   * @param condition the expected java condition
   * @param appends the append arguments, one per generated line
   * @return the expected code
   */
  private static String guarded(String condition, String... appends) {
    StringBuilder buf = new StringBuilder(INDENT).append("if (").append(condition).append(") {\n");
    for (String line : code(appends).split("\n")) {
      buf.append("  ").append(line).append('\n');
    }
    return buf.append(INDENT).append("}\n").toString();
  }


  @Test
  public void generatesAColumnComparison() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user")),
                 code("CN_USER", "Backend.SQL_EQUAL_PAR"));
  }

  @Test
  public void generatesTheOperatorOfTheArgument() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "txId:>=")),
                 code("CN_TXID", "Backend.SQL_GREATEROREQUAL_PAR"));
  }

  /**
   * A literal is inlined, so the operator and its value end up on one appended line.
   */
  @Test
  public void inlinesALiteralValue() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user:=#\"admin\"")),
                 INDENT + "sql.append(CN_USER);\n"
                 + INDENT + "sql.append(Backend.SQL_EQUAL).append(\"'\\\"admin\\\"'\");\n");
  }

  /**
   * A multi-column type has to compare every one of its columns, joined by AND - otherwise a
   * {@code BMoney} would match on its value while ignoring its scale.
   */
  @Test
  public void comparesEveryColumnOfAMultiColumnType() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "amount")),
                 code("CN_AMOUNT", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_AND",
                      "CN_AMOUNT_2", "Backend.SQL_EQUAL_PAR"));
  }


  /**
   * A boolean operator between two arguments is emitted as the matching {@code Backend} constant.
   */
  @Test
  public void generatesBooleanOperators() throws Exception {
    WurbletArgumentExpression and = parseExpression(entity, "user", "AND", "txId");
    assertEquals(generator.generate(and.getOperators().getFirst()), code("Backend.SQL_AND"));

    WurbletArgumentExpression or = parseExpression(entity, "user", "OR", "txId");
    assertEquals(generator.generate(or.getOperators().getFirst()), code("Backend.SQL_OR"));
  }

  /**
   * An expression is wrapped in parentheses so the operator precedence written in the wurblet
   * argument survives into the SQL.
   */
  @Test
  public void parenthesizesAnExpression() throws Exception {
    assertEquals(generator.generate(parseExpression(entity, "user", "AND", "txId")),
                 code("Backend.SQL_LEFT_PARENTHESIS",
                      "CN_USER", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_AND",
                      "CN_TXID", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_RIGHT_PARENTHESIS"));
  }

  @Test
  public void parenthesizesNestedExpressions() throws Exception {
    assertEquals(generator.generate(parseExpression(entity, "user", "AND", "(", "txId", "OR", "modType", ")")),
                 code("Backend.SQL_LEFT_PARENTHESIS",
                      "CN_USER", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_AND",
                      "Backend.SQL_LEFT_PARENTHESIS",
                      "CN_TXID", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_OR",
                      "CN_MODTYPE", "Backend.SQL_EQUAL_PAR",
                      "Backend.SQL_RIGHT_PARENTHESIS",
                      "Backend.SQL_RIGHT_PARENTHESIS"));
  }

  /**
   * An optional condition is only appended if its boolean method argument is true.
   */
  @Test
  public void guardsAnOptionalCondition() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user?withUser")),
                 guarded("withUser", "CN_USER", "Backend.SQL_EQUAL_PAR"));
  }

  /**
   * Every column of a multi-column type belongs to the same condition, i.e. one guard for all.
   */
  @Test
  public void guardsEveryColumnOfAMultiColumnType() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "amount?withAmount")),
                 guarded("withAmount",
                         "CN_AMOUNT", "Backend.SQL_EQUAL_PAR",
                         "Backend.SQL_AND",
                         "CN_AMOUNT_2", "Backend.SQL_EQUAL_PAR"));
  }

  /**
   * The operators and parentheses are emitted unconditionally. Whatever ends up empty because its
   * conditions were skipped is removed from the SQL at runtime by {@code Backend.reduceSql}.
   */
  @Test
  public void emitsOperatorsOfOptionalConditionsUnconditionally() throws Exception {
    WurbletArgumentExpression expr = parseExpression(entity, "user?withUser", "OR", "txId?withTxId");
    assertEquals(generator.generate(expr.getOperators().getFirst()), code("Backend.SQL_OR"));
  }

  /**
   * A mandatory condition is never guarded, not even next to optional ones.
   */
  @Test
  public void leavesMandatoryConditionsUnguarded() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user")),
                 code("CN_USER", "Backend.SQL_EQUAL_PAR"));
  }

  /**
   * Anything that is not an argument, operator or expression is appended verbatim, which is how
   * the wurblets splice in fixed SQL fragments.
   */
  @Test
  public void appendsAnythingElseVerbatim() throws Exception {
    assertEquals(generator.generate("\"AND deleted IS NULL\""), code("\"AND deleted IS NULL\""));
    assertEquals(generator.generate("CN_TXID"), code("CN_TXID"));
  }

}
