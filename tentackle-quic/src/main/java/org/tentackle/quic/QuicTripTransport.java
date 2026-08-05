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

import tech.kwik.core.QuicStream;
import tech.kwik.core.log.Logger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import org.tentackle.io.SslParameters;
import org.tentackle.io.UriHelper;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.AbstractTransport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * Transport implementation for QUIC.
 */
@TransportService(QuicTripTransport.PROTOCOL)
public class QuicTripTransport extends AbstractTransport {

  /**
   * The protocol name for QUIC.
   */
  public static final String PROTOCOL = "tripq";

  /**
   * The default QUIC max idle timeout in seconds, applied if the URI does not specify {@code quicidle}.
   */
  public static final int DEFAULT_MAX_IDLE_SECONDS = 30;

  /**
   * The default flow control window per stream in bytes, applied if the URI does not specify {@code streamwindow}.
   * <p>
   * This is the same value KWIK's client uses by default, so that both directions are configured symmetrically.
   */
  public static final int DEFAULT_STREAM_WINDOW = 250_000;

  /**
   * The factor by which the connection's flow control window exceeds the window of a single stream.
   * <p>
   * The connection window must be larger than the stream window, or both limits would be exhausted at the very
   * same offset. KWIK does not retransmit a lost {@code MAX_DATA} frame and ignores an incoming
   * {@code DATA_BLOCKED} frame, so a connection-level limit that is reached in lockstep with the stream-level
   * limit cannot be recovered from once the datagram carrying the update is lost: the peer would wait for data
   * the sender is not allowed to send. Keeping the connection window a multiple of the stream window makes the
   * stream limit the binding one, and that one <em>is</em> retransmitted.
   */
  public static final int CONNECTION_WINDOW_FACTOR = 10;

  /**
   * The smallest flow control window accepted for {@code streamwindow}.
   * <p>
   * A window must hold at least one maximum-sized QUIC packet. This is KWIK's {@code MIN_RECEIVER_BUFFER_SIZE},
   * which is not accessible from here because its package is not exported.
   */
  public static final int MIN_STREAM_WINDOW = 1500;


  private ServerConnector serverConnector;    // the running KWIK server connector, null if not accepting
  private boolean accepting;

  /**
   * Creates a new QUIC transport.
   *
   * @param uri the URI of the transport
   */
  public QuicTripTransport(URI uri) {
    super(uri);
  }


  @Override
  public synchronized void accept() {
    super.accept();

    Logger log = new QuicLogger();
    log.timeFormat(Logger.TimeFormat.Long);
    log.logWarning(true);
    log.logInfo(true);

    URI uri = getUri();

    Map<String, String> queryParameters = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);
    String alpnId = queryParameters.getOrDefault("alpnid", uri.getScheme());

    // the keystore, certificate alias and password are resolved consistently with the TCP transports
    SslParameters sslParameters = new SslParameters(uri);
    KeyStore keyStore = sslParameters.getKeyStore();
    String certificateAlias = sslParameters.getCertificateAlias();
    if (keyStore == null || certificateAlias == null) {
      throw new TripRuntimeException(
        "keystore-file (ksfile) and certificate-alias (ksalias, or a single-entry keystore) must be specified");
    }

    int streamWindow = getStreamWindow();
    ServerConnectionConfig serverConnectionConfig =
      ServerConnectionConfig.builder()
                            .maxOpenPeerInitiatedBidirectionalStreams(getUriPars().getOrDefault("maxstreams", 128))
                            // configured explicitly so that both ends agree instead of relying on
                            // the differing KWIK client- and server defaults
                            .maxIdleTimeoutInSeconds(getMaxIdleSeconds())
                            // Without an explicit window, KWIK falls back to the minimum of 5kB advertised by
                            // ApplicationProtocolSettings, while its client advertises 250kB. Every call larger
                            // than 5kB would then have to squeeze through the window in stop-and-go fashion,
                            // driven by the server reading and granting credits in small increments.
                            .maxBidirectionalStreamBufferSize(streamWindow)
                            .maxConnectionBufferSize((long) streamWindow * CONNECTION_WINDOW_FACTOR)
                            .build();

    DatagramSocket socket = null;       // the UDP socket bound here, owned by the connector once it is built
    ServerConnector connector = null;   // the connector, owned by this transport once it is accepting

