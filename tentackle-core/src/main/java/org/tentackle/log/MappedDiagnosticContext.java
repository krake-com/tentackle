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


package org.tentackle.log;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A mapped diagnostic context.<br>
 * Provides thread-local optional key-value pairs to enhance logging.
 *
 * @author harald
 */
public interface MappedDiagnosticContext {

  /**
   * Put a context value (the <code>val</code> parameter) as identified with
   * the <code>key</code> parameter into the current thread's context map.
   * The <code>key</code> parameter cannot be null. The <code>val</code> parameter
   * can be null only if the underlying implementation supports it.
   * <p>
   * This method delegates all work to the MDC of the underlying logging system.
   *
   * @param key the key
   * @param val the value
   * @throws RuntimeException if the key is null, usually an {@link IllegalArgumentException} or {@link NullPointerException}
   */
  void put(String key, String val);


  /**
   * Get the context identified by the <code>key</code> parameter. The
   * <code>key</code> parameter cannot be null.
   * <p>
   * This method delegates all work to the MDC of the underlying logging system.
   *
   * @param key the key
   * @return the string value identified by the <code>key</code> parameter, null if none
   * @throws RuntimeException if the key is null, usually an {@link IllegalArgumentException} or {@link NullPointerException}
   */
  String get(String key);


  /**
   * Removes the context identified by the <code>key</code> parameter using
   * the underlying system's MDC implementation. The  <code>key</code> parameter
   * cannot be null. This method does nothing if there is no previous value
   * associated with <code>key</code>.
   *
   * @param key the key
   * @throws RuntimeException if the key is null, usually an {@link IllegalArgumentException} or {@link NullPointerException}
   */
  void remove(String key);


  /**
   * Clear all entries in the MDC of the underlying implementation.
   */
  void clear();


  /**
   * Gets all keys.
   *
   * @return the set of all keys, never null
   */
  Set<String> getKeys();

  /**
   * Checks whether this mapped diagnostic context matches a given pattern.<br>
   * The pattern is applied to the string representation of the context.
   *
   * @param pattern the pattern
   * @return true if matches
   */
  boolean matchesPattern(Pattern pattern);

}
