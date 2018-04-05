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


package org.tentackle.sql;

import java.util.Collection;
import org.tentackle.common.ServiceFactory;


interface BackendFactory$Singleton {
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
    return BackendFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a backend by jdbc url.
   *
   * @param url the connection url
   * @return the backend
   * @throws BackendException if no such backend
   */
  Backend getBackendByUrl(String url);


  /**
   * Creates a backend by name.
   *
   * @param name the backend name
   * @return the backend
   * @throws BackendException if no such backend
   */
  Backend getBackendByName(String name);


  /**
   * Creates all backends in classpath.
   *
   * @return the collection of all backends
   * @throws BackendException if retrieving all configured backends failed
   */
  Collection<Backend> getAllBackends();

}
