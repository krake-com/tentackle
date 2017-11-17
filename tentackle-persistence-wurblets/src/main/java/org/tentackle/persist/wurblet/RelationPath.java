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
 * Path of {@link Relation}s.
 *
 * @author harald
 */
public class RelationPath implements Path<RelationPath, Relation> {

  private List<Relation> elements;            // the relations forming a path
  private List<RelationPath> paths;           // continuation paths

  /**
   * Creates a relation path.
   *
   * @param elements the relations, null or empty if none
   * @param paths the continuation paths, null or empty if none
   */
  public RelationPath(List<Relation> elements, List<RelationPath> paths) {
    this.elements = elements;
    this.paths = paths;
  }

  @Override
  public List<Relation> getElements() {
    if (elements == null) {
      elements = new ArrayList<>();
    }
    return elements;
  }

  @Override
  public List<RelationPath> getPaths() {
    if (paths == null) {
      paths = new ArrayList<>();
    }
    return paths;
  }

}
