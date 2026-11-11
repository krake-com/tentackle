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

package org.tentackle.quiche.ffm;

import java.net.InetSocketAddress;

/**
 * A {@code quiche_path_event}: something happened to one of a connection's network paths.
 * <p>
 * Copied out of the native event right away, so it outlives the call that retrieved it.
 *
 * @param type what happened
 * @param local the local address of the path, null for {@link Type#REUSED_SOURCE_CONNECTION_ID}
 * @param peer the peer address of the path, null for {@link Type#REUSED_SOURCE_CONNECTION_ID}
 */
public record PathEvent(Type type, InetSocketAddress local, InetSocketAddress peer) {

  /**
   * The event types, in the order of {@code enum quiche_path_event_type}.
   */
  public enum Type {

    /**
     * The server saw a packet on a path it did not know yet. Server side only.
     */
    NEW,

    /**
     * The path has been validated, i.e., the peer answered a PATH_CHALLENGE on it.
     */
    VALIDATED,

    /**
     * The path could not be validated and will not be used.
     */
    FAILED_VALIDATION,

    /**
     * The path has been closed and is unusable.
     */
    CLOSED,

    /**
     * The peer reused a connection ID on another path.
     */
    REUSED_SOURCE_CONNECTION_ID,

    /**
     * The peer moved its traffic to a validated path. Server side only.
     */
    PEER_MIGRATED,

    /**
     * The path MTU discovery found a new maximum packet size. Only if PMTU discovery is enabled.
     */
    PMTU_UPDATED;

    /**
     * Maps the native enum value.
     *
     * @param value the value of {@code quiche_path_event_type()}
     * @return the type
     * @throws QuicheException if the value is unknown
     */
    public static Type of(int value) {
      Type[] types = values();
      if (value < 0 || value >= types.length) {
        throw new QuicheException("unknown path event type " + value);
      }
      return types[value];
    }
  }

}
