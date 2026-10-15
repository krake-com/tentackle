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

package org.tentackle.trip.transport;

import org.tentackle.misc.PoolSlot;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TripRuntimeException;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * The TCP client connection pool.
 * <p>
 * Establishing a connection is bounded by the URI query parameter {@code connecttimeout} (seconds,
 * default {@value #DEFAULT_CONNECT_TIMEOUT}). Connections are created with the pool's monitor held, by
 * the threads requesting one as well as by the {@link org.tentackle.misc.PoolTimeoutThread} all pools
 * share. Without a bound, a server that does not answer at all would block both for as long as the
 * operating system keeps retrying, which is a matter of minutes.
 * <p>
 * Notice that {@link #restoreMinimumSize(int)} is deliberately not overridden, unlike in the QUIC pools.
 * Those multiplex their pooled connections as streams over a single QUIC connection: they can tell whether
 * that connection is still alive, and only leave the pool below its minimum size if it is not, since a
 * stream on a live connection is cheap. TCP provides no such indication. Each pooled connection is a
 * connection of its own, and whether the server is reachable is only known after trying to connect. Not
 * restoring the minimum size would therefore have to apply always, which defeats the purpose of the
 * {@code minimum}: to keep connections ready, so that no request has to wait for the TCP connect and the
 * TLS handshake. It would not help the threads requesting a connection either, which wait for the server
 * just the same. The connect timeout bounds both.
 */
public class TcpConnectionPool extends AbstractConnectionPool {

  /**
   * The default connect timeout in seconds, applied if the URI does not specify {@code connecttimeout}.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT = 10;


  private final TcpTransport transport;
  private final int connectTimeoutSeconds;

  /**
   * Creates a TCP connection pool.
   *
   * @param transport     the transport associated with this pool
   * @param dictionary    the dictionary associated with this pool
   * @throws IllegalArgumentException if {@code connecttimeout} is not &gt; 0
   */
  public TcpConnectionPool(TcpTransport transport, Dictionary dictionary) {
    super(transport, dictionary);
    this.transport = transport;
    connectTimeoutSeconds = transport.getUriPars().getOrDefault("connecttimeout", DEFAULT_CONNECT_TIMEOUT);
    if (connectTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
        "TCP connect timeout must be > 0 seconds, got " + connectTimeoutSeconds + " from URI " + transport.getUri());
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to connect without holding the pool's monitor, since connecting a socket and the
   * TLS handshake may take a while, and other threads must still be able to get and put connections.
   */
  @Override
  protected boolean isCreatingSlotsConcurrently() {
    return true;
  }

  @Override
  protected PoolSlot<Connection> createSlot(int slotNumber) {
    return new ConnectionPoolSlot(this, slotNumber,
                                  new TcpConnection(transport, getDictionary(), createSocket()));
  }


  /**
   * Creates a new client socket.
   * <p>
   * Connecting is bounded by {@code connecttimeout}. For an SSL socket, this includes the TLS handshake,
   * which would otherwise be performed &mdash; unbounded &mdash; by the first write to the socket.
   * <p>
   * Notice: the socket is created unconnected, which the transport's socket factory must support.
   *
   * @return the connected socket
   * @throws TripRuntimeException if the connection could not be established, in time or at all
   */
  protected Socket createSocket() {
    URI uri = transport.getUri();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(connectTimeoutSeconds);
    Socket socket = null;
    try {
      socket = transport.getSocketFactory().createSocket();
      // resolves the host name, which the SSL socket needs for SNI and the endpoint identification
      socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), remainingMillis(deadline));
      if (socket instanceof SSLSocket sslSocket) {
        int soTimeout = sslSocket.getSoTimeout();
        sslSocket.setSoTimeout(remainingMillis(deadline));
        sslSocket.startHandshake();
        sslSocket.setSoTimeout(soTimeout);
      }
      return socket;
    }
    catch (IOException e) {
      closeQuietly(socket);
      throw new TripRuntimeException("connection to " + uri + " failed", e);
    }
    catch (RuntimeException e) {
      closeQuietly(socket);
      throw e;
    }
  }

  /**
   * Gets the time left until a deadline.
   *
   * @param deadline the deadline according to {@link System#nanoTime()}
   * @return the milliseconds left, at least 1 since 0 means no timeout at all
   */
  private static int remainingMillis(long deadline) {
    return (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
  }

  /**
   * Closes a socket that could not be connected.
   *
   * @param socket the socket, null if not created at all
   */
  private static void closeQuietly(Socket socket) {
    if (socket != null) {
      try {
        socket.close();
      }
      catch (IOException e) {
        // nothing we can do about it: the failure to connect is what gets reported
      }
    }
  }

}
