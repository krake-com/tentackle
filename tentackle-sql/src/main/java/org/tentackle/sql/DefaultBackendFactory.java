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

package org.tentackle.sql;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of a backend factory.
 *
 * @author harald
 */
@Service(BackendFactory.class)
public class DefaultBackendFactory implements BackendFactory {

  /**
   * All backend singletons.
   * One per backend type.
   */
  private final List<Backend> backends;


  /**
   * Creates a backend factory.<br>
   * Collects all Backend services.
   */
  public DefaultBackendFactory() {
    backends = new ArrayList<>();
    try {
      for (Class<Backend> backendClass: ServiceFactory.getServiceFinder().findServiceProviders(Backend.class)) {
        backends.add(backendClass.getDeclaredConstructor().newInstance());
      }
      // we need a predictable evaluation order (backends sorted by name)
      Collections.sort(backends, Comparator.comparing(backend -> backend.getName()));
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           InvocationTargetException | NoSuchMethodException nfe) {
      throw new BackendException("supported backends could not be determined", nfe);
    }
  }


  @Override
  public Backend getBackendByUrl(String url) {
    for (Backend backend: backends) {
      if (backend.isMatchingUrl(url)) {
        return backend;
      }
    }
    throw new BackendException("no backend for jdbc-url=" + url);
  }

  @Override
  public Backend getBackendByName(String name) {
    for (Backend backend: backends) {
      if (backend.isMatchingName(name)) {
        return backend;
      }
    }
    throw new BackendException("no backend for name=" + name);
  }

  @Override
  public Collection<Backend> getAllBackends() {
    return backends;
  }

}
