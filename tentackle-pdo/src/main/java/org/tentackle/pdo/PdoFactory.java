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
import org.tentackle.reflect.ClassMapper;
import org.tentackle.session.Session;


interface PdoFactoryHolder {
  PdoFactory INSTANCE = ServiceFactory.createService(PdoFactory.class, DefaultPdoFactory.class);
}


/**
 * A factory for persistent domain objects.
 *
 * @author harald
 */
public interface PdoFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoFactory getInstance() {
    return PdoFactoryHolder.INSTANCE;
  }

  /**
   * Gets the persistence class mapper associated with this factory.<br>
   * The classmapper maps PDO interfaces to their persistence implementation.
   *
   * @return the mapper for the persistence implementations
   */
  ClassMapper getPersistenceMapper();

  /**
   * Gets the domain class mapper associated with this factory.<br>
   * The classmapper maps PDO interfaces to their domain implementation.
   *
   * @return the mapper for the domain implementations
   */
  ClassMapper getDomainMapper();

  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param context the domain context
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context);

  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param session the session
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, Session session);

  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param context the domain context
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, DomainContext context);

  /**
   * Creates a PDO for a session only.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param session the session
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, Session session);

  /**
   * Creates a PDO without any domain context or session.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz);

  /**
   * Creates a PDO without any domain context or session.
   * <p>
   * Note: the application must set the context.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className);

  /**
   * Creates a PDO.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @return the created PDO belonging to the same class as the given {@code pdo} and in same context.
   */
  <T extends PersistentDomainObject<T>> T create(T pdo);

  /**
   * Creates a PDO within a sub context.<br>
   * If the current context is already within the given context name, the result is equivalent
   * to {@link #create(PersistentDomainObject)}.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @param contextName the context name
   * @return the created PDO belonging to the same class as the given {@code pdo} in a sub context of given name
   */
  <T extends PersistentDomainObject<T>> T create(T pdo, String contextName);

  /**
   * Creates a PDO for a given persistence delegate.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate);

  /**
   * Creates a PDO for a given persistence delegate.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate);

  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context, DomainObject<T> domainDelegate);

  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, DomainContext context, DomainObject<T> domainDelegate);

  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param session the session
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, Session session, DomainObject<T> domainDelegate);

  /**
   * Creates a PDO for a given domain delegate.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param session the session
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, Session session, DomainObject<T> domainDelegate);

  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the PDO type
   * @param clazz the class of the PDO, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate,
                                                 DomainObject<T> domainDelegate);

  /**
   * Creates a PDO for given delegates.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   * @return the created PDO
   */
  <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate,
                                                 DomainObject<T> domainDelegate);

  /**
   * Gets the class implementing the persistence layer.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the persistence class
   */
  <T extends PersistentDomainObject<T>> Class<PersistentObject<T>> getPersistenceClass(String className);

  /**
   * Gets the class implementing the domain layer.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the domain class
   */
  <T extends PersistentDomainObject<T>> Class<DomainObject<T>> getDomainClass(String className);

  /**
   * Creates the delegate implementing the persistence layer.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the persistent delegate
   */
  <T extends PersistentDomainObject<T>> PersistentObject<T> createPersistenceDelegate(String className);

  /**
   * Creates the delegate implementing the domain layer.
   *
   * @param <T> the PDO type
   * @param className the name of PDO-class, usually an interface
   * @return the domain delegate
   */
  <T extends PersistentDomainObject<T>> DomainObject<T> createDomainDelegate(String className);

}
