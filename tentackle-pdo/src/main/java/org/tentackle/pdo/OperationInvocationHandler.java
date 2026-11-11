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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.DummyDelegate;
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


  /**
   * The effective super classes per operation class, computed once.
   */
  private static final ClassValue<List<Class<?>>> EFFECTIVE_SUPER_CLASSES = new ClassValue<>() {
    @Override
    protected List<Class<?>> computeValue(Class<?> type) {
      List<Class<?>> list = new ArrayList<>();
      for (Class<?> ifClass: type.getInterfaces()) {
        if (Operation.class.isAssignableFrom(ifClass)) {
          list.add(ifClass);
        }
      }
      return List.copyOf(list);
    }
  };


  // the following methods are an OperationInvocation, mapped by OperationMethodCache

  @SuppressWarnings("rawtypes")
  private static Object invokePersistence(OperationInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.persistenceDelegate, method, args);
  }

  @SuppressWarnings("rawtypes")
  private static Object invokeDomain(OperationInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.domainDelegate, method, args);
  }


  @CanonicalField(ordinal = 1)
  private final Class<T> clazz;                                       // the operation class
  // The delegates are not canonical: they back-reference the proxy, which cannot be
  // created before the handler exists. Hence, they are set by TRIP after construction.
  private PersistentOperation<T> persistenceDelegate;                 // the persistence delegate, null if dummy
  private DomainOperation<T> domainDelegate;                          // the domain delegate, null if dummy

  // the mixins are shared by all operations of the same class (not serialized!)
  private transient Mixin<T,PersistentOperation<T>> persistenceMixin; // the persistence mixin
  private transient Mixin<T,DomainOperation<T>> domainMixin;          // the domain mixin

  private transient OperationMethodCache<T> methodCache;              // the method cache (not serialized!)


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
      persistenceMixin = Mixin.get(persistenceMapper, clazz, PersistentOperation.class);
    }
    catch (ClassNotFoundException e) {
      // no persistence implementation is ok
    }

    try {
      domainMixin = Mixin.get(domainMapper, clazz, DomainOperation.class);
    }
    catch (ClassNotFoundException e) {
      // no domain implementation is ok
    }

    if (persistenceMixin == null && domainMixin == null) {
      throw new ClassNotFoundException("neither domain- nor persistence mapping found for '" + clazz.getName() + "'");
    }
    else if (persistenceMixin == null) {
      // create dummy persistence mixin
      persistenceMixin = Mixin.forImplementation(clazz, null, PersistentOperation.class);
    }
    else if (domainMixin == null) {
      // create dummy domain mixin
      domainMixin = Mixin.forImplementation(clazz, null, DomainOperation.class);
    }
  }

  /**
   * Constructor provided for TRIP.<br>
   * The delegates are deserialized after the handler is created, because they
   * back-reference the proxy, which needs the handler to exist (see {@code ProxyTripType}).
   * The mixins are derived from the delegates when needed.
   *
   * @param clazz the operation declaring interface
   */
  @CanonicalConstructor
  public OperationInvocationHandler(Class<T> clazz) {
    this.clazz = clazz;
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

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                                          new Object[] { operation, context });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { Operation.class },
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

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                                          new Object[] { operation, session });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { Operation.class },
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
    this.persistenceDelegate = persistenceDelegate;

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { Operation.class },
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

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                                          new Object[] { operation, context });

    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    this.domainDelegate = domainDelegate;
  }


  /**
   * Creates the delegates for the given proxy instance, a session, and domain delegate.
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

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                                          new Object[] { operation, session });

    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    this.domainDelegate = domainDelegate;
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
    this.persistenceDelegate = persistenceDelegate;
    DomainDelegateLinker.getInstance().linkDomainOperation(operation, domainDelegate);
    this.domainDelegate = domainDelegate;
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

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { Operation.class },
                                                          new Object[] { operation });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { Operation.class },
                                                new Object[] { operation });
  }



  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // a few methods should never be caught by an interceptor and are always executed directly.
    // The methods are identified by their declaring class and name rather than Method.equals,
    // which is safe because none of those methods is overloaded.
    Class<?> declaringClass = method.getDeclaringClass();

    if (declaringClass == Object.class) {
      // the proxy dispatches only hashCode, equals and toString of Object, see java.lang.reflect.Proxy
      if (args != null) {   // equals(Object) is the only one with an argument
        Object obj = args[0];
        if (obj instanceof Operation) {
          if (isPersistenceDummy()) {   // persistence delegate takes precedence over domain for equals
            return domainDelegate.equals(((Operation) obj).getDomainDelegate());
          }
          else {
            // at least one of the domain- or persistence delegate is present
            return persistenceDelegate.equals(((Operation) obj).getPersistenceDelegate());
          }
        }
        else  {
          return false;
        }
      }
      else if (method.getReturnType() == int.class) {
        return isPersistenceDummy() ? domainDelegate.hashCode() : persistenceDelegate.hashCode();
      }
      else {
        // domain delegate takes precedence over persistence for toString
        return isDomainDummy() ? persistenceDelegate.toString() : domainDelegate.toString();
      }
    }
    else if (declaringClass == Comparable.class) {
      // compareTo is the only method of Comparable
      Object obj = args[0];
      if (obj instanceof Operation) {
        // persistence delegate takes precedence over domain for compareTo
        if (isPersistenceDummy()) {
          return ((Comparable) domainDelegate).compareTo(((Operation) obj).getDomainDelegate());
        }
        else {
          return ((Comparable) persistenceDelegate).compareTo(((Operation) obj).getPersistenceDelegate());
        }
      }
      else  {
        return Integer.MAX_VALUE;
      }
    }
    else if (declaringClass == Operation.class) {
      switch (method.getName()) {
        case "getDomainDelegate" -> {
          return domainDelegate;
        }
        case "getPersistenceDelegate" -> {
          return persistenceDelegate;
        }
        default -> {}
      }
    }
    else if (declaringClass == EffectiveClassProvider.class) {
      switch (method.getName()) {
        case "getEffectiveClass" -> {
          return clazz;
        }
        case "getEffectiveSuperClasses" -> {
          return EFFECTIVE_SUPER_CLASSES.get(clazz);
        }
        default -> {}
      }
    }
    else if (declaringClass == OperationProvider.class) {
      // op() and op(String) should be executed via the method cache below, if overridden
      if (method.getName().equals("getOperation")) {
        return proxy;
      }
    }

    if (methodCache == null) {
      if (isPersistenceDummy()) {
        // use the global cache map
        methodCache = OperationMethodCache.getCache(clazz);
      }
      else {  // faster, but needs persistence delegate
        // all persistence delegates must implement OperationMethodCacheProvider, but we don't
        // want PersistentOperation to extend that because that's an implementation detail
        methodCache = ((OperationMethodCacheProvider<T>) persistenceDelegate).getOperationMethodCache();
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

    if (getPersistenceMixin().matches(declaringClass)) {
      invocation = OperationInvocationHandler::invokePersistence;
      delegateClass = persistenceDelegate.getClass();
    }
    else if (getDomainMixin().matches(declaringClass)) {
      invocation = OperationInvocationHandler::invokeDomain;
      delegateClass = domainDelegate.getClass();
    }
    else {
      throw new PdoRuntimeException("no delegate found for " + method);
    }

    return new OperationMethodCacheInfo(invocation, delegateClass);
  }



  /**
   * Returns whether the persistence side is a dummy.
   *
   * @return true if no persistence delegate or a {@link DummyDelegate}
   */
  private boolean isPersistenceDummy() {
    return persistenceDelegate == null || persistenceDelegate instanceof DummyDelegate;
  }

  /**
   * Returns whether the domain side is a dummy.
   *
   * @return true if no domain delegate or a {@link DummyDelegate}
   */
  private boolean isDomainDummy() {
    return domainDelegate == null || domainDelegate instanceof DummyDelegate;
  }

  /**
   * Gets the persistence mixin.<br>
   * After deserialization, the mixin is derived from the delegate.
   *
   * @return the mixin
   */
  private Mixin<T,PersistentOperation<T>> getPersistenceMixin() {
    if (persistenceMixin == null) {
      persistenceMixin = Mixin.forImplementation(clazz, persistenceDelegate == null ? null : persistenceDelegate.getClass(),
                                                 PersistentOperation.class);
    }
    return persistenceMixin;
  }

  /**
   * Gets the domain mixin.<br>
   * After deserialization, the mixin is derived from the delegate.
   *
   * @return the mixin
   */
  private Mixin<T,DomainOperation<T>> getDomainMixin() {
    if (domainMixin == null) {
      domainMixin = Mixin.forImplementation(clazz, domainDelegate == null ? null : domainDelegate.getClass(),
                                            DomainOperation.class);
    }
    return domainMixin;
  }

}
