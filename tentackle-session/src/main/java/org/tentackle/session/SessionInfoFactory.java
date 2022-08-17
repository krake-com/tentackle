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


package org.tentackle.session;

import org.tentackle.common.ServiceFactory;

import java.util.Properties;


interface SessionInfoFactoryHolder {
  SessionInfoFactory INSTANCE = ServiceFactory.createService(SessionInfoFactory.class);
}


/**
 * Factory for {@link SessionInfo}.
 *
 * @author harald
 */
public interface SessionInfoFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static SessionInfoFactory getInstance() {
    return SessionInfoFactoryHolder.INSTANCE;
  }


  /**
   * Creates a session info from a username, password and
   * property file holding the connection parameters.
   *
   * @param username is the name of the user, null if {@code System.getProperty("user.name")}
   * @param password is the password, null if none
   * @param propertiesName name of additional properties resources, null if default
   * @return the created session info
   */
  SessionInfo create(String username, char[] password, String propertiesName);

  /**
   * Creates a session info from a property file holding the connection parameters.
   *
   * @param propertiesName name of additional properties resource, null if default
   * @return the created session info
   */
  SessionInfo create(String propertiesName);

  /**
   * Creates a session info from a properties object.
   *
   * @param properties the properties
   * @return the created session info
   */
  SessionInfo create(Properties properties);

  /**
   * Creates a session info from the default property file holding the connection parameters.
   *
   * @return the created session info
   */
  SessionInfo create();

}
