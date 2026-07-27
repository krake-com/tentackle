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
 */
public class QuicTripConnectionPool extends AbstractConnectionPool {

  /**
   * The default connect timeout in seconds, applied if the URI does not specify {@code connecttimeout}.
   * <p>
   * This is the timeout for establishing the QUIC connection, i.e., the handshake with the server,
   * not for the individual streams created per pool slot.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10;


  private final QuicTripTransport transport;
  private final QuicClientConnection connection;

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
    QuicClientConnection.Builder builder = QuicClientConnection.newBuilder()
                                                               .uri(uri)  // only host and port are used
                                                               .applicationProtocol(alpnId)
                                                               .connectTimeout(Duration.ofSeconds(connectTimeout));
    if (!Boolean.parseBoolean(queryParameters.getOrDefault("certcheck", "false"))) {
      builder.noServerCertificateCheck();
    }

    try {
      connection = builder.build();
      connection.connect();
    }
    catch (IOException e) {
      throw new TripRuntimeException("creating QUIC connection to " + uri + " failed", e);
    }
  }

  @Override
  public synchronized void shutdown() {
    super.shutdown();
    connection.close();
  }


  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    return new ConnectionPoolSlot(this, slotNumber,
                                  new QuicTripConnection(transport, getDictionary(), createQuicStream()));
  }

  /**
   * Creates a new client quic stream.
   *
   * @return the quic stream
   */
  protected QuicStream createQuicStream() {
    try {
      return connection.createStream(true);
    }
    catch (IOException e) {
      throw new TripRuntimeException("creating QUIC stream failed", e);
    }
  }

}
