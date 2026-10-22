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
 *
 */

package org.tentackle.persist.wurblet;

import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.List;

/**
 * A node of the eager join tree of an entity.
 * <p>
 * The tree mirrors the {@code select = eager[:depth]} relations of the model: each node is one
 * {@code LEFT JOIN} of the generic selects, its children are the eager relations of the joined
 * entity that still fit into the declared depth (see {@link Relation#getEagerDepth()}).
 * <p>
 * The alias is the name of the joined table within the generated statement. Root nodes are named
 * {@code e_1}, {@code e_2}, ... and children extend their parent's alias ({@code e_1_1}), which
 * keeps the aliases unique throughout the tree even if a relation refers back to its own entity.
 *
 * @author harald
 */
public class EagerJoin {

  private final Relation relation;
  private final String alias;
  private final List<EagerJoin> joins;

  /**
   * Creates an eager join.
   *
   * @param relation the eagerly selected relation
   * @param alias the alias of the joined table
   */
  public EagerJoin(Relation relation, String alias) {
    this.relation = relation;
    this.alias = alias;
    this.joins = new ArrayList<>();
  }

  /**
   * Gets the eagerly selected relation.
   *
   * @return the relation, never null
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the alias of the joined table.
   *
   * @return the alias, never null
   */
  public String getAlias() {
    return alias;
  }

  /**
   * Gets the cascaded joins.
   *
   * @return the joins, empty if the join does not cascade, never null
   */
  public List<EagerJoin> getJoins() {
    return joins;
  }

  @Override
  public String toString() {
    return alias + ":" + relation;
  }

}
