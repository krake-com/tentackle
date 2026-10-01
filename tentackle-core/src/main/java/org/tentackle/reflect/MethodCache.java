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

import org.tentackle.log.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache to speed up method lookup by reflection.
 * <p>
 * Method caching makes sense if a method lookup involves algorithms to find
 * a "best match", for example as in {@link ReflectiveVisitor}.
 * The cache is thread-safe.
 *
 * @author harald
 */
public class MethodCache {

  private static final Logger LOGGER = Logger.get(MethodCache.class);


  /**
   * Method lookup key.
   *
   * @param clazz          the class the method to search in
   * @param parameterTypes the parameter types
   */
  private record Key(Class<?> clazz, Class<?>[] parameterTypes) {

    // Important: overrides of equals and hashCode necessary since record's defaults don't do deepEquals/deepHashCode on arrays.
    // see test/java/.../reflect/RecordTest.java.

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Key other = (Key) obj;
      if (this.clazz != other.clazz) {
        return false;
      }
      return Arrays.equals(this.parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 83 * hash + (this.clazz != null ? this.clazz.hashCode() : 0);
      hash = 83 * hash + Arrays.hashCode(this.parameterTypes);
      return hash;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append("class=");
      buf.append(clazz.getName());
      if (parameterTypes != null) {
        buf.append(", parameters=");
        boolean needComma = false;
        for (Class<?> type : parameterTypes) {
          if (needComma) {
            buf.append(',');
          }
          else {
            needComma = true;
          }
          buf.append(type.getName());
        }
      }
      return buf.toString();
    }
  }


  private final ConcurrentMap<Key, Method> methodMap;   // the method map
  private final String name;                            // the cache's name (for logging only)


  /**
   * Creates a method cache.
   *
   * @param name the cache's name
   */
  public MethodCache(String name) {
    this.name = Objects.requireNonNull(name, "name");
    methodMap = new ConcurrentHashMap<>();
  }

  /**
   * Gets the name of the method cache.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Gets a method from the cache.
   *
   * @param clazz the class the method belongs to
   * @param parameterTypes the parameter types
   * @return the method, null if no such method
   */
  public Method getMethod(Class<?> clazz, Class<?>... parameterTypes) {
    return methodMap.get(new Key(clazz, parameterTypes));
  }

  /**
   * Adds the method to the cache.
   *
   * @param method the method to add
   * @param clazz the class the method belongs to
   * @param parameterTypes the parameter types
   * @return null if added, the old method if replaced
   */
  public Method addMethod(Method method, Class<?> clazz, Class<?>... parameterTypes) {
    Key key = new Key(clazz, parameterTypes);
    Method oldMethod = methodMap.put(key, method);
    if (oldMethod == null) {
      LOGGER.info("added {0} for {1} to {2}", method, key, this);
    }
    else  {
      LOGGER.info("replaced {0} by {1} for {2} to {3}", oldMethod, method, key, this);
    }
    return oldMethod;
  }

  /**
   * Removes a method from the cache.
   *
   * @param clazz the class the method belongs to
   * @param parameterTypes the parameter types
   * @return the removed method, null if no such method in cache
   */
  public Method removeMethod(Class<?> clazz, Class<?>... parameterTypes) {
    Key key = new Key(clazz, parameterTypes);
    Method method = methodMap.remove(key);
    if (method == null) {
      LOGGER.info("no such method for {0} in {1}", key, this);
    }
    else  {
      LOGGER.info("removed {0} for {1} from {2}", method, key, this);
    }
    return method;
  }

}
