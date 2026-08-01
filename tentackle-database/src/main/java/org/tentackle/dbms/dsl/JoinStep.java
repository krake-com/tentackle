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
 * The pending join of a {@link Select}.<br>
 * Enforces the join condition to be provided before the select can be continued.
 *
 * @author harald
 */
public class JoinStep {

  private final Select select;        // the select being built
  private final JoinType joinType;    // the kind of join
  private final Table table;          // the joined table


  /**
   * Creates a join step.
   *
   * @param select the select being built
   * @param joinType the kind of join
   * @param table the joined table
   */
  JoinStep(Select select, JoinType joinType, Table table) {
    this.select = select;
    this.joinType = joinType;
    this.table = Objects.requireNonNull(table, "table must be provided");
  }


  /**
   * Sets the join condition.
   *
   * @param condition the join condition
   * @return the select
   */
  public Select on(Condition condition) {
    return select.addJoin(new Join(joinType, table, condition));
  }

}
