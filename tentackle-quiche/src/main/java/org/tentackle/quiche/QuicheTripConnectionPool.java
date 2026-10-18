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
import org.tentackle.log.Logger;
import org.tentackle.misc.PoolSlot;
import org.tentackle.quiche.engine.QuicheConnection;
import org.tentackle.quiche.engine.QuicheEndpoint;
import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.quiche.engine.StreamLimitException;
import org.tentackle.quiche.ffm.ConnectionError;
import org.tentackle.quiche.ffm.PemExporter;
import org.tentackle.quiche.ffm.QuicheConfig;
import org.tentackle.quiche.ffm.QuicheException;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.AbstractConnectionPool;
import org.tentackle.trip.transport.ConnectionPoolSlot;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;

/**
 * The QUIC client connection pool.
 * <p>
 * All pooled connections are streams multiplexed over a single QUIC connection. Since that connection is
 * discarded silently by both peers as soon as it stays idle for longer than the QUIC max idle timeout
 * (see {@link QuicheTripTransport#getMaxIdleSeconds()}), which is much shorter than the pool's idle
 * timeout, it is kept alive by sending ack-eliciting packets. Otherwise, the pool would hand out
 * connections belonging to a QUIC connection that is long gone.
 * <p>
 * Because each pooled connection holds one of the streams the peer allows for the whole time it is in the
 * pool, the pool's maximum size defaults to {@link QuicheTripTransport#getMaxStreams()} instead of being
 * unlimited. Should the peer nevertheless grant fewer streams, opening another one fails at once with a
 * <em>temporary</em> {@link TripRuntimeException}.
 * <p>
 * The QUIC connection is established right away when the pool is created, which in turn happens when the
 * transport hands out its first client connection. The server's host name is resolved only then, once
 * for the lifetime of the pool.
 * <p>
 * The QUIC connection migrates to a new source port when its path stalls (see
 * {@link QuicheTripTransport#getMigrationProbeTimeouts()}). If it is lost anyway, it is re-established on
 * a new endpoint, i.e., from a fresh source port as well: the one the lost connection used may well be
 * what got it stuck. That happens when the next connection is requested from the pool, see
 * {@link #restoreMinimumSize(int)}.
 * <p>
 * A lent connection holds a lease on the QUIC connection (see {@link QuicheConnection#acquire()}), since a
 * call is in flight on it. A QUIC connection whose path stalls when it cannot migrate any further is
 * therefore given up on right away if no call is in flight, and replaced with the next {@link #get()},
 * but kept for the calls in flight otherwise.
 */
public class QuicheTripConnectionPool extends AbstractConnectionPool {

  /**
   * The default connect timeout in seconds, applied if the URI does not specify {@code connecttimeout}.
   * <p>
   * This is the timeout for establishing the QUIC connection, i.e., the handshake with the server,
   * not for the individual streams created per pool slot.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10;

  /**
   * The shortest keep-alive interval derived from the QUIC max idle timeout.
   * <p>
   * Keeps the keep-alive traffic reasonable for the usual idle timeouts of tens of seconds. It is a lower
   * bound only: a short idle timeout wins over it, see {@link #determineKeepAliveMillis()}.
   */
  private static final long MIN_KEEP_ALIVE_MILLIS = 500;

  private static final Logger LOGGER = Logger.get(QuicheTripConnectionPool.class);


  private final QuicheTripTransport transport;
  private QuicheEndpoint endpoint;          // replaced with every new QUIC connection
  private final QuicheConfig config;
  private final InetSocketAddress serverAddress;
  private final String serverName;         // verified against the server's certificate, null to skip that check
  private final int connectTimeoutSeconds;
  private final int keepAliveSeconds;
  private final int migrationProbeTimeouts;
  private QuicheConnection connection;

  /**
   * Creates a QUIC connection pool and establishes the QUIC connection.
   *
   * @param transport the transport associated with this pool
   * @param dictionary the dictionary associated with this pool
   * @throws IllegalArgumentException if the URI has no port &gt; 0, or {@code connecttimeout} is not &gt; 0
   * @throws TripRuntimeException if a URI parameter is invalid or the QUIC connection cannot be established
   */
  public QuicheTripConnectionPool(QuicheTripTransport transport, Dictionary dictionary) {
    super(transport, dictionary);
    this.transport = transport;

    QuicheLogger.install();

    URI uri = transport.getUri();
    if (uri.getPort() <= 0) {
      throw new IllegalArgumentException("QUIC port number must be > 0, got " + uri.getPort() + " from URI " + uri);
    }
    connectTimeoutSeconds = transport.getUriPars().getOrDefault("connecttimeout", DEFAULT_CONNECT_TIMEOUT);
    if (connectTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
        "QUIC connect timeout must be > 0 seconds, got " + connectTimeoutSeconds + " from URI " + uri);
    }
    /*
     * The peer grants at most maxstreams concurrent streams per QUIC connection, and every pooled
     * connection holds one of them for as long as it sits in the pool. An unlimited pool would
     * therefore keep enlarging itself until it runs into that limit.
     */
    if (getMaximumSize() == 0) {    // not configured in the URI: unlimited
      setMaximumSize(transport.getMaxStreams());
    }

