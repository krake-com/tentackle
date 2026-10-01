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

package org.tentackle.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A factory for {@link Path}s.
 *
 * @author harald
 * @param <P> the path type
 * @param <E> the path element type
 */
public interface PathFactory<P extends Path<P,E>, E> {

  /**
   * Creates a new path from elements and continuation paths.
   *
   * @param elements the elements of the path
   * @param paths the continuation paths
   * @return the new path
   */
  P create(List<E> elements, List<P> paths);

  /**
   * Creates a new path by prepending elements to an existing path.
   *
   * @param elements the elements to prepend
   * @param path the path to prepend the elements to
   * @return the new concatenated path
   */
  P create(List<E> elements, P path);



  /**
   * Merges paths to remove redundant path segments.<br>
   * The returned consolidated paths form a hierarchical graph.
   * <pre>
   * Example:
   *
   * given:
   *
   * a1 -- b1 -- c1 -- d1 -- eX
   * a1 -- b1 -- c1 -- d2
   * a1 -- b1 -- c2
   * a1 -- b1 -- c2 -- d3
   * a1 -- b1 -- c2 -- d3 -- eX
   * a1 -- b2 -- cX -- dX
   * a1 -- b2
   *
   * merged result:
   *
   * a1 -- b1 -- c1 -- d1 -- eX
   *    |     |     |
   *    |     |     -- d2
   *    |     |
   *    |     -- c2 -- d3 -- eX
   *    |
   *    -- b2 -- cX -- dX
   *
   * </pre>
   *
   * @param paths input paths
   * @return consolidated paths
   */
  default List<P> merge(List<P> paths) {
    List<P> consolidatedPaths = new ArrayList<>();
    Map<E,List<P>> pathMap = new LinkedHashMap<>();  // paths starting with the same element (LinkedHashMap to keep order)
    for (P path: paths) {
      List<E> elements = path.getElements();
      if (elements.isEmpty()) {
        consolidatedPaths.add(path);    // nothing to consolidate
      }
      else {
        E element = elements.get(0);
        List<P> pathList = pathMap.computeIfAbsent(element, k -> new ArrayList<>());
        // element is new
        pathList.add(path);
      }
    }

    // consolidate paths starting with the same element
    for (Map.Entry<E,List<P>> entry: pathMap.entrySet()) {
      List<P> pathList = entry.getValue();
      if (pathList.size() == 1) {
        consolidatedPaths.add(pathList.get(0));
      }
      else {
        List<E> elements = new ArrayList<>();
        elements.add(entry.getKey());             // single element of new lead path
        List<P> contPaths = new ArrayList<>();
        for (P path: pathList) {
          List<E> pathElements = new ArrayList<>(path.getElements());
          if (!pathElements.isEmpty()) {
            pathElements.remove(0);   // remove element which moved to the lead path
            if (pathElements.isEmpty()) {
              // pathological case...
              contPaths.addAll(path.getPaths());
            }
            else {
              // create new continuation path
              contPaths.add(PathFactory.this.create(pathElements, path.getPaths()));
            }
          }
        }

        // consolidate recursively
        contPaths = merge(contPaths);

        P leadPath;
        if (contPaths.size() == 1) {
          // re-combine lead with continuation path
          leadPath = create(elements, contPaths.get(0));
        }
        else {
          leadPath = PathFactory.this.create(elements, contPaths);
        }
        consolidatedPaths.add(leadPath);
      }
    }

    return consolidatedPaths;
  }


}
