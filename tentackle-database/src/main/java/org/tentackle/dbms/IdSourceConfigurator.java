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

package org.tentackle.dbms;

import org.tentackle.common.ServiceFactory;


interface IdSourceConfiguratorHolder {
  IdSourceConfigurator INSTANCE = ServiceFactory.createService(
              IdSourceConfigurator.class, DefaultIdSourceConfigurator.class);
}


/**
 * Configurator for an {@link IdSource}.
 *
 * @author harald
 */
public interface IdSourceConfigurator {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static IdSourceConfigurator getInstance() {
    return IdSourceConfiguratorHolder.INSTANCE;
  }


  /**
   * Creates the ID source from its configuration.
   *
   * @param db the session
   * @param idConfig the configuration, null for the backend's default
   * @return the configured IdSource
   */
  IdSource configure(Db db, String idConfig);

}
