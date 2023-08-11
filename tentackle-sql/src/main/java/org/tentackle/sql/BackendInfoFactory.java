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

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.ServiceFactory;


interface BackendInfoFactoryHolder {
  BackendInfoFactory INSTANCE = ServiceFactory.createService(BackendInfoFactory.class, DefaultBackendInfoFactory.class);
}


/**
 * The backend info factory.
 *
 * @author harald
 */
public interface BackendInfoFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static BackendInfoFactory getInstance() {
    return BackendInfoFactoryHolder.INSTANCE;
  }


  /**
   * Creates a backend info from a backend.<br>
   * The info cannot be used to connect.
   *
   * @param backend the backend
   * @return the backend info
   */
  BackendInfo create(Backend backend);

  /**
   * Creates a backend info from a backend name.<br>
   * The info cannot be used to connect.
   *
   * @param backendName the backend name
   * @return the backend info
   */
  BackendInfo create(String backendName);

  /**
   * Creates a backend info.<br>
   * This info is able to create a connection.
   *
   * @param url the backend url
   * @param user the username
   * @param password the password
   * @param schemas the optional schemas, null if no schema check
   * @return the backend info
   */
  BackendInfo create(String url, String user, char[] password, String[] schemas);

  /**
   * Creates a backend info from another one with a different user and password.
   *
   * @param backendInfo the original backend info
   * @param user the username
   * @param password the password
   * @return the backend info
   */
  BackendInfo create(BackendInfo backendInfo, String user, char[] password);

  /**
   * Creates a backend info from backend properties.<br>
   * The info is able to create a connection.
   *
   * @param backendProperties the properties
   * @return the backend info
   */
  BackendInfo create(EncryptedProperties backendProperties);

}
