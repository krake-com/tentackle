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

import org.tentackle.session.SessionInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI remote connection.<br>
 * This is the first remote object passed to the client when it connects
 * to the application server. The connection object handles the login
 * and logout to/from a session. The socket factories used for the
 * connection object are defined by the server. The session, however,
 * and all subsequent remote objects can get different socket factories
 * depending on what the client requests. See {@link RmiServer} for more details.
 *
 * @author harald
 */
public interface RemoteDbConnection extends Remote {

  /**
   * Gets the application-specific version of the server.
   *
   * @return the server version
   * @throws RemoteException if failed
   */
  String getServerVersion() throws RemoteException;


  /**
   * Login to remote RMI-server.
   *
   * @param clientInfo the session information
   * @return the session
   * @throws RemoteException if some error, for example wrong password
   * @see RemoteDbSession#close()
   */
  RemoteDbSession login(SessionInfo clientInfo) throws RemoteException;

}
