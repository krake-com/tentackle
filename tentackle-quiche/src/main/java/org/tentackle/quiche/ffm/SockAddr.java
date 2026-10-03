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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Conversion between {@link InetSocketAddress} and the C {@code sockaddr} structures quiche expects.
 * <p>
 * Quiche needs the local and the peer address for every packet it processes, so this sits in the hot path
 * of both directions.
 * <p>
 * The layout is <em>not</em> the same everywhere, which is the reason this class exists rather than a
 * {@link MemoryLayout} constant:
 * <ul>
 *   <li>The BSDs, and with them macOS, begin the structure with a one-byte length followed by a one-byte
 *       address family. Linux and Windows have no length byte and a two-byte family instead.</li>
 *   <li>{@code AF_INET6} is 10 on Linux, 30 on macOS, 23 on Windows, 28 on FreeBSD and DragonFly BSD, and 24
 *       on OpenBSD and NetBSD. Only {@code AF_INET} happens to be 2 everywhere.</li>
 *   <li>The family is in host byte order, while the port and the address bytes are in network byte order.
 *       Mixing those up yields an address that looks plausible and is wrong.</li>
 * </ul>
 */
public final class SockAddr {

  /**
   * The size of {@code struct sockaddr_storage}, which is large enough to hold any address family.
   * <p>
   * 128 bytes on Linux, macOS, and Windows alike.
   */
  public static final long STORAGE_SIZE = 128;

  /**
   * The layout of {@code struct sockaddr_storage}.
   * <p>
   * Modeled as opaque bytes: nothing here interprets the structure other than this class, and its real
   * members differ per platform. The 8-byte alignment is the one the C structure has, courtesy of its
   * {@code __ss_align} member.
   */
  public static final MemoryLayout STORAGE =
    MemoryLayout.sequenceLayout(STORAGE_SIZE, ValueLayout.JAVA_BYTE).withByteAlignment(8);

  /**
   * The size of {@code struct sockaddr_in}.
   */
  public static final int SOCKADDR_IN_SIZE = 16;

  /**
   * The size of {@code struct sockaddr_in6}.
   */
  public static final int SOCKADDR_IN6_SIZE = 28;

  /**
   * {@code AF_INET}, the same value on every supported platform.
   */
  public static final int AF_INET = 2;


  /**
   * True if the structure starts with a one-byte length, as on the BSDs and macOS.
   */
  private static final boolean HAS_SA_LEN;

  /**
   * {@code AF_INET6}, which differs per platform.
   */
  private static final int AF_INET6;

  static {
    String osName = System.getProperty("os.name", "");
    HAS_SA_LEN = hasSaLen(osName);
    AF_INET6 = afInet6(osName);
  }

  /**
   * Tells whether the {@code sockaddr} of an operating system starts with a length byte.
   *
   * @param osName the value of the system property {@code os.name}
   * @return true for macOS and the BSDs
   */
  static boolean hasSaLen(String osName) {
    String name = osName.toLowerCase(Locale.ROOT);
    return name.startsWith("mac") || name.startsWith("darwin") || name.contains("bsd") || name.contains("dragonfly");
  }

  /**
   * Gets the value of {@code AF_INET6} for an operating system.
   *
   * @param osName the value of the system property {@code os.name}
   * @return the address family
   */
  static int afInet6(String osName) {
    String name = osName.toLowerCase(Locale.ROOT);
    if (name.startsWith("mac") || name.startsWith("darwin")) {
      return 30;
    }
    if (name.startsWith("windows")) {
      return 23;
    }
    if (name.contains("freebsd") || name.contains("dragonfly")) {
      return 28;
    }
    if (name.contains("openbsd") || name.contains("netbsd")) {
      return 24;
    }
    return 10;    // Linux
  }


  /**
   * Tells whether this platform's {@code sockaddr} starts with a length byte.
   *
   * @return true on the BSDs and macOS, false on Linux and Windows
   */
  public static boolean hasSaLen() {
    return HAS_SA_LEN;
  }

