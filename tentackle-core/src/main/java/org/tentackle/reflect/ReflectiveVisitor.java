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


package org.tentackle.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.StringHelper;

/**
 * A reflective visitor to add application-specific functionality to existing objects.
 * <p>
 * The effective visit method for the concrete persistence class is determined by reflection.
 * The most specific method will be used, which allows to use a method for a class hierarchy.
 *
 * @author harald
 */
public abstract class ReflectiveVisitor {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ReflectiveVisitor.class);


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
   * @param args the visited object plus optional parameters
   * @throws NoSuchMethodException if no type specific implementation could be found
   */
  public void visit(Object... args) throws NoSuchMethodException {

    if (args == null || args.length == 0) {
      throw new TentackleRuntimeException("cannot dispatch no-args visit method");
    }

    Class<?>[] types = new Class<?>[args.length];
    for (int i=0; i < args.length; i++) {
      types[i] = args[i].getClass();
    }

    // figure out most specific overloaded method for concrete object
    Method method = null;
    Object visitedObject  = args[0];
    Class<?> visitedClass = visitedObject.getClass();

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

    if (method == null) {
      throw new NoSuchMethodException("no visit method in " + getClass().getName() + " for " + visitedClass.getName());
    }

    // invoke the method
    try {
      if (LOGGER.isFineLoggable()) {
        LOGGER.fine("invoking " + method + "(" + StringHelper.objectArrayToString(args, ", ") + ")");
      }
      method.invoke(this, args);
    }
    catch (IllegalAccessException iae) {
      handleException(iae, method, args);
    }
    catch (InvocationTargetException ite) {
      Throwable thrbl = ite.getCause();
      if (thrbl instanceof RuntimeException) {
        // let it pass directly to the application (for example DbRuntimeExceptions)
        throw (RuntimeException) thrbl;
      }
      handleException(ite.getCause(), method, args);
    }
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
