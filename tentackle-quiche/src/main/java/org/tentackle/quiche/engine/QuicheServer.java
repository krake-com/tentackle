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

import org.tentackle.log.Logger;
import org.tentackle.quiche.ffm.PacketHeader;
import org.tentackle.quiche.ffm.Quiche;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.quiche.ffm.QuicheConn;
import org.tentackle.quiche.ffm.QuicheException;
import org.tentackle.quiche.ffm.RecvInfo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Turns datagrams that belong to no connection yet into new server-side connections.
 * <p>
 * Everything a QUIC server has to do before it may spend any memory on a client happens here, in the
 * order RFC-9000 prescribes:
 * <ol>
 *   <li><strong>Version negotiation</strong> (6): a client offering a QUIC version this library does not
 *       implement gets a list of the ones it does.</li>
 *   <li><strong>Address validation</strong> (8.1.2): the first initial packet is answered with a retry
 *       carrying a token, and only a client that sends that token back has proven it can receive at the
 *       address it claims. Without this, the server would be an amplifier: an attacker spoofing a
 *       victim's address would have the handshake response, several times the size of the request, sent
 *       to the victim.</li>
 *   <li><strong>Accept</strong>: only now is a connection created, with the original destination
 *       connection ID recovered from the token so that quiche can verify the client saw the same retry.
 *       </li>
 * </ol>
 * The token is an HMAC over the client's address and the original connection ID, keyed by a secret this
 * server generates at startup. It is therefore unforgeable, needs no server-side state at all, and stops
 * being valid when the server restarts.
 * <p>
 * Every method is invoked on the endpoint's event loop thread only.
 */
public class QuicheServer {

  /**
   * The algorithm the address validation tokens are signed with.
   */
  public static final String TOKEN_ALGORITHM = "HmacSHA256";

  /**
   * The number of HMAC bytes kept in a token.
   * <p>
   * 16 bytes is far beyond what forging one is worth: the token is only useful for the moments between a
   * retry and the initial packet answering it.
   */
  public static final int TOKEN_SIGNATURE_LENGTH = 16;

  private static final Logger LOGGER = Logger.get(QuicheServer.class);


  private final QuicheEndpoint endpoint;
  private final QuicheConfig config;
  private final StreamListener listener;
  private final byte[] tokenKey;
  private final Arena arena;
  private final MemorySegment outBuffer;

  /**
   * Creates a server.
   *
   * @param endpoint the endpoint to accept on
   * @param config the configuration new connections are created with, which must outlive them
   * @param listener notified about the streams the clients open
   */
  public QuicheServer(QuicheEndpoint endpoint, QuicheConfig config, StreamListener listener) {
    this.endpoint = endpoint;
    this.config = config;
    this.listener = listener;

    tokenKey = new byte[32];
    new SecureRandom().nextBytes(tokenKey);
    arena = Arena.ofShared();
    outBuffer = arena.allocate(QuicheConnection.SEND_BUFFER_SIZE);
  }


  /**
   * Handles a datagram that belongs to no connection.
   *
   * @param peer the client's address
   * @param buffer the datagram
   * @param length the number of bytes
   * @param recvInfo the receive-info to reuse once a connection exists
   */
  public void accept(InetSocketAddress peer, MemorySegment buffer, int length, RecvInfo recvInfo) {
    PacketHeader header;
    try {
      header = Quiche.headerInfo(buffer, length, QuicheEndpoint.CONNECTION_ID_LENGTH);
    }
    catch (QuicheException e) {
      LOGGER.fine("dropping an unparsable datagram from {0}: {1}", peer, e.getMessage());
      return;
    }

    if (!Quiche.versionIsSupported(header.version())) {
      LOGGER.fine("{0} offers the unsupported QUIC version 0x{1}",
                  peer, Integer.toHexString(header.version()));
      sendVersionNegotiation(header, peer);
      return;
    }

    if (!header.isInitial()) {
      // a stray packet of a connection that is gone: dropping it is all a server can do without
      // keeping the state a stateless reset would need
      LOGGER.fine("dropping a non-initial packet from {0} for the unknown connection {1}",
                  peer, HexFormat.of().formatHex(header.destinationConnectionId()));
      return;
    }

    if (header.token().length == 0) {
      sendRetry(header, peer);
      return;
    }

    byte[] originalDcid = validateToken(header.token(), peer);
    if (originalDcid == null) {
      LOGGER.warning("dropping an initial packet from " + peer + " with an invalid address validation token");
      return;
    }

    createConnection(header, originalDcid, peer, buffer, length, recvInfo);
  }

  /**
   * Releases the server's resources.
   */
  public void close() {
    arena.close();
  }


  /**
   * Answers with the list of QUIC versions this library implements.
   *
   * @param header the client's packet header
   * @param peer the client's address
   */
  private void sendVersionNegotiation(PacketHeader header, InetSocketAddress peer) {
    try {
      long written = Quiche.negotiateVersion(header.sourceConnectionId(), header.destinationConnectionId(),
                                             outBuffer, QuicheConnection.SEND_BUFFER_SIZE);
      endpoint.send(outBuffer, written, peer);
    }
    catch (QuicheException e) {
      LOGGER.fine("cannot send a version negotiation packet to {0}: {1}", peer, e.getMessage());
    }
  }

