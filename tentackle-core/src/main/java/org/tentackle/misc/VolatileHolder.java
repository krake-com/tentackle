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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Same as {@link Holder}, but with a volatile object reference.<br>
 * Useful if the object is set and retrieved by different threads.
 *
 * @author harald
 * @param <T> the object type
 */
public class VolatileHolder<T> implements Consumer<T>, Supplier<T> {

  private volatile T object;

  /**
   * Creates a holder without an initial value.
   */
  public VolatileHolder() {}

  /**
   * Creates a holder with an initial value.
   *
   * @param t the initial value
   */
  public VolatileHolder(T t) {
    object = t;
  }

  @Override
  public void accept(T t) {
    object = t;
  }

  @Override
  public T get() {
    return object;
  }

  @Override
  public String toString() {
    T obj = object;
    return obj == null ? "<null>" : obj.toString();
  }
}
