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


package org.tentackle.pdo;


/**
 * Session Manager.<br>
 *
 * Each session is assigned to a session manager.
 *
 * @author harald
 */
public interface SessionManager {

  /**
   * The manager's name.
   *
   * @return the name
   */
  String getName();


  /**
   * Logs in a session.<br>
   * It is up to the manager how to verify whether the session is allowed to open,
   * a real connection is initiated or just an application level authorization
   * is performed. Note that the ID may be recycled by the manager after a
   * session is logged out.
   *
   * @param session the session to login
   * @return the unique ID of the session (&gt; 0)
   * @throws PersistenceException if login failed.
   */
  int login(Session session);


  /**
   * Logs out a session.<br>
   * The session is not allowed to attach anymore. If the session is still attached,
   * a rollback of any pending transaction is done and an exception thrown.
   *
   * @param session the session to logout
   * @throws PersistenceException if logout failed.
   */
  void logout(Session session);


  /**
   * Gets the maximum number of allowed sessions managed by this manager.
   *
   * @return max. number of concurrent logins, 0 = unlimited
   */
  int getMaxSessions();


  /**
   * Gets the number of sessions.
   *
   * @return the number of concurrent logins
   */
  int getNumSessions();


  /**
   * Shuts down this session manager.<br>
   * All connections are closed and the threads stopped.
   * Application servers should invoke this method when shut down.
   */
  void shutdown();

}
