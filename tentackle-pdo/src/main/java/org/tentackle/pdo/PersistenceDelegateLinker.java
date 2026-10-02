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

interface PersistenceDelegateLinkerHolder {

  static PersistenceDelegateLinker createLinker() {
    try {
      return ServiceFactory.createService(PersistenceDelegateLinker.class);
    }
    catch (TentackleRuntimeException rx) {
      // this is the case in domain unit tests, for example, since the DefaultPersistenceDelegateLinker resides
      // in the persistence-layer, which is not within the dependencies of the domain module(s).
      Logger.get(PersistenceDelegateLinkerHolder.class).warning("no PersistenceDelegateLinker configured -> fallback to reflection");

      return new PersistenceDelegateLinker() {

        @Override
        public <T extends PersistentDomainObject<T>> void linkPersistentObject(T pdo, PersistentObject<T> delegate) {
          try {
            Method setPdoMethod = delegate.getClass().getMethod("setPdo", PersistentDomainObject.class);
            setPdoMethod.invoke(delegate, pdo);
          }
          catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new TentackleRuntimeException(e);
          }
        }

        @Override
        public <T extends Operation<T>> void linkPersistentOperation(T operation, PersistentOperation<T> delegate) {
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

  PersistenceDelegateLinker INSTANCE = createLinker();
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
  static PersistenceDelegateLinker getInstance() {
    return PersistenceDelegateLinkerHolder.INSTANCE;
  }

  /**
   * Links the persistence delegate to its {@link PersistentDomainObject}.
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
