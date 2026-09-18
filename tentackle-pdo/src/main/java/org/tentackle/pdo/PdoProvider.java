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

package org.tentackle.pdo;

import org.tentackle.reflect.NotInterceptable;

/**
 * Provider for a PDO.
 *
 * @param <T> the PDO type
 * @author harald
 */
public interface PdoProvider<T extends PersistentDomainObject<T>> {

  /**
   * Gets the PDO.
   *
   * @return the pdo
   */
  @NotInterceptable
  T getPdo();

  /**
   * Creates a new instance of the same type as the PDO.
   *
   * @return the new instance
   */
  default T on() {
    return PdoFactory.getInstance().create(getPdo());
  }

  /**
   * Creates a new instance of the same type as the PDO within a new sub context.
   *
   * @param contextName the context name
   * @return the new instance
   */
  default T on(String contextName) {
    return PdoFactory.getInstance().create(getPdo(), contextName);
  }

}
