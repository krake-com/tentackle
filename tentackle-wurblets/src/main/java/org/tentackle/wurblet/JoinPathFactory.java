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

import org.tentackle.common.PathFactory;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import java.util.ArrayList;
import java.util.List;

interface JoinPathFactoryHolder {
  JoinPathFactory INSTANCE = ServiceFactory.createService(JoinPathFactory.class);
}

/**
 * Factory for relation paths.
 *
 * @author harald
 */
@Service(JoinPathFactory.class)   // defaults to self
public class JoinPathFactory implements PathFactory<JoinPath, Join> {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static JoinPathFactory getInstance() {
    return JoinPathFactoryHolder.INSTANCE;
  }


  @Override
  public JoinPath create(List<Join> elements, List<JoinPath> paths) {
    return new JoinPath(elements, paths);
  }

  @Override
  public JoinPath create(List<Join> elements, JoinPath path) {
    List<Join> allElements = new ArrayList<>(elements);
    allElements.addAll(path.getElements());
    return new JoinPath(allElements, path.getPaths());
  }


  /**
   * Creates a list of consolidated paths from wurblet arguments.<br>
   * Identical leading path segments are consolidated to a single path
   * with child paths.
   *
   * @param arguments the wurblet arguments
   * @return the list of paths, empty if none, never null
   */
  public List<JoinPath> createPaths(List<WurbletArgument> arguments) {
    List<JoinPath> paths = new ArrayList<>();
    for (WurbletArgument argument: arguments) {
      if (argument.isPath()) {
        List<Join> joins = new ArrayList<>();
        for (WurbletRelation wurbletRelation: argument.getWurbletRelations()) {
          joins.add(new Join(wurbletRelation, argument));
        }
        paths.add(new JoinPath(joins, null));
      }
    }
    // recursively consolidate the paths
    paths = merge(paths);
    // set the join names
    int index = 1;
    for (JoinPath path: paths) {
      namePath("j_" + index++, path);
    }
    return paths;
  }


  private void namePath(String prefix, JoinPath path) {
    int index = 1;
    String lastPrefix = prefix;
    if (path.getElements().size() == 1) {
      path.getElements().get(0).setName(prefix);
    }
    else {
      for (Join join: path.getElements()) {
        lastPrefix = prefix + "_" + index++;
        join.setName(lastPrefix);
      }
    }
    index = 1;
    for (JoinPath subPath: path.getPaths()) {
      namePath(lastPrefix + "_" + index++, subPath);
    }
  }

}
