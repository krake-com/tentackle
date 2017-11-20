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

import java.util.ArrayList;
import java.util.List;
import org.tentackle.common.Path;
import org.tentackle.model.Relation;

/**
 * Path of {@link Join}s.
 *
 * @author harald
 */
public class JoinPath implements Path<JoinPath, Join> {

  private List<Join> elements;            // the relations forming a path
  private List<JoinPath> paths;           // continuation paths

  /**
   * Creates a relation path.
   *
   * @param elements the relations, null or empty if none
   * @param paths the continuation paths, null or empty if none
   */
  public JoinPath(List<Join> elements, List<JoinPath> paths) {
    this.elements = elements;
    this.paths = paths;
  }

  @Override
  public List<Join> getElements() {
    if (elements == null) {
      elements = new ArrayList<>();
    }
    return elements;
  }

  @Override
  public List<JoinPath> getPaths() {
    if (paths == null) {
      paths = new ArrayList<>();
    }
    return paths;
  }

  /**
   * Find the join for given relation path.
   *
   * @param relations the relation path
   * @return the join if found, else null
   */
  public Join findJoin(List<Relation> relations) {
    return findJoinImpl(this, relations);
  }

  private Join findJoinImpl(JoinPath path, List<Relation> relations) {
    Join join = null;
    int depth = 0;
    List<Join> joins = path.getElements();
    int joinCount = joins.size();
    for (Relation relation: relations) {
      join = joins.get(depth);    // last join
      if (depth >= joinCount || !relation.equals(join.getRelation())) {
        return null;    // not in this join path
      }
      depth++;
    }
    if (depth < relations.size()) {
      relations = relations.subList(depth, relations.size()); // remaining relations
      // perhaps in one of the subpaths
      for (JoinPath subPath: path.getPaths()) {
        join = findJoinImpl(subPath, relations);
        if (join != null) {
          break;
        }
      }
    }
    // else: last join matches
    return join;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (Join join: getElements()) {
      buf.append(" * ").append(join);
    }
    for (JoinPath subPath: getPaths()) {
      buf.append(" [ ").append(subPath).append(" ]");
    }
    return buf.toString();
  }

}