  /**
   * Answers with a retry packet carrying an address validation token.
   *
   * @param header the client's packet header
   * @param peer the client's address
   */
  private void sendRetry(PacketHeader header, InetSocketAddress peer) {
    byte[] newScid = endpoint.newConnectionId();
    byte[] token = createToken(header.destinationConnectionId(), peer);
    try {
      long written = Quiche.retry(header.sourceConnectionId(), header.destinationConnectionId(),
                                  newScid, token, header.version(),
                                  outBuffer, QuicheConnection.SEND_BUFFER_SIZE);
      endpoint.send(outBuffer, written, peer);
      LOGGER.fine("sent a retry to {0}", peer);
    }
    catch (QuicheException e) {
      LOGGER.fine("cannot send a retry packet to {0}: {1}", peer, e.getMessage());
    }
  }

  /**
   * Creates the connection and feeds it the packet that triggered it.
   *
   * @param header the client's packet header
   * @param originalDcid the connection ID the client used before the retry
   * @param peer the client's address
   * @param buffer the datagram
   * @param length the number of bytes
   * @param recvInfo the receive-info
   */
  private void createConnection(PacketHeader header, byte[] originalDcid, InetSocketAddress peer,
                                MemorySegment buffer, int length, RecvInfo recvInfo) {
    // the client is now addressing the connection ID the retry told it to use, and that is the one
    // this side must keep routing by
    byte[] scid = header.destinationConnectionId();
    QuicheConn conn;
    try {
      conn = QuicheConn.accept(scid, originalDcid, endpoint.getLocalAddress(), peer, config);
    }
    catch (QuicheException e) {
      LOGGER.warning("cannot accept a QUIC connection from " + peer, e);
      return;
    }

    QuicheConnection connection = new QuicheConnection(endpoint, conn, scid, peer, false);
    endpoint.register(connection);
    LOGGER.fine("accepted {0}", connection);

    recvInfo.set(peer, endpoint.getLocalAddress());
    connection.receive(buffer, length, recvInfo);

    if (listener != null) {
      connection.setStreamListener(listener);
    }
  }

  /**
   * Creates an address validation token for a client.
   * <p>
   * The layout is {@code HMAC(address || dcid)[0..16] || dcid}: the connection ID has to travel inside
   * the token because quiche needs it back when the connection is created, and the signature is what
   * keeps a client from making one up.
   *
   * @param dcid the connection ID the client addressed its first packet to
   * @param peer the client's address
   * @return the token
   */
  private byte[] createToken(byte[] dcid, InetSocketAddress peer) {
    byte[] signature = sign(dcid, peer);
    byte[] token = new byte[TOKEN_SIGNATURE_LENGTH + dcid.length];
    System.arraycopy(signature, 0, token, 0, TOKEN_SIGNATURE_LENGTH);
    System.arraycopy(dcid, 0, token, TOKEN_SIGNATURE_LENGTH, dcid.length);
    return token;
  }

  /**
   * Checks a token a client sent back and recovers the connection ID from it.
   *
   * @param token the token
   * @param peer the client's address
   * @return the original connection ID, null if the token is not one this server issued to this client
   */
  private byte[] validateToken(byte[] token, InetSocketAddress peer) {
    if (token.length <= TOKEN_SIGNATURE_LENGTH || token.length > Quiche.MAX_TOKEN_LEN) {
      return null;
    }
    byte[] dcid = Arrays.copyOfRange(token, TOKEN_SIGNATURE_LENGTH, token.length);
    byte[] expected = sign(dcid, peer);
    // constant time: the comparison result must not leak how much of a forged signature was right
    if (!java.security.MessageDigest.isEqual(
          Arrays.copyOf(expected, TOKEN_SIGNATURE_LENGTH),
          Arrays.copyOf(token, TOKEN_SIGNATURE_LENGTH))) {
      return null;
    }
    return dcid;
  }

  /**
   * Signs a connection ID together with the address it was seen from.
   *
   * @param dcid the connection ID
   * @param peer the client's address
   * @return the HMAC
   */
  private byte[] sign(byte[] dcid, InetSocketAddress peer) {
    try {
      Mac mac = Mac.getInstance(TOKEN_ALGORITHM);
      mac.init(new SecretKeySpec(tokenKey, TOKEN_ALGORITHM));
      mac.update(peer.getAddress().getAddress());
      mac.update((byte) (peer.getPort() >>> 8));
      mac.update((byte) peer.getPort());
      mac.update(dcid);
      return mac.doFinal();
    }
    catch (GeneralSecurityException e) {
      // HmacSHA256 is required of every JRE
      throw new QuicheException("cannot sign an address validation token", e);
    }
  }

}
