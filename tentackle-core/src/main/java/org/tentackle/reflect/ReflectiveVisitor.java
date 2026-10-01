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

package org.tentackle.reflect;

import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A reflective visitor to add application-specific functionality to existing objects.
 * <p>
 * The effective visit method for the concrete class of the visited object is determined by reflection.
 * The most specific method will be used, which allows to use a method for a class hierarchy.
 * The methods found are cached per visitor class and visited class.
 *
 * @author harald
 */
public abstract class ReflectiveVisitor {

  private static final Logger LOGGER = Logger.get(ReflectiveVisitor.class);

  private static final String METHOD_NAME = "visit";    // fixed method name

  // the cached method map
  private static final MethodCache METHOD_CACHE = new MethodCache("methodcache[" + ReflectiveVisitor.class.getName() + "]");


  /**
   * Creates a reflective visitor.
   */
  public ReflectiveVisitor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Visits an object.
   * <p>
   * Must be <em>overloaded</em> for application-specific types to implement double dispatch.
   * The method determines the most specific method by reflection.
   * <p>
   * Notice: do not <em>override</em> this method!
   *
   * @param visitedObject the visited object
   * @param types the parameter types of the visit method. The first element is a placeholder
   *              for the visited object's class and will be replaced by its effective class.
   * @param args the arguments of the visit method according to types, the first being the visited object
   * @throws NoSuchMethodException if no type specific implementation could be found
   * @throws TentackleRuntimeException if the visit method threw a checked exception
   */
  public void visit(Object visitedObject, Class<?>[] types, Object... args) throws NoSuchMethodException {

    // figure out most specific overloaded method for concrete object
    Class<?> visitedClass = EffectiveClassProvider.getEffectiveClass(visitedObject);
    Method method = findBestMethod(visitedClass, types);

    if (method == null) {
      throw new NoSuchMethodException("no visit method in " + getClass().getName() + " for " + visitedClass.getName());
    }

    // invoke the method
    try {
      LOGGER.fine(() -> "invoking " + method + "(" + StringHelper.objectArrayToString(args, ", ") + ")");
      method.invoke(this, args);
    }
    catch (IllegalAccessException iae) {
      handleException(iae, method, args);
    }
    catch (InvocationTargetException ite) {
      Throwable thrbl = ite.getCause();
      if (thrbl instanceof RuntimeException) {
        // let it pass directly to the application (for example PersistenceExceptions)
        throw (RuntimeException) thrbl;
      }
      handleException(ite.getCause(), method, args);
    }
  }


  /**
   * Finds the most specific overloaded method for a given class and method arguments.<br>
   * The visited class and its superclasses are tried until a matching visit method is found.
   *
   * @param visitedClass the class to visit
   * @param types the parameter types of the visit method, the first element is replaced by the visited class
   * @return the method, null if no such method
   */
  protected Method findBestMethod(Class<?> visitedClass, Class<?>[] types) {
    types[0] = visitedClass;
    Method method = METHOD_CACHE.getMethod(getClass(), types);
    if (method == null) {
      Class<?>[] searchTypes = types.clone();   // don't modify the caller's array while searching
      Class<?> clazz = visitedClass;
      while (clazz != null && isImplementedClass(clazz)) {
        searchTypes[0] = clazz;
        try {
          method = findVisitMethod(searchTypes);
          // the key must not refer to the caller's array, which may be modified later
          METHOD_CACHE.addMethod(method, getClass(), types.clone());
          break;
        }
        catch (NoSuchMethodException nme) {
          clazz = clazz.getSuperclass();
        }
      }
    }
    return method;
  }


  /**
   * Finds the visit method for a class.
   *
   * @param types the parameter types of the method
   * @return the method
   * @throws NoSuchMethodException if no such method
   */
  protected Method findVisitMethod(Class<?>[] types) throws NoSuchMethodException {
    return getClass().getMethod(METHOD_NAME, types);
  }


  /**
   * Checks whether a class is potentially implemented by a visit method.<br>
   * The default implementation returns true.
   *
   * @param clazz the class to test
   * @return true if check for visit method, false to stop the search in class hierarchy
   */
  protected boolean isImplementedClass(Class<?> clazz) {
    return true;
  }


  /**
   * Handles the invocation exception.<br>
   * Invoked for checked exceptions thrown by the visit method and for illegal access.
   * Runtime exceptions thrown by the visit method are passed to the caller unchanged.
   * <p>
   * The default implementation throws a {@link TentackleRuntimeException}.
   *
   * @param thrbl the exception
   * @param method the failed method
   * @param args the visited object plus optional parameters
   */
  protected void handleException(Throwable thrbl, Method method, Object... args) {
    String message = "invocation of " + method + " failed for ";
    try {
      message += StringHelper.objectArrayToString(args, ", ");
    }
    catch (RuntimeException re) {
      message += "? -> " + re.getMessage();
    }
    throw new TentackleRuntimeException(message, thrbl);
  }

}
