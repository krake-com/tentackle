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
 * Provider for local or remote session pools.
 * <p>
 * Implemented by server applications. A server serving remote clients takes the sessions for its
 * clients from the single-user {@link #getSessionPool() session pool}, if available.
 * Otherwise, typically for a middle tier connected to another server, from the
 * {@link #getRemoteSessionPool() multi-user pool}, which keeps the sessions per client user.
 * If neither is available, a new session is created for each client.
 *
 * @author harald
 */
public interface SessionPoolProvider {

  /**
   * Gets the single-user session pool, shared by all clients.
   *
   * @return the pool, null if none available
   */
  SessionPool getSessionPool();

  /**
   * Gets the multi-user session pool, keeping the sessions per client.
   *
   * @return the pool, null if none available
   */
  MultiUserSessionPool getRemoteSessionPool();

}
