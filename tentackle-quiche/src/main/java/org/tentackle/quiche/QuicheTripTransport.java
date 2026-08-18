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

package org.tentackle.quiche;

import org.tentackle.io.SslParameters;
import org.tentackle.io.UriHelper;
import org.tentackle.misc.Pool;
import org.tentackle.quiche.engine.QuicheEndpoint;
import org.tentackle.quiche.engine.QuicheServer;
import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.quiche.ffm.PemExporter;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.AbstractTransport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;

/**
 * Transport implementation for QUIC on top of Cloudflare quiche.
 * <p>
 * The sibling of {@code tentackle-quic}, which provides the same transport on top of KWIK. Both speak
 * RFC-9000 and interoperate; they register different URI schemes so that an application can use either,
 * or both at once, and pick per connection.
 */
@TransportService(QuicheTripTransport.PROTOCOL)
public class QuicheTripTransport extends AbstractTransport {

  /**
   * The protocol name for QUIC over quiche.
   */
  public static final String PROTOCOL = "tripj";

  /**
   * The default QUIC max idle timeout in seconds, applied if the URI does not specify {@code quicidle}.
   */
  public static final int DEFAULT_MAX_IDLE_SECONDS = 30;

  /**
   * The default maximum number of concurrent peer-initiated bidirectional streams, applied if the URI
   * does not specify {@code maxstreams}.
   */
  public static final int DEFAULT_MAX_STREAMS = 128;

  /**
   * The default flow control window per stream in bytes, applied if the URI does not specify
   * {@code streamwindow}.
   */
  public static final int DEFAULT_STREAM_WINDOW = 250_000;

  /**
   * The factor by which the connection's flow control window exceeds the window of a single stream.
   * <p>
   * The connection window must be larger than the stream window, or both limits would be exhausted at the
   * very same offset and every transfer would proceed in lockstep with the credit updates. Keeping the
   * connection window a multiple of the stream window makes the stream limit the binding one.
   * <p>
   * Note that the connection window is shared by <em>all</em> streams of the connection, so the stream
   * limit is the binding one only as long as fewer than this many streams transfer at full window at the
   * same time. Sizing {@code streamwindow} so that ordinary calls fit into it keeps the number of streams
   * that are blocked on flow control small.
   */
  public static final int CONNECTION_WINDOW_FACTOR = 10;

  /**
   * The smallest flow control window accepted for {@code streamwindow}.
   * <p>
   * A window must hold at least one maximum-sized QUIC packet.
   */
  public static final int MIN_STREAM_WINDOW = 1500;


  private QuicheEndpoint endpoint;          // the endpoint accepting connections, null if not accepting
  private QuicheServer server;              // the accept policy of that endpoint
  private QuicheConfig serverConfig;        // outlives every connection created from it
  private volatile boolean accepting;       // volatile: isAccepting() is not synchronized

  /**
   * Creates a new QUIC transport.
   *
   * @param uri the URI of the transport
   */
  public QuicheTripTransport(URI uri) {
    super(uri);
  }


