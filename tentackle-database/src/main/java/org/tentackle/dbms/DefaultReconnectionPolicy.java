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

import org.tentackle.io.ReconnectionPolicy;

/**
 * The default reconnection policy.<br>
 * The policy will reconnect with a fixed interval.
 */
public class DefaultReconnectionPolicy implements ReconnectionPolicy<Db> {

  private final boolean blocking;
  private final long millis;

  /**
   * Creates the policy.
   *
   * @param blocking true if blocking
   * @param millis the fixed reconnection interval
   */
  public DefaultReconnectionPolicy(boolean blocking, long millis) {
    this.blocking = blocking;
    this.millis = millis;
  }

  @Override
  public String toString() {
    return "default reconnection policy: blocking=" + blocking + ", millis=" + millis + " ms";
  }

  @Override
  public boolean isBlocking() {
    return blocking;
  }

  @Override
  public long timeToReconnect() {
    return millis;
  }

  @Override
  public void reconnect(Db resource) {
    resource.reOpen();
  }

}
