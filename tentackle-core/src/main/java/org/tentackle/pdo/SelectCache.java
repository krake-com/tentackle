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

package org.tentackle.pdo;

import java.lang.reflect.Method;


/**
 * A select cache.<br>
 * Caches the results for select single PDOs and list of PDOs.
 *
 * @author harald
 */
public interface SelectCache {

  /**
   * Select an object.<br>
   * If the result is not in cache, it will be loaded from the backend.
   * Otherwise the cached result will be returned.
   *
   * @param method the select method
   * @param proxy the pdo to invoke the method on
   * @param args the method's arguments
   * @return the select result
   */
  Object select(Method method, PersistentDomainObject<?> proxy, Object[] args);

  /**
   * Clears the cache.
   */
  void clear();

}
