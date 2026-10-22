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


  // preloaded methods of java.lang.Object
  private static final Method HASHCODE_METHOD;
  private static final Method EQUALS_METHOD;
  private static final Method TOSTRING_METHOD;
  private static final Method CLONE_METHOD;

  // preloaded methods of java.lang.Comparable
  private static final Method COMPARETO_METHOD;

  // preloaded methods of PersistentDomainObject
  private static final Method GETDOMAINDELEGATE_METHOD;
  private static final Method GETPERSISTENCEDELEGATE_METHOD;

  // preloaded methods of Identifiable and PdoProvider
  private static final Method TOGENERICSTRING_METHOD;
  private static final Method GETPDO_METHOD;
  private static final Method ON_METHOD;

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
      GETDOMAINDELEGATE_METHOD = PersistentDomainObject.class.getDeclaredMethod("getDomainDelegate");
      GETPERSISTENCEDELEGATE_METHOD = PersistentDomainObject.class.getDeclaredMethod("getPersistenceDelegate");
      TOGENERICSTRING_METHOD = Identifiable.class.getDeclaredMethod("toGenericString");
      GETPDO_METHOD = PdoProvider.class.getDeclaredMethod("getPdo");
      ON_METHOD = PdoProvider.class.getDeclaredMethod("on");
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveClass");
      GETEFFECTIVESUPERCLASSES_METHOD = EffectiveClassProvider.class.getDeclaredMethod("getEffectiveSuperClasses");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }


  // the following methods are a PdoInvocation, mapped by PdoMethodCache

  @SuppressWarnings("rawtypes")
  private static Object invokePersistence(PdoInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.persistenceMixin.getDelegate(), method, args);
  }

  @SuppressWarnings("rawtypes")
  private static Object invokeDomain(PdoInvocationHandler invocationHandler, InterceptableMethod method, Object[] args) throws Throwable {
    return INVOKER.invoke(invocationHandler.domainMixin.getDelegate(), method, args);
  }


  private final Class<T> clazz;                                   // the pdo class
  private final Mixin<T,PersistentObject<T>> persistenceMixin;    // the persistence mixin
  private final Mixin<T,DomainObject<T>> domainMixin;             // the domain mixin

  private transient PdoMethodCache<T> methodCache;                // the method cache (not serialized!)


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
    persistenceMixin = new Mixin<>(persistenceMapper, clazz, PersistentObject.class);
    domainMixin = new Mixin<>(domainMapper, clazz, DomainObject.class);
  }

  /**
   * No-args constructor provided for TRIP.
   */
  public PdoInvocationHandler() {
    this.clazz = null;
    this.persistenceMixin = null;
    this.domainMixin = null;
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

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                    new Object[] { pdo, context });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
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

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                    new Object[] { pdo, session });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
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
    persistenceMixin.setDelegate(persistenceDelegate);

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
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

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                    new Object[] { pdo, context });

    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance, a session and domain delegate.
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

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                    new Object[] { pdo, session });

    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
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
    persistenceMixin.setDelegate(persistenceDelegate);
    DomainDelegateLinker.getInstance().linkDomainObject(pdo, domainDelegate);
    domainMixin.setDelegate(domainDelegate);
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

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                    new Object[] { pdo });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                               new Object[] { pdo });
  }


  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // a few methods should never be caught by an interceptor and are always executed directly
    Class<?> declaringClass = method.getDeclaringClass();

    if (declaringClass == Object.class) {
      if (method.equals(HASHCODE_METHOD)) {
        return persistenceMixin.getDelegate().hashCode();
      }
      else if (method.equals(EQUALS_METHOD)) {
        Object obj = args[0];
        if (obj instanceof PersistentDomainObject) {
          return persistenceMixin.getDelegate().equals(((PersistentDomainObject) obj).getPersistenceDelegate());
        }
        else  {
          return false;
        }
      }
      else if (method.equals(TOSTRING_METHOD)) {
        return domainMixin.getDelegate().toString();
      }
      else if (method.equals(CLONE_METHOD)) {
        throw new CloneNotSupportedException("PDOs are not cloneable, use copy() instead");
      }
    }
    else if (declaringClass == Comparable.class) {
      if (method.equals(COMPARETO_METHOD)) {
        Object obj = args[0];
        if (obj instanceof PersistentDomainObject) {
          return ((Comparable) persistenceMixin.getDelegate()).compareTo(((PersistentDomainObject) obj).getPersistenceDelegate());
        }
        else  {
          return Integer.MAX_VALUE;
        }
      }
    }
    else if (declaringClass == PersistentDomainObject.class) {
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
          if (PersistentDomainObject.class.isAssignableFrom(ifClass)) {
            list.add(ifClass);
          }
        }
        return list;
      }
    }
    else if (declaringClass == Identifiable.class) {
      if (method.equals(TOGENERICSTRING_METHOD)) {
        T pdo = (T) proxy;
        return clazz.getName() + '[' + pdo.getId() + '/' + pdo.getSerial() + ']';
      }
    }
    else if (declaringClass == PdoProvider.class &&
             !method.equals(ON_METHOD)) {    // on() should be executed via mixin below!
      if (method.equals(GETPDO_METHOD)) {
        return proxy;
      }
    }

    if (methodCache == null) {
      // all persistence delegates must implement PdoMethodCacheProvider, but we don't
      // want PersistentObject to extend that because that's an implementation detail
      methodCache = ((PdoMethodCacheProvider<T>) persistenceMixin.getDelegate()).getPdoMethodCache();
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

    if (persistenceMixin.matches(declaringClass)) {
      invocation = PdoInvocationHandler::invokePersistence;
      delegateClass = persistenceMixin.getDelegate().getClass();
    }
    else if (domainMixin.matches(declaringClass)) {
      invocation = PdoInvocationHandler::invokeDomain;
      delegateClass = domainMixin.getDelegate().getClass();
    }
    else {
      throw new PdoRuntimeException("no delegate found for " + method);
    }

    return new PdoMethodCacheInfo(invocation, delegateClass);
  }

}
