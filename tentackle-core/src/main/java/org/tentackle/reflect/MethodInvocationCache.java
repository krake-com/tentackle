/*
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.TentackleRuntimeException;

/**
 * A cache for method invocations.
 * <p>
 * Minimizes expensive method invocations. Should be used only for methods that do not manipulate
 * any other objects and just return a value. Examples are database queries or long running
 * computations. Not applicable to things like setters or persistence operations.
 *
 * @author harald
 */
public class MethodInvocationCache {


  // the return value (to allow null-values to be a valid return value).
  private static class ReturnValue {

    private final Object value;

    private ReturnValue(Object value) {
      this.value = value;
    }

    private Object getValue() {
      return value;
    }
  }


  private final Map<MethodInvocationKey,ReturnValue> methodMap;    // map holding the return values


  /**
   * Creates a method invocation cache.
   */
  public MethodInvocationCache() {
    this.methodMap = new ConcurrentHashMap<>();
  }


  /**
   * Remembers a method invocation.
   *
   * @param methodKey the method key
   * @param returnValue the return value
   * @return null if new, else old return value
   */
  public Object put(MethodInvocationKey methodKey, Object returnValue) {
    return methodMap.put(methodKey, new ReturnValue(returnValue));
  }


  /**
   * Gets the return value for a method invocation.
   *
   * @param methodKey the method key
   * @return the return value
   * @throws NoSuchMethodException if no such method cached
   */
  public Object get(MethodInvocationKey methodKey) throws NoSuchMethodException {
    ReturnValue returnValue = methodMap.get(methodKey);
    if (returnValue == null) {
      throw new NoSuchMethodException(methodKey.toString());
    }
    return returnValue.getValue();
  }


  /**
   * Invokes the method.<br>
   * If the method is known to the cache, the cached return value is returned.
   * Otherwise the method will be invoked and its returnvalue cached.
   *
   * @param methodKey the method key
   * @return the return value
   * @throws TentackleRuntimeException if invocation failed
   */
  public MethodInvocationCacheResult invoke(MethodInvocationKey methodKey) {
    ReturnValue returnValue = methodMap.get(methodKey);
    boolean cached;
    if (returnValue == null) {
      // method unknown so far
      cached = false;
      try {
        // make method accessible
        if (!methodKey.getMethod().isAccessible()) {
          methodKey.getMethod().setAccessible(true);
        }
        // invoke it
        returnValue = new ReturnValue(methodKey.getMethod().invoke(methodKey.getObject(), methodKey.getArguments()));
        // remember it
        put(methodKey, returnValue);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        throw new TentackleRuntimeException("invocation failed for " + methodKey, ex);
      }
    }
    else  {
      cached = true;
    }
    return new MethodInvocationCacheResult(returnValue.getValue(), cached);
  }

  /**
   * Invokes the method.<br>
   * If the method is known to the cache, the cached return value is returned.
   * Otherwise the method will be invoked and its returnvalue cached.
   *
   * @param method the method
   * @param object the object the method is invoked on, null if object doesnt matter (e.g. static methods)
   * @param arguments the argument values
   * @return the return value
   * @throws TentackleRuntimeException if invocation failed
   */
  public MethodInvocationCacheResult invoke(Method method, Object object, Object[] arguments) {
    return invoke(new MethodInvocationKey(method, object, arguments));
  }


  /**
   * Clears the method cache.
   */
  public void clear() {
    methodMap.clear();
  }
  
}
