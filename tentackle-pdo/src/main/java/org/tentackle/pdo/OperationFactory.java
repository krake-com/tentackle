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
import org.tentackle.session.Session;


interface OperationFactoryHolder {
  OperationFactory INSTANCE = ServiceFactory.createService(
              OperationFactory.class, DefaultOperationFactory.class);
}


/**
 * A factory for operation objects.
 *
 * @author harald
 */
public interface OperationFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static OperationFactory getInstance() {
    return OperationFactoryHolder.INSTANCE;
  }

  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param context the domain context
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, DomainContext context);

  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param session the session
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, Session session);

  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param context the domain context
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, DomainContext context);

  /**
   * Creates an operation for a session only.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param session the session
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, Session session);

  /**
   * Creates an operation without any domain context or session.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz);

  /**
   * Creates an operation without any domain context or session.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className);

  /**
   * Creates an operation.
   *
   * @param <T> the operation type
   * @param op the operation
   * @return the created operation belonging to the same class as the given {@code op} and in same context.
   */
  <T extends Operation<T>> T create(T op);

  /**
   * Creates an operation within a sub context.<br>
   * If the current context is already within the given context name, the result is equivalent
   * to {@link #create(Operation)}.
   *
   * @param <T> the operation type
   * @param op the operation
   * @param contextName the context name
   * @return the created operation belonging to the same class as the given {@code op} in a sub context of given name
   */
  <T extends Operation<T>> T create(T op, String contextName);

  /**
   * Creates an operation for a given persistence delegate.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, PersistentOperation<T> persistenceDelegate);

  /**
   * Creates an operation for a given persistence delegate.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, PersistentOperation<T> persistenceDelegate);

  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, DomainContext context, DomainOperation<T> domainDelegate);

  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, DomainContext context, DomainOperation<T> domainDelegate);

  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param session the session
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, Session session, DomainOperation<T> domainDelegate);

  /**
   * Creates an operation for a given domain delegate.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param session the session
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, Session session, DomainOperation<T> domainDelegate);

  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the operation type
   * @param clazz the class of the operation, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(Class<T> clazz, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate);

  /**
   * Creates an operation for given delegates.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created operation
   */
  <T extends Operation<T>> T create(String className, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate);

  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @return the persistence class
   */
  <T extends Operation<T>> Class<PersistentOperation<T>> getPersistenceClass(String className);

  /**
   * Gets the class implementing the domain layer.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @return the domain class
   */
  <T extends Operation<T>> Class<DomainOperation<T>> getDomainClass(String className);

  /**
   * Creates the delegate implementing the persistence layer.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @return the persistent delegate
   */
  <T extends Operation<T>> PersistentOperation<T> createPersistenceDelegate(String className);

  /**
   * Creates the delegate implementing the domain layer.
   *
   * @param <T> the operation type
   * @param className the name of operation-class, usually an interface
   * @return the domain delegate
   */
  <T extends Operation<T>> DomainOperation<T> createDomainDelegate(String className);

}
