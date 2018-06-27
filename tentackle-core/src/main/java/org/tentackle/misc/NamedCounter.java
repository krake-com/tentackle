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

package org.tentackle.misc;

import java.util.HashMap;
import java.util.Map;

/**
 * Counter per name.
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
 * @author harald
 */
public class NamedCounter {


  // simple counter to avoid a Long and puts
  private static class Counter {
    private long count;
  }

  /**
   * Counters for instances with the same name.
   */
  private final Map<String,Counter> counters = new HashMap<>();


  /**
   * Returns the next number for given name.
   *
   * @param name the name
   * @return the number, starting at 1
   */
  public synchronized long next(String name) {
    Counter counter = counters.computeIfAbsent(name, k -> new Counter());
    // create new counter
    return ++counter.count;
  }

}
