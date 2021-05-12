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

package org.tentackle.maven.plugin.i18n;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Properties that keep the loading order.<br>
 * Improves readability of changes via VCS.<br>
 * Minimal implementation, sufficient only for load/store.
 * Not thread-safe!
 */
public class OrderedProperties extends Properties {

  private final LinkedHashMap<Object, Object> map;

  public OrderedProperties() {
    map = new LinkedHashMap<>();
  }

  @Override
  public Set<Map.Entry<Object, Object>> entrySet() {
    return map.entrySet();
  }

  @Override
  public Enumeration<Object> keys() {
    return Collections.enumeration(map.keySet());
  }

  @Override
  public Set<Object> keySet() {
    return map.keySet();
  }

  @Override
  public Object put(Object key, Object value) {
    map.put(key, value);
    return super.put(key, value);
  }

  @Override
  public synchronized Object remove(Object key) {
    map.remove(key);
    return super.remove(key);
  }

  @Override
  public synchronized void putAll(Map<?, ?> t) {
    map.putAll(t);
    super.putAll(t);
  }

  @Override
  public String toString() {
    return map.toString();
  }

}
