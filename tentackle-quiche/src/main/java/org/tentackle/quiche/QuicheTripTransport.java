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
 * Transport implementation for QUIC on top of Cloudflare quiche, scheme {@value #PROTOCOL}.
 * <p>
 * This is the sibling of {@code tentackle-quic}, which provides the same transport on top of KWIK. Both
 * speak RFC-9000 and interoperate; they register different URI schemes so that an application can use
 * either, or both at once, and pick per connection. The URI parameters are described in the module's
 * documentation ({@code quiche.md}).
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

  /**
   * The default number of consecutive probe timeouts without any acknowledgment after which a client
   * migrates its QUIC connection to a new source port, applied if the URI does not specify {@code migrate}.
   * <p>
   * Two is the smallest number that tells a stalled path from a single burst of losses: the first probe
   * timeout alone fires on an ordinary lost tail, the second one means that not even the probe got through.
   * Since the probe timeout backs off exponentially, two of them take about three times the first one,
   * i.e., a few round trip times.
   */
  public static final int DEFAULT_MIGRATION_PROBE_TIMEOUTS = 2;

  /**
   * The number of connection IDs each side is willing to hold for the peer, including the one in use.
   * <p>
   * Every migration needs a spare connection ID of the peer, and quiche keeps the network paths a
   * connection has used for as long as they have connection IDs, so this also bounds how often a connection
   * can migrate. A connection that cannot migrate any further stays where it is, and if its path does not
   * recover, it runs into the idle timeout and the pool re-establishes it from a fresh source port.
   */
  public static final int ACTIVE_CONNECTION_ID_LIMIT = 8;


  private volatile QuicheEndpoint endpoint; // the endpoint accepting connections, null if not accepting
  private QuicheServer server;              // the accept policy of that endpoint
  private QuicheConfig serverConfig;        // outlives every connection created from it
  private volatile boolean accepting;       // volatile: isAccepting() is not synchronized
  private boolean closing;                  // true once close() was invoked

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
    if (closing) {
      // close() released the monitor to shut down the endpoint, but did not mark the transport closed yet
      throw new TripRuntimeException("transport " + this + " is closed");
    }
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
      // validates the URI parameters, before the private key leaves the keystore
      config = createConfig(alpnId)
                 // a Tentackle server does not ask its clients for a certificate; they authenticate
                 // through TRIP once the connection stands
                 .verifyPeer(false);

      // quiche takes PEM file paths, not a KeyStore, so the entry has to be exported. Quiche reads the
      // files right away, so they are deleted as soon as they are loaded.
      Path certificateChain = PemExporter.writeCertificateChain(keyStore, certificateAlias);
      try {
        config.loadCertChain(certificateChain);
      }
      finally {
        PemExporter.delete(certificateChain);
      }
      Path privateKey = PemExporter.writePrivateKey(keyStore, certificateAlias, sslParameters.getKeyPassword());
      try {
        config.loadPrivateKey(privateKey);
      }
      finally {
        PemExporter.delete(privateKey);
      }

      // "0" binds all interfaces, IPv6 as well where available, and "0.0.0.0" all IPv4 interfaces
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
        // give the UDP port and the native memory back instead of leaking them, in the order of close()
        if (newEndpoint != null) {
          newEndpoint.close();
        }
        if (newServer != null) {
          newServer.close();
        }
        if (config != null) {
          config.close();
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * False as well once the endpoint closed itself because its event loop failed.
   */
  @Override
  public boolean isAccepting() {
    QuicheEndpoint currentEndpoint = endpoint;
    return accepting && currentEndpoint != null && !currentEndpoint.isClosed();
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
   * @throws TripRuntimeException if the configured value is &lt; 1
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
   * credits (see RFC-9000, 4). It is configured on both ends: on the server it applies to the data a client
   * sends, i.e., to the size of the remote calls, and on the client to the size of the responses. The window
   * should be at least as large as the largest call to be transferred without intermediate credit updates.
   * The connection window is a multiple of it, see {@link #CONNECTION_WINDOW_FACTOR}.
   *
   * @return the window in bytes
   * @throws TripRuntimeException if the configured window is smaller than {@link #MIN_STREAM_WINDOW}
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
   * Gets after how many consecutive probe timeouts without any acknowledgment a client migrates its QUIC
   * connection to a new source port.<br>
   * Some networks hash each flow onto one of several paths by its addresses and ports, and a flow that
   * lands on a path that stopped delivering stays stuck there, whereas a new source port puts it onto
   * another one. QUIC can move a connection without disturbing its streams (RFC-9000, 9).
   *
   * @return the number of probe timeouts, 0 if the client never migrates
   * @throws TripRuntimeException if the configured value is &lt; 0
   */
  public int getMigrationProbeTimeouts() {
    int probeTimeouts = getUriPars().getOrDefault("migrate", DEFAULT_MIGRATION_PROBE_TIMEOUTS);
    if (probeTimeouts < 0) {
      throw new TripRuntimeException(
        "QUIC migrate must be >= 0, got " + probeTimeouts + " from URI " + getUri());
    }
    return probeTimeouts;
  }

  /**
   * Creates the QUIC configuration both ends of this transport share.
   * <p>
   * The client applies the same settings, so that the two directions are symmetric and the effective
   * limits do not depend on which end a value came from.
   *
   * @param alpnId the ALPN protocol name
   * @return the configuration, to be closed after every connection created from it
   * @throws TripRuntimeException if a URI parameter is invalid
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
      .setInitialMaxStreamsUni(0)
      // spare connection IDs are what a client migrates to a new source port with
      .setActiveConnectionIdLimit(ACTIVE_CONNECTION_ID_LIMIT);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to shut down the QUIC endpoint, which releases the UDP socket and terminates the
   * connections of all clients.
   * <p>
   * The endpoint is closed <em>before</em> the transport's thread pools are shut down, so that no
   * incoming stream is dispatched to a pool that is already gone. Closing it sends every client a
   * {@code CONNECTION_CLOSE} and waits up to a second for the event loop to stop, which is why it happens
   * outside the transport's monitor: connection handlers still running must be able to invoke synchronized
   * transport methods.
   */
  @Override
  public void close() {
    stopAccepting().run();
    super.close();
  }

  /**
   * Detaches the QUIC endpoint from this transport, the first step of {@link #close()}.
   * <p>
   * From then on {@link #accept()} fails. Since the endpoint is closed outside the transport's monitor and
   * the transport is marked closed only afterward, an {@code accept()} getting in between would otherwise
   * start an endpoint that nobody closes anymore.
   *
   * @return the action closing what was detached, to be run without holding the transport's monitor
   */
  protected synchronized Runnable stopAccepting() {
    closing = true;
    QuicheEndpoint currentEndpoint = endpoint;
    QuicheServer currentServer = server;
    QuicheConfig currentConfig = serverConfig;
    endpoint = null;
    server = null;
    serverConfig = null;
    accepting = false;
    return () -> {
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
    };
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
   * Creates a TRIP stream for the given QUIC stream.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param quicStream the QUIC stream
   * @return the TRIP stream
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
