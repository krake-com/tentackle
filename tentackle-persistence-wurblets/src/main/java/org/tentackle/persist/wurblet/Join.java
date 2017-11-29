/*
 * Tentackle - http://www.tentackle.org
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

import java.util.Objects;
import org.tentackle.model.Relation;

/**
 * Load join.
 *
 * @author harald
 */
public class Join {

  private final Relation relation;
  private String name;

  /**
   * Creates an unnamed join.
   *
   * @param relation the relation
   */
  public Join(Relation relation) {
    this.relation = relation;
  }

  /**
   * Gets the relation wrapped by this join.
   *
   * @return the relation, never null
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the name of the join.
   *
   * @return the name, null if yet unnamed
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the join.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 41 * hash + Objects.hashCode(this.relation);
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
    final Join other = (Join) obj;
    return Objects.equals(this.relation, other.relation);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(relation.getEntity()).append('.');
    buf.append(relation.getName());
    if (name != null) {
      buf.append(" as ").append(name);
    }
    return buf.toString();
  }

}
