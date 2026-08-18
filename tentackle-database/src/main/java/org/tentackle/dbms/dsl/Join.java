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

package org.tentackle.dbms.dsl;

import org.tentackle.sql.JoinType;

import java.util.Objects;

/**
 * A joined table.
 *
 * @author harald
 */
public class Join implements SqlElement {

  private final JoinType joinType;      // the kind of join
  private final Table table;            // the joined table
  private final Condition condition;    // the join condition


  /**
   * Creates a join.
   *
   * @param joinType the kind of join
   * @param table the joined table
   * @param condition the join condition
   */
  public Join(JoinType joinType, Table table, Condition condition) {
    this.joinType = Objects.requireNonNull(joinType, "join type must be provided");
    this.table = Objects.requireNonNull(table, "table must be provided");
    this.condition = Objects.requireNonNull(condition, "join condition must be provided");
  }


  /**
   * Gets the kind of join.
   *
   * @return the join type
   */
  public JoinType getJoinType() {
    return joinType;
  }

  /**
   * Gets the joined table.
   *
   * @return the table
   */
  public Table getTable() {
    return table;
  }

  /**
   * Gets the join condition.
   *
   * @return the condition
   */
  public Condition getCondition() {
    return condition;
  }


  @Override
  public void render(RenderContext context) {
    // the table is rendered before the condition to keep the parameters in the order of the question marks
    String tableSql = table.createNameSql(context);
    context.append(context.getBackend().sqlJoin(joinType, tableSql, table.getAlias(),
                                                context.renderToString(condition)));
  }

}
