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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.Interceptable;
import org.tentackle.reflect.InterceptableMethodCache;
import org.tentackle.reflect.Mixin;
import org.tentackle.session.Session;

/**
 * Invocation handler for operations.<br>
 * Each instance of an operation gets its own handler.
 *
 * @param <T> the operation type
 * @author harald
 */
public class OperationInvocationHandler<T extends Operation<T>> implements InvocationHandler {

  /**
   * The method cache.<br>
   * Speeds up method invocations and provides logging and statistics.
   */
  public static final InterceptableMethodCache METHOD_CACHE = new InterceptableMethodCache("OPN");


  // preloaded methods of java.lang.Object
  private static final Method HASHCODE_METHOD;
  private static final Method EQUALS_METHOD;
  private static final Method TOSTRING_METHOD;

  // preloaded methods of java.lang.Comparable
  private static final Method COMPARETO_METHOD;

  // preloaded methods of Operation
  private static final Method CLONE_METHOD;
  private static final Method GETDOMAINDELEGATE_METHOD;
  private static final Method GETPERSISTENTDELEGATE_METHOD;

  // preloaded methods of OperationHolder
  private static final Method GETOPERATION_METHOD;
  private static final Method SETOPERATION_METHOD;

  // preloaded methods of EffectiveClassProvider
  private static final Method GETEFFECTIVECLASS_METHOD;
  private static final Method GETEFFECTIVESUPERCLASS_METHOD;

  static {
    try {
      HASHCODE_METHOD = Object.class.getMethod("hashCode");
      EQUALS_METHOD = Object.class.getMethod("equals", Object.class);
      TOSTRING_METHOD = Object.class.getMethod("toString");
      COMPARETO_METHOD = Comparable.class.getMethod("compareTo", Object.class);
      CLONE_METHOD = Operation.class.getMethod("clone");
      GETDOMAINDELEGATE_METHOD = Operation.class.getMethod("getDomainDelegate");
      GETPERSISTENTDELEGATE_METHOD = Operation.class.getMethod("getPersistenceDelegate");
      GETOPERATION_METHOD = OperationHolder.class.getMethod("getOperation");
      SETOPERATION_METHOD = OperationHolder.class.getMethod("setOperation", Operation.class);
      GETEFFECTIVECLASS_METHOD = EffectiveClassProvider.class.getMethod("getEffectiveClass");
      GETEFFECTIVESUPERCLASS_METHOD = EffectiveClassProvider.class.getMethod("getEffectiveSuperClasses");
    }
    catch (NoSuchMethodException e) {
      throw new NoSuchMethodError(e.getMessage());
    }
  }


