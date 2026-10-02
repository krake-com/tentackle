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

package org.tentackle.domain;

import org.tentackle.common.Service;
import org.tentackle.pdo.DomainDelegateLinker;
import org.tentackle.pdo.DomainObject;
import org.tentackle.pdo.DomainOperation;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Default implementation of {@link DomainDelegateLinker}.<br>
 * Links PDO and operation proxies to their domain delegates, which must
 * extend {@link AbstractDomainObject} or {@link AbstractDomainOperation}, respectively.
 */
@Service(DomainDelegateLinker.class)
public class DefaultDomainDelegateLinker implements DomainDelegateLinker {

  /**
   * Creates a linker.
   */
  public DefaultDomainDelegateLinker() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends PersistentDomainObject<T>> void linkDomainObject(T pdo, DomainObject<T> delegate) {
    ((AbstractDomainObject) delegate).setPdo(pdo);
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends Operation<T>> void linkDomainOperation(T operation, DomainOperation<T> delegate) {
    ((AbstractDomainOperation) delegate).setOperation(operation);
  }
}
