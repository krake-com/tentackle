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

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.common.Path;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.List;

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

  /**
   * Creates a normalized relation path.
   *
   * @param element the relation
   * @param paths the continuation paths, null or empty if none
   */
  public JoinPath(Join element, List<JoinPath> paths) {
    elements = new ArrayList<>();
    elements.add(element);
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
   * Finds the join for a given component.
   *
   * @param component the component
   * @return the path if unique, null if no path
   * @throws WurbelException if component path is ambiguous
   */
  public Join findJoin(Entity component) throws WurbelException {
    return component == null ? null : findJoinImpl(this, component);
  }

  private Join findJoinImpl(JoinPath path, Entity component) throws WurbelException {
    Join join = null;
    JoinPath joinPath = null;

    List<Join> joins = path.getElements();
    int joinCount = joins.size();
    for (Join jn : path.getElements()) {
      if (component.equals(jn.getRelation().getForeignEntity())) {
        if (join != null) {
          throw new WurbelException("ambiguous path to " + component + " in path " + path);
        }
        join = jn;
        joinPath = path;
      }
    }
    for (JoinPath subPath: path.getPaths()) {
      Join jn = findJoinImpl(subPath, component);
      if (jn != null) {
        if (join != null) {
          throw new WurbelException("ambiguous path to " + component + " in paths " + joinPath + " / " + subPath);
        }
        join = jn;
        joinPath = subPath;
      }
    }
    return join;
  }


  /**
   * Find the join for given relation path.
   *
   * @param relations the relation path
   * @return the join if found, else null
   */
  public Join findJoin(List<Relation> relations) {
    return relations == null || relations.isEmpty() ? null : findJoinImpl(this, relations);
  }

  private Join findJoinImpl(JoinPath path, List<Relation> relations) {
    Join join = null;
    int depth = 0;
    List<Join> joins = path.getElements();
    int joinCount = joins.size();
    for (Relation relation: relations) {
      if (depth >= joinCount) {
        break;
      }
      join = joins.get(depth);    // last join
      if (!relation.equals(join.getRelation())) {
        return null;    // not in this join path
      }
      depth++;
    }
    if (depth < relations.size()) {
      relations = relations.subList(depth, relations.size()); // remaining relations
      // perhaps in one of the sub paths
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


  /**
   * Normalizes the join and all subjoins.<br>
   * A normalized join contains only a single element.
   */
  public void normalize() {
    for (JoinPath subPath: getPaths()) {
      subPath.normalize();
    }
    int size;
    while ((size = getElements().size()) > 1) {
      Join last = getElements().get(size - 1);
      JoinPath path = new JoinPath(last, new ArrayList<>(getPaths()));
      getElements().remove(size - 1);
      getPaths().clear();
      getPaths().add(path);
    }
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