  private final Class<T> clazz;                                 // the operation class
  private Mixin<T,PersistentOperation<T>> persistenceMixin;     // the persistence mixin
  private Mixin<T,DomainOperation<T>> domainMixin;              // the domain mixin



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
      throw new ClassNotFoundException("neither domain- nor persistence mapping found for '" + clazz.getName());
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
   * Creates the delegates for the given proxy instance and the domain context.
   *
   * @param operation the dynamic proxy instance
   * @param context the domain context
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, DomainContext context)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                    new Object[] { operation, context });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }


  /**
   * Creates the delegates for the given proxy instance and the session.
   * <p>
   * Note: The domain context must be set by the application!
   *
   * @param operation the dynamic proxy instance
   * @param session the session
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, Session session)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                    new Object[] { operation, session });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }


  /**
   * Creates the delegates for the given proxy instance and a persistence delegate.
   * <p>
   * Note: The domain context must be set by the application!
   *
   * @param operation the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, PersistentOperation<T> persistenceDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceDelegate.setOperation(operation);
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
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, DomainContext context, DomainOperation<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, DomainContext.class },
                                    new Object[] { operation, context });

    domainDelegate.setOperation(operation);
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
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation, Session session, DomainOperation<T> domainDelegate)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class, Session.class },
                                    new Object[] { operation, session });

    domainDelegate.setOperation(operation);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance, a session and domain delegate.
   *
   * @param operation the dynamic proxy instance
   * @param persistenceDelegate the persistence delegate
   * @param domainDelegate the domain delegate
   */
  public void setupDelegates(T operation, PersistentOperation<T> persistenceDelegate, DomainOperation<T> domainDelegate) {
    persistenceDelegate.setOperation(operation);
    persistenceMixin.setDelegate(persistenceDelegate);
    domainDelegate.setOperation(operation);
    domainMixin.setDelegate(domainDelegate);
  }


  /**
   * Creates the delegates for the given proxy instance without any domain context or session.
   *
   * @param operation the dynamic proxy instance
   * @throws NoSuchMethodException if no matching delegate constructor found
   * @throws InstantiationException if delegate could not be instantiated
   * @throws IllegalAccessException if delegate could not be instantiated
   * @throws IllegalArgumentException if delegate could not be instantiated
   * @throws InvocationTargetException if delegate could not be instantiated
   */
  public void setupDelegates(T operation)
         throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {

    persistenceMixin.createDelegate(new Class<?>[] { Operation.class },
                                    new Object[] { operation });

    domainMixin.createDelegate(new Class<?>[] { Operation.class },
                               new Object[] { operation });
  }



  @SuppressWarnings({"unchecked", "unchecked"})
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    Class<?> declaringClass = method.getDeclaringClass();
    Interceptable delegate = null;

    if (declaringClass == Object.class) {
      if (method.equals(HASHCODE_METHOD)) {
        delegate = persistenceMixin.isDummy() ? domainMixin.getDelegate() : persistenceMixin.getDelegate();
      }
      else if (method.equals(EQUALS_METHOD)) {
        Object obj = args[0];
        if (obj instanceof Operation) {
          if (persistenceMixin.isDummy()) {   // persistence delegate takes precedence over domain for equals
            delegate = domainMixin.getDelegate();
            args = new Object[] { ((Operation) obj).getDomainDelegate() };
          }
          else {
            // at least one of domain- or persistence delegate is present
            delegate = persistenceMixin.getDelegate();
            args = new Object[] { ((Operation) obj).getPersistenceDelegate() };
          }
        }
        else  {
          return false;
        }
      }
      else if (method.equals(TOSTRING_METHOD)) {
        // domain delegate takes precedence over persistence for toString
        delegate = domainMixin.isDummy() ? persistenceMixin.getDelegate() : domainMixin.getDelegate();
      }
    }
    else if (declaringClass == Comparable.class) {
      if (method.equals(COMPARETO_METHOD)) {
        Object obj = args[0];
        if (obj instanceof Operation) {
          // persistence delegate takes precedence over domain for compareTo
          if (persistenceMixin.isDummy()) {
            delegate = domainMixin.getDelegate();
            args = new Object[] { ((Operation) obj).getDomainDelegate() };
          }
          else {
            delegate = persistenceMixin.getDelegate();
            args = new Object[] { ((Operation) obj).getPersistenceDelegate() };
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
      else if (method.equals(GETPERSISTENTDELEGATE_METHOD)) {
        return persistenceMixin.getDelegate();
      }
      else if (method.equals(CLONE_METHOD)) {
        throw new CloneNotSupportedException("operations are not cloneable");
      }
    }
    else if (declaringClass == EffectiveClassProvider.class) {
      if (method.equals(GETEFFECTIVECLASS_METHOD)) {
        return clazz;
      }
      else if (method.equals(GETEFFECTIVESUPERCLASS_METHOD)) {
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> ifClass: clazz.getInterfaces()) {
          if (Operation.class.isAssignableFrom(ifClass)) {
            list.add(ifClass);
          }
        }
        return list;
      }
    }
    else if (declaringClass == OperationHolder.class) {
      if (method.equals(GETOPERATION_METHOD)) {
        return proxy;
      }
      else if (method.equals(SETOPERATION_METHOD)) {
        throw new PdoRuntimeException("method " + SETOPERATION_METHOD + " not allowed on operation " + clazz);
      }
    }
    else if (domainMixin.matches(declaringClass)) {
      delegate = domainMixin.getDelegate();
    }
    else if (persistenceMixin.matches(declaringClass)) {
      delegate = persistenceMixin.getDelegate();
    }

    if (delegate == null) {
      throw new PdoRuntimeException("no delegate found for " + method + " declared in " + declaringClass);
    }

    // execute method
    return METHOD_CACHE.invoke(clazz, delegate, method, args);
  }

}
