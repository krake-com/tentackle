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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cache to access methods of a class by an index.<br>
 * There is one such cache per class.<br>
 * The cache is thread-safe.
 */
public class IndexedMethodCache {

  private static final Logger LOGGER = Logger.get(IndexedMethodCache.class);

  private final Class<?> clazz;
  private final Map<Integer, Method> idMap;
  private final Map<Method, Integer> reverseMap;
  private final AtomicInteger nextIndex = new AtomicInteger();

  /**
   * Creates a new cache.
   *
   * @param clazz the class the cache belongs to
   */
  public IndexedMethodCache(Class<?> clazz) {
    this.clazz = clazz;
    idMap = new ConcurrentHashMap<>();
    reverseMap = new ConcurrentHashMap<>();
  }

  /**
   * Gets the class the cache belongs to.
   *
   * @return the class
   */
  public Class<?> getClazz() {
    return clazz;
  }

  /**
   * Gets the method at the given index.
   *
   * @param index the index
   * @return the method, null if no such method in cache
   */
  public Method get(int index) {
    return idMap.get(index);
  }

  /**
   * Adds a method to the cache, if not already present.<br>
   * Indexes start at 0 and are never reused, even if a method is removed.
   *
   * @param method the method to add
   * @return the index of the method
   */
  public int add(Method method) {
    return reverseMap.computeIfAbsent(method, m -> {
      int index = nextIndex.getAndIncrement();
      idMap.put(index, m);
      LOGGER.fine("added {0} with index {1} to {2}", m, index, this);
      return index;
    });
  }

  /**
   * Removes a method from the cache.
   *
   * @param index the index of the method to remove
   * @return the removed method, null if no such method in cache
   */
  public Method remove(int index) {
    Method method = idMap.remove(index);
    if (method != null) {
      reverseMap.remove(method);
      LOGGER.fine("removed {0} with index {1} from {2}", method, index, this);
    }
    return method;
  }

  @Override
  public String toString() {
    return "IndexedMethodCache for " + clazz;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    IndexedMethodCache that = (IndexedMethodCache) o;
    return Objects.equals(clazz, that.clazz);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(clazz);
  }

}
