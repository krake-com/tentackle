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

package org.tentackle.pdo;

import org.tentackle.common.ServiceFactory;

interface PersistenceDelegateLinkerHolder {
  PersistenceDelegateLinker INSTANCE = ServiceFactory.createService(PersistenceDelegateLinker.class);
}

/**
 * Links the persistence delegate with its dynamic proxy.<br>
 * Provided to exclude the methods {@code setPdo} and {@code setOperation} from the interfaces {@link PdoProvider} and
 * {@link OperationProvider}.
 */
public interface PersistenceDelegateLinker {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PersistenceDelegateLinker getInstance() {
    return PersistenceDelegateLinkerHolder.INSTANCE;
  }

  /**
   * Link the persistence delegate to its {@link PersistentDomainObject}.
   *
   * @param pdo the PDO proxy
   * @param delegate the persistence delegate implementation
   * @param <T> the PDO type
   */
  <T extends PersistentDomainObject<T>> void linkPersistentObject(T pdo, PersistentObject<T> delegate);

  /**
   * Links the persistence delegate to its {@link Operation}.
   *
   * @param operation the operation proxy
   * @param delegate the persistence delegate implementation
   * @param <T> the operation type
   */
  <T extends Operation<T>> void linkPersistentOperation(T operation, PersistentOperation<T> delegate);

}
