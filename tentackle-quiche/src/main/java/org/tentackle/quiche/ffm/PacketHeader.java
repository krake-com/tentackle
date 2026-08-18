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

import java.util.HexFormat;

/**
 * The part of a QUIC packet header a server can read before it has a connection for the packet.
 *
 * @param version the QUIC version, 0 for a short header packet
 * @param type the packet type, see the {@code TYPE_} constants
 * @param sourceConnectionId the sender's connection ID, empty for a short header packet
 * @param destinationConnectionId the connection ID the packet is addressed to
 * @param token the address validation token, empty unless this is an initial packet following a retry
 */
public record PacketHeader(int version, int type,
                           byte[] sourceConnectionId, byte[] destinationConnectionId, byte[] token) {

  /**
   * An initial packet, i.e., the start of a connection.
   */
  public static final int TYPE_INITIAL = 1;

  /**
   * A retry packet.
   */
  public static final int TYPE_RETRY = 2;

  /**
   * A handshake packet.
   */
  public static final int TYPE_HANDSHAKE = 3;

  /**
   * A zero-RTT packet.
   */
  public static final int TYPE_ZERO_RTT = 4;

  /**
   * A short header packet, i.e., one of an established connection.
   */
  public static final int TYPE_SHORT = 5;

  /**
   * A version negotiation packet.
   */
  public static final int TYPE_VERSION_NEGOTIATION = 6;


  /**
   * Tells whether this is an initial packet.
   *
   * @return true if initial
   */
  public boolean isInitial() {
    return type == TYPE_INITIAL;
  }

  @Override
  public String toString() {
    HexFormat hex = HexFormat.of();
    return "PacketHeader[version=0x" + Integer.toHexString(version) + ", type=" + type +
           ", scid=" + hex.formatHex(sourceConnectionId) +
           ", dcid=" + hex.formatHex(destinationConnectionId) +
           ", token=" + token.length + " bytes]";
  }

}
