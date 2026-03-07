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

import org.tentackle.common.ServiceFactory;


interface SessionPoolFactoryHolder {
  SessionPoolFactory INSTANCE = ServiceFactory.createService(SessionPoolFactory.class);
}


/**
 * Factory for session pools.
 */
public interface SessionPoolFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static SessionPoolFactory getInstance() {
    return SessionPoolFactoryHolder.INSTANCE;
  }

  /**
   * Creates a single-user session pool.
   *
   * @param name the name of the pool
   * @param session the lead session to get the configuration from
   * @param initialSize the initial pool size
   * @param incrementSize the number of sessions to enlarge the pool if all in use
   * @param minimumSize the minimum number of sessions to keep in pool, &lt; 0 to shut down pool if all sessions closed
   * @param maximumSize the maximum number of sessions, 0 = unlimited
   * @param idleMinutes the idle timeout in minutes to close unused sessions, 0 = unlimited
   * @param usageMinutes the max. used time in minutes, 0 = unlimited
   * @return the pool
   */
  SessionPool create(String name, Session session,
                     int initialSize, int incrementSize, int minimumSize, int maximumSize,
                     long idleMinutes, long usageMinutes);

  /**
   * Creates a multi-user session pool.
   *
   * @param name the name of the pool
   * @param session the lead session to get the low-level configuration from
   * @param maximumSize the maximum number of sessions per session info
   * @param maximumTotalSize the maximum total number of sessions
   * @param idleMinutes the idle timeout in minutes to close unused sessions per session info, 0 = unlimited
   * @param usageMinutes the max. used time in minutes per session info, 0 = unlimited
   * @return the pool
   */
  MultiUserSessionPool create(String name, Session session, int maximumSize, int maximumTotalSize,
                              long idleMinutes, long usageMinutes);

}
