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

import org.tentackle.common.ServiceFactory;

import java.util.Collection;


interface BackendFactoryHolder {
  BackendFactory INSTANCE = ServiceFactory.createService(BackendFactory.class, DefaultBackendFactory.class);
}


/**
 * The backend factory.
 *
 * @author harald
 */
public interface BackendFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static BackendFactory getInstance() {
    return BackendFactoryHolder.INSTANCE;
  }


  /**
   * Creates a backend by jdbc url.
   *
   * @param url the connection url
   * @return the backend, never null
   * @throws BackendException if no such backend
   */
  Backend getBackendByUrl(String url);


  /**
   * Creates a backend by name.
   *
   * @param name the backend name
   * @return the backend, never null
   * @throws BackendException if no such backend
   */
  Backend getBackendByName(String name);


  /**
   * Creates a collection of all backends.<br>
   * Skips backends marked as deprecated.
   *
   * @return the collection of all backends
   * @throws BackendException if retrieving all configured backends failed
   * @see Backend#isDeprecated()
   */
  Collection<Backend> getAllBackends();

  /**
   * Creates a collection of backends via a comma-separated list of backend names.<br>
   * There are some special names as well:
   * <ul>
   *   <li>all: all non-deprecated backends</li>
   *   <li>none: does not match any backend</li>
   *   <li>deprecated: all deprecated backends</li>
   * </ul>
   * The returned list contains each backend only once.
   *
   * @param backendNames the backend names, null or empty if none
   * @return the backends found, empty if none
   * @throws BackendException if retrieving backends failed
   */
  Collection<Backend> getBackends(String backendNames);

}
