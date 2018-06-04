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

import org.tentackle.session.SessionProvider;

/**
 * The domain interface.
 *
 * @param <T> the operation type
 * @author harald
 */
public interface DomainOperation<T extends Operation<T>>
       extends DomainDelegate<T>, OperationHolder<T>, DomainContextProvider, SessionProvider {

  /**
   * Gets the operation instance.
   * <p>
   * Same as {@link OperationHolder#getOperation()} but shorter.
   *
   * @return the operation
   * @deprecated use me() instead
   */
  @Deprecated
  T operation();

  /**
   * Creates a clone of this DO.
   *
   * @return the cloned DO
   */
  DomainOperation<T> cloneDomainOperation();

}
