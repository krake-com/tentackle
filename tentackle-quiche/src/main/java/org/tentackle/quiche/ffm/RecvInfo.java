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
import java.net.InetSocketAddress;

/**
 * The {@code quiche_recv_info} structure, telling quiche where a received packet came from and went to.
 * <p>
 * Unlike {@link SendInfo}, this one holds <em>pointers</em> to the addresses rather than the addresses
 * themselves, so an instance owns two {@code sockaddr_storage} buffers alongside the structure and keeps
 * them alive for as long as quiche may look at them.
 * <p>
 * An instance is reused for every packet of a connection: the addresses are rewritten in place, which
 * avoids an allocation per datagram in the receive-path.
 */
public final class RecvInfo {

  /**
   * The layout of {@code quiche_recv_info}.
   */
  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    ValueLayouts.POINTER.withName("from"),
    ValueLayouts.SOCKLEN_T.withName("from_len"),
    MemoryLayout.paddingLayout(4),
    ValueLayouts.POINTER.withName("to"),
    ValueLayouts.SOCKLEN_T.withName("to_len"),
    MemoryLayout.paddingLayout(4));

  private static final long FROM_OFFSET = 0;
  private static final long FROM_LEN_OFFSET = 8;
  private static final long TO_OFFSET = 16;
  private static final long TO_LEN_OFFSET = 24;


  private final MemorySegment segment;    // the structure itself
  private final MemorySegment from;       // the sockaddr_storage the "from" pointer refers to
  private final MemorySegment to;         // the sockaddr_storage the "to" pointer refers to

  /**
   * Allocates a receive-info structure and the two address buffers it points to.
   *
   * @param arena the arena owning the memory
   */
  public RecvInfo(Arena arena) {
    segment = arena.allocate(LAYOUT);
    from = arena.allocate(SockAddr.STORAGE);
    to = arena.allocate(SockAddr.STORAGE);
    segment.set(ValueLayouts.POINTER, FROM_OFFSET, from);
    segment.set(ValueLayouts.POINTER, TO_OFFSET, to);
  }

  /**
   * Sets the addresses of the packet about to be handed to quiche.
   *
   * @param peer the address the packet was received from
   * @param local the local address it was received on
   */
  public void set(InetSocketAddress peer, InetSocketAddress local) {
    segment.set(ValueLayouts.SOCKLEN_T, FROM_LEN_OFFSET, SockAddr.encode(from, peer));
    segment.set(ValueLayouts.SOCKLEN_T, TO_LEN_OFFSET, SockAddr.encode(to, local));
  }

  /**
   * Gets the structure to pass to {@code quiche_conn_recv}.
   *
   * @return the segment
   */
  public MemorySegment getSegment() {
    return segment;
  }

}
