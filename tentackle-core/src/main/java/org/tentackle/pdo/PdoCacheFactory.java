/**
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

import java.util.Collection;
import org.tentackle.common.ServiceFactory;


interface PdoCacheFactory$Singleton {
  static PdoCacheFactory INSTANCE = ServiceFactory.createService(
              PdoCacheFactory.class, DefaultPdoCacheFactory.class);
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
    return PdoCacheFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a PDO Cache.<br>
   * Multiple calls on this method will return the same object reference.
   * <p>
   * If the current PDO tracker is a dummy tracker, the tracker
   * will be replaced.
   *
   * @param <T> the data object class
   * @param clazz is the PersistentDomainObject-class managed by the cache.
   * @param preload is true if preload all objects in domain context of cache.
   * @param readOnly true if cache is readonly (shared)
   * @param checkSecurity true if check read permission
   * @return the cache
   */
  <T extends PersistentDomainObject<T>> PdoCache<T> createCache(
          Class<T> clazz, boolean preload, boolean readOnly, boolean checkSecurity);

  /**
   * Removes all objects in ALL caches that refer to a given db.
   * Useful after having closed a db-connection in an RMI-Server, for example.
   *
   * @param session the session (probably closed)
   */
  void removeObjectsForSessionInAllCaches(Session session);


  /**
   * Gets a list of all registered caches.
   *
   * @return all registered caches
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
