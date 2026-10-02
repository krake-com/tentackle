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

package org.tentackle.ns;

import org.tentackle.common.ServiceFactory;


interface NumberSourceFactoryHolder {
  NumberSourceFactory INSTANCE = ServiceFactory.createService(NumberSourceFactory.class, DefaultNumberSourceFactory.class);
}


/**
 * A factory of number sources.
 *
 * @author harald
 */
public interface NumberSourceFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static NumberSourceFactory getInstance() {
    return NumberSourceFactoryHolder.INSTANCE;
  }


  /**
   * Gets a local number source.
   *
   * @param pool the pool name
   * @param realm the realm, null if none
   * @return the number source
   * @throws NumberSourceException if failed
   */
  NumberSource getNumberSource(String pool, String realm);

}