    Map<String, String> queryParameters = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);
    String alpnId = queryParameters.getOrDefault("alpnid", uri.getScheme());
    keepAliveSeconds = transport.getUriPars().getOrDefault("keepalive", -1);   // negative: derived
    migrationProbeTimeouts = transport.getMigrationProbeTimeouts();
    try {
      serverAddress = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot resolve the host of URI " + uri, e);
    }

    SslParameters sslParameters = new SslParameters(uri);
    boolean certCheck = Boolean.parseBoolean(queryParameters.getOrDefault("certcheck", "true"));
    /*
     * The host name is handed to quiche, which sends it as SNI and, if the peer is verified, checks the
     * server's certificate against it. A client connecting by an address the certificate is not issued for
     * can opt out with hostcheck=false: without a name, quiche verifies the certificate chain only (and
     * sends no SNI, which neither a Tentackle server nor KWIK needs to pick its certificate).
     */
    serverName = certCheck && !sslParameters.isHostCheck() ? null : uri.getHost();

    QuicheConfig newConfig = transport.createConfig(alpnId);
    try {
      /*
       * Like the TCP transports, a QUIC client checks the server's certificate by default, against the
       * truststore (tsfile), or the JVM's default CAs if none is configured. BoringSSL knows neither, so
       * the certificates are handed over explicitly. A Tentackle server that presents a self-signed
       * certificate and relies on the TRIP handshake to authenticate the peers instead can opt out with
       * certcheck=false.
       */
      if (certCheck) {
        newConfig.verifyPeer(true);
        KeyStore trustStore = trustStore(sslParameters);
        if (trustStore != null) {
          Path trusted = PemExporter.writeTrustedCertificates(trustStore);
          try {
            newConfig.loadVerifyLocations(trusted);
          }
          finally {
            PemExporter.delete(trusted);    // read by quiche right away
          }
        }
      }
      else {
        newConfig.verifyPeer(false);
      }
    }
    catch (RuntimeException e) {
      newConfig.close();
      throw e;
    }
    config = newConfig;

    try {
      connect();
    }
    catch (RuntimeException e) {
      // the endpoint and the configuration would leak otherwise: the pool is never handed out
      if (endpoint != null) {
        endpoint.close();
      }
      config.close();
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Deliberately not synchronized: {@link org.tentackle.misc.AbstractPool#shutdown()} closes the pooled
   * connections without holding the pool's monitor, on purpose, since it waits for each of them. Only
   * accessing the QUIC connection needs the monitor.
   */
  @Override
  public void shutdown() {
    try {
      super.shutdown();
    }
    finally {
      synchronized (this) {
        // the QUIC connection must be released even if closing the pooled streams failed
        connection = null;
        if (endpoint != null) {
          endpoint.close();
        }
        // the configuration must outlive the connections created from it, hence after the endpoint
        config.close();
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to take a lease on the QUIC connection for the call the connection is lent for. Not
   * synchronized: the lease is lock-free, and taking the pool's monitor here would serialize all calls.
   */
  @Override
  public Connection get() {
    for (;;) {
      QuicheTripConnection tripConnection = (QuicheTripConnection) super.get();
      if (tripConnection.lease()) {
        return tripConnection;
      }
      // The QUIC connection was given up on between the pool's check that the connection is open and the
      // lease. It is not open anymore, so put() removes it from the pool, and the next round takes another
      // connection or creates one, which re-establishes the QUIC connection.
      super.put(tripConnection);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to release the lease taken by {@link #get()}.
   */
  @Override
  public void put(Connection connection) {
    if (connection instanceof QuicheTripConnection tripConnection) {
      tripConnection.unlease();
    }
    super.put(connection);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to do nothing while the QUIC connection is gone. Creating a slot would re-establish it
   * first, which waits for the handshake for as long as {@code connecttimeout} allows if the server is
   * not reachable &mdash; with the pool's monitor held, on the thread that monitors all pools, and again
   * with every round. The connection is re-established by the next {@link #get()} instead, i.e., when it
   * is needed.
   */
  @Override
  protected void restoreMinimumSize(int num) {
    QuicheConnection quicConnection = connection;
    if (quicConnection != null && quicConnection.isConnected()) {
      super.restoreMinimumSize(num);
    }
  }

  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    QuicheStream quicStream = createQuicStream();   // may re-establish the QUIC connection
    try {
      return new ConnectionPoolSlot(this, slotNumber,
                                    new QuicheTripConnection(transport, getDictionary(), quicStream));
    }
    catch (RuntimeException e) {
      // The connection did not take ownership of the stream because sending the TRIP preamble failed.
      // Dropping the stream would keep one of the peer's stream slots occupied until the whole QUIC
      // connection is gone, so repeated failures would run the pool out of streams for good.
      closeQuietly(quicStream);
      throw e;
    }
  }

  /**
   * Creates a new client QUIC stream.<br>
   * Re-establishes the QUIC connection if it has been terminated meanwhile.
   *
   * @return the QUIC stream
   * @throws TripRuntimeException if no stream can be opened, temporary if the peer grants no further one
   */
  QuicheStream createQuicStream() {
    QuicheConnection quicConnection = connection;
    if (quicConnection == null || !quicConnection.isConnected()) {
      // idle timeout, peer restarted, network loss, ... (or a previous connect attempt failed)
      LOGGER.info("QUIC connection to {0} is gone -> reconnecting", getTransport().getUri());
      connect();
      quicConnection = connection;
    }
    try {
      return quicConnection.openStream();
    }
    catch (StreamLimitException e) {
      TripRuntimeException tex = new TripRuntimeException(
        "no QUIC stream available on " + getTransport().getUri() +
        ": the peer grants fewer than the configured " + transport.getMaxStreams() + " streams", e);
      tex.setTemporary(true);   // may well succeed again once a pooled connection is returned and closed
      throw tex;
    }
    catch (QuicheException e) {
      throw new TripRuntimeException("creating QUIC stream failed", e);
    }
  }

  /**
   * Establishes the QUIC connection all pooled connections are multiplexed over.<br>
   * A previously established connection is closed first, together with its endpoint: the new connection
   * gets an endpoint of its own, and with it a fresh source port.
   */
  protected void connect() {
    closeConnection();
    URI uri = getTransport().getUri();
    QuicheEndpoint oldEndpoint = endpoint;
    try {
      endpoint = new QuicheEndpoint(new InetSocketAddress(0));
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot create the QUIC endpoint for " + uri, e);
    }
    finally {
      if (oldEndpoint != null) {
        oldEndpoint.close();
      }
    }
    QuicheConnection quicConnection = endpoint.connect(serverAddress, serverName, config);
    try {
      if (!quicConnection.awaitEstablished(connectTimeoutSeconds * 1000L)) {
        String reason = quicConnection.getFailure();
        throw new TripRuntimeException(
          "the QUIC handshake with " + uri + " did not complete within " + connectTimeoutSeconds +
          " seconds" + (reason == null ? "" : ": " + reason),
          certificateFailure(quicConnection.getLocalError()));
      }
    }
    catch (RuntimeException e) {
      quicConnection.closeGracefully(0, "handshake failed");
      throw e;
    }
    if (keepAliveSeconds != 0) {
      quicConnection.setKeepAliveMillis(determineKeepAliveMillis());
    }
    quicConnection.setMigrationProbeTimeouts(migrationProbeTimeouts);
    connection = quicConnection;
  }

  /**
   * The error code offset of a locally detected TLS alert among the QUIC transport error codes, per
   * <a href="https://www.rfc-editor.org/rfc/rfc9001.html#section-4.8">RFC 9001 &sect;4.8</a>: a TLS alert
   * numbered {@code n} is signaled as QUIC error code {@code TLS_ALERT_BASE + n}.
   */
  private static final long TLS_ALERT_BASE = 0x100;

  /**
   * The TLS alert numbers ({@code AlertDescription}, <a href="https://www.rfc-editor.org/rfc/rfc8446.html#section-6">RFC
   * 8446 &sect;6</a>) that mean the peer's certificate was rejected, as opposed to any other handshake
   * failure (protocol mismatch, decode error, ...). {@code certcheck=true} is the only thing that can make
   * quiche's own BoringSSL send one of these.
   */
  private static final Set<Long> CERTIFICATE_ALERTS = Set.of(
    42L,   // bad_certificate
    43L,   // unsupported_certificate
    44L,   // certificate_revoked
    45L,   // certificate_expired
    46L,   // certificate_unknown
    48L,   // unknown_ca
    49L,   // access_denied
    116L); // certificate_required

  /**
   * Recognizes a handshake failure caused by a rejected server certificate, and wraps it as an
   * {@link SSLHandshakeException} - matching what the TCP/SSL transports throw for the same failure - so
   * that code reacting to a certificate failure (such as the client auto-update's
   * {@code LoginFailedWithUpdateHandler}) does not need to know which transport is in use.
   * <p>
   * Any other handshake failure (an unreachable peer, a protocol mismatch, ...) is deliberately left
   * without a certificate-specific cause: misclassifying it would risk firing that auto-update for a
   * problem a new client version cannot fix.
   *
   * @param localError the structured error {@link QuicheConnection#getLocalError()} reported, null if none
   * @return the wrapped certificate failure, or null if {@code localError} is not a certificate alert
   */
  private static Throwable certificateFailure(ConnectionError localError) {
    if (localError == null || localError.isApp()) {
      return null;
    }
    long alert = localError.errorCode() - TLS_ALERT_BASE;
    if (alert < 0 || !CERTIFICATE_ALERTS.contains(alert)) {
      return null;
    }
    String reason = localError.reason();
    return new SSLHandshakeException(reason.isEmpty() ? "certificate rejected (alert " + alert + ")" : reason);
  }

  /**
   * Determines how often a keep-alive packet is to be sent.
   * <p>
   * A positive {@code keepalive} is taken as it is, with a warning if it is not safely below the idle
   * timeout. Otherwise (negative, the default) the interval is a third of the QUIC max idle timeout, so that
   * two keep-alives in a row may be lost before the connection is at risk, and at least
   * {@link #MIN_KEEP_ALIVE_MILLIS} &mdash; but never more than half of the idle timeout: the keep-alive must
   * fall due safely <em>before</em> the idle timeout, never together with it.
   * {@link QuicheConnection#runDueWork()} processes the timeout first, on purpose, so a keep-alive that is
   * due at the same moment comes too late and the connection is torn down instead of being kept alive. The
   * margin has to be large enough to absorb the drift of the idle timer, which the packets the connection
   * exchanges on its own &mdash; a loss probe and the acknowledgment answering it &mdash; restart.
   *
   * @return the interval in milliseconds, 0 to send no keep-alive packets
   */
  private long determineKeepAliveMillis() {
    long idleMillis = transport.getMaxIdleSeconds() * 1000L;
    if (keepAliveSeconds > 0) {
      long interval = keepAliveSeconds * 1000L;
      if (idleMillis > 0 && interval >= idleMillis / 2) {
        LOGGER.warning("the keep-alive interval of " + keepAliveSeconds + " seconds configured in " +
                       getTransport().getUri() + " is too close to the QUIC max idle timeout of " +
                       transport.getMaxIdleSeconds() + " seconds: the connection may time out anyway");
      }
      return interval;
    }
    // derived: no idle timeout at all (quicidle=0) means there is nothing to keep alive
    return Math.min(Math.max(idleMillis / 3, MIN_KEEP_ALIVE_MILLIS), idleMillis / 2);
  }

  /**
   * Gets the certificates the server's certificate is verified against.
   *
   * @param sslParameters the SSL parameters of the transport's URI
   * @return the configured truststore ({@code tsfile}), or the JVM's default CAs if none is configured,
   *         null if there are none at all
   * @throws TripRuntimeException if the default CAs cannot be determined
   */
  static KeyStore trustStore(SslParameters sslParameters) {
    KeyStore trustStore = sslParameters.getTrustStore();
    if (trustStore != null) {
      return trustStore;
    }
    // the very CAs a JSSE trust manager uses by default, i.e., what tripq and the TCP transports check against
    try {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init((KeyStore) null);
      KeyStore defaultCAs = KeyStore.getInstance(KeyStore.getDefaultType());
      defaultCAs.load(null, null);
      int index = 0;
      for (TrustManager trustManager : factory.getTrustManagers()) {
        if (trustManager instanceof X509TrustManager x509TrustManager) {
          for (X509Certificate issuer : x509TrustManager.getAcceptedIssuers()) {
            defaultCAs.setCertificateEntry("ca-" + index++, issuer);
          }
        }
      }
      return defaultCAs.size() > 0 ? defaultCAs : null;
    }
    catch (GeneralSecurityException | IOException e) {
      throw new TripRuntimeException("cannot determine the JVM's default trusted certificates", e);
    }
  }

  /**
   * Closes the QUIC connection if there is one.<br>
   * Closing is best effort: the connection may be gone already.
   */
  protected void closeConnection() {
    QuicheConnection quicConnection = connection;
    connection = null;
    if (quicConnection != null) {
      try {
        quicConnection.closeGracefully(0, "closed by the pool");
      }
      catch (RuntimeException e) {
        LOGGER.warning("closing the QUIC connection to " + getTransport().getUri() + " failed", e);
      }
    }
  }

  /**
   * Closes a QUIC stream, logging instead of propagating a failure.
   *
   * @param quicStream the stream to close
   */
  private void closeQuietly(QuicheStream quicStream) {
    try {
      QuicheTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      LOGGER.warning("closing " + quicStream + " failed", e);
    }
  }

}
