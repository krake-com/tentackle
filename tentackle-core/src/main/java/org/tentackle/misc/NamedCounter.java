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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe named counter.
 *
 * <p>
 * Example:
 * <pre>
 *   private static final NamedCounter INSTANCE_COUNTER = new NamedCounter();   // thread instance counter
 *
 *   private static final String THREAD_NAME = "BlahThread";
 *
 *   ...
 *
 *      String name = THREAD_NAME + something;
 *      return name + " (" + INSTANCE_COUNTER.next(name) + ")";
 *
 * </pre>
 *
 * @see NamedValue for named values of any type
 */
public class NamedCounter {

  /**
   * Counters mapped by name.
   */
  private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();


  /**
   * Creates a named counter.
   */
  public NamedCounter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Increments and gets the value for given name.
   *
   * @param name the counter's name
   * @return the number, starting at 1
   */
  public long next(String name) {
    return getCounter(name).incrementAndGet();
  }

  /**
   * Decrements and gets the value for given name.
   *
   * @param name the counter's name
   * @return the number, starting at -1
   */
  public long previous(String name) {
    return getCounter(name).decrementAndGet();
  }

  /**
   * Gets the value for given name.
   *
   * @param name the counter's name
   * @return the counter's value
   */
  public long get(String name) {
    return getCounter(name).get();
  }

  /**
   * Sets the value for given name.
   *
   * @param name the counter's name
   * @param value the counter's value
   */
  public void set(String name, long value) {
    getCounter(name).set(value);
  }

  /**
   * Gets the names of all counters.
   *
   * @return the names
   */
  public Collection<String> names() {
    return new ArrayList<>(counters.keySet());
  }


  private AtomicLong getCounter(String name) {
    return counters.computeIfAbsent(name, n -> new AtomicLong());
  }

}
