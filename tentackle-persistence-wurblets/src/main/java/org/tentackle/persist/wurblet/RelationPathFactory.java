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
import org.tentackle.common.PathFactory;
import org.tentackle.model.Relation;

/**
 * Factory for relation paths.
 *
 * @author harald
 */
public class RelationPathFactory implements PathFactory<RelationPath, Relation> {

  @Override
  public RelationPath create(List<Relation> elements, List<RelationPath> paths) {
    return new RelationPath(elements, paths);
  }

  @Override
  public RelationPath create(List<Relation> elements, RelationPath path) {
    List<Relation> allElements = new ArrayList<>(elements);
    allElements.addAll(path.getElements());
    return new RelationPath(allElements, path.getPaths());
  }


  /**
   * Creates a list of consolidated paths from wurblet arguments.<br>
   * Identical leading path segments are consolidated to a single path
   * with child paths.
   *
   * @param arguments the wurblet arguments
   * @return the list of paths, empty if none, never null
   */
  public List<RelationPath> createPaths(List<WurbletArgument> arguments) {
    List<RelationPath> paths = new ArrayList<>();
    for (WurbletArgument argument: arguments) {
      if (argument.isPath()) {
        paths.add(new RelationPath(argument.getRelations(), null));
      }
    }
    // recursively consolidate the paths
    return merge(paths);
  }

}