  /**
   * Gets this platform's {@code AF_INET6} constant.
   *
   * @return the address family
   */
  public static int getAfInet6() {
    return AF_INET6;
  }

  /**
   * Writes a socket address into a {@code struct sockaddr_storage}.
   * <p>
   * The unused tail of the structure is zeroed, so that quiche never compares against stale bytes when it
   * decides whether two addresses are the same path.
   *
   * @param target the segment to write to, at least {@link #STORAGE_SIZE} bytes long
   * @param address the address to write
   * @return the length of the written structure, i.e., the {@code socklen_t} that goes with it
   * @throws IllegalArgumentException if the address is unresolved
   */
  public static int encode(MemorySegment target, InetSocketAddress address) {
    InetAddress inetAddress = address.getAddress();
    if (inetAddress == null) {
      throw new IllegalArgumentException("unresolved address " + address);
    }
    byte[] bytes = inetAddress.getAddress();
    boolean ipv6 = bytes.length == 16;
    int size = ipv6 ? SOCKADDR_IN6_SIZE : SOCKADDR_IN_SIZE;
    int family = ipv6 ? AF_INET6 : AF_INET;

    target.asSlice(0, STORAGE_SIZE).fill((byte) 0);

    if (HAS_SA_LEN) {
      target.set(ValueLayout.JAVA_BYTE, 0, (byte) size);
      target.set(ValueLayout.JAVA_BYTE, 1, (byte) family);
    }
    else {
      // host byte order, unlike everything else in this structure
      target.set(ValueLayout.JAVA_SHORT, 0, (short) family);
    }

    // the port is big endian regardless of the platform
    int port = address.getPort();
    target.set(ValueLayout.JAVA_BYTE, 2, (byte) (port >>> 8));
    target.set(ValueLayout.JAVA_BYTE, 3, (byte) port);

    if (ipv6) {
      // 4..7 is sin6_flowinfo, left at zero
      MemorySegment.copy(bytes, 0, target, ValueLayout.JAVA_BYTE, 8, 16);
      int scopeId = inetAddress instanceof Inet6Address inet6 ? inet6.getScopeId() : 0;
      // sin6_scope_id is in host byte order
      target.set(ValueLayout.JAVA_INT, 24, scopeId);
    }
    else {
      MemorySegment.copy(bytes, 0, target, ValueLayout.JAVA_BYTE, 4, 4);
    }

    return size;
  }

  /**
   * Reads a socket address from a {@code struct sockaddr}.
   *
   * @param source the segment to read from
   * @return the address
   * @throws QuicheException if the address family is not one of {@code AF_INET} / {@code AF_INET6}
   */
  public static InetSocketAddress decode(MemorySegment source) {
    int family = HAS_SA_LEN
                 ? Byte.toUnsignedInt(source.get(ValueLayout.JAVA_BYTE, 1))
                 : Short.toUnsignedInt(source.get(ValueLayout.JAVA_SHORT, 0));

    int port = (Byte.toUnsignedInt(source.get(ValueLayout.JAVA_BYTE, 2)) << 8)
             | Byte.toUnsignedInt(source.get(ValueLayout.JAVA_BYTE, 3));

    byte[] bytes;
    int scopeId = 0;
    if (family == AF_INET) {
      bytes = new byte[4];
      MemorySegment.copy(source, ValueLayout.JAVA_BYTE, 4, bytes, 0, 4);
    }
    else if (family == AF_INET6) {
      bytes = new byte[16];
      MemorySegment.copy(source, ValueLayout.JAVA_BYTE, 8, bytes, 0, 16);
      scopeId = source.get(ValueLayout.JAVA_INT, 24);
    }
    else {
      throw new QuicheException("unsupported address family " + family);
    }

    try {
      InetAddress address = scopeId == 0 ? InetAddress.getByAddress(bytes)
                                         : Inet6Address.getByAddress(null, bytes, scopeId);
      return new InetSocketAddress(address, port);
    }
    catch (UnknownHostException e) {
      // cannot happen: the array length is 4 or 16 by construction
      throw new QuicheException("cannot decode the socket address", e);
    }
  }

  private SockAddr() {
  }

}
