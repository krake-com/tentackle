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

package org.tentackle.wurblet;

import java.util.Objects;

/**
 * A load join.
 *
 * @author harald
 */
public class Join {

  private final WurbletRelation wurbletRelation;
  private final WurbletArgument argument;
  private String name;

  /**
   * Creates an unnamed load join.
   *
   * @param wurbletRelation the wurblet relation
   * @param argument the wurblet argument
   */
  public Join(WurbletRelation wurbletRelation, WurbletArgument argument) {
    this.wurbletRelation = wurbletRelation;
    this.argument = argument;
  }

  /**
   * Gets the relation wrapped by this load join.
   *
   * @return the relation, never null
   */
  public WurbletRelation getWurbletRelation() {
    return wurbletRelation;
  }

  /**
   * Gets the wurblet argument representing this join.
   *
   * @return the wurblet argument
   */
  public WurbletArgument getArgument() {
    return argument;
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
    hash = 41 * hash + Objects.hashCode(this.wurbletRelation);
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
    return Objects.equals(this.wurbletRelation, other.wurbletRelation);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(wurbletRelation);
    if (name != null) {
      buf.append(" as ").append(name);
    }
    return buf.toString();
  }

}
