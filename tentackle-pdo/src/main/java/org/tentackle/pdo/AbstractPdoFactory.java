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

import org.tentackle.session.Session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Base implementation of a {@link PdoFactory}.
 *
 * @author harald
 */
public abstract class AbstractPdoFactory implements PdoFactory {

  private static final String CANNOT_CREATE_PDO_FOR = "cannot create PDO for ";

  /**
   * Parent constructor.
   */
  public AbstractPdoFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " in context '" + context + "'", e);
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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " for session " + session, e);
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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " for persistence delegate " + persistenceDelegate, e);
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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " for domain delegate " + domainDelegate, e);
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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " for domain delegate " + domainDelegate, e);
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
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz + " for delegates " + persistenceDelegate + " : " + domainDelegate, e);
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
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + clazz, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(T pdo, String contextName) {
    try {
      Class<T> clazz = pdo.getEffectiveClass();
      PdoInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      DomainContext context = pdo.getPersistenceDelegate().getDomainContext();
      if (contextName != null && !context.isWithinContext(contextName)) {
        context = context.cloneKeepRoot(contextName);
      }
      handler.setupDelegates(instance, context);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create PDO from template " + pdo, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, DomainContext context) {
    try {
      return createPdo((Class<T>)Class.forName(className), context);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " in context " + context, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, Session session) {
    try {
      return createPdo((Class<T>) Class.forName(className), session);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " for session " + session, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, PersistentObject<T> persistenceDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), persistenceDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " for persistence delegate " + persistenceDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, DomainContext context, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), context, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, Session session, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), session, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    try {
      return createPdo((Class<T>) Class.forName(className), persistenceDelegate, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className + " for delegates " + persistenceDelegate + " : " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PersistentDomainObject<T>> T createPdo(String className) {
    try {
      return createPdo((Class<T>) Class.forName(className));
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException(CANNOT_CREATE_PDO_FOR + className, e);
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
    return createPdo(pdo, null);
  }

  @Override
  public <T extends PersistentDomainObject<T>> T create(T pdo, String contextName) {
    return createPdo(pdo, contextName);
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
  public <T extends PersistentDomainObject<T>> Class<PersistentObject<T>> getPersistenceClass(String className) {
    try {
      return (Class<PersistentObject<T>>) getPersistenceMapper().mapLenient(className);
    }
    catch (ClassNotFoundException ex) {
      throw new PdoRuntimeException("could not create persistence class for " + className, ex);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> Class<DomainObject<T>> getDomainClass(String className) {
    try {
      return (Class<DomainObject<T>>) getDomainMapper().mapLenient(className);
    }
    catch (ClassNotFoundException ex) {
      throw new PdoRuntimeException("could not create domain class for " + className, ex);
    }
  }

  @Override
  public <T extends PersistentDomainObject<T>> PersistentObject<T> createPersistenceDelegate(String className) {
    Class<PersistentObject<T>> clazz = getPersistenceClass(className);
    try {
      return clazz.getConstructor().newInstance();
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new PdoRuntimeException("could not create persistence delegate for " + clazz, ex);
    }
  }

  @Override
  public <T extends PersistentDomainObject<T>> DomainObject<T> createDomainDelegate(String className) {
    Class<DomainObject<T>> clazz = getDomainClass(className);
    try {
      return clazz.getConstructor().newInstance();
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new PdoRuntimeException("could not create domain delegate for " + className, ex);
    }
  }

}
