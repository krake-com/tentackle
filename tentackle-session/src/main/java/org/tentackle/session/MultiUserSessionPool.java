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

/**
 * A pool of sessions for multiple session infos.<br>
 * Allows different users to use multiple sessions in parallel.
 *
 * @see SessionPoolFactory
 * @author harald
 */
public interface MultiUserSessionPool {

  /**
   * Gets a session from the pool for a given session info.
   *
   * @param sessionInfo the session info
   * @return the session, never null
   */
  Session get(SessionInfo sessionInfo);


  /**
   * Returns a session back to the pool.<br>
   * Returning a closed session will remove it from the pool.
   *
   * @param session the session
   */
  void put(Session session);


  /**
   * Closes all sessions currently open for a given session info.<br>
   * Useful to force logout for a user.<br>
   * It is not an error, if there were no open sessions for the session info at all.
   *
   * @param sessionInfo the session info
   */
  void close(SessionInfo sessionInfo);


  /**
   * Gets the name of this pool.
   *
   * @return the name
   */
  String getName();


  /**
   * Gets the maximum pool size per session info.
   *
   * @return the max. number of concurrent session instances, 0 = unlimited
   */
  int getMaxSize();

  /**
   * Gets the maximum pool size for all sessions.
   *
   * @return the max. number of concurrent session instances, 0 = unlimited
   */
  int getMaxTotalSize();


  /**
   * Gets the current number of session instances.
   *
   * @return the number of sessions managed by this pool
   */
  int getSize();


  /**
   * Closes all sessions in the pool, cleans up and makes the pool unusable.
   */
  void shutdown();


  /**
   * Returns whether the pool is shutdown.
   *
   * @return true if shutdown
   */
  boolean isShutdown();

}
