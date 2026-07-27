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
import org.tentackle.wurblet.Join;
import org.tentackle.wurblet.WurbletArgumentExpression;

import static org.testng.Assert.assertEquals;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createEntity;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createWurblet;
import static org.tentackle.persist.wurblet.WurbletTestSupport.parseArgument;
import static org.tentackle.persist.wurblet.WurbletTestSupport.parseExpression;

/**
 * Tests for {@link JoinClauseGenerator}, the sibling of {@link WhereClauseGenerator} that emits
 * plain SQL rather than java code: the filter of an eagerly loaded relation, qualified with the
 * join's table alias.
 * <p>
 * The generator is exercised through {@code generate(Object)}, the {@code CodeGenerator} entry
 * point the expression walks into. The no-argument {@code generate()} is only a thin wrapper that
 * prefixes {@code AND} and pulls the expression out of the join's relation - a step that needs the
 * relation's foreign entity resolved from a model directory, which is beyond a unit test.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JoinClauseGeneratorTest {

  private static final String JOIN_ALIAS = "j0";

  private Entity entity;
  private JoinClauseGenerator generator;

  @BeforeClass
  public void setUp() throws Exception {
    entity = createEntity();
    Join join = new Join(null, null);   // only the alias matters for generate(Object)
    join.setName(JOIN_ALIAS);
    generator = new JoinClauseGenerator(createWurblet(), join);
  }


  /**
   * The column is qualified with the join alias, because the filter is evaluated against the
   * joined table, not the selecting one.
   */
  @Test
  public void qualifiesTheColumnWithTheJoinAlias() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user")), "j0.moduser=?");
    assertEquals(generator.generate(parseArgument(entity, "txId")), "j0.txid=?");
  }

  @Test
  public void generatesTheOperatorOfTheArgument() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user:<>")), "j0.moduser<>?");
    assertEquals(generator.generate(parseArgument(entity, "txId:>=")), "j0.txid>=?");
    assertEquals(generator.generate(parseArgument(entity, "user:LIKE")), "j0.moduser LIKE ?");
  }

  @Test
  public void inlinesALiteralValue() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "user:=#\"admin\"")), "j0.moduser=\"'\\\"admin\\\"'\"");
  }

  @Test
  public void generatesBooleanOperators() throws Exception {
    WurbletArgumentExpression and = parseExpression(entity, "user", "AND", "txId");
    assertEquals(generator.generate(and.getOperators().getFirst()), " AND ");

    WurbletArgumentExpression or = parseExpression(entity, "user", "OR", "txId");
    assertEquals(generator.generate(or.getOperators().getFirst()), " OR ");
  }

  @Test
  public void parenthesizesAnExpression() throws Exception {
    assertEquals(generator.generate(parseExpression(entity, "user", "AND", "txId")),
                 "(j0.moduser=? AND j0.txid=?)");
  }

  @Test
  public void parenthesizesNestedExpressions() throws Exception {
    assertEquals(generator.generate(parseExpression(entity, "user", "AND", "(", "txId", "OR", "modType", ")")),
                 "(j0.moduser=? AND (j0.txid=? OR j0.modtype=?))");
  }

  @Test
  public void appendsAnythingElseVerbatim() throws Exception {
    assertEquals(generator.generate("j0.deleted IS NULL"), " j0.deleted IS NULL ");
  }

  /**
   * Every column of a multi-column type gets its own, suffixed column name - a {@code BMoney}
   * must compare its value and its scale, not its value twice.
   */
  @Test
  public void comparesEveryColumnOfAMultiColumnType() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "amount")), "j0.amount=? AND j0.amount_2=?");
  }

  /**
   * A relop other than {@code =} on a multi-column type selects the type's sortable column, and
   * then exactly that one column is compared.
   */
  @Test
  public void comparesOnlyTheSelectedColumn() throws Exception {
    assertEquals(generator.generate(parseArgument(entity, "amount:<")), "j0.amount<?");
  }

}
