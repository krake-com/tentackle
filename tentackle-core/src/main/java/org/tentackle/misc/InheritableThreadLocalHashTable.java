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

import java.util.Hashtable;

/**
 * An thread-inheritable thread-local hashtable.
 * <p>
 * Ensures that each child thread gets its own copy.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @author harald
 */
public class InheritableThreadLocalHashTable<K, V> extends InheritableThreadLocal<Hashtable<K, V>> {

  @Override
  @SuppressWarnings("unchecked")
  protected Hashtable<K, V> childValue(Hashtable<K, V> parentValue) {
    if (parentValue != null) {
      return (Hashtable<K, V>) parentValue.clone();
    }
    return null;
  }

  @Override
  protected Hashtable<K, V> initialValue() {
    return new Hashtable<>();
  }
}
