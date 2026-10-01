/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.misc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to canonicalize objects.
 * <p>
 * Canonicalized objects occupy the same memory reference if they are equal.
 *
 * @author harald
 * @param <T> the object type
 */
public class Canonicalizer<T> {

  private final int limit;            // the size limit, 0 if unlimited
  private final Map<T,T> objectMap;   // the object map

  /**
   * Creates a {@code Canonicalizer}.
   *
   * @param threadSafe true if we need a thread-safe {@code Canonicalizer}
   * @param limit the maximum size of the internal map, 0 if unlimited.
   *              If the limit is exceeded, the map is cleared and starts over.
   */
  public Canonicalizer(boolean threadSafe, int limit) {
    this.limit = limit;
    this.objectMap = threadSafe ? new ConcurrentHashMap<>() : new HashMap<>();
  }

  /**
   * Creates a non-thread-safe unlimited {@code Canonicalizer}.
   */
  public Canonicalizer() {
    this(false, 0);
  }

  /**
   * Gets the canonicalized form of an object.<br>
   * If an equal object was canonicalized before, that object is returned.
   * Otherwise, the given object becomes the canonical one.
   *
   * @param obj the object
   * @return the canonical object, null if obj is null
   */
  public T canonicalize(T obj) {
    if (obj == null) {
      return null;
    }
    if (limit > 0 && objectMap.size() > limit) {
      objectMap.clear();
    }
    T canon = objectMap.putIfAbsent(obj, obj);
    return canon == null ? obj : canon;
  }

  /**
   * Gets the canonicalized objects so far.
   *
   * @return the canonicalized objects (a live view of the internal map)
   */
  public Collection<T> getObjects() {
    return objectMap.values();
  }

}
