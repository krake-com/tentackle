/*
 * Tentackle - a framework for java desktop applications.
 * http://www.tentackle.org
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.tentackle.model.Relation;

/**
 * Left join to select other components within one select statement.
 *
 * @author harald
 */
public class LeftJoin {

  private final Relation relation;              // the relation
  private final List<LeftJoin> joins;           // the joins cascaded starting at this join

  /**
   * Creates a left join to load joined entities.
   *
   * @param relation the relation
   */
  public LeftJoin(Relation relation) {
    if (relation == null) {
      throw new IllegalArgumentException("relation must be given");
    }
    this.relation = relation;
    this.joins = new ArrayList<>();
  }

  /**
   * Gets the relation of this join.
   *
   * @return the relation, never null
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the joins cascaded starting at this join.
   *
   * @return the cascaded joins, empty if no more joins
   */
  public List<LeftJoin> getJoins() {
    return joins;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(relation);
    if (!joins.isEmpty()) {
      for (LeftJoin join: joins) {
        buf.append("\n-> ").append(join.relation);
      }
    }
    return buf.toString();
  }


  @Override
  public int hashCode() {
    int hash = 3;
    hash = 59 * hash + Objects.hashCode(this.relation);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final LeftJoin other = (LeftJoin) obj;
    return this.relation.equals(other.relation);
  }

}
