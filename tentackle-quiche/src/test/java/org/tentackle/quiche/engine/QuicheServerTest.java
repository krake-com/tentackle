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

package org.tentackle.quiche.engine;

import org.tentackle.quiche.QuicheTestSupport;
import org.tentackle.quiche.ffm.Quiche;
import org.tentackle.quiche.ffm.QuicheConfig;

import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the stateless part of the server: version negotiation and address validation.
 */
public class QuicheServerTest {

  private static final InetSocketAddress PEER = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4711);

  private static final byte[] DCID = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

  /**
   * A QUIC version no implementation supports, reserved to exercise version negotiation (RFC-9000, 15).
   */
  private static final int UNSUPPORTED_VERSION = 0x0a0a0a0a;


  private QuicheConfig serverConfig;
  private QuicheEndpoint serverEndpoint;
  private QuicheServer server;

  @AfterMethod
  public void tearDown() {
    if (serverEndpoint != null) {
      serverEndpoint.close();
      serverEndpoint = null;
    }
    if (server != null) {
      server.close();
      server = null;
    }
    if (serverConfig != null) {
      serverConfig.close();
      serverConfig = null;
    }
  }


  @Test
  public void testTokenIsAcceptedWhileFresh() {
    Reporter.log("a token sent back in time by the client it was issued to yields the original DCID", true);
    AtomicLong clock = new AtomicLong(1_000_000_000L);
    server = new QuicheServer(null, null, null, clock::get);
    byte[] token = server.createToken(DCID, PEER);
    clock.addAndGet(TimeUnit.SECONDS.toNanos(QuicheServer.RETRY_TOKEN_LIFETIME_SECONDS));
    assertEquals(server.validateToken(token, PEER), DCID);
  }

  @Test
  public void testExpiredTokenIsRejected() {
    Reporter.log("a token older than its lifetime is rejected", true);
    AtomicLong clock = new AtomicLong(1_000_000_000L);
    server = new QuicheServer(null, null, null, clock::get);
    byte[] token = server.createToken(DCID, PEER);
    clock.addAndGet(TimeUnit.SECONDS.toNanos(QuicheServer.RETRY_TOKEN_LIFETIME_SECONDS) + 1);
    assertNull(server.validateToken(token, PEER));
  }

  @Test
  public void testTamperedTokenIsRejected() {
    Reporter.log("a token whose issuing time, DCID or address was changed is rejected", true);
    AtomicLong clock = new AtomicLong(1_000_000_000L);
    server = new QuicheServer(null, null, null, clock::get);
    byte[] token = server.createToken(DCID, PEER);
    assertNotNull(server.validateToken(token, PEER));

    // pretend a later issuing time to extend the lifetime
    byte[] rejuvenated = token.clone();
    ByteBuffer.wrap(rejuvenated).putLong(QuicheServer.TOKEN_SIGNATURE_LENGTH, clock.get() + 1);
    assertNull(server.validateToken(rejuvenated, PEER), "the issuing time is not signed");

    byte[] otherDcid = token.clone();
    otherDcid[otherDcid.length - 1] ^= 1;
    assertNull(server.validateToken(otherDcid, PEER), "the DCID is not signed");

    InetSocketAddress otherPort = new InetSocketAddress(PEER.getAddress(), PEER.getPort() + 1);
    assertNull(server.validateToken(token, otherPort), "the port is not signed");
  }

  @Test
  public void testCloseIsIdempotent() {
    Reporter.log("closing a server twice is harmless", true);
    server = new QuicheServer(null, null, null);
    server.close();
    server.close();
  }

  @Test(timeOut = 60000)
  public void testShortHeaderPacketIsNotAnsweredWithVersionNegotiation() throws Exception {
    Reporter.log("a stray short header packet is dropped instead of answered with version negotiation", true);
    startServer();
    byte[] datagram = new byte[Quiche.MIN_CLIENT_INITIAL_LEN];
    datagram[0] = 0x40;   // short header, fixed bit set
    System.arraycopy(DCID, 0, datagram, 1, DCID.length);
    assertNull(exchange(datagram), "a short header packet was answered");
  }

  @Test(timeOut = 60000)
  public void testUnsupportedVersionIsAnsweredWithVersionNegotiation() throws Exception {
    Reporter.log("a client's initial packet with an unknown version gets the supported versions", true);
    startServer();
    byte[] reply = exchange(longHeaderInitial(Quiche.MIN_CLIENT_INITIAL_LEN));
    assertNotNull(reply, "no version negotiation");
    assertEquals(ByteBuffer.wrap(reply, 1, 4).getInt(), 0, "not a version negotiation packet");
  }

  @Test(timeOut = 60000)
  public void testShortDatagramWithUnsupportedVersionIsNotAnswered() throws Exception {
    Reporter.log("a datagram too short for a client initial is not answered, to prevent amplification", true);
    startServer();
    assertNull(exchange(longHeaderInitial(100)), "a short datagram was answered");
  }

  @Test
  public void testTokenRequiresSignatureTimeAndDcid() {
    Reporter.log("a token too short to hold all its parts is rejected", true);
    server = new QuicheServer(null, null, null);
    assertNull(server.validateToken(new byte[QuicheServer.TOKEN_SIGNATURE_LENGTH + Long.BYTES], PEER));
  }


  /**
   * Starts a server endpoint on the loopback interface.
   *
   * @throws Exception if that fails
   */
  private void startServer() throws Exception {
    serverConfig = QuicheTestSupport.serverConfig();
    serverEndpoint = new QuicheEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    server = new QuicheServer(serverEndpoint, serverConfig, stream -> { });
    serverEndpoint.setServer(server);
  }

  /**
   * Builds a client initial packet offering {@link #UNSUPPORTED_VERSION}.
   *
   * @param length the size of the datagram
   * @return the datagram
   */
  private static byte[] longHeaderInitial(int length) {
    ByteBuffer buffer = ByteBuffer.allocate(length);
    buffer.put((byte) 0xc0)                // long header, fixed bit set, type initial
          .putInt(UNSUPPORTED_VERSION)
          .put((byte) DCID.length).put(DCID)
          .put((byte) 8).put(new byte[] {8, 7, 6, 5, 4, 3, 2, 1})
          .put((byte) 0);                  // no token
    return buffer.array();
  }

  /**
   * Sends a datagram to the server and waits for an answer.
   *
   * @param datagram the datagram
   * @return the answer, null if there was none
   * @throws IOException if sending failed
   */
  private byte[] exchange(byte[] datagram) throws IOException {
    try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
      socket.setSoTimeout(1000);
      socket.send(new DatagramPacket(datagram, datagram.length, InetAddress.getLoopbackAddress(),
                                     serverEndpoint.getLocalAddress().getPort()));
      byte[] buffer = new byte[2048];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(packet);
      }
      catch (SocketTimeoutException e) {
        return null;
      }
      assertTrue(packet.getLength() > 5);
      byte[] reply = new byte[packet.getLength()];
      System.arraycopy(buffer, 0, reply, 0, reply.length);
      return reply;
    }
  }

}
