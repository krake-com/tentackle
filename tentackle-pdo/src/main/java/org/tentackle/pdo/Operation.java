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
 * An operation.
 * <p>
 * Operations provide methods that are not directly related to some PDO.
 * Such operations usually cover complex transactions or services.
 * Operations differ from PDOs in two ways:
 * <ol>
 * <li>there is no model, i.e. they are not related to database tables</li>
 * <li>either the persistence- or the domain-part is optional (but not both)</li>
 * </ol>
 * As with PDOs the persistence part may operate on a remote session, i.e. physically
 * runs in another JVM connected to a database backend. The domain-part always runs in
 * the local JVM.
 *
 * @param <T> the operation class
 * @author harald
 */
public interface Operation<T extends Operation<T>>
       extends PersistentOperation<T>, DomainOperation<T>, EffectiveClassProvider<T> {

  /**
   * Gets the delegate implementing the domain logic.
   * <p>
   * Note: the method is invoked directly by the {@link OperationInvocationHandler}, bypassing
   * the interceptor chain entirely (see {@link NotInterceptable}).
   *
   * @return the domain object
   */
  @Override
  @NotInterceptable
  DomainOperation<T> getDomainDelegate();

  /**
   * Gets the delegate implementing the persistence layer.
   * <p>
   * Note: the method is invoked directly by the {@link OperationInvocationHandler}, bypassing
   * the interceptor chain entirely (see {@link NotInterceptable}).
   *
   * @return the persistent object
   */
  @Override
  @NotInterceptable
  PersistentOperation<T> getPersistenceDelegate();

}
