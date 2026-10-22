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

import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.sql.XAConnection;

/**
 * Creates the XA connections for the XA connection managers.
 * <p>
 * Implements the connection hooks of {@link org.tentackle.dbms.DefaultConnectionManager}, which are
 * inherited by the {@link org.tentackle.dbms.MpxConnectionManager}:
 * the physical XA connection is created in {@code connect}, and its logical connection is wrapped
 * into an {@link XaManagedConnection} in {@code createManagedConnection}. In between, the physical
 * connection is kept here.
 *
 * @author harald
 * @see XaConnectionManager
 * @see XaMpxConnectionManager
 */
final class XaConnectionFactory {

  private static final Logger LOGGER = Logger.get(XaConnectionFactory.class);

  /**
   * The physical connections of the logical connections created by {@link #connect(BackendInfo)},
   * but not yet wrapped by {@link #createManagedConnection}.
   */
  private final Map<Connection, XAConnection> pendingConnections = new IdentityHashMap<>();


  /**
   * Creates the physical XA connection.
   *
   * @param backendInfo the backend info
   * @return the logical connection in autocommit mode
   * @throws SQLException if the connection could not be established
   */
  Connection connect(BackendInfo backendInfo) throws SQLException {
    XAConnection xaConnection = backendInfo.connectXA();
    try {
      Connection connection = xaConnection.getConnection();
      if (!connection.getAutoCommit()) {
        connection.setAutoCommit(true);   // like BackendInfo.connect()
      }
      synchronized (pendingConnections) {
        pendingConnections.put(connection, xaConnection);
      }
      return connection;
    }
    catch (SQLException | RuntimeException ex) {
      try {
        xaConnection.close();
      }
      catch (SQLException sx) {
        ex.addSuppressed(sx);
      }
      throw ex;
    }
  }

  /**
   * Wraps a logical connection created by {@link #connect(BackendInfo)}.
   *
   * @param manager the connection manager
   * @param backend the backend
   * @param versionInfo the backend's database version
   * @param connection the logical connection
   * @return the XA managed connection
   */
  ManagedConnection createManagedConnection(ConnectionManager manager, Backend backend, BackendVersionInfo versionInfo,
                                            Connection connection) {
    XAConnection xaConnection;
    synchronized (pendingConnections) {
      xaConnection = pendingConnections.remove(connection);
    }
    if (xaConnection == null) {
      throw new PersistenceException(manager + ": connection " + connection + " is not an XA connection");
    }
    try {
      return new XaManagedConnection(manager, backend, versionInfo, connection, xaConnection);
    }
    catch (RuntimeException rx) {
      synchronized (pendingConnections) {
        pendingConnections.put(connection, xaConnection);   // closed by closeUnmanagedConnection
      }
      throw rx;
    }
  }

  /**
   * Closes the physical connection of a logical connection that could not be wrapped.<br>
   * The logical connection must be closed by the caller.
   *
   * @param connection the logical connection
   */
  void closeUnmanagedConnection(Connection connection) {
    XAConnection xaConnection;
    synchronized (pendingConnections) {
      xaConnection = pendingConnections.remove(connection);
    }
    if (xaConnection != null) {
      try {
        xaConnection.close();
      }
      catch (SQLException sx) {
        LOGGER.severe("closing XA connection failed -> ignored", sx);
      }
    }
  }

}
