/**
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


package org.tentackle.pdo;

import org.tentackle.session.SessionDependable;

/**
 * A persistent operation.<br>
 * POs usually contain persistent-relevant methods that can not or should not be
 * associated with a operation.
 *
 * @param <T> the operation type
 * @author harald
 */
public interface PersistentOperation<T extends Operation<T>>
       extends PersistenceDelegate<T>, OperationHolder<T>, DomainContextDependable, SessionDependable {

  /**
   * Gets the operation instance.
   * <p>
   * Same as {@link OperationHolder#getOperation()} but shorter.
   *
   * @return the operation
   */
  T me();

  /**
   * Gets the delegate implementing the persistence operation.
   * <p>
   * Note: the method is intercepted and executed by the {@link org.tentackle.pdo.OperationInvocationHandler}.
   *
   * @return the persistent operation
   */
  PersistentOperation<T> getDelegate();

  /**
   * Creates a clone of this PO.
   *
   * @return the cloned PO
   */
  PersistentOperation<T> clonePersistentOperation();

  /**
   * Creates a new instance similar to this object but initialized to the default values.
   * <br>
   * Sets the domain context as well.
   * @return the created instance
   */
  PersistentOperation<T> newInstance();

}
