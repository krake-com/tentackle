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
import java.util.List;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.reflect.MethodInvocationCache;
import org.tentackle.reflect.MethodInvocationCacheResult;

/**
 * The default select cache implementation.
 *
 * @author harald
 */
public class DefaultSelectCache implements SelectCache {

  private final MethodInvocationCache methodCache;
  private final IdentifiableMap<PersistentDomainObject<?>> pdoMap;
  private boolean used;

  /**
   * Creates a select cache.
   * @param pdoMap
   */
  public DefaultSelectCache(IdentifiableMap<PersistentDomainObject<?>> pdoMap) {
    this.pdoMap = pdoMap;
    methodCache = new MethodInvocationCache();
  }


  @Override
  public Object select(Method method, PersistentDomainObject<?> proxy, Object[] args) {
    used = true;
    MethodInvocationCacheResult result = methodCache.invoke(method, proxy.getPersistenceDelegate(), args);
    Object obj = result.object;
    if (obj instanceof PersistentDomainObject) {
      PersistentDomainObject<?> pdo = (PersistentDomainObject<?>) obj;
      if (result.cached) {
        PersistentDomainObject<?> cachedPdo = pdoMap.get(pdo.getEffectiveClass(), pdo.getId());
        if (cachedPdo != null) {
          obj = cachedPdo;    // replace with cached value
        }
      }
      else {
        // append to cache
        pdoMap.add(pdo);
      }
    }
    else if (obj instanceof List) {
      @SuppressWarnings("unchecked")
      List<PersistentDomainObject<?>> pdoList = (List<PersistentDomainObject<?>>) obj;
      if (!pdoList.isEmpty()) {
        int i = 0;
        for (PersistentDomainObject<?> pdo: pdoList) {
          if (pdo != null) {
            if (result.cached) {
              PersistentDomainObject<?> cachedPdo = pdoMap.get(pdo.getEffectiveClass(), pdo.getId());
              if (cachedPdo != null) {
                pdoList.set(i, cachedPdo);    // replace with cached value
              }
            }
            else  {
              // append to cache
              pdoMap.add(pdo);
            }
          }
          ++i;
        }
      }
    }
    return obj;
  }

  @Override
  public void clear() {
    if (used) {
      methodCache.clear();
      pdoMap.clear();
      used = false;
    }
  }

}
