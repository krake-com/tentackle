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

import org.tentackle.io.SslParameters;
import org.tentackle.io.UriHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.PoolSlot;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.AbstractConnectionPool;
import org.tentackle.trip.transport.ConnectionPoolSlot;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The QUIC client connection pool.
 * <p>
 * All pooled connections are streams multiplexed over a single QUIC connection. Since that
 * connection is discarded silently by both peers as soon as it stays idle for longer than the QUIC
 * max idle timeout (see {@link QuicTripTransport#getMaxIdleSeconds()}), which is way shorter than
 * the pool's idle timeout, it is kept alive by sending PING frames. Otherwise, the pool would hand
 * out connections belonging to a QUIC connection that is long gone.
 * <p>
 * Because each pooled connection holds one of the streams the peer allows for the whole time it is
 * in the pool, the pool's maximum size defaults to {@link QuicTripTransport#getMaxStreams()} instead
 * of being unlimited. Running into the peer's stream limit does not fail an individual request. It
 * blocks the requesting thread indefinitely, and with it every other thread using this transport.
 */
public class QuicTripConnectionPool extends AbstractConnectionPool {

  /**
   * The default connect timeout in seconds, applied if the URI does not specify {@code connecttimeout}.
   * <p>
   * This is the timeout for establishing the QUIC connection, i.e., the handshake with the server,
   * not for the individual streams created per pool slot.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10;

  /**
   * The default stream timeout in seconds, applied if the URI does not specify {@code streamtimeout}.
   * <p>
   * This is the timeout for opening a stream on an established QUIC connection, i.e., for waiting until
   * the peer grants the credits to do so.
   */
  public static final int DEFAULT_STREAM_TIMEOUT = 10;

  /**
   * The seconds waited for the handshake beyond {@code connecttimeout}.
   * <p>
   * KWIK applies that timeout itself, so this is only the margin that keeps {@link #connect()} from
   * waiting forever should its wait not return at all.
   */
  public static final int CONNECT_TIMEOUT_GRACE = 5;

  private static final Logger LOGGER = Logger.get(QuicTripConnectionPool.class);


  private final QuicTripTransport transport;
  private final QuicClientConnection.Builder connectionBuilder;
  private final int keepAliveSeconds;
  private final int connectTimeoutSeconds;
  private final int streamTimeoutSeconds;
  private QuicClientConnection connection;

  /**
   * The certificate exception the trust manager installed by {@link #createCertificateCheckingTrustManager}
   * saw during the handshake started by the current or most recent {@link #connect()}, null if there was
   * none (or {@code certcheck} is disabled). KWIK discards the original exception once it converts it
   * into its own {@code BadCertificateAlert}, so this is the only way to recover it for
   * {@link #wrapCertificateFailure}.
   */
  private volatile CertificateException certificateFailure;

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
    connectTimeoutSeconds = transport.getUriPars().getOrDefault("connecttimeout", DEFAULT_CONNECT_TIMEOUT);
    if (connectTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
        "QUIC connect timeout must be > 0 seconds, got " + connectTimeoutSeconds + " from URI " + uri);
    }
    streamTimeoutSeconds = transport.getUriPars().getOrDefault("streamtimeout", DEFAULT_STREAM_TIMEOUT);
    if (streamTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
        "QUIC stream timeout must be > 0 seconds, got " + streamTimeoutSeconds + " from URI " + uri);
    }
    /*
     * The peer grants at most maxstreams concurrent streams per QUIC connection, and every pooled
     * connection holds one of them for as long as it sits in the pool. An unlimited pool would
     * therefore keep enlarging itself until it runs into that limit, where KWIK's createStream()
     * waits for credits that can only become available when a pooled connection is closed - which
     * the pool cannot do while a thread is stuck in createStream() holding its monitor.
     */
    if (getMaximumSize() == 0) {    // not configured in the URI: unlimited
      setMaximumSize(transport.getMaxStreams());
    }
    Map<String, String> queryParameters = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);
    String scheme = uri.getScheme();    // default alpnId is the same as the scheme
    String alpnId = queryParameters.getOrDefault("alpnid", scheme);
    keepAliveSeconds = transport.getUriPars().getOrDefault("keepalive", -1);
    connectionBuilder = QuicClientConnection.newBuilder()
                                            .uri(uri)  // only host and port are used
                                            .applicationProtocol(alpnId)
                                            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                                            .maxIdleTimeout(Duration.ofSeconds(transport.getMaxIdleSeconds()))
                                            // the window the server may use for its responses, configured
                                            // explicitly so that both directions are symmetric
                                            .defaultStreamReceiveBufferSize((long) transport.getStreamWindow());
    if (!Boolean.parseBoolean(queryParameters.getOrDefault("certcheck", "true"))) {
      connectionBuilder.noServerCertificateCheck();
    }
    else {
      connectionBuilder.customTrustManager(createCertificateCheckingTrustManager(uri));
    }

    connect();
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
        closeConnection();
      }
    }
  }


  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    QuicStream quicStream = createQuicStream();   // may re-establish the QUIC connection
    QuicClientConnection quicConnection = connection;
    try {
      return new ConnectionPoolSlot(this, slotNumber,
                                    new QuicTripConnection(transport, getDictionary(),
                                                           quicConnection::isConnected, quicStream));
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
   * Creates a new client quic stream.<br>
   * Re-establishes the QUIC connection if it has been terminated meanwhile.
   *
   * @return the quic stream
   */
  protected QuicStream createQuicStream() {
    QuicClientConnection quicConnection = connection;
    if (quicConnection == null || !quicConnection.isConnected()) {
      // idle timeout, peer restarted, network loss, ... (or a previous connect attempt failed)
      LOGGER.info("QUIC connection to {0} is gone -> reconnecting", getTransport().getUri());
      connect();
      quicConnection = connection;
    }
    return createQuicStream(quicConnection);
  }

  /**
   * Opens a bidirectional stream on the given QUIC connection, bounded by {@code streamtimeout}.
   * <p>
   * KWIK's {@code createStream} blocks until the peer grants the credits to open another stream, and the
   * only way to get them is closing one of the streams already open. Since all of them belong to pooled
   * connections and this method runs with the pool's (and the transport's) monitor held, no other thread
   * could return one: the wait must be bounded, or the whole client transport deadlocks.
   * <p>
   * Under normal circumstances the limit is never reached because the pool's maximum size is capped to
   * the number of streams the peer allows. It can only happen if the peer grants fewer streams than this
   * client's {@code maxstreams} says, so the timeout is a safety net for a configuration mismatch.
   * <p>
   * Waiting for the stream is <em>interrupt-agnostic</em>, just like a read or a write on a blocking
   * socket: an interrupt of the calling thread neither aborts it nor gets lost. Tentackle's task
   * dispatchers interrupt their own thread whenever work is queued, in order to cut a pending sleep
   * short, and such an interrupt regularly lands while that thread is inside a remote invocation
   * ({@code DbModificationTracker} polls from its dispatcher thread). Failing here would turn that into
   * a broken TRIP connection, so the interrupt is only remembered and re-asserted before returning. The
   * deadline is absolute, so no number of interrupts can stretch the wait beyond {@code streamtimeout}.
   *
   * @param quicConnection the established QUIC connection
   * @return the quic stream
   */
  protected QuicStream createQuicStream(QuicClientConnection quicConnection) {
    CompletableFuture<QuicStream> future = new CompletableFuture<>();
    Thread creator = Thread.ofVirtual().name("QUIC stream creator " + getTransport().getUri()).start(() -> {
      try {
        QuicStream quicStream = quicConnection.createStream(true);
        if (!future.complete(quicStream)) {
          // we were too late: close the stream instead of leaking it (and one of the peer's stream slots)
          closeQuietly(quicStream);
        }
      }
      catch (Throwable t) {
        future.completeExceptionally(t);
      }
    });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(streamTimeoutSeconds);
    boolean interrupted = false;
    try {
      for (;;) {
        try {
          return future.get(Math.max(deadline - System.nanoTime(), 0), TimeUnit.NANOSECONDS);
        }
        catch (InterruptedException e) {
          interrupted = true;   // keep waiting, see above
        }
      }
    }
    catch (TimeoutException e) {
      future.completeExceptionally(e);    // marks the stream abandoned, should it still show up
      creator.interrupt();                // KWIK aborts a blocked createStream when interrupted
      TripRuntimeException tex = new TripRuntimeException(
        "no QUIC stream available on " + getTransport().getUri() + " within " + streamTimeoutSeconds +
        " seconds: the peer grants fewer than the configured " + transport.getMaxStreams() + " streams", e);
      tex.setTemporary(true);   // may well succeed again once a pooled connection is returned and closed
      throw tex;
    }
    catch (ExecutionException e) {
      throw new TripRuntimeException("creating QUIC stream failed", e.getCause());
    }
    finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Establishes the QUIC connection all pooled connections are multiplexed over.<br>
   * A previously established connection is closed first.
   * <p>
   * KWIK waits for the handshake on the calling thread and gives up as soon as that thread is
   * interrupted: it aborts the handshake and throws a bare {@code RuntimeException}. An interrupt of the
   * calling thread is nothing this transport may fail on, though &mdash; see
   * {@link #createQuicStream(QuicClientConnection)} &mdash; so the handshake runs on a thread of its own
   * and is waited for the same interrupt-agnostic way, bounded by {@code connecttimeout}.
   */
  protected void connect() {
    /*
     * KWIK shuts a connection's keep-alive thread down when the connection is closed explicitly, but not
     * when it is terminated silently (idle timeout, peer close, network loss). Simply dropping the old
     * connection here would leak that thread - a non-daemon one - for every connection ever lost, and
     * keep the JVM from terminating.
     */
    closeConnection();
    URI uri = getTransport().getUri();
    certificateFailure = null;   // stale value of a previous attempt must not be misattributed

    CompletableFuture<QuicClientConnection> future = new CompletableFuture<>();
    Thread connector = Thread.ofVirtual().name("QUIC connector " + uri).start(() -> {
      QuicClientConnection quicConnection = null;
      try {
        quicConnection = connectionBuilder.build();
        quicConnection.connect();
        if (!future.complete(quicConnection)) {
          // we were too late: close it instead of leaking the connection and its threads
          closeQuietly(quicConnection);
        }
      }
      catch (Throwable t) {
        if (quicConnection != null) {
          closeQuietly(quicConnection);
        }
        future.completeExceptionally(t);
      }
    });

    // KWIK bounds the handshake itself, this is the outer bound in case its wait does not return at all
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(connectTimeoutSeconds + CONNECT_TIMEOUT_GRACE);
    boolean interrupted = false;
    try {
      for (;;) {
        try {
          QuicClientConnection quicConnection =
            future.get(Math.max(deadline - System.nanoTime(), 0), TimeUnit.NANOSECONDS);
          if (keepAliveSeconds != 0) {
            // negative means: as long as this pool lives
            quicConnection.keepAlive(keepAliveSeconds < 0 ? Integer.MAX_VALUE : keepAliveSeconds);
          }
          connection = quicConnection;
          return;
        }
        catch (InterruptedException e) {
          interrupted = true;   // keep waiting, see above
        }
      }
    }
    catch (TimeoutException e) {
      future.completeExceptionally(e);    // marks the connection abandoned, should it still show up
      connector.interrupt();
      throw new TripRuntimeException("creating QUIC connection to " + uri + " failed", e);
    }
    catch (ExecutionException e) {
      throw new TripRuntimeException("creating QUIC connection to " + uri + " failed",
                                      wrapCertificateFailure(e.getCause()));
    }
    finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Creates the {@link X509TrustManager} installed on {@link #connectionBuilder} when {@code certcheck} is
   * enabled.<br>
   * KWIK converts any {@link CertificateException} thrown by the trust manager into its own
   * {@code BadCertificateAlert}, which is in turn downgraded to a plain, causeless {@link java.net.ConnectException}
   * by the time {@link #connect()} sees it (see {@link #wrapCertificateFailure}). Delegating to a trust
   * manager of our own, rather than leaving KWIK to build its default one internally, is the only way to
   * recover the original exception describing <em>why</em> the certificate was rejected.
   * <p>
   * The delegate itself is built exactly the way KWIK's own default trust manager is: a {@code PKIX}
   * {@link TrustManagerFactory} initialized with the truststore configured via {@code tsfile}/{@code tspass}
   * ({@link SslParameters#getTrustStore()}), or the JVM's default cacerts if none is configured - so
   * behavior is unchanged for callers that do not set a truststore.
   *
   * @param uri the transport URI
   * @return the trust manager
   */
  private X509TrustManager createCertificateCheckingTrustManager(URI uri) {
    X509TrustManager delegate;
    try {
      TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
      factory.init(new SslParameters(uri).getTrustStore());
      delegate = (X509TrustManager) factory.getTrustManagers()[0];
    }
    catch (GeneralSecurityException e) {
      throw new TripRuntimeException("cannot create the trust manager for " + uri, e);
    }
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
          delegate.checkServerTrusted(chain, authType);
        }
        catch (CertificateException e) {
          certificateFailure = e;
          throw e;
        }
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
      }
    };
  }

  /**
   * Wraps a QUIC connect failure caused by a rejected server certificate as an {@link SSLHandshakeException},
   * matching what the TCP/SSL transport throws for the same failure, so code reacting to a certificate
   * failure (such as {@code LoginFailedWithUpdateHandler}) does not need to know which transport is in use.
   *
   * @param cause the exception KWIK threw, typically a causeless {@code ConnectException}
   * @return an {@code SSLHandshakeException} wrapping the certificate failure seen by the trust manager
   *         installed in {@link #createCertificateCheckingTrustManager}, or {@code cause} unchanged if
   *         there was none
   */
  private Throwable wrapCertificateFailure(Throwable cause) {
    CertificateException certEx = certificateFailure;
    if (certEx == null) {
      return cause;
    }
    SSLHandshakeException sslEx = new SSLHandshakeException(certEx.getMessage());
    sslEx.initCause(certEx);
    return sslEx;
  }

  /**
   * Closes the QUIC connection if there is one.<br>
   * Closing is best effort: the connection may be gone already.
   */
  protected void closeConnection() {
    QuicClientConnection quicConnection = connection;
    connection = null;
    if (quicConnection != null) {
      try {
        quicConnection.close();
      }
      catch (RuntimeException e) {
        LOGGER.warning("closing the QUIC connection to " + getTransport().getUri() + " failed", e);
      }
    }
  }

  /**
   * Closes a quic connection, ignoring any errors.
   *
   * @param quicConnection the connection to close
   */
  private void closeQuietly(QuicClientConnection quicConnection) {
    try {
      quicConnection.close();
    }
    catch (RuntimeException e) {
      LOGGER.warning("closing the QUIC connection to " + getTransport().getUri() + " failed", e);
    }
  }

  /**
   * Closes a quic stream, ignoring any errors.
   *
   * @param quicStream the stream to close
   */
  private void closeQuietly(QuicStream quicStream) {
    try {
      QuicTripTransport.closeStream(quicStream);
    }
    catch (IOException e) {
      LOGGER.warning("closing " + quicStream + " failed", e);
    }
  }

}
