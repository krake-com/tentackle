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

import org.tentackle.common.InterruptedRuntimeException;
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
   * The smallest QUIC max idle timeout the keep-alive can work with, in seconds.
   * <p>
   * KWIK derives the PING interval of its keep-alive actor as {@code idleTimeout / 2}, truncated to whole
   * seconds. Below this, the interval comes out as <em>zero</em> and the actor reschedules itself without
   * any delay: it then sends PING frames as fast as a CPU core can produce them, saturating the link and
   * making the datagram loss that QUIC flow control does not recover from (see {@link GuardedQuicOutputStream})
   * far more likely. Since there is no way to configure the interval, {@code quicidle} has to stay above this.
   */
  public static final int MIN_KEEP_ALIVE_IDLE_SECONDS = 2;

  private static final Logger LOGGER = Logger.get(QuicTripConnectionPool.class);


  private final QuicTripTransport transport;
  private final QuicClientConnection.Builder connectionBuilder;
  private final int keepAliveSeconds;
  private final int streamTimeoutSeconds;
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
    if (keepAliveSeconds != 0 && transport.getMaxIdleSeconds() < MIN_KEEP_ALIVE_IDLE_SECONDS) {
      throw new IllegalArgumentException(
        "QUIC quicidle must be >= " + MIN_KEEP_ALIVE_IDLE_SECONDS + " seconds when the keep-alive is enabled, got " +
        transport.getMaxIdleSeconds() + " from URI " + uri + " (or set keepalive=0)");
    }
    connectionBuilder = QuicClientConnection.newBuilder()
                                            .uri(uri)  // only host and port are used
                                            .applicationProtocol(alpnId)
                                            .connectTimeout(Duration.ofSeconds(connectTimeout))
                                            .maxIdleTimeout(Duration.ofSeconds(transport.getMaxIdleSeconds()))
                                            // the window the server may use for its responses, configured
                                            // explicitly so that both directions are symmetric
                                            .defaultStreamReceiveBufferSize((long) transport.getStreamWindow());
    if (!Boolean.parseBoolean(queryParameters.getOrDefault("certcheck", "false"))) {
      connectionBuilder.noServerCertificateCheck();
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


  /**
   * Discards the QUIC connection all pooled connections are multiplexed over.
   * <p>
   * Invoked when a write on one of the pooled streams stalled because the QUIC connection's flow control
   * got wedged (see {@link GuardedQuicOutputStream}). The credits of such a connection cannot be
   * recovered, so all of its streams are lost, not just the one that ran into it. Closing it makes
   * {@link QuicTripConnection#isOpen()} report {@code false} for every connection still in the pool, so
   * that they are dropped rather than handed out, and the next request establishes a replacement.
   * <p>
   * Runs on the watchdog thread, which is not the one that is stuck: the stream is reset first, so the
   * blocked writer is on its way out and releases the pool's monitor if it happens to hold it.
   */
  public void abortConnection() {
    synchronized (this) {
      closeConnection();
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
    try {
      return future.get(streamTimeoutSeconds, TimeUnit.SECONDS);
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
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedRuntimeException(e);
    }
  }

  /**
   * Establishes the QUIC connection all pooled connections are multiplexed over.<br>
   * A previously established connection is closed first.
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
