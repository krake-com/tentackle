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

package org.tentackle.jta;

import org.tentackle.dbms.ManagedConnection;
import org.tentackle.dbms.MpxConnectionManager;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A multiplexing connection manager for XA connections.
 * <p>
 * Works like the {@link MpxConnectionManager}, i.e. N sessions share M &lt; N connections,
 * but the connections are {@link XaManagedConnection}s that can be enlisted in global transactions.
 * A session taking part in a global transaction stays attached to its connection until the
 * global transaction completes. Only then is the connection available to other sessions.
 *
 * @author harald
 * @see JtaTransactionSupport
 */
public class XaMpxConnectionManager extends MpxConnectionManager {

  private final XaConnectionFactory connectionFactory = new XaConnectionFactory();


  /**
   * Creates a new XA multiplexing connection manager.
   *
   * @param name the name of the connection manager
   * @param backendInfo the backend info to use for creating connections
   * @param maxSessions the maximum number of session instances, 0 = no limit
   * @param idOffset the offset for the session ids (&gt; 0)
   * @param iniSize the initial size of the connection pool
   * @param incSize the number of connections to add if all in use
   * @param minSize the minimum number of connections
   * @param maxSize the maximum number of connections
   * @param minMinutes minimum minutes a connection should be used
   * @param maxMinutes maximum minutes a connection should be used
   */
  public XaMpxConnectionManager(String name, BackendInfo backendInfo, int maxSessions, int idOffset, int iniSize,
                                int incSize, int minSize, int maxSize, int minMinutes, int maxMinutes) {
    super(name, backendInfo, maxSessions, idOffset, iniSize, incSize, minSize, maxSize, minMinutes, maxMinutes);
  }

  /**
   * Creates a connection manager with the defaults of {@link MpxConnectionManager#MpxConnectionManager(BackendInfo, int)}.
   *
   * @param backendInfo backend info to use for creating connections
   * @param idOffset the offset for connection ids (&gt; 0)
   */
  public XaMpxConnectionManager(BackendInfo backendInfo, int idOffset) {
    this("xa-mpx-mgr", backendInfo, 1000, idOffset, 8, 2, 4, 40, 720, 2160);
  }


  @Override
  protected Connection connect(BackendInfo backendInfo) throws SQLException {
    return connectionFactory.connect(backendInfo);
  }

  @Override
  protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
    return connectionFactory.createManagedConnection(this, backend, versionInfo, connection);
  }

  @Override
  protected void closeUnmanagedConnection(Connection connection) {
    super.closeUnmanagedConnection(connection);
    connectionFactory.closeUnmanagedConnection(connection);
  }

}
