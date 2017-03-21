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

import java.lang.reflect.Proxy;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.session.Session;

/**
 * Base implementation of a {@link PdoFactory}.
 *
 * @author harald
 */
public abstract class AbstractPdoFactory implements PdoFactory {


  /**
   * Gets the persistence class mapper associated to this factory.<br>
   * The classmapper usually maps interfaces (domain + persistence) to
   * the persistence implementation.
   *
   * @return the mapper for the persistence classes
   */
  public abstract ClassMapper getPersistenceMapper();


  /**
   * Gets the domain class mapper associated to this factory.<br>
   * The classmapper usually maps interfaces (domain + persistence) to
   * the domain implementation.
   *
   * @return the mapper for the domain classes
   */
  public abstract ClassMapper getDomainMapper();



  /**
   * Creates a PDO invocation handler for given class.<br>
   * Override this method if you need an application-specific invocation handler.
   *
   * @param <T> the PDO type
   * @param clazz the PDO interface
   * @return the invocation handler
   * @throws ClassNotFoundException if clazz not found
   * @see PdoInvocationHandler
   */
  protected <T extends PersistentDomainObject<T>> PdoInvocationHandler<T> createInvocationHandler(Class<T> clazz) throws ClassNotFoundException {
    return new PdoInvocationHandler<>(getPersistenceMapper(), getDomainMapper(), clazz);
  }



  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, DomainContext context) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, context);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " in context '" + context + "'", e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, Session session) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, session);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " for session " + session, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, PersistentObject<T> persistenceDelegate) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, persistenceDelegate);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " for persistence delegate " + persistenceDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, DomainContext context, DomainObject<T> domainDelegate) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, context, domainDelegate);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " for domain delegate " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, Session session, DomainObject<T> domainDelegate) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, session, domainDelegate);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " for domain delegate " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, persistenceDelegate, domainDelegate);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz + " for delegates " + persistenceDelegate + " : " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(Class<T> clazz) {
    try {
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance);
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO for " + clazz, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(T pdo) {
    try {
      Class<T> clazz = pdo.getEffectiveClass();
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, pdo.getPersistenceDelegate().getDomainContext());
      return instance;
    }
    catch (Exception e) {
      throw new PdoRuntimeException("cannot create PDO from template " + pdo, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, DomainContext context) {
    try {
      return createPdo((Class<T>)Class.forName(className), context);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " in context " + context, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, Session session) {
    try {
      return createPdo((Class<T>) Class.forName(className), session);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " for session " + session, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, PersistentObject<T> persistenceDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), persistenceDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " for persistence delegate " + persistenceDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, DomainContext context, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), context, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " for domain delegate " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, Session session, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), session, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " for domain delegate " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), persistenceDelegate, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className + " for domain delegate " + domainDelegate, e);
    }
  }


  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className) {
    try {
      return createPdo((Class<T>) Class.forName(className));
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create PDO for " + className, e);
    }
  }



  // ----------------------- implements PdoFactory --------------------------------

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context) {
    return createPdo(clazz, context);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, DomainContext context) {
    return createPdo(className, context);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(T pdo) {
    return createPdo(pdo);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, Session session) {
    return createPdo(clazz, session);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, Session session) {
    return createPdo(className, session);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate) {
    return createPdo(clazz, persistenceDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate) {
    return createPdo(className, persistenceDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz) {
    return createPdo(clazz);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className) {
    return createPdo(className);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, DomainContext context, DomainObject<T> domainDelegate) {
    return createPdo(clazz, context, domainDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, DomainContext context, DomainObject<T> domainDelegate) {
    return createPdo(className, context, domainDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, Session session, DomainObject<T> domainDelegate) {
    return createPdo(clazz, session, domainDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, Session session, DomainObject<T> domainDelegate) {
    return createPdo(className, session, domainDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(Class<T> clazz, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    return createPdo(clazz, persistenceDelegate, domainDelegate);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(String className, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    return createPdo(className, persistenceDelegate, domainDelegate);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> Class<? extends PersistentObject<T>> getPersistentClass(Class<T> clazz) {
    try {
      return (Class<? extends PersistentObject<T>>) getPersistenceMapper().map(clazz);
    }
    catch (ClassNotFoundException nfe) {
      throw new PdoRuntimeException("could not map " + clazz + " to persistence class", nfe);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> Class<? extends DomainObject<T>> getDomainClass(Class<T> clazz) {
    try {
      return (Class<? extends DomainObject<T>>) getDomainMapper().map(clazz);
    }
    catch (ClassNotFoundException nfe) {
      throw new PdoRuntimeException("could not map " + clazz + " to domain class", nfe);
    }
  }

}
