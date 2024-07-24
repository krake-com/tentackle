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

import org.wurbelizer.wurbel.ArgScanner;
import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A relation within a {@link WurbletArgument}.<br>
 * Describes an element of a path to an attribute or entity.
 */
public class WurbletRelation {

  private final Relation relation;
  private final String filter;
  private final WurbletArgumentParser parser;

  /**
   * Creates a wurblet relation.
   *
   * @param relation the model relation
   * @param filter an optional filter string (only for load joins), null if none
   * @throws WurbelException if parsing the filter failed
   */
  public WurbletRelation(Relation relation, String filter) throws WurbelException {
    this.relation = Objects.requireNonNull(relation, "relation");
    this.filter = filter;

    if (filter != null) {
      ArgScanner argScanner = new ArgScanner(filter, null);
      List<String> filterArgs = new ArrayList<>();
      String filterArg;
      while((filterArg = argScanner.next()) != null) {
        filterArgs.add(filterArg);
      }
      parser = new WurbletArgumentParser(relation.getForeignEntity(), false, filterArgs);
    }
    else {
      parser = null;
    }
  }

  /**
   * Gets the model relation.
   *
   * @return the relation
   */
  public Relation getRelation() {
    return relation;
  }

  /**
   * Gets the optional filter string.
   *
   * @return the filter, null if none
   */
  public String getFilter() {
    return filter;
  }

  /**
   * Gets the optional parser that has been applied to the filter string.
   *
   * @return the parser, null if none
   */
  public WurbletArgumentParser getParser() {
    return parser;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder().append(relation.getEntity())
                                           .append(".")
                                           .append(relation.getName());
    if (filter != null) {
      buf.append(" filtered by '").append(filter).append("'");
    }
    return buf.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WurbletRelation that = (WurbletRelation) o;
    return relation.equals(that.relation);
  }

  @Override
  public int hashCode() {
    return relation.hashCode();
  }
}
