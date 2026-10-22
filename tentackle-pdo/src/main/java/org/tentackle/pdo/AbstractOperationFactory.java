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

import org.tentackle.reflect.ClassMapper;
import org.tentackle.session.Session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Base implementation of a {@link OperationFactory}.
 *
 * @author harald
 */
public abstract class AbstractOperationFactory implements OperationFactory {

  /**
   * Parent constructor.
   */
  public AbstractOperationFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the persistence class mapper associated with this factory.<br>
   * The classmapper usually maps interfaces (domain + persistence) to
   * the persistence implementation.
   *
   * @return the mapper for the persistence classes
   */
  public abstract ClassMapper getPersistenceMapper();


  /**
   * Gets the domain class mapper associated with this factory.<br>
   * The classmapper usually maps interfaces (domain + persistence) to
   * the domain implementation.
   *
   * @return the mapper for the domain classes
   */
  public abstract ClassMapper getDomainMapper();


  /**
   * Creates an operation invocation handler for given class.<br>
   * Override this method if you need an application-specific invocation handler.
   *
   * @param <T> the operation type
   * @param clazz the operation interface
   * @return the invocation handler
   * @throws ClassNotFoundException if clazz not found
   * @see OperationInvocationHandler
   */
  protected <T extends Operation<T>> OperationInvocationHandler<T> createInvocationHandler(Class<T> clazz)
            throws ClassNotFoundException {
    return new OperationInvocationHandler<>(getPersistenceMapper(), getDomainMapper(), clazz);
  }


  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, DomainContext context) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, context);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " in context " + context, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, Session session) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, session);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " for session " + session, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, PersistentOperation<T> persistenceDelegate) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, persistenceDelegate);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " for persistence delegate " + persistenceDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, DomainContext context, DomainOperation<T> domainDelegate) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, context, domainDelegate);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, Session session, DomainOperation<T> domainDelegate) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, session, domainDelegate);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance, persistenceDelegate, domainDelegate);
      return instance;
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz + " for delegates " +
                                    persistenceDelegate + " : " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(Class<T> clazz) {
    try {
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      handler.setupDelegates(instance);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation for class " + clazz, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(T op, String contextName) {
    try {
      Class<T> clazz = op.getEffectiveClass();
      OperationInvocationHandler<T> handler = createInvocationHandler(clazz);
      T instance = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz }, handler);
      DomainContext context = op.getPersistenceDelegate().getDomainContext();
      if (contextName != null && !context.isWithinContext(contextName)) {
        context = context.cloneKeepRoot(contextName);
      }
      handler.setupDelegates(instance, context);
      return instance;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException e) {
      throw new PdoRuntimeException("cannot create operation from template " + op, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, DomainContext context) {
    try {
      return createOperation((Class<T>) Class.forName(className), context);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " in context " + context, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, Session session) {
    try {
      return createOperation((Class<T>)  Class.forName(className), session);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " for session " + session, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, PersistentOperation<T> persistenceDelegate) {
    try {
      return createOperation((Class<T>) Class.forName(className), persistenceDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " for persistence delegate " + persistenceDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, DomainContext context, DomainOperation<T> domainDelegate) {
    try {
      return createOperation((Class<T>) Class.forName(className), context, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, Session session, DomainOperation<T> domainDelegate) {
    try {
      return createOperation((Class<T>) Class.forName(className), session, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " for domain delegate " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    try {
      return createOperation((Class<T>) Class.forName(className), persistenceDelegate, domainDelegate);
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className + " for delegates " +
                                    persistenceDelegate + " : " + domainDelegate, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Operation<T>> T createOperation(String className) {
    try {
      return createOperation((Class<T>) Class.forName(className));
    }
    catch (ClassNotFoundException e) {
      throw new PdoRuntimeException("cannot create operation for class " + className, e);
    }
  }



  // ----------------------- implements OperationFactory --------------------------------

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, DomainContext context) {
    return createOperation(clazz, context);
  }

  @Override
  public <T extends Operation<T>> T create(String className, DomainContext context) {
    return createOperation(className, context);
  }

  @Override
  public <T extends Operation<T>> T create(T op) {
    return createOperation(op, null);
  }

  @Override
  public <T extends Operation<T>> T create(T op, String contextName) {
    return createOperation(op, contextName);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, Session session) {
    return createOperation(clazz, session);
  }

  @Override
  public <T extends Operation<T>> T create(String className, Session session) {
    return createOperation(className, session);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, PersistentOperation<T> persistenceDelegate) {
    return createOperation(clazz, persistenceDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(String className, PersistentOperation<T> persistenceDelegate) {
    return createOperation(className, persistenceDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz) {
    return createOperation(clazz);
  }

  @Override
  public <T extends Operation<T>> T create(String className) {
    return createOperation(className);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, DomainContext context, DomainOperation<T> domainDelegate) {
    return createOperation(clazz, context, domainDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(String className, DomainContext context, DomainOperation<T> domainDelegate) {
    return createOperation(className, context, domainDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, Session session, DomainOperation<T> domainDelegate) {
    return createOperation(clazz, session, domainDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(String className, Session session, DomainOperation<T> domainDelegate) {
    return createOperation(className, session, domainDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(Class<T> clazz, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    return createOperation(clazz, persistenceDelegate, domainDelegate);
  }

  @Override
  public <T extends Operation<T>> T create(String className, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    return createOperation(className, persistenceDelegate, domainDelegate);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Operation<T>> Class<PersistentOperation<T>> getPersistenceClass(String className) {
    try {
      return (Class<PersistentOperation<T>>) getPersistenceMapper().mapLenient(className);
    }
    catch (ClassNotFoundException ex) {
      throw new PdoRuntimeException("could not create persistence class for " + className, ex);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Operation<T>> Class<DomainOperation<T>> getDomainClass(String className) {
    try {
      return (Class<DomainOperation<T>>) getDomainMapper().mapLenient(className);
    }
    catch (ClassNotFoundException ex) {
      throw new PdoRuntimeException("could not create domain class for " + className, ex);
    }
  }

  @Override
  public <T extends Operation<T>> PersistentOperation<T> createPersistenceDelegate(String className) {
    Class<PersistentOperation<T>> clazz = getPersistenceClass(className);
    try {
      return clazz.getConstructor().newInstance();
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new PdoRuntimeException("could not create persistence delegate for " + className, ex);
    }
  }

  @Override
  public <T extends Operation<T>> DomainOperation<T> createDomainDelegate(String className) {
    Class<DomainOperation<T>> clazz = getDomainClass(className);
    try {
      return clazz.getConstructor().newInstance();
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new PdoRuntimeException("could not create domain delegate for " + className, ex);
    }
  }

}