  @Override
  public synchronized void accept() {
    super.accept();

    QuicheLogger.install();

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

    int port = uri.getPort();
    if (port < 0 || port > 0xffff) {
      throw new TripRuntimeException("invalid port number " + port + " in URI " + uri);
    }
    String host = uri.getHost();
    if (host == null) {
      throw new TripRuntimeException("missing host in URI " + uri);
    }

    QuicheConfig config = null;             // owned by this transport once it is accepting
    QuicheEndpoint newEndpoint = null;      // ditto
    QuicheServer newServer = null;
    boolean started = false;
    try {
      // quiche takes PEM file paths, not a KeyStore, so the entry has to be exported
      Path certificateChain = PemExporter.writeCertificateChain(keyStore, certificateAlias);
      Path privateKey = PemExporter.writePrivateKey(keyStore, certificateAlias, sslParameters.getKeyPassword());

      config = createConfig(alpnId)
                 .loadCertChain(certificateChain)
                 .loadPrivateKey(privateKey)
                 // a Tentackle server does not ask its clients for a certificate; they authenticate
                 // through TRIP once the connection stands
                 .verifyPeer(false);

      // "0" binds all IPv4 interfaces, as it does for the TCP transports
      InetAddress bindAddress = "0".equals(host) ? null : InetAddress.getByName(host);
      newEndpoint = new QuicheEndpoint(bindAddress == null ? new InetSocketAddress(port)
                                                           : new InetSocketAddress(bindAddress, port));
      newServer = new QuicheServer(newEndpoint, config, new QuicheProtocolConnection(this));
      newEndpoint.setServer(newServer);

      serverConfig = config;
      endpoint = newEndpoint;
      server = newServer;
      accepting = true;
      started = true;
    }
    catch (UnknownHostException e) {
      throw new TripRuntimeException("cannot resolve the host of URI " + uri, e);
    }
    catch (IOException e) {
      throw new TripRuntimeException("starting QUIC service failed", e);
    }
    finally {
      // a local flag, not the instance field: the cleanup must apply to what this invocation built
      if (!started) {
        // give the UDP port and the native memory back instead of leaking them
        if (newServer != null) {
          newServer.close();
        }
        if (newEndpoint != null) {
          newEndpoint.close();
        }
        if (config != null) {
          config.close();
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
   * Gets the maximum number of concurrent peer-initiated bidirectional streams per QUIC connection.<br>
   * On the server this is the limit advertised to the clients. On the client it bounds the size of the
   * connection pool, since every pooled connection holds one stream for as long as it is in the pool
   * (see {@link QuicheTripConnectionPool}).
   *
   * @return the maximum number of streams, always &ge; 1
   */
  public int getMaxStreams() {
    int maxStreams = getUriPars().getOrDefault("maxstreams", DEFAULT_MAX_STREAMS);
    if (maxStreams < 1) {
      throw new TripRuntimeException(
        "QUIC maxstreams must be >= 1, got " + maxStreams + " from URI " + getUri());
    }
    return maxStreams;
  }

  /**
   * Gets the flow control window per stream in bytes.<br>
   * A peer may send at most that many bytes on a stream before the receiving side has to grant further
   * credits (see RFC-9000, 4). The window should be at least as large as the largest call to be
   * transferred without intermediate credit updates. The connection window is a multiple of it, see
   * {@link #CONNECTION_WINDOW_FACTOR}.
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
   * Creates the QUIC configuration both ends of this transport share.
   * <p>
   * The client applies the same settings, so that the two directions are symmetric and the effective
   * limits do not depend on which end a value came from.
   *
   * @param alpnId the ALPN protocol name
   * @return the configuration, to be closed after every connection created from it
   */
  QuicheConfig createConfig(String alpnId) {
    int streamWindow = getStreamWindow();
    return new QuicheConfig()
      .setApplicationProtocols(alpnId)
      .setMaxIdleTimeout(getMaxIdleSeconds() * 1000L)
      .setInitialMaxData((long) streamWindow * CONNECTION_WINDOW_FACTOR)
      .setInitialMaxStreamDataBidiLocal(streamWindow)
      .setInitialMaxStreamDataBidiRemote(streamWindow)
      .setInitialMaxStreamsBidi(getMaxStreams())
      // TRIP uses bidirectional streams only
      .setInitialMaxStreamsUni(0);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to shut down the QUIC endpoint, which releases the UDP socket and terminates the
   * connections of all clients.
   * <p>
   * The endpoint is closed <em>before</em> the transport's thread pools are shut down, so that no
   * incoming stream is dispatched to a pool that is already gone. Closing it waits for the client
   * connections to terminate, which is why it happens outside the transport's monitor: connection
   * handlers still running must be able to invoke synchronized transport methods.
   */
  @Override
  public void close() {
    QuicheEndpoint currentEndpoint;
    QuicheServer currentServer;
    QuicheConfig currentConfig;
    synchronized (this) {
      currentEndpoint = endpoint;
      currentServer = server;
      currentConfig = serverConfig;
      endpoint = null;
      server = null;
      serverConfig = null;
      accepting = false;
    }
    if (currentEndpoint != null) {
      currentEndpoint.close();
    }
    if (currentServer != null) {
      currentServer.close();
    }
    // the configuration must outlive the connections created from it, hence last
    if (currentConfig != null) {
      currentConfig.close();
    }
    super.close();
  }

  /**
   * Closes both halves of a QUIC stream.
   * <p>
   * Both are closed even if closing one of them fails, because a half-closed stream is never released
   * by the peer: it keeps one of the peer's stream slots occupied until the whole QUIC connection is gone.
   * The failures are reported combined, the first one carrying the others as suppressed exceptions.
   *
   * @param quicStream the stream to close
   * @throws IOException if closing one or both halves failed
   */
  static void closeStream(QuicheStream quicStream) throws IOException {
    IOException failure = null;
    try {
      quicStream.getOutputStream().close();
    }
    catch (IOException e) {
      failure = e;
    }
    try {
      quicStream.getInputStream().close();
    }
    catch (IOException e) {
      if (failure == null) {
        failure = e;
      }
      else if (e != failure) {    // addSuppressed rejects self-suppression
        failure.addSuppressed(e);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  /**
   * Creates a trip stream for the given QUIC stream.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param quicStream the QUIC stream
   * @return the trip stream
   */
  TripStream createStream(Dictionary dictionary, QuicheStream quicStream) {
    TripFactory factory = TripFactory.getInstance();
    return factory.createStream(dictionary,
                                factory.createSerializer(quicStream.getOutputStream()),
                                factory.createDeserializer(quicStream.getInputStream()));
  }


  @Override
  protected Pool<Connection> createClientConnectionPool() {
    return new QuicheTripConnectionPool(this, getClientDictionary());
  }

}
