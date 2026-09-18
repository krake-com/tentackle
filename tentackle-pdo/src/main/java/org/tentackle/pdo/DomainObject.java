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
import org.tentackle.session.SessionProvider;

/**
 * The domain interface.
 *
 * @param <T> the PDO type
 * @author harald
 */
public interface DomainObject<T extends PersistentDomainObject<T>>
       extends DomainDelegate<T>, PdoProvider<T>, DomainContextProvider, SessionProvider {

  /**
   * Returns whether PDO provides a unique domain key.<br>
   * Use this method to prevent a DomainException.
   *
   * @return true if provided, else no unique domain key
   */
  boolean isUniqueDomainKeyProvided();

  /**
   * Gets the type of the unique domain key.
   *
   * @return the unique domain key type
   */
  Class<?> getUniqueDomainKeyType();

  /**
   * Gets the unique key of this object.<br>
   * The unique key is the domain-key, not the technical object id.
   *
   * @return the unique domain key
   */
  @NotInterceptable
  Object getUniqueDomainKey();

  /**
   * Sets the unique key of this object.<br>
   * The unique key is the domain-key, not the technical object id.
   *
   * @param domainKey the unique domain key
   */
  void setUniqueDomainKey(Object domainKey);

  /**
   * Finds an object of this class by its unique domain-key.
   * <p>
   * This is not the technical primary key, which is the object id!
   *
   * @param domainKey the primary domain key
   * @return the object found, null if none
   */
  @NotInterceptable
  T findByUniqueDomainKey(Object domainKey);

  /**
   * Gets the human-readable name for one (1) object of this class.
   *
   * @return the name
   */
  String getSingular();

  /**
   * Gets the human-readable name for multiple (&gt; 1) objects of this class.
   *
   * @return the name
   */
  String getPlural();

  /**
   * Gets the generic string representation of the domain object.
   *
   * @return the generic string
   */
  String toGenericString();

}
