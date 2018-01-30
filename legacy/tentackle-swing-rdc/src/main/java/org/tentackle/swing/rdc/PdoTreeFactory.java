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
import org.tentackle.common.ServiceFactory;


interface PdoTreeFactory$Singleton {
  PdoTreeFactory INSTANCE = ServiceFactory.createService(PdoTreeFactory.class, DefaultPdoTreeFactory.class);
}



/**
 * A factory for {@link PdoTree}s.
 *
 * @author harald
 */
public interface PdoTreeFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoTreeFactory getInstance() {
    return PdoTreeFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection will be shown in the tree. If it is some
   * other object, only that object is shown.<br>
   * Notice that the objects need not necessarily be {@link org.tentackle.pdo.PersistentDomainObject}s.
   * <p>
   * @param objects the objects, null if empty tree
   * <p>
   * @return the tree
   */
  PdoTree createPdoTree(Collection<?> objects);

  /**
   * Creates a tree.<br>
   * If the given object is a {@link Collection} the objects of the collection will be shown in the tree. If it is some
   * other object, only that object is shown.<br>
   * Notice that the objects need not necessarily be {@link org.tentackle.pdo.PersistentDomainObject}s.
   * <p>
   * @param object the object or collection of objects, null if empty tree
   * <p>
   * @return the tree
   */
  PdoTree createPdoTree(Object object);

  /**
   * Creates an empty tree.
   * <p>
   * @return the tree
   */
  PdoTree createPdoTree();

}
