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
 * A pool of sessions.
 *
 * @author harald
 */
public interface SessionPool extends ReturnableSessionProvider {

  /**
   * Gets the session info.
   *
   * @return the session info
   */
  SessionInfo getSessionInfo();

  /**
   * Gets the name of this pool.
   *
   * @return the name
   */
  String getName();


  /**
   * Gets the maximum poolsize.
   *
   * @return the max. number of concurrent session instances, 0 = unlimited
   */
  int getMaxSize();


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
