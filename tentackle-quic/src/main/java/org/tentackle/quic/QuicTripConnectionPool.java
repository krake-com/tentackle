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

package org.tentackle.quic;

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import org.tentackle.io.UriHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.PoolSlot;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.AbstractConnectionPool;
import org.tentackle.trip.transport.ConnectionPoolSlot;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * The QUIC client connection pool.
 * <p>
 * All pooled connections are streams multiplexed over a single QUIC connection. Since that
 * connection is discarded silently by both peers as soon as it stays idle for longer than the QUIC
 * max idle timeout (see {@link QuicTripTransport#getMaxIdleSeconds()}), which is way shorter than
 * the pool's idle timeout, it is kept alive by sending PING frames. Otherwise, the pool would hand
 * out connections belonging to a QUIC connection that is long gone.
 */
public class QuicTripConnectionPool extends AbstractConnectionPool {

  /**
   * The default connect timeout in seconds, applied if the URI does not specify {@code connecttimeout}.
   * <p>
   * This is the timeout for establishing the QUIC connection, i.e., the handshake with the server,
   * not for the individual streams created per pool slot.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10;

  private static final Logger LOGGER = Logger.get(QuicTripConnectionPool.class);


  private final QuicTripTransport transport;
  private final QuicClientConnection.Builder connectionBuilder;
  private final int keepAliveSeconds;
  private QuicClientConnection connection;

  /**
   * Creates a QUIC connection pool.
   *
   * @param transport     the transport associated with this pool
   * @param dictionary    the dictionary associated with this pool
   */
  public QuicTripConnectionPool(QuicTripTransport transport, Dictionary dictionary) {
    super(transport, dictionary);
    this.transport = transport;
    URI uri = transport.getUri();
    if (uri.getPort() <= 0) {
      throw new IllegalArgumentException("QUIC port number must be > 0, got " + uri.getPort() + " from URI " + uri);
    }
    int connectTimeout = transport.getUriPars().getOrDefault("connecttimeout", DEFAULT_CONNECT_TIMEOUT);
    if (connectTimeout <= 0) {
      throw new IllegalArgumentException(
        "QUIC connect timeout must be > 0 seconds, got " + connectTimeout + " from URI " + uri);
    }
    Map<String, String> queryParameters = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);
    String scheme = uri.getScheme();    // default alpnId is the same as the scheme
    String alpnId = queryParameters.getOrDefault("alpnid", scheme);
    keepAliveSeconds = transport.getUriPars().getOrDefault("keepalive", -1);
    connectionBuilder = QuicClientConnection.newBuilder()
                                            .uri(uri)  // only host and port are used
                                            .applicationProtocol(alpnId)
                                            .connectTimeout(Duration.ofSeconds(connectTimeout))
                                            .maxIdleTimeout(Duration.ofSeconds(transport.getMaxIdleSeconds()));
    if (!Boolean.parseBoolean(queryParameters.getOrDefault("certcheck", "false"))) {
      connectionBuilder.noServerCertificateCheck();
    }

    connect();
  }

  @Override
  public synchronized void shutdown() {
    super.shutdown();
    connection.close();
  }


  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    QuicStream quicStream = createQuicStream();   // may re-establish the QUIC connection
    QuicClientConnection quicConnection = connection;
    return new ConnectionPoolSlot(this, slotNumber,
                                  new QuicTripConnection(transport, getDictionary(),
                                                         quicConnection::isConnected, quicStream));
  }

  /**
   * Creates a new client quic stream.<br>
   * Re-establishes the QUIC connection if it has been terminated meanwhile.
   *
   * @return the quic stream
   */
  protected QuicStream createQuicStream() {
    if (!connection.isConnected()) {
      // idle timeout, peer restarted, network loss, ...
      LOGGER.info("QUIC connection to {0} is gone -> reconnecting", getTransport().getUri());
      connect();
    }
    try {
      return connection.createStream(true);
    }
    catch (IOException e) {
      throw new TripRuntimeException("creating QUIC stream failed", e);
    }
  }

  /**
   * Establishes the QUIC connection all pooled connections are multiplexed over.
   */
  protected void connect() {
    URI uri = getTransport().getUri();
    try {
      QuicClientConnection quicConnection = connectionBuilder.build();
      quicConnection.connect();
      if (keepAliveSeconds != 0) {
        // negative means: as long as this pool lives
        quicConnection.keepAlive(keepAliveSeconds < 0 ? Integer.MAX_VALUE : keepAliveSeconds);
      }
      connection = quicConnection;
    }
    catch (IOException e) {
      throw new TripRuntimeException("creating QUIC connection to " + uri + " failed", e);
    }
  }

}
