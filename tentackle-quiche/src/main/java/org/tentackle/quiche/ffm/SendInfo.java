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

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetSocketAddress;

/**
 * The {@code quiche_send_info} structure, filled by quiche for every packet it wants to be sent.
 * <p>
 * It carries the addresses the packet belongs to &mdash; which is what makes connection migration work,
 * since the destination is not necessarily the one the connection started with &mdash; and the time the
 * packet should go out at, which is quiche's pacing decision. Pacing is not implemented: packets are sent
 * as soon as quiche produces them, which is allowed and merely forgoes the smoothing.
 * <p>
 * Unlike {@link RecvInfo} the addresses are embedded, not referenced, so no extra buffers are needed.
 * An instance is reused for every packet of a connection.
 */
public final class SendInfo {

  /**
   * The layout of {@code struct timespec}, 16 bytes on every supported platform.
   * <p>
   * Modeled after LP64 (Linux, macOS). On Windows, {@code tv_nsec} is a 32-bit {@code long} followed by
   * 4 bytes of padding, which makes no difference to the size and alignment this layout is needed for.
   * The field itself is never read, see the class comment.
   */
  public static final MemoryLayout TIMESPEC = MemoryLayout.structLayout(
    ValueLayout.JAVA_LONG.withName("tv_sec"),
    ValueLayout.JAVA_LONG.withName("tv_nsec"));

  /**
   * The layout of {@code quiche_send_info}.
   */
  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    SockAddr.STORAGE.withName("from"),
    ValueLayouts.SOCKLEN_T.withName("from_len"),
    MemoryLayout.paddingLayout(4),
    SockAddr.STORAGE.withName("to"),
    ValueLayouts.SOCKLEN_T.withName("to_len"),
    MemoryLayout.paddingLayout(4),
    TIMESPEC.withName("at"));

  private static final long TO_OFFSET = SockAddr.STORAGE_SIZE + 8;


  private final MemorySegment segment;

  /**
   * Allocates a send info structure.
   *
   * @param arena the arena owning the memory
   */
  public SendInfo(Arena arena) {
    segment = arena.allocate(LAYOUT);
  }

  /**
   * Gets the local port the packet is to be sent from, as quiche last filled it in.
   * <p>
   * A connection that migrated, or is probing a new path, sends from more than one local address, and
   * each packet must leave through the socket bound to the address it names. The port alone tells which
   * of an endpoint's sockets that is, and reading it allocates nothing, unlike decoding the address.
   *
   * @return the source port
   */
  public int getFromPort() {
    // sin_port and sin6_port both sit at offset 2, in network byte order
    return (Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, 2)) << 8)
         | Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, 3));
  }

  /**
   * Gets the address the packet is to be sent to, as quiche last filled it in.
   *
   * @return the destination address
   */
  public InetSocketAddress getTo() {
    return SockAddr.decode(segment.asSlice(TO_OFFSET, SockAddr.STORAGE_SIZE));
  }

  /**
   * Gets the structure to pass to {@code quiche_conn_send}.
   *
   * @return the segment
   */
  public MemorySegment getSegment() {
    return segment;
  }

}
