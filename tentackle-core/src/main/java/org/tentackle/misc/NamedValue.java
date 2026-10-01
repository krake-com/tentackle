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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe named value.
 *
 * @param <T> the value type
 * @see NamedCounter for a named counter of longs
 */
public class NamedValue<T> {

  /**
   * References mapped by name.
   */
  private final Map<String, AtomicReference<T>> references = new ConcurrentHashMap<>();


  /**
   * Creates a named value.
   */
  public NamedValue() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the value for given name.
   *
   * @param name the value's name
   * @return the value, null if not set
   */
  public T get(String name) {
    return getReference(name).get();
  }

  /**
   * Sets the value for given name.
   *
   * @param name the value's name
   * @param value the value
   */
  public void set(String name, T value) {
    getReference(name).set(value);
  }

  /**
   * Gets the names of all values.
   *
   * @return the names
   */
  public Collection<String> names() {
    return new ArrayList<>(references.keySet());
  }

  /**
   * Gets the atomic reference.<br>
   * Creates it if it does not exist yet.
   *
   * @param name the value's name
   * @return the atomic reference
   */
  protected AtomicReference<T> getReference(String name) {
    return references.computeIfAbsent(name, _ -> new AtomicReference<>());
  }

}