    try {
      ServerConnector.Builder builder =
        ServerConnector.builder()
                       .withConfiguration(serverConnectionConfig)
                       .withKeyStore(keyStore, certificateAlias, sslParameters.getKeyPassword())
                       .withLogger(log);
      String host = uri.getHost();
      int port = uri.getPort();
      if (port < 0 || port > 0xffff) {
        throw new TripRuntimeException("invalid port number " + port + " in URI " + uri);
      }
      if ("0".equals(host)) { // use 0.0.0.0 to bind on all IPV4 interfaces, 0 binds also IPV6
        builder.withPort(port);
      }
      else if (host != null) {
        socket = new DatagramSocket(port, InetAddress.getByName(host));
        builder.withSocket(socket);
      }
      else {
        throw new TripRuntimeException("missing host in URI " + uri);
      }
      connector = builder.build();
      connector.registerApplicationProtocol(alpnId, new QuicProtocolConnectionFactory(this));
      connector.start();
      serverConnector = connector;
      accepting = true;
    }
    catch (CertificateException | IOException e) {
      throw new TripRuntimeException("starting QUIC service failed", e);
    }
    finally {
      if (!accepting) {
        // building or starting the connector failed: give the UDP port back instead of leaking it
        if (connector != null) {
          connector.close();    // closes the socket as well, whoever created it
        }
        else if (socket != null) {
          socket.close();
        }
      }
    }
  }

  @Override
  public boolean isAccepting() {
    return accepting;
  }

  /**
   * Gets the QUIC max idle timeout in seconds.<br>
   * A QUIC connection is discarded silently by both peers when it stays idle for longer than the
   * minimum of the timeouts of both peers (see RFC-9000, 10.1). The same value is configured on the
   * server and on the client, so that the effective timeout is well-defined.
   * <p>
   * Note that this has nothing to do with the {@code idle} parameter, which is the timeout in
   * <em>minutes</em> after which the connection pool closes unused connections.
   *
   * @return the timeout in seconds
   */
  public int getMaxIdleSeconds() {
    return getUriPars().getOrDefault("quicidle", DEFAULT_MAX_IDLE_SECONDS);
  }

  /**
   * Gets the flow control window per stream in bytes.<br>
   * A peer may send at most that many bytes on a stream before the receiving side has to grant further credits
   * (see RFC-9000, 4). The window applies to the data a <em>client</em> sends to this server, i.e., to the
   * size of the remote calls, and should be at least as large as the largest call to be transferred without
   * intermediate credit updates. The connection window is a multiple of it, see {@link #CONNECTION_WINDOW_FACTOR}.
   *
   * @return the window in bytes
   */
  public int getStreamWindow() {
    int streamWindow = getUriPars().getOrDefault("streamwindow", DEFAULT_STREAM_WINDOW);
    if (streamWindow < MIN_STREAM_WINDOW) {
      throw new TripRuntimeException(
        "QUIC stream window must be >= " + MIN_STREAM_WINDOW + " bytes, got " + streamWindow +
        " from URI " + getUri());
    }
    return streamWindow;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to shut down the KWIK server connector, which releases the UDP socket and
   * terminates the QUIC connections of all clients.
   * <p>
   * The connector is closed <em>before</em> the transport's thread pools are shut down, so that
   * no incoming stream is dispatched to a pool that is already gone. Closing it waits for the
   * client connections to terminate, which is why it happens outside the transport's monitor:
   * connection handlers still running must be able to invoke synchronized transport methods.
   */
  @Override
  public void close() {
    ServerConnector connector;
    synchronized (this) {
      connector = serverConnector;
      serverConnector = null;
      accepting = false;
    }
    if (connector != null) {
      connector.close();
    }
    super.close();
  }

  /**
   * Creates a trip stream for the given QUIC stream.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param quicStream the QUIC stream
   * @return the trip stream
   */
  public TripStream createStream(Dictionary dictionary, QuicStream quicStream) {
    TripFactory factory = TripFactory.getInstance();
    return factory.createStream(dictionary,
                                factory.createSerializer(quicStream.getOutputStream()),
                                factory.createDeserializer(quicStream.getInputStream()));
  }


  @Override
  protected Pool<Connection> createClientConnectionPool() {
    return new QuicTripConnectionPool(this, getClientDictionary());
  }

}
