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

import org.tentackle.common.Service;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The default Pdo-Cache factory.
 *
 * @author harald
 */
@Service(PdoCacheFactory.class)
public class DefaultPdoCacheFactory implements PdoCacheFactory {


  // all registered caches
  private final Map<Class<? extends PersistentDomainObject<?>>, PdoCache<? extends PersistentDomainObject<?>>> cacheMap;


  /**
   * Creates a cache factory.
   */
  public DefaultPdoCacheFactory() {
    cacheMap = new HashMap<>();
    Pdo.registerGlobalSessionCloseHandler(createCloseHandler());
  }

  /**
   * Creates the session close handler.
   *
   * @return the close handler
   */
  protected SessionCloseHandler createCloseHandler() {
    return new SessionCloseHandler() {

      @Override
      public void beforeClose(Session session) {
        // not used
      }

      @Override
      public void afterClose(Session session) {
        removeObjectsForSessionInAllCaches(session);
      }

    };
  }

  @Override
  public synchronized <T extends PersistentDomainObject<T>> PdoCache<T> createCache(
          Class<T> clazz, boolean readOnly) {
    if (cacheMap.get(clazz) != null) {
      throw new PersistenceException("pdo cache already registered for " + clazz);
    }
    PdoCache<T> cache = new PdoCache<>(clazz, readOnly);
    cacheMap.put(clazz, cache);
    return cache;
  }

  @Override
  public <T extends PersistentDomainObject<T>, C extends Comparable<? super C>> PdoCacheIndex<T, C> createCacheIndex(
    String name, BiFunction<DomainContext, C, T> selectFunction, Function<T, C> extractFunction) {

    return new PdoCacheIndex<>(name) {
      @Override
      public T select(DomainContext context, C key) {
        return selectFunction.apply(context, key);
      }

      @Override
      public C extract(T pdo) {
        return extractFunction.apply(pdo);
      }
    };
  }

  @Override
  public synchronized Collection<PdoCache<? extends PersistentDomainObject<?>>> getAllCaches() {
    return new ArrayList<>(cacheMap.values());    // a copy, since caches may be created while the caller iterates
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T extends PersistentDomainObject<T>> PdoCache<T> getCache(Class<T> clazz) {
    return (PdoCache<T>) cacheMap.get(clazz);
  }

  @Override
  public void removeObjectsForSessionInAllCaches(Session session)  {
    for (PdoCache<? extends PersistentDomainObject<?>> cache: getAllCaches()) {
      cache.removeObjectsForSession(session);
    }
  }
}
