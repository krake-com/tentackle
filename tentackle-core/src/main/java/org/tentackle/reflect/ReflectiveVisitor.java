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
 * The effective visit method for the concrete persistence class is determined by reflection.
 * The most specific method will be used, which allows to use a method for a class hierarchy.
 *
 * @author harald
 */
public abstract class ReflectiveVisitor {

  private static final Logger LOGGER = Logger.get(ReflectiveVisitor.class);


  private static final String METHOD_NAME = "visit";    // fixed method name



  // the cached method map
  private static final MethodCache METHOD_CACHE = new MethodCache("methodcache[" + ReflectiveVisitor.class.getName() + "]");




  /**
   * Visits an object.
   * <p>
   * Must be <em>overloaded</em> for application-specific types to implement double dispatch.
   * The method determines the most specific method by reflection.
   * <p>
   * Notice: do not <em>override</em> this method!
   *
   * @param visitedObject the visited object
   * @param types optional parameters
   * @param args optional arguments according to types
   * @throws NoSuchMethodException if no type specific implementation could be found
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
   * Finds the most specific overloaded method for a given class and method arguments.
   *
   * @param visitedClass the class to visit
   * @param types the method arguments
   * @return the method, null if no such method
   */
  protected Method findBestMethod(Class<?> visitedClass, Class<?>[] types) {
    Method method = null;
    Class<?> clazz = visitedClass;
    while (clazz != null && isImplementedClass(clazz)) {
      method = METHOD_CACHE.getMethod(getClass(), types);
      if (method == null) {
        try {
          types[0] = clazz;
          method = findVisitMethod(types);
          types[0] = visitedClass;
          METHOD_CACHE.addMethod(method, getClass(), types);
        }
        catch (NoSuchMethodException nme) {
          clazz = clazz.getSuperclass();
          continue;
        }
      }
      break;
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
   * Checks whether a class is potentially implemented by a visit-method.<br>
   * The default implementation returns true.
   *
   * @param clazz the class to test
   * @return true if check for visit method, false to stop the search in class hierarchy
   */
  protected boolean isImplementedClass(Class<?> clazz) {
    return true;
  }


  /**
   * Handles the invocation exception.
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
