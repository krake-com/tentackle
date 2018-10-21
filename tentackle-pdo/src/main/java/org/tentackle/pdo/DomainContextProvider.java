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

/**
 * DomainContext provider.
 * <p>
 * Provides access to a certain domain context.
 *
 * @author harald
 */
public interface DomainContextProvider {

  /**
   * Gets the domain context.
   *
   * @return the domain context
   */
  DomainContext getDomainContext();

  /**
   * Creates a PDO for another entity type within the domain context of this object.<br>
   * The method is an acronym for "object new" and is provided as a shorter and better readable alternative to
   * {@link Pdo#create(java.lang.Class, org.tentackle.pdo.DomainContext)}.
   *
   * <p>
   * Example:
   * <pre>
   * List&lt;Invoice&gt; invoices = on(Invoice.class).selectByCustomerId(customerId);
   * </pre>
   * is equivalent to
   * <pre>
   * List&lt;Invoice&gt; invoices = Pdo.create(Invoice.class, getDomainContext()).selectByCustomerId(customerId);
   * </pre>
   *
   * @param <T> the persistent domain object type
   * @param clazz the PDO interface
   * @return the created PDO
   */
  default <T extends PersistentDomainObject<T>> T on(Class<T> clazz) {
    return PdoFactory.getInstance().create(clazz, getDomainContext());
  }

  /**
   * Creates an operation within the domain context of this object.<br>
   * Same as {@link #on} but for operations.
   *
   * @param <T> the operation type
   * @param clazz the operation interface
   * @return the created operation
   */
  default <T extends Operation<T>> T op(Class<T> clazz) {
    return OperationFactory.getInstance().create(clazz, getDomainContext());
  }

}
