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

package org.tentackle.swing.rdc;

import java.util.Collection;
import org.tentackle.common.Service;

/**
 * A factory for {@link PdoTree}s.
 *
 * @author harald
 */
@Service(PdoTreeFactory.class)
public class DefaultPdoTreeFactory implements PdoTreeFactory {

  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link org.tentackle.pdo.PersistentDomainObject}s.
   *
   * @param objects the objects, null if empty tree
   * @return the tree
   */
  @Override
  public PdoTree createPdoTree(Collection<?> objects) {
    return new PdoTree(objects);
  }


  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection
   * will be shown in the tree. If it is some other object, only that
   * object is shown.<br>
   * Notice that the objects need not necessarily be {@link org.tentackle.pdo.PersistentDomainObject}s.
   *
   * @param object the object or collection of objects, null if empty tree
   * @return the tree
   */
  @Override
  public PdoTree createPdoTree(Object object) {
    return new PdoTree(object);
  }


  /**
   * Creates an empty tree.
   *
   * @return the tree
   */
  @Override
  public PdoTree createPdoTree() {
    return new PdoTree();
  }

}
