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

import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.NotInterceptable;

/**
 * A persistent domain object.
 * <p>
 * PDOs consist of a persistence- and a domain-part. Both are interfaces.
 * Their implementations are instantiated by a factory at runtime and
 * provided to the application by a dynamic proxy. Thus, the concrete implementation
 * is not known to the PDO-layer and can be replaced without changing a single
 * line of application code.
 *
 * @param <T> the PDO class
 * @author harald
 */
public interface PersistentDomainObject<T extends PersistentDomainObject<T>>
       extends PersistentObject<T>, DomainObject<T>, EffectiveClassProvider<T>, Comparable<T> {

  /**
   * Gets the delegate implementing the domain logic.
   * <p>
   * Note: the method is invoked directly by the {@link PdoInvocationHandler}, bypassing
   * the interceptor chain entirely (see {@link NotInterceptable}).
   *
   * @return the domain object
   */
  @Override
  @NotInterceptable
  DomainObject<T> getDomainDelegate();

  /**
   * Gets the delegate implementing the persistence layer.
   * <p>
   * Note: the method is invoked directly by the {@link PdoInvocationHandler}, bypassing
   * the interceptor chain entirely (see {@link NotInterceptable}).
   *
   * @return the persistent object
   */
  @Override
  @NotInterceptable
  PersistentObject<T> getPersistenceDelegate();

}
