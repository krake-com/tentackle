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
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Turns datagrams that belong to no connection yet into new server-side connections.
 * <p>
 * Everything a QUIC server has to do before it may spend any memory on a client happens here, in the
 * order RFC-9000 prescribes:
 * <ol>
 *   <li><strong>Version negotiation</strong> (6): a client whose long header packet offers a QUIC version this
 *       library does not implement gets a list of the ones it does &mdash; provided the datagram is at least
 *       as large as a client's initial packet, so that the answer cannot be abused for amplification.
 *       Short header packets and version negotiation packets carry no version and are never answered.</li>
 *   <li><strong>Address validation</strong> (8.1.2): the first initial packet is answered with a retry
 *       carrying a token, and only a client that sends that token back has proven it can receive at the
 *       address it claims. Without this, the server would be an amplifier: an attacker spoofing a
 *       victim's address would have the handshake response, several times the size of the request, sent
 *       to the victim.</li>
 *   <li><strong>Accept</strong>: only now is a connection created, with the original destination
 *       connection ID recovered from the token so that quiche can verify the client saw the same retry.
 *       </li>
 * </ol>
 * The token is an HMAC over the client's IP address and port, the original connection ID and the time the
 * token was issued, keyed by a secret this server generates at startup. It is therefore unforgeable, needs
 * no server-side state at all, expires after {@link #RETRY_TOKEN_LIFETIME_SECONDS}, and stops being valid
 * when the server restarts.
 * <p>
 * {@link #accept} is invoked on the endpoint's event loop thread only, {@link #close()} by whoever closes
 * the server once its endpoint is closed.
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
   * retry and the initial packet answering it, see {@link #RETRY_TOKEN_LIFETIME_SECONDS}.
   */
  public static final int TOKEN_SIGNATURE_LENGTH = 16;

  /**
   * How long an address validation token stays valid.
   * <p>
   * A client sends the token back in the initial packet answering the retry, i.e., one round trip after it
   * was issued. The lifetime is generous enough for any real round trip and short enough that a token
   * captured on the path cannot be replayed later on.
   */
  public static final long RETRY_TOKEN_LIFETIME_SECONDS = 10;

  /**
   * The number of bytes of the issuing time stored in a token.
   */
  private static final int TOKEN_TIME_LENGTH = Long.BYTES;

  private static final Logger LOGGER = Logger.get(QuicheServer.class);


  private final QuicheEndpoint endpoint;
  private final QuicheConfig config;
  private final StreamListener listener;
  private final LongSupplier nanoClock;
  private final byte[] tokenKey;
  private final Arena arena;
  private final MemorySegment outBuffer;
  private final AtomicBoolean closed;

  /**
   * Creates a server.
   *
   * @param endpoint the endpoint to accept on
   * @param config the configuration new connections are created with, which must outlive them
   * @param listener notified about the streams the clients open
   */
  public QuicheServer(QuicheEndpoint endpoint, QuicheConfig config, StreamListener listener) {
    this(endpoint, config, listener, System::nanoTime);
  }

  /**
   * Creates a server with a clock of its own.
   *
   * @param endpoint the endpoint to accept on
   * @param config the configuration new connections are created with, which must outlive them
   * @param listener notified about the streams the clients open
   * @param nanoClock the monotonic clock the tokens are timestamped with, in nanoseconds
   */
  QuicheServer(QuicheEndpoint endpoint, QuicheConfig config, StreamListener listener, LongSupplier nanoClock) {
    this.endpoint = endpoint;
    this.config = config;
    this.listener = listener;
    this.nanoClock = nanoClock;
    closed = new AtomicBoolean();

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
   * @throws QuicheException if a token cannot be signed, which a JRE without HMAC-SHA256 would cause
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

    // version 0 is what a short header packet and a version negotiation packet report: neither may be
    // answered with a version negotiation (RFC-9000, 5.2.2 and 6.1)
    if (header.version() != 0 && !Quiche.versionIsSupported(header.version())) {
      if (length >= Quiche.MIN_CLIENT_INITIAL_LEN) {
        LOGGER.fine("{0} offers the unsupported QUIC version 0x{1}",
                    peer, Integer.toHexString(header.version()));
        sendVersionNegotiation(header, peer);
      }
      else {
        LOGGER.fine("dropping a short datagram from {0} with the unsupported QUIC version 0x{1}",
                    peer, Integer.toHexString(header.version()));
      }
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
      LOGGER.warning("dropping an initial packet from " + peer + " with an invalid or expired address validation token");
      return;
    }

    createConnection(header, originalDcid, peer, buffer, length, recvInfo);
  }

  /**
   * Releases the server's resources.
   * <p>
   * Invoked once the endpoint is closed. Should its event loop still be running nevertheless &mdash; it
   * is given only a limited time to stop &mdash; the native buffer is deliberately leaked rather than
   * freed underneath it. Closing more than once is harmless.
   */
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        arena.close();
      }
      catch (IllegalStateException e) {
        LOGGER.warning("the server's native buffer is still in use and will not be freed", e);
      }
    }
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
   * The layout is {@code HMAC(address || port || issued || dcid)[0..16] || issued || dcid}: the connection
   * ID has to travel inside the token because quiche needs it back when the connection is created, the
   * issuing time to let the token expire, and the signature is what keeps a client from making either up.
   * The time is taken from a monotonic clock, which is fine since nobody but this server ever reads it.
   *
   * @param dcid the connection ID the client addressed its first packet to
   * @param peer the client's address
   * @return the token
   */
  byte[] createToken(byte[] dcid, InetSocketAddress peer) {
    long issued = nanoClock.getAsLong();
    byte[] signature = sign(dcid, peer, issued);
    return ByteBuffer.allocate(TOKEN_SIGNATURE_LENGTH + TOKEN_TIME_LENGTH + dcid.length)
                     .put(signature, 0, TOKEN_SIGNATURE_LENGTH)
                     .putLong(issued)
                     .put(dcid)
                     .array();
  }

  /**
   * Checks a token a client sent back and recovers the connection ID from it.
   *
   * @param token the token
   * @param peer the client's address
   * @return the original connection ID, null if the token is not one this server issued to this client,
   *         or it has expired
   */
  byte[] validateToken(byte[] token, InetSocketAddress peer) {
    int prefixLength = TOKEN_SIGNATURE_LENGTH + TOKEN_TIME_LENGTH;
    if (token.length <= prefixLength || token.length > Quiche.MAX_TOKEN_LEN) {
      return null;
    }
    long issued = ByteBuffer.wrap(token, TOKEN_SIGNATURE_LENGTH, TOKEN_TIME_LENGTH).getLong();
    byte[] dcid = Arrays.copyOfRange(token, prefixLength, token.length);
    byte[] expected = sign(dcid, peer, issued);
    // constant time: the comparison result must not leak how much of a forged signature was right
    if (!MessageDigest.isEqual(Arrays.copyOf(expected, TOKEN_SIGNATURE_LENGTH),
                               Arrays.copyOf(token, TOKEN_SIGNATURE_LENGTH))) {
      return null;
    }
    long age = nanoClock.getAsLong() - issued;
    if (age < 0 || age > TimeUnit.SECONDS.toNanos(RETRY_TOKEN_LIFETIME_SECONDS)) {
      LOGGER.fine("the address validation token of {0} has expired", peer);
      return null;
    }
    return dcid;
  }

  /**
   * Signs a connection ID together with the address it was seen from and the time the token is issued.
   *
   * @param dcid the connection ID
   * @param peer the client's address
   * @param issued the time the token is issued, see {@link #createToken}
   * @return the HMAC
   */
  private byte[] sign(byte[] dcid, InetSocketAddress peer, long issued) {
    try {
      Mac mac = Mac.getInstance(TOKEN_ALGORITHM);
      mac.init(new SecretKeySpec(tokenKey, TOKEN_ALGORITHM));
      mac.update(peer.getAddress().getAddress());
      mac.update((byte) (peer.getPort() >>> 8));
      mac.update((byte) peer.getPort());
      mac.update(ByteBuffer.allocate(TOKEN_TIME_LENGTH).putLong(issued).array());
      mac.update(dcid);
      return mac.doFinal();
    }
    catch (GeneralSecurityException e) {
      // HmacSHA256 is required of every JRE
      throw new QuicheException("cannot sign an address validation token", e);
    }
  }

}
