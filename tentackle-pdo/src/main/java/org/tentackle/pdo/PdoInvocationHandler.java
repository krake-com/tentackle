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

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.tentackle.misc.Identifiable;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.InterceptableMethodCache;
import org.tentackle.reflect.Mixin;
import org.tentackle.session.Session;

/**
 * Invocation handler for PDOs.<br>
 * Each instance of a PDO gets its own handler.
 *
 * @param <T> the PDO type
 * @author harald
 */
public class PdoInvocationHandler<T extends PersistentDomainObject<T>> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * The method cache.<br>
   * Speeds up method invocations and provides logging and statistics.
   */
  public static final InterceptableMethodCache METHOD_CACHE = new InterceptableMethodCache("PDO");


  // preloaded methods of java.lang.Object
  private static final Method HASHCODE_METHOD;
  private static final Method EQUALS_METHOD;
  private static final Method TOSTRING_METHOD;

  // preloaded methods of java.lang.Comparable
  private static final Method COMPARETO_METHOD;

  // preloaded methods of PersistentDomainObject
  private static final Method CLONE_METHOD;
  private static final Method GETDOMAINOBJECT_METHOD;
  private static final Method GETPERSISTENTOBJECT_METHOD;

  // preloaded methods of PdoHolder
  private static final Method TOGENERICSTRING_METHOD;
  private static final Method GETPDO_METHOD;
  private static final Method SETPDO_METHOD;
  private static final Method ON_METHOD;

  // preloaded methods of EffectiveClassProvider
  private static final Method GETEFFECTIVECLASS_METHOD;
  private static final Method GETEFFECTIVESUPERCLASS_METHOD;

  static {
    try {
      HASHCODE_METHOD = Object.class.getMethod("hashCode");
      EQUALS_METHOD = Object.class.getMethod("equals", Object.class);
      TOSTRING_METHOD = Object.class.getMethod("toString");
      COMPARETO_METHOD = Comparable.class.getMethod("compareTo", Object.class);
      CLONE_METHOD = PersistentDomainObject.class.getMethod("clonePdo");
      GETDOMAINOBJECT_METHOD = PersistentDomainObject.class.getMethod("getDomainDelegate");
      GETPERSISTENTOBJECT_METHOD = PersistentDomainObject.class.getMethod("getPersistenceDelegate");
      TOGENERICSTRING_METHOD = Identifiable.class.getMethod("toGenericString");
      GETPDO_METHOD = PdoHolder.class.getMethod("getPdo");
      SETPDO_METHOD = PdoHolder.class.getMethod("setPdo", PersistentDomainObject.class);
      ON_METHOD = PdoHolder.class.getMethod("on");
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getMethod("getEffectiveClass");
      GETEFFECTIVESUPERCLASS_METHOD = EffectiveClassProvider.class.getMethod("getEffectiveSuperClasses");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }


  private final Class<T> clazz;                            // the pdo class
  private final Mixin<T,PdoHolder<T>> persistenceMixin;    // the persistence mixin
  private final Mixin<T,PdoHolder<T>> domainMixin;         // the domain mixin



  /**
   * Creates an invocation handler.
   *
   * @param persistenceMapper the classmapper for the persistence implementations
   * @param domainMapper the classmapper for the domain implementations
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
   * Creates the delegates for the given proxy instance and the domain context.
   *
   * @param pdo the dynamic proxy instance
   * @param context the domain context
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, DomainContext context)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                    new Object[] { pdo, context });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                               new Object[] { pdo });
  }


  /**
   * Creates the delegates for the given proxy instance and the session.
   * <p>
   * Note: The domain context must be set by the application!
   *
   * @param pdo the dynamic proxy instance
   * @param session the session
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, Session session)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                    new Object[] { pdo, session });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                               new Object[] { pdo });
  }


  /**
   * Creates the delegates for the given proxy instance and a persistence delegate.
   * <p>
   * Note: The domain context must be set by the application!
   *
   * @param pdo the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, PersistentObject<T> persistenceDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceDelegate.setPdo(pdo);
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
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, DomainContext context, DomainObject<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, DomainContext.class },
                                    new Object[] { pdo, context });

    domainDelegate.setPdo(pdo);
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
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo, Session session, DomainObject<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class, Session.class },
                                    new Object[] { pdo, session });

    domainDelegate.setPdo(pdo);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance, a session and domain delegate.
   *
   * @param pdo the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   */
  public void setupDelegates(T pdo, PersistentObject<T> persistenceDelegate, DomainObject<T> domainDelegate) {
    persistenceDelegate.setPdo(pdo);
    persistenceMixin.setDelegate(persistenceDelegate);
    domainDelegate.setPdo(pdo);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance without any domain context or session.
   *
   * @param pdo the dynamic proxy instance
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T pdo)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                                    new Object[] { pdo });

    domainMixin.createDelegate(new Class<?>[] { PersistentDomainObject.class },
                               new Object[] { pdo });
  }



  @SuppressWarnings("unchecked")
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    Class<?> declaringClass = method.getDeclaringClass();
    Interceptable delegate = null;

    if (declaringClass == Object.class) {
      if (method.equals(HASHCODE_METHOD)) {
        delegate = persistenceMixin.getDelegate();
      }
      else if (method.equals(EQUALS_METHOD)) {
        Object obj = args[0];
        if (obj instanceof PersistentDomainObject) {
          args = new Object[] { ((PersistentDomainObject) obj).getPersistenceDelegate() };
          delegate = persistenceMixin.getDelegate();
        }
        else  {
          return false;
        }
      }
      else if (method.equals(TOSTRING_METHOD)) {
        delegate = domainMixin.getDelegate();
      }
    }
    else if (declaringClass == Comparable.class) {
      if (method.equals(COMPARETO_METHOD)) {
        Object obj = args[0];
        if (obj instanceof PersistentDomainObject) {
          args = new Object[] { ((PersistentDomainObject) obj).getPersistenceDelegate() };
          delegate = persistenceMixin.getDelegate();
        }
        else  {
          return Integer.MAX_VALUE;
        }
      }
    }
    else if (declaringClass == PersistentDomainObject.class) {
      if (method.equals(GETDOMAINOBJECT_METHOD)) {
        return domainMixin.getDelegate();
      }
      else if (method.equals(GETPERSISTENTOBJECT_METHOD)) {
        return persistenceMixin.getDelegate();
      }
      else if (method.equals(CLONE_METHOD)) {
        T pdo = (T) proxy;
        return Pdo.create(clazz, pdo.getPersistenceDelegate().clonePersistentObject(),
                          pdo.getDomainDelegate().cloneDomainObject());
      }
    }
    else if (declaringClass == EffectiveClassProvider.class) {
      if (method.equals(GETEFFECTIVECLASS_METHOD)) {
        return clazz;
      }
      else if (method.equals(GETEFFECTIVESUPERCLASS_METHOD)) {
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> ifClass: clazz.getInterfaces()) {
          if (PersistentDomainObject.class.isAssignableFrom(ifClass)) {
            list.add(ifClass);
          }
        }
        return list;
      }
    }
    else if (declaringClass == PdoHolder.class &&
             !method.equals(ON_METHOD)) {    // on() should be executed via mixin below!
      if (method.equals(TOGENERICSTRING_METHOD)) {
        T pdo = (T) proxy;
        return clazz.getName() + '[' + pdo.getId() + '/' + pdo.getSerial() + ']';
      }
      else if (method.equals(GETPDO_METHOD)) {
        return proxy;
      }
      else if (method.equals(SETPDO_METHOD)) {
        throw new PdoRuntimeException("method " + SETPDO_METHOD + " not allowed on PDO " + clazz);
      }
    }
    else if (persistenceMixin.matches(declaringClass)) {
      delegate = persistenceMixin.getDelegate();
    }
    else if (domainMixin.matches(declaringClass)) {
      delegate = domainMixin.getDelegate();
    }
    else {
      throw new PdoRuntimeException("no delegate found for " + declaringClass);
    }

    // execute method
    return METHOD_CACHE.invoke(clazz, delegate, method, args);
  }

}
