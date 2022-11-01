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

package org.tentackle.dbms.rmi;

import org.tentackle.common.ServiceFactory;


interface RemoteDelegateLocatorHolder {
  RemoteDelegateLocator INSTANCE = ServiceFactory.createService(
              RemoteDelegateLocator.class, DbRemoteDelegateLocator.class);
}



/**
 * Finds the remote delegate classes for a given class.
 *
 * @author harald
 */
public interface RemoteDelegateLocator {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static RemoteDelegateLocator getInstance() {
    return RemoteDelegateLocatorHolder.INSTANCE;
  }

  /**
   * The result.
   */
  interface Result {

    /**
     * Gets the effectively serviced class.
     *
     * @return the serviced class
     */
    Class<?> effectiveClass();

    /**
     * Gets the remote delegate interface for a class.
     *
     * @return the remote delegate interface
     */
    Class<RemoteDelegate> remoteDelegate();

    /**
     * Gets the remote delegate implementation for a class.
     *
     * @return the remote delegate class
     */
    Class<RemoteDelegateImpl<?>> remoteDelegateImpl();

  }



  /**
   * Finds the remote delegate interface and implementation for a class.
   *
   * @param clazz the serviced class
   * @return the remote delegate classes
   * @throws java.lang.ClassNotFoundException if no such interface
   */
  Result findRemoteDelegate(Class<?> clazz) throws ClassNotFoundException;

}
