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
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

/**
 * A managed connection that can be enlisted in global transactions.
 * <p>
 * Wraps the logical connection of a physical {@link XAConnection}. The physical connection
 * provides the {@link XAResource} and is closed together with the managed connection.
 *
 * @author harald
 */
public class XaManagedConnection extends ManagedConnection {

  private static final Logger LOGGER = Logger.get(XaManagedConnection.class);

  private final XAConnection xaConnection;    // the physical connection
  private XAResource xaResource;              // the XA resource, lazily retrieved
  private boolean xaClosed;                   // true if the physical connection is closed


  /**
   * Creates an XA managed connection.
   *
   * @param manager the connection manager
   * @param backend the backend
   * @param backendVersionInfo the backend's database version
   * @param connection the logical connection, i.e. {@code xaConnection.getConnection()}
   * @param xaConnection the physical XA connection
   */
  public XaManagedConnection(ConnectionManager manager, Backend backend, BackendVersionInfo backendVersionInfo,
                             Connection connection, XAConnection xaConnection) {
    super(manager, backend, backendVersionInfo, connection);
    this.xaConnection = Objects.requireNonNull(xaConnection, "xaConnection");
  }


  /**
   * Gets the physical XA connection.
   *
   * @return the XA connection
   */
  public XAConnection getXAConnection() {
    return xaConnection;
  }

  /**
   * Gets the XA resource to enlist in a global transaction.
   *
   * @return the XA resource
   */
  public synchronized XAResource getXAResource() {
    if (xaResource == null) {
      try {
        xaResource = xaConnection.getXAResource();
      }
      catch (SQLException sx) {
        throw createFromSqlException("cannot retrieve the XA resource", sx);
      }
    }
    return xaResource;
  }

  /**
   * Closes the logical and the physical connection.<br>
   * Closing an already closed connection is allowed.
   */
  @Override
  public void close() {
    try {
      super.close();
    }
    finally {
      closeXAConnection();
    }
  }


  /**
   * Closes the physical connection.
   */
  private synchronized void closeXAConnection() {
    if (!xaClosed) {
      xaClosed = true;
      try {
        xaConnection.close();
      }
      catch (SQLException | RuntimeException ex) {
        LOGGER.warning("closing XA connection of " + this + " failed", ex);
      }
    }
  }

}
