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

package org.tentackle.misc;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A map of {@link Identifiable}s.
 * <p>
 * By default, the map uses a hashing key composed of the class and object id.
 * The first invocation of one of the methods {@link #getAllSorted()} or {@link #getAllSorted(Class)}
 * creates an additional sorted map, which is maintained from then on (and makes modifications a bit slower).
 * <p>
 * The map is <em>not</em> thread-safe and is meant to be built and consumed by a single thread,
 * typically to collect the components of an aggregate. Sharing it between threads requires
 * external synchronization: already a single {@code add} updates two maps non-atomically,
 * {@code getAllSorted} lazily creates the sorted one, and {@link #getAll()} hands out a live view.
 *
 * @param <T> the identifiable type
 * @author harald
 */
public class IdentifiableMap<T extends Identifiable> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("serial")
  private Map<IdentifiableKey<T>, T> objectMap;         // the object map (fast)
  @SuppressWarnings("serial")
  private Map<IdentifiableKey<T>, T> sortedObjectMap;   // the sorted object map (slow)


  /**
   * Creates an {@link Identifiable} map.
   */
  public IdentifiableMap() {
    clear();
  }


  /**
   * Clears the map.<br>
   * Also drops the sorted map.
   */
  public void clear() {
    // creating new maps is faster than clear() for large maps
    objectMap = new LinkedHashMap<>();
    sortedObjectMap = null;
  }

  /**
   * Creates an object map with a single object.
   *
   * @param object the object to add
   */
  public IdentifiableMap(T object) {
    this();
    add(object);
  }

  /**
   * Adds an object to the map.
   *
   * @param object the object to add
   * @return the replaced object, null if added
   */
  public T add(T object) {
    IdentifiableKey<T> key = new IdentifiableKey<>(object);
    if (sortedObjectMap != null) {
      sortedObjectMap.put(key, object);
    }
    return objectMap.put(key, object);
  }

  /**
   * Adds a collection to the map.
   * <p>
   * Objects already in the map are replaced but not counted.
   *
   * @param objects the collection
   * @return the number of objects added
   */
  public int add(Collection<? extends T> objects) {
    int count = 0;
    for (T object: objects) {
      if (add(object) == null) {
        count++;
      }
    }
    return count;
  }

  /**
   * Removes an object from the map.
   *
   * @param <P> the class type
   * @param clazz the object class
   * @param id the object ID
   * @return the removed object, null if not in the map
   */
  @SuppressWarnings("unchecked")
  public <P extends T> P remove(Class<P> clazz, long id) {
    IdentifiableKey<T> key = new IdentifiableKey<>((Class<T>) clazz, id);
    if (sortedObjectMap != null) {
      sortedObjectMap.remove(key);
    }
    return (P) objectMap.remove(key);
  }

  /**
   * Removes an object from the map.
   *
   * @param object the object to remove
   * @return the removed object, null if not in the map
   */
  public T remove(T object) {
    IdentifiableKey<T> key = new IdentifiableKey<>(object);
    if (sortedObjectMap != null) {
      sortedObjectMap.remove(key);
    }
    return objectMap.remove(key);
  }

  /**
   * Removes a collection from the map.
   * <p>
   * Objects not in the map are ignored and not counted.
   *
   * @param objects the object collection to remove
   * @return the number of objects removed
   */
  public int remove(Collection<? extends T> objects) {
    int count = 0;
    for (T object: objects) {
      if (remove(object) != null) {
        count++;
      }
    }
    return count;
  }

  /**
   * Retrieves an object from the map.
   *
   * @param <P> the class type
   * @param clazz the object class
   * @param id the object ID
   * @return the object, null if not in map
   */
  @SuppressWarnings("unchecked")
  public <P extends T> P get(Class<P> clazz, long id) {
    return (P) objectMap.get(new IdentifiableKey<>(clazz, id));
  }

  /**
   * Gets all objects in the map.
   * <p>
   * The returned objects are in the order they were added.
   *
   * @return all objects (a live view)
   */
  public Collection<T> getAll() {
    return objectMap.values();
  }

  /**
   * Gets all objects sorted by class name and ID.
   *
   * @return all objects (a live view)
   */
  public Collection<T> getAllSorted() {
    return getSortedMap().values();
  }

  /**
   * Gets all objects for a given class.<br>
   * Objects of subclasses are not included.
   *
   * @param <P> the given class
   * @param clazz the object class
   * @return the objects sorted by ID, empty collection if none
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <P extends T> Collection<P> getAllSorted(Class<P> clazz) {
    return ((TreeMap) getSortedMap()).
            subMap(new IdentifiableKey<>(clazz, Long.MIN_VALUE),
                   new IdentifiableKey<>(clazz, Long.MAX_VALUE)).values();
  }


  /**
   * Gets the sorted map.<br>
   * Creates it from the hash map, if not done yet.
   */
  private Map<IdentifiableKey<T>, T> getSortedMap() {
    if (sortedObjectMap == null) {
      // sort the first time
      sortedObjectMap = new TreeMap<>(objectMap);
    }
    return sortedObjectMap;
  }

}
