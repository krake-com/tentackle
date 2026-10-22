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

package org.tentackle.persist;

import org.tentackle.common.Service;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistenceDelegateLinker;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.pdo.PersistentOperation;

/**
 * Default implementation of {@link PersistenceDelegateLinker}.<br>
 * Links PDO and operation proxies to their persistence delegates, which must
 * extend {@link AbstractPersistentObject} or {@link AbstractPersistentOperation}, respectively.
 */
@Service(PersistenceDelegateLinker.class)
public class DefaultPersistenceDelegateLinker implements PersistenceDelegateLinker {

  /**
   * Creates the persistence delegate linker.
   */
  public DefaultPersistenceDelegateLinker() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends PersistentDomainObject<T>> void linkPersistentObject(T pdo, PersistentObject<T> delegate) {
    ((AbstractPersistentObject) delegate).setPdo(pdo);
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends Operation<T>> void linkPersistentOperation(T operation, PersistentOperation<T> delegate) {
    ((AbstractPersistentOperation) delegate).setOperation(operation);
  }
}
