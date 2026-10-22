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

import org.tentackle.dbms.DefaultConnectionManager;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A connection manager for XA connections.
 * <p>
 * Works like the {@link DefaultConnectionManager}, i.e. each session gets its own connection,
 * but the connections are {@link XaManagedConnection}s that can be enlisted in global transactions.
 * The physical XA connections are created via {@link BackendInfo#connectXA()}.
 * <p>
 * Outside a global transaction, the connections work like any other connection, including local transactions.
 * <p>
 * For application servers with many sessions, see {@link XaMpxConnectionManager}.
 *
 * @author harald
 * @see JtaTransactionSupport
 */
public class XaConnectionManager extends DefaultConnectionManager {

  private final XaConnectionFactory connectionFactory = new XaConnectionFactory();


  /**
   * Creates a new XA connection manager.
   *
   * @param name the name of the connection manager
   * @param iniSize the initial size of the session table
   * @param maxSize the maximum number of connections, 0 = unlimited (dangerous!)
   * @param idOffset the offset for session IDs (&gt; 0)
   * @param minMinutes minimum minutes a connection should be used, 0 to keep the connection forever
   * @param maxMinutes maximum minutes a connection should be used
   */
  public XaConnectionManager(String name, int iniSize, int maxSize, int idOffset, int minMinutes, int maxMinutes) {
    super(name, iniSize, maxSize, idOffset, minMinutes, maxMinutes);
  }

  /**
   * Creates a connection manager with the defaults of {@link DefaultConnectionManager#DefaultConnectionManager()}.
   */
  public XaConnectionManager() {
    this("xa-mgr", 2, 8, 1, 720, 2160);
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
