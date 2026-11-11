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

import org.tentackle.misc.Identifiable;
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.InterceptableMethod;
import org.tentackle.reflect.InterceptableMethodInvoker;
import org.tentackle.reflect.Mixin;
import org.tentackle.session.Session;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Invocation handler for PDOs.<br>
 * Each instance of a PDO gets its own handler.
 *
 * @param <T> the PDO type
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class PdoInvocationHandler<T extends PersistentDomainObject<T>> implements InvocationHandler, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The method invoker.<br>
   * Performs the method invocations and provides logging and statistics.
   */
  public static final InterceptableMethodInvoker INVOKER = new InterceptableMethodInvoker("PDO");


  /**
   * The effective super classes per PDO class, computed once.
   */
  private static final ClassValue<List<Class<?>>> EFFECTIVE_SUPER_CLASSES = new ClassValue<>() {
    @Override
    protected List<Class<?>> computeValue(Class<?> type) {
      List<Class<?>> list = new ArrayList<>();
      for (Class<?> ifClass: type.getInterfaces()) {
        if (PersistentDomainObject.class.isAssignableFrom(ifClass)) {
          list.add(ifClass);
        }
      }
      return List.copyOf(list);
    }
  };


  // the following methods are a PdoInvocation, mapped by PdoMethodCache

  @SuppressWarnings("rawtypes")
  private static Object invokePersistence(PdoInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.persistenceDelegate, method, args);
  }

  @SuppressWarnings("rawtypes")
  private static Object invokeDomain(PdoInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.domainDelegate, method, args);
  }


  @CanonicalField(ordinal = 1)
  private final Class<T> clazz;                                     // the pdo class
  // The delegates are not canonical: they back-reference the proxy, which cannot be
  // created before the handler exists. Hence, they are set by TRIP after construction.
  private PersistentObject<T> persistenceDelegate;                  // the persistence delegate
  private DomainObject<T> domainDelegate;                           // the domain delegate

  // the mixins are shared by all PDOs of the same class (not serialized!)
  private transient Mixin<T,PersistentObject<T>> persistenceMixin;  // the persistence mixin
  private transient Mixin<T,DomainObject<T>> domainMixin;           // the domain mixin

  private transient PdoMethodCache<T> methodCache;                  // the method cache (not serialized!)


  /**
   * Creates an invocation handler.
   *
   * @param persistenceMapper the class mapper for the persistence implementations
   * @param domainMapper the class mapper for the domain implementations
   * @param clazz the PDO declaring interface
   *
   * @throws ClassNotFoundException if no implementation found
   */
  public PdoInvocationHandler(ClassMapper persistenceMapper, ClassMapper domainMapper, Class<T> clazz)
         throws ClassNotFoundException {
    this.clazz = clazz;
    persistenceMixin = Mixin.get(persistenceMapper, clazz, PersistentObject.class);
    domainMixin = Mixin.get(domainMapper, clazz, DomainObject.class);
  }

  /**
   * Constructor provided for TRIP.<br>
   * The delegates are deserialized after the handler is created, because they
   * back-reference the proxy, which needs the handler to exist (see {@code ProxyTripType}).
   * The mixins are derived from the delegates when needed.
   *
   * @param clazz the PDO declaring interface
   */
  @CanonicalConstructor
  public PdoInvocationHandler(Class<T> clazz) {
    this.clazz = clazz;
  }


  /**
   * Creates the delegates for the given proxy instance and the domain context.
   *
   * @param pdo the dynamic proxy instance
   * @param context the domain context
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, DomainContext context)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                                          new Object[] { pdo, context });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                                new Object[] { pdo });
  }


  /**
   * Creates the delegates for the given proxy instance and the session.
   * <p>
   * Note: The application must set the domain context!
   *
   * @param pdo the dynamic proxy instance
   * @param session the session
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, Session session)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                                          new Object[] { pdo, session });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                                new Object[] { pdo });
  }


  /**
   * Creates the delegates for the given proxy instance and a persistence delegate.
   * <p>
   * Note: The application must set the domain context!
   *
   * @param pdo the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, PersistentObject<T> persistenceDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    PersistenceDelegateLinker.getInstance().linkPersistentObject(pdo, persistenceDelegate);
    this.persistenceDelegate = persistenceDelegate;

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                                new Object[] { pdo });
  }


  /**
   * Creates the delegates for the given proxy instance, a context and domain delegate.
   *
   * @param pdo the dynamic proxy instance
   * @param context the domain context
   * @param domainDelegate the domain delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, DomainContext context, DomainObject<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                                          new Object[] { pdo, context });

    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    this.domainDelegate = domainDelegate;
  }


  /**
   * Creates the delegates for the given proxy instance, a session, and domain delegate.
   *
   * @param pdo the dynamic proxy instance
   * @param session the session
   * @param domainDelegate the domain delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, Session session, DomainObject<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                                          new Object[] { pdo, session });

    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    this.domainDelegate = domainDelegate;
  }


  /**
   * Sets the delegates for the given proxy instance.
   *
   * @param pdo the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   */
  public void setupDelegates(T pdo, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    PersistenceDelegateLinker.getInstance().linkPersistentObject(pdo, persistenceDelegate);
    this.persistenceDelegate = persistenceDelegate;
    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    this.domainDelegate = domainDelegate;
  }


  /**
   * Creates the delegates for the given proxy instance without any domain context or session.
   *
   * @param pdo the dynamic proxy instance
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    persistenceDelegate = persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                                          new Object[] { pdo });

    domainDelegate = domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                                new Object[] { pdo });
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
        if (obj instanceof PersistentDomainObject) {
          return persistenceDelegate.equals(((PersistentDomainObject) obj).getPersistenceDelegate());
        }
        else  {
          return false;
        }
      }
      else if (method.getReturnType() == int.class) {
        return persistenceDelegate.hashCode();
      }
      else {
        return domainDelegate.toString();
      }
    }
    else if (declaringClass == Comparable.class) {
      // compareTo is the only method of Comparable
      Object obj = args[0];
      if (obj instanceof PersistentDomainObject) {
        return ((Comparable) persistenceDelegate).compareTo(((PersistentDomainObject) obj).getPersistenceDelegate());
      }
      else  {
        return Integer.MAX_VALUE;
      }
    }
    else if (declaringClass == PersistentDomainObject.class) {
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
    else if (declaringClass == Identifiable.class) {
      if (method.getName().equals("toGenericString")) {
        T pdo = (T) proxy;
        return clazz.getName() + '[' + pdo.getId() + '/' + pdo.getSerial() + ']';
      }
    }
    else if (declaringClass == PdoProvider.class) {
      // on() and on(String) are executed via the method cache below!
      if (method.getName().equals("getPdo")) {
        return proxy;
      }
    }

    if (methodCache == null) {
      // all persistence delegates must implement PdoMethodCacheProvider, but we don't
      // want PersistentObject to extend that because that's an implementation detail
      methodCache = ((PdoMethodCacheProvider<T>) persistenceDelegate).getPdoMethodCache();
    }

    return methodCache.invoke(this, method, args);
  }


  /**
   * Determines the invocation method to be cached by the {@link PdoMethodCache}.
   *
   * @param method the interface method
   * @return the info necessary to set up the cache entry
   */
  public PdoMethodCacheInfo determineInvocation(Method method) {

    PdoInvocation invocation;
    Class<? extends Interceptable> delegateClass;

    Class<?> declaringClass = method.getDeclaringClass();

    if (getPersistenceMixin().matches(declaringClass)) {
      invocation = PdoInvocationHandler::invokePersistence;
      delegateClass = persistenceDelegate.getClass();
    }
    else if (getDomainMixin().matches(declaringClass)) {
      invocation = PdoInvocationHandler::invokeDomain;
      delegateClass = domainDelegate.getClass();
    }
    else {
      throw new PdoRuntimeException("no delegate found for " + method);
    }

    return new PdoMethodCacheInfo(invocation, delegateClass);
  }



  /**
   * Gets the persistence mixin.<br>
   * After deserialization, the mixin is derived from the delegate.
   *
   * @return the mixin
   */
  private Mixin<T,PersistentObject<T>> getPersistenceMixin() {
    if (persistenceMixin == null) {
      persistenceMixin = Mixin.forImplementation(clazz, persistenceDelegate.getClass(), PersistentObject.class);
    }
    return persistenceMixin;
  }

  /**
   * Gets the domain mixin.<br>
   * After deserialization, the mixin is derived from the delegate.
   *
   * @return the mixin
   */
  private Mixin<T,DomainObject<T>> getDomainMixin() {
    if (domainMixin == null) {
      domainMixin = Mixin.forImplementation(clazz, domainDelegate.getClass(), DomainObject.class);
    }
    return domainMixin;
  }

}
