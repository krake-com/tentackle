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
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

interface DomainDelegateLinkerHolder {

  static DomainDelegateLinker createLinker() {
    try {
      return ServiceFactory.createService(DomainDelegateLinker.class);
    }
    catch (TentackleRuntimeException rx) {
      // this is the case in persistence unit tests, for example, since the DefaultDomainDelegateLinker resides
      // in the domain-layer, which is not within the dependencies of the persistence module(s).
      Logger.get(DomainDelegateLinkerHolder.class).warning("no DomainDelegateLinker configured -> fallback to reflection");

      return new DomainDelegateLinker() {

        @Override
        public <T extends PersistentDomainObject<T>> void linkDomainObject(T pdo, DomainObject<T> delegate) {
          try {
            Method setPdoMethod = delegate.getClass().getMethod("setPdo", PersistentDomainObject.class);
            setPdoMethod.invoke(delegate, pdo);
          }
          catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new TentackleRuntimeException(e);
          }
        }

        @Override
        public <T extends Operation<T>> void linkDomainOperation(T operation, DomainOperation<T> delegate) {
          try {
            Method setPdoMethod = delegate.getClass().getMethod("setOperation", Operation.class);
            setPdoMethod.invoke(delegate, operation);
          }
          catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new TentackleRuntimeException(e);
          }
        }
      };
    }
  }

  DomainDelegateLinker INSTANCE = createLinker();
}

/**
 * Links the domain delegate with its dynamic proxy.<br>
 * Provided to exclude the methods {@code setPdo} and {@code setOperation} from the interfaces {@link PdoProvider} and
 * {@link OperationProvider}.
 */
public interface DomainDelegateLinker {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static DomainDelegateLinker getInstance() {
    return DomainDelegateLinkerHolder.INSTANCE;
  }


  /**
   * Links the domain delegate to its {@link PersistentDomainObject}.
   *
   * @param pdo the PDO proxy
   * @param delegate the domain delegate implementation
   * @param <T> the PDO type
   */
  <T extends PersistentDomainObject<T>> void linkDomainObject(T pdo, DomainObject<T> delegate);

  /**
   * Links the domain delegate to its {@link Operation}.
   *
   * @param operation the operation proxy
   * @param delegate the domain delegate implementation
   * @param <T> the operation type
   */
  <T extends Operation<T>> void linkDomainOperation(T operation, DomainOperation<T> delegate);

}
