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

import org.testng.Reporter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the conversion between {@link InetSocketAddress} and the C {@code sockaddr} structures.
 * <p>
 * The layout differs per platform and a wrong one produces an address that looks plausible while
 * pointing somewhere else, so the byte-level assertions matter as much as the round-trips.
 */
public class SockAddrTest {

  @DataProvider(name = "addresses")
  public Object[][] addresses() throws UnknownHostException {
    return new Object[][] {
      {new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 4711)},
      {new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0)},
      {new InetSocketAddress(InetAddress.getByName("192.168.178.42"), 65535)},
      {new InetSocketAddress(InetAddress.getByName("::1"), 4711)},
      {new InetSocketAddress(InetAddress.getByName("::"), 1)},
      {new InetSocketAddress(InetAddress.getByName("2001:db8::dead:beef"), 443)},
    };
  }

  @Test(dataProvider = "addresses", timeOut = 30000)
  public void testRoundTrip(InetSocketAddress address) {
    Reporter.log("an address survives the trip through a sockaddr unchanged: " + address, true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      int length = SockAddr.encode(segment, address);
      assertEquals(length, address.getAddress().getAddress().length == 4
                           ? SockAddr.SOCKADDR_IN_SIZE : SockAddr.SOCKADDR_IN6_SIZE);
      InetSocketAddress decoded = SockAddr.decode(segment);
      assertEquals(decoded.getAddress(), address.getAddress());
      assertEquals(decoded.getPort(), address.getPort());
    }
  }

  @Test(timeOut = 30000)
  public void testPortIsBigEndian() {
    Reporter.log("the port is in network byte order, regardless of the platform", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      SockAddr.encode(segment, new InetSocketAddress("127.0.0.1", 0x1234));
      assertEquals(segment.get(ValueLayout.JAVA_BYTE, 2), (byte) 0x12);
      assertEquals(segment.get(ValueLayout.JAVA_BYTE, 3), (byte) 0x34);
    }
  }

  @Test(timeOut = 30000)
  public void testFamilyLayoutMatchesThePlatform() {
    Reporter.log("the BSDs put a length byte in front of the family, Linux and Windows do not", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      SockAddr.encode(segment, new InetSocketAddress("127.0.0.1", 1));
      if (SockAddr.hasSaLen()) {
        assertEquals(segment.get(ValueLayout.JAVA_BYTE, 0), (byte) SockAddr.SOCKADDR_IN_SIZE);
        assertEquals(segment.get(ValueLayout.JAVA_BYTE, 1), (byte) SockAddr.AF_INET);
      }
      else {
        assertEquals(segment.get(ValueLayout.JAVA_SHORT, 0), (short) SockAddr.AF_INET);
      }
      // AF_INET6 is the one that actually differs between the platforms
      assertTrue(SockAddr.getAfInet6() == 10 || SockAddr.getAfInet6() == 23 || SockAddr.getAfInet6() == 30,
                 "unexpected AF_INET6 " + SockAddr.getAfInet6());
      assertNotEquals(SockAddr.getAfInet6(), SockAddr.AF_INET);
    }
  }

  @Test(timeOut = 30000)
  public void testAddressBytesAreInNetworkOrder() {
    Reporter.log("the address bytes are copied verbatim, not byte-swapped", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      SockAddr.encode(segment, new InetSocketAddress("1.2.3.4", 0));
      for (int i = 0; i < 4; i++) {
        assertEquals(segment.get(ValueLayout.JAVA_BYTE, 4 + i), (byte) (i + 1));
      }
    }
  }

  @Test(timeOut = 30000)
  public void testTailIsZeroed() {
    Reporter.log("the unused tail is zeroed so quiche never compares against stale bytes", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      segment.fill((byte) 0x55);
      SockAddr.encode(segment, new InetSocketAddress("127.0.0.1", 1));
      for (long i = SockAddr.SOCKADDR_IN_SIZE; i < SockAddr.STORAGE_SIZE; i++) {
        assertEquals(segment.get(ValueLayout.JAVA_BYTE, i), (byte) 0, "byte " + i + " was not zeroed");
      }
    }
  }

  @Test(timeOut = 30000)
  public void testReencodingReplacesAnIpv6AddressCompletely() {
    Reporter.log("an IPv4 address written over an IPv6 one leaves nothing of the latter behind", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      SockAddr.encode(segment, new InetSocketAddress("2001:db8::1", 443));
      SockAddr.encode(segment, new InetSocketAddress("10.0.0.1", 80));
      InetSocketAddress decoded = SockAddr.decode(segment);
      assertEquals(decoded, new InetSocketAddress("10.0.0.1", 80));
    }
  }

  @Test(timeOut = 30000)
  public void testUnknownFamilyIsRejected() {
    Reporter.log("an address family the transport cannot use is reported, not misread", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      segment.fill((byte) 0);
      if (SockAddr.hasSaLen()) {
        segment.set(ValueLayout.JAVA_BYTE, 1, (byte) 1);      // AF_UNIX
      }
      else {
        segment.set(ValueLayout.JAVA_SHORT, 0, (short) 1);
      }
      QuicheException e = expectThrows(QuicheException.class, () -> SockAddr.decode(segment));
      assertTrue(e.getMessage().contains("address family"), e.getMessage());
    }
  }

  @Test(timeOut = 30000)
  public void testUnresolvedAddressIsRejected() {
    Reporter.log("an unresolved address cannot be encoded and says so", true);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(SockAddr.STORAGE);
      InetSocketAddress unresolved = InetSocketAddress.createUnresolved("nowhere.invalid", 443);
      expectThrows(IllegalArgumentException.class, () -> SockAddr.encode(segment, unresolved));
    }
  }

  @Test(timeOut = 30000)
  public void testStructureSizes() {
    Reporter.log("the structure sizes are the ones the C library expects", true);
    assertEquals(SockAddr.SOCKADDR_IN_SIZE, 16);
    assertEquals(SockAddr.SOCKADDR_IN6_SIZE, 28);
    assertEquals(SockAddr.STORAGE_SIZE, 128);
    assertEquals(RecvInfo.LAYOUT.byteSize(), 32);
    assertEquals(SendInfo.LAYOUT.byteSize(), 288);
  }

}
