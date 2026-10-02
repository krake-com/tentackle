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
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.InterceptableMethod;
import org.tentackle.reflect.InterceptableMethodInvoker;
import org.tentackle.reflect.Mixin;
import org.tentackle.session.Session;
import org.tentackle.trip.TripReferencable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Invocation handler for operations.<br>
 * Each instance of an operation gets its own handler.
 *
 * @param <T> the operation type
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class OperationInvocationHandler<T extends Operation<T>> implements InvocationHandler {

  /**
   * The method invoker.<br>
   * Performs the method invocations and provides logging and statistics.
   */
  public static final InterceptableMethodInvoker INVOKER = new InterceptableMethodInvoker("OPN");


  // preloaded methods of java.lang.Object
  private static final Method HASHCODE_METHOD;
  private static final Method EQUALS_METHOD;
  private static final Method TOSTRING_METHOD;
  private static final Method CLONE_METHOD;

  // preloaded methods of java.lang.Comparable
  private static final Method COMPARETO_METHOD;

  // preloaded methods of Operation
  private static final Method GETDOMAINDELEGATE_METHOD;
  private static final Method GETPERSISTENCEDELEGATE_METHOD;

  // preloaded methods of OperationProvider
  private static final Method GETOPERATION_METHOD;
  private static final Method OP_METHOD;

  // preloaded methods of EffectiveClassProvider
  private static final Method GETEFFECTIVECLASS_METHOD;
  private static final Method GETEFFECTIVESUPERCLASSES_METHOD;

  static {
    try {
      HASHCODE_METHOD = Object.class.getDeclaredMethod("hashCode");
      EQUALS_METHOD = Object.class.getDeclaredMethod("equals", Object.class);
      TOSTRING_METHOD = Object.class.getDeclaredMethod("toString");
      CLONE_METHOD = Object.class.getDeclaredMethod("clone");
      COMPARETO_METHOD = Comparable.class.getDeclaredMethod("compareTo", Object.class);
      GETDOMAINDELEGATE_METHOD = Operation.class.getDeclaredMethod("getDomainDelegate");
      GETPERSISTENCEDELEGATE_METHOD = Operation.class.getDeclaredMethod("getPersistenceDelegate");
      GETOPERATION_METHOD = OperationProvider.class.getDeclaredMethod("getOperation");
      OP_METHOD = OperationProvider.class.getDeclaredMethod("op");
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveClass");
      GETEFFECTIVESUPERCLASSES_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveSuperClasses");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }


  // the following methods are an OperationInvocation, mapped by OperationMethodCache

  @SuppressWarnings("rawtypes")
  private static Object invokePersistence(OperationInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.persistenceMixin.getDelegate(), method, args);
  }

  @SuppressWarnings("rawtypes")
  private static Object invokeDomain(OperationInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.domainMixin.getDelegate(), method, args);
  }


  private final Class<T> clazz;                                 // the operation class
  private Mixin<T,PersistentOperation<T>> persistenceMixin;     // the persistence mixin
  private Mixin<T,DomainOperation<T>> domainMixin;              // the domain mixin

  private transient OperationMethodCache<T> methodCache;        // the method cache (not serialized!)

  /**
   * Creates an invocation handler.
   *
   * @param persistenceMapper the persistence operation class mapper
   * @param domainMapper the domain operation class mapper
   * @param clazz the operation declaring interface
   *
   * @throws ClassNotFoundException if no implementations found
   */
  public OperationInvocationHandler(ClassMapper persistenceMapper, ClassMapper domainMapper, Class<T> clazz)
         throws ClassNotFoundException {

    this.clazz = clazz;

    try {
      persistenceMixin = new Mixin<>(persistenceMapper, clazz, PersistentOperation.class);
    }
    catch (ClassNotFoundException e) {
      // no persistence implementation is ok
    }

    try {
      domainMixin = new Mixin<>(domainMapper, clazz, DomainOperation.class);
    }
    catch (ClassNotFoundException e) {
      // no domain implementation is ok
    }

    if (persistenceMixin == null && domainMixin == null) {
      throw new ClassNotFoundException("neither domain- nor persistence mapping found for '" + clazz.getName() + "'");
    }
    else if (persistenceMixin == null) {
      // create dummy persistence mixin
      persistenceMixin = new Mixin<>(clazz, PersistentOperation.class);
    }
    else if (domainMixin == null) {
      // create dummy domain mixin
      domainMixin = new Mixin<>(clazz, DomainOperation.class);
    }
  }

  /**
   * No-args constructor provided for TRIP.
   */
  public OperationInvocationHandler() {
    this.clazz = null;
    this.persistenceMixin = null;
    this.domainMixin = null;
  }


  /**
   * Creates the delegates for the given proxy instance and the domain context.
   *
   * @param operation the dynamic proxy instance
   * @param context the domain context
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, DomainContext context)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                    new Object[] { operation, context });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }


  /**
   * Creates the delegates for the given proxy instance and the session.
   * <p>
   * Note: The application must set the domain context!
   *
   * @param operation the dynamic proxy instance
   * @param session the session
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, Session session)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                    new Object[] { operation, session });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }


  /**
   * Creates the delegates for the given proxy instance and a persistence delegate.
   * <p>
   * Note: The application must set the domain context!
   *
   * @param operation the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, PersistentOperation<T> persistenceDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    PersistenceDelegateLinker.getInstance().linkPersistentOperation(operation, persistenceDelegate);
    persistenceMixin.setDelegate(persistenceDelegate);

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }


  /**
   * Creates the delegates for the given proxy instance, a context and domain delegate.
   *
   * @param operation the dynamic proxy instance
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, DomainContext context, DomainOperation<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                    new Object[] { operation, context });

    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance, a session and domain delegate.
   *
   * @param operation the dynamic proxy instance
   * @param session the session
   * @param domainDelegate the domain delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, Session session, DomainOperation<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                    new Object[] { operation, session });

    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Sets the delegates for the given proxy instance.
   *
   * @param operation the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   */
  public void setupDelegates(T operation, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    PersistenceDelegateLinker.getInstance().linkPersistentOperation(operation, persistenceDelegate);
    persistenceMixin.setDelegate(persistenceDelegate);
    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance without any domain context or session.
   *
   * @param operation the dynamic proxy instance
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class },
                                    new Object[] { operation });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }



  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // a few methods should never be caught by an interceptor and are always executed directly
    Class<?> declaringClass = method.getDeclaringClass();

    if (declaringClass == Object.class) {
      if (method.equals(HASHCODE_METHOD)) {
        return persistenceMixin.isDummy() ? domainMixin.getDelegate().hashCode() : persistenceMixin.getDelegate().hashCode();
      }
      else if (method.equals(EQUALS_METHOD)) {
        Object obj = args[0];
        if (obj instanceof Operation) {
          if (persistenceMixin.isDummy()) {   // persistence delegate takes precedence over domain for equals
            return domainMixin.getDelegate().equals(((Operation) obj).getDomainDelegate());
          }
          else {
            // at least one of the domain- or persistence delegate is present
            return persistenceMixin.getDelegate().equals(((Operation) obj).getPersistenceDelegate());
          }
        }
        else  {
          return false;
        }
      }
      else if (method.equals(TOSTRING_METHOD)) {
        // domain delegate takes precedence over persistence for toString
        return domainMixin.isDummy() ? persistenceMixin.getDelegate().toString() : domainMixin.getDelegate().toString();
      }
      else if (method.equals(CLONE_METHOD)) {
        throw new CloneNotSupportedException("operations are not cloneable");
      }
    }
    else if (declaringClass == Comparable.class) {
      if (method.equals(COMPARETO_METHOD)) {
        Object obj = args[0];
        if (obj instanceof Operation) {
          // persistence delegate takes precedence over domain for compareTo
          if (persistenceMixin.isDummy()) {
            return ((Comparable) domainMixin.getDelegate()).compareTo(((Operation) obj).getDomainDelegate());
          }
          else {
            return ((Comparable) persistenceMixin.getDelegate()).compareTo(((Operation) obj).getPersistenceDelegate());
          }
        }
        else  {
          return Integer.MAX_VALUE;
        }
      }
    }
    else if (declaringClass == Operation.class) {
      if (method.equals(GETDOMAINDELEGATE_METHOD)) {
        return domainMixin.getDelegate();
      }
      else if (method.equals(GETPERSISTENCEDELEGATE_METHOD)) {
        return persistenceMixin.getDelegate();
      }
    }
    else if (declaringClass == EffectiveClassProvider.class) {
      if (method.equals(GETEFFECTIVECLASS_METHOD)) {
        return clazz;
      }
      else if (method.equals(GETEFFECTIVESUPERCLASSES_METHOD)) {
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> ifClass: clazz.getInterfaces()) {
          if (Operation.class.isAssignableFrom(ifClass)) {
            list.add(ifClass);
          }
        }
        return list;
      }
    }
    else if (declaringClass == OperationProvider.class &&
             !method.equals(OP_METHOD)) {    // op() should be executed via mixin below, if overridden
      if (method.equals(GETOPERATION_METHOD)) {
        return proxy;
      }
    }

    if (methodCache == null) {
      if (persistenceMixin.isDummy()) {
        // use the global cache map
        methodCache = OperationMethodCache.getCache(clazz);
      }
      else {  // faster, but needs persistence delegate
        // all persistence delegates must implement OperationMethodCacheProvider, but we don't
        // want PersistentOperation to extend that because that's an implementation detail
        methodCache = ((OperationMethodCacheProvider<T>) persistenceMixin.getDelegate()).getOperationMethodCache();
      }
    }

    return methodCache.invoke(this, method, args);
  }


  /**
   * Determines the invocation method to be cached by the {@link OperationMethodCache}.
   *
   * @param method the interface method
   * @return the info necessary to set up the cache entry
   */
  public OperationMethodCacheInfo determineInvocation(Method method) {

    OperationInvocation invocation;
    Class<? extends Interceptable> delegateClass;

    Class<?> declaringClass = method.getDeclaringClass();

    if (persistenceMixin.matches(declaringClass)) {
      invocation = OperationInvocationHandler::invokePersistence;
      delegateClass = persistenceMixin.getDelegate().getClass();
    }
    else if (domainMixin.matches(declaringClass)) {
      invocation = OperationInvocationHandler::invokeDomain;
      delegateClass = domainMixin.getDelegate().getClass();
    }
    else {
      throw new PdoRuntimeException("no delegate found for " + method);
    }

    return new OperationMethodCacheInfo(invocation, delegateClass);
  }

}
