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

import org.tentackle.common.ServiceFactory;
import org.tentackle.session.Session;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;


interface PdoCacheFactoryHolder {
  PdoCacheFactory INSTANCE = ServiceFactory.createService(PdoCacheFactory.class, DefaultPdoCacheFactory.class);
}


/**
 * Factory for PDO-Caches.<br>
 * The factory keeps a list of all created caches.
 *
 * @author harald
 */
public interface PdoCacheFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoCacheFactory getInstance() {
    return PdoCacheFactoryHolder.INSTANCE;
  }


  /**
   * Creates a PDO-cache.<br>
   * The cache is created with the default strategy {@link PdoCacheStrategy#PRELOAD}.
   *
   * @param <T> the PDO type
   * @param clazz is the PDO-class managed by the cache
   * @param readOnly true if the cache is readonly
   * @return the cache
   * @throws org.tentackle.session.PersistenceException if a cache is already registered for the class
   *         (PDO-caches are singletons per PDO-type)
   */
  <T extends PersistentDomainObject<T>> PdoCache<T> createCache(Class<T> clazz, boolean readOnly);

  /**
   * Creates an index for a PDO-cache.
   *
   * @param name the index name
   * @param selectFunction the function to select a PDO by key
   * @param extractFunction the function to extract the key from a PDO
   * @return the index
   * @param <T> the PDO type
   * @param <C> the key type
   */
  <T extends PersistentDomainObject<T>, C extends Comparable<? super C>> PdoCacheIndex<T, C> createCacheIndex(
    String name, BiFunction<DomainContext, C, T> selectFunction, Function<T, C> extractFunction);


  /**
   * Removes all objects in ALL caches that refer to a given session.<br>
   * Useful after having closed a session in a TRIP-Server, for example.
   *
   * @param session the session (probably closed)
   */
  void removeObjectsForSessionInAllCaches(Session session);


  /**
   * Gets all registered caches.
   *
   * @return a snapshot of all registered caches
   */
  Collection<PdoCache<? extends PersistentDomainObject<?>>> getAllCaches();


  /**
   * Checks whether a PDO-class provides a cache.
   *
   * @param <T> the PDO class type
   * @param clazz the class
   * @return the cache, null if none
   */
  <T extends PersistentDomainObject<T>> PdoCache<T> getCache(Class<T> clazz);

}
