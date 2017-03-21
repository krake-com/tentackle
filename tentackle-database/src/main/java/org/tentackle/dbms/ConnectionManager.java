/**
 * Tentackle - http://www.tentackle.org
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

import java.util.Collection;
import org.tentackle.session.Session;
import org.tentackle.session.SessionManager;


/**
 * Connection Manager for local connections.<br>
 *
 * JDBC connections are never used directly. Instead Db-instances refer to a ConnectionManager
 * in order to use a connection. The usage of a connection is initiated by an attach operation.
 * After completion of the task (transaction, one-shot, etc...) the Db will be detached.
 * The connection manager as a broker in between the Db and a connection gives the opportunity to
 * multiplex connections. This is especially useful in application servers.
 * Note that connection managers are not used in remote Db instances.
 *
 * @author harald
 */
public interface ConnectionManager extends SessionManager {

  /**
   * Attaches a {@link Db} to a connection.<br>
   * A Db must be attached before it can use any statements.
   * The framework will attach at the begining of a transaction or when getting a prepared
   * statement or when getting a one-shot non-prepared statement.
   * Note that attachments can be nested to any depth, i.e. only the first attach really binds
   * the connection to the Db.
   * <p>
   * Upon return the the session will reference the connection.
   *
   * @param session the session
   * @throws org.tentackle.pdo.PersistenceException if attach failed.
   */
  void attach(Session session);


  /**
   * Detaches a connection from a {@link Db}.<br>
   * A Db must be detached to release the connection for use of other Db instances.
   * The framework will detach the db on every commit or rollback, after executeUpdate
   * or after a resultset is closed for an executeQuery.
   * Note that attachments can be nested to any depth, i.e. only the last detach really
   * unbinds the connection from the Db.
   *
   * @param session the session
   * @throws org.tentackle.pdo.PersistenceException if detach failed.
   */
  void detach(Session session);


  /**
   * Detaches but also scans for pending statements to cleanup.
   * <p>
   * This will detach at any nesting level!
   *
   * @param session the session
   */
  void forceDetach(Session session);


  /**
   * Gets the maximum number of connections.
   *
   * @return max. number of concurrent connections, 0 = unlimited
   */
  int getMaxConnections();


  /**
   * Gets the number of open connections.
   *
   * @return the number of connections
   */
  int getNumConnections();

  /**
   * Gets the connections.
   *
   * @return the connection
   */
  Collection<ManagedConnection> getConnections();

}
