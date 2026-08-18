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

import java.util.Collection;

/**
 * Connection Manager for local connections.<br>
 *
 * JDBC connections are never used directly. Instead, sessions refer to a ConnectionManager
 *  to use a connection. The usage of a connection is initiated by an attach-operation.
 * After completion of the task (transaction, one-shot, etc...), the session will be detached.
 * The connection manager as a broker in between the session and a connection gives the opportunity to
 * multiplex connections. This is especially useful in application servers.
 * Note that connection managers are not used in remote sessions.
 *
 * @author harald
 */
public interface ConnectionManager {

  /**
   * The manager's name.
   *
   * @return the name
   */
  String getName();

  /**
   * Logs in a session.<br>
   * It is up to the connection manager how to verify whether the session is allowed to open,
   * a real connection is initiated, or just an application level authorization
   * is performed. Note that the manager may recycle the session-ID after a
   * session is logged out.
   * <p>
   * Throws a {@link org.tentackle.session.PersistenceException} if the login failed.
   *
   * @param session the session to login
   * @return the unique ID of the session (&gt; 0)
   */
  int login(Db session);

  /**
   * Logs out a session.<br>
   * The session is not allowed to attach anymore. If the session is still attached,
   * a rollback of any pending transaction is done and an exception thrown.
   * <p>
   * Throws a {@link org.tentackle.session.PersistenceException} if the logout failed.
   *
   * @param session the session to logout
   */
  void logout(Db session);

  /**
   * Cleans up any resources for the given session ID.<br>
   * Connection managers should not keep references to the sessions.
   * Instead, they map connections via a session ID which is unique for each session.
   * If, for whatever reason, the session is garbage collected (see {@link java.lang.ref.Cleaner}),
   * the connection manager must be notified that the session ID is no longer valid and the
   * connection is no more associated with a session.
   *
   * @param sessionId the session ID given by the connection manager
   * @param connection the connection
   */
  void cleanup(int sessionId, ManagedConnection connection);

  /**
   * Gets the maximum number of allowed sessions managed by this manager.
   *
   * @return max. number of concurrent logins, 0 = unlimited
   */
  int getMaxSessions();

  /**
   * Gets the minimum session id.
   *
   * @return the lowest valid session ID.
   * @see #getMaxSessions()
   */
  int getSessionIdOffset();

  /**
   * Gets the number of sessions.
   *
   * @return the number of concurrent logins
   */
  int getNumSessions();

  /**
   * Shuts down this connection manager.<br>
   * All connections are closed and the threads stopped.
   * Application servers should invoke this method when shut down.
   */
  void shutdown();

  /**
   * Attaches a {@link Db} to a connection.<br>
   * A Db must be attached before it can use any statements.
   * The framework will attach at the beginning of a transaction or when getting a prepared
   * statement or when getting a one-shot non-prepared statement.
   * Note that attachments can be nested to any depth, i.e., only the first attach operation really binds
   * the connection to the Db.
   * <p>
   * Upon return, the session will reference the connection.
   * <p>
   * Throws a {@link org.tentackle.session.PersistenceException} if attach failed.
   *
   * @param session the session
   */
  void attach(Db session);

  /**
   * Detaches a connection from a {@link Db}.<br>
   * A Db must be detached to release the connection for use of other Db instances.
   * The framework will detach the db on every commit or rollback, after executeUpdate
   * or after a ResultSet is closed for an executeQuery.
   * Note that attachments can be nested to any depth, i.e., only the last detach operation really
   * unbinds the connection from the Db.
   * <p>
   * Throws a {@link org.tentackle.session.PersistenceException} if detach failed.
   *
   * @param session the session
   */
  void detach(Db session);

  /**
   * Detaches but also scans for pending statements to clean up.
   * <p>
   * This will detach at any nesting level!
   *
   * @param session the session
   */
  void forceDetach(Db session);

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
