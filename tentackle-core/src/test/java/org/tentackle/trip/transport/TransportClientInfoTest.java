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

package org.tentackle.trip.transport;

import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.testng.Assert.assertEquals;

/**
 * Tests {@link TransportClientInfo#getHost(String)} for the connection names of all transports.<br>
 * The pre-authentication policy groups its connection limits by that host, so a name that doesn't
 * carry the peer's address would put all clients into one group.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TransportClientInfoTest {

  @Test
  public void tcpConnectionName() throws Exception {
    // the name of a TCP connection is the socket address
    assertEquals(TransportClientInfo.getHost(new InetSocketAddress(InetAddress.ofLiteral("192.168.1.2"), 4711).toString()),
                 "192.168.1.2");
    assertEquals(TransportClientInfo.getHost("localhost/127.0.0.1:4711"), "127.0.0.1");
    assertEquals(TransportClientInfo.getHost(new InetSocketAddress(InetAddress.ofLiteral("fe80::1"), 4711).toString()),
                 "[fe80:0:0:0:0:0:0:1]");
  }

  @Test
  public void quicheConnectionName() {
    assertEquals(TransportClientInfo.getHost("stream 4 of QUIC connection 0a1b2c to /10.0.0.7:50000"), "10.0.0.7");
    assertEquals(TransportClientInfo.getHost("stream 0 of QUIC connection 0a1b2c to /[2001:db8:0:0:0:0:0:5]:50000"),
                 "[2001:db8:0:0:0:0:0:5]");
  }

  @Test
  public void kwikConnectionName() {
    assertEquals(TransportClientInfo.getHost("stream 0 of QUIC connection to /10.0.0.8:50001"), "10.0.0.8");
    assertEquals(TransportClientInfo.getHost("stream 8 of QUIC connection to /[2001:db8:0:0:0:0:0:6]:50001"),
                 "[2001:db8:0:0:0:0:0:6]");
  }

}
