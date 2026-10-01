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

import org.tentackle.io.TcpServerSocketFactory;
import org.tentackle.io.TcpSocketFactory;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;

/**
 * Transport implementation for TCP.
 * <p>
 * The server binds to the host and port of the URI. Host {@code 0} binds to all interfaces.
 * The URI query parameter {@code backlog} sets the server socket's backlog (default 50).
 */
@TransportService(TcpTransport.PROTOCOL)
public class TcpTransport extends AbstractTransport {

  /**
   * The protocol name for TCP.
   */
  public static final String PROTOCOL = "trip";


  private SocketFactory socketFactory;
  private ServerSocketFactory serverSocketFactory;
  private boolean accepting;

  /**
   * Creates a new TCP transport.
   *
   * @param uri the URI of the transport
   */
  public TcpTransport(URI uri) {
    super(uri);
  }


  @Override
  public synchronized void accept() {
    super.accept();

    try {
      createServerThread(new SocketAcceptLoop(this, createServerSocket())).start();
      accepting = true;
    }
    catch (java.net.BindException e) {
      throw new TripRuntimeException("port already in use: " + getUri(), e);
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot initialize server socket: " + getUri(), e);
    }
  }

  @Override
  public boolean isAccepting() {
    return accepting;
  }

  /**
   * Creates a trip stream for the given client socket.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param socket the socket to use for communication
   * @return the trip stream
   */
  public TripStream createStream(Dictionary dictionary, Socket socket) {
    TripFactory factory = TripFactory.getInstance();
    try {
      return factory.createStream(dictionary,
                                  factory.createSerializer(socket.getOutputStream()),
                                  factory.createDeserializer(socket.getInputStream()));
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot create trip stream", e);
    }
  }


  /**
   * Gets the client socket factory.
   *
   * @return the socket factory
   */
  protected synchronized SocketFactory getSocketFactory() {
    if (socketFactory == null) {
      socketFactory = createSocketFactory();
    }
    return socketFactory;
  }

  /**
   * Gets the server socket factory.
   *
   * @return the socket factory
   */
  protected synchronized ServerSocketFactory getServerSocketFactory() {
    if (serverSocketFactory == null) {
      serverSocketFactory = createServerSocketFactory();
    }
    return serverSocketFactory;
  }

  /**
   * Creates the client socket factory.<br>
   * Plain TCP transports use an unencrypted factory. SSL-based transports
   * override this method to create an SSL-aware factory.
   *
   * @return the socket factory
   */
  protected SocketFactory createSocketFactory() {
    return new TcpSocketFactory();
  }

  /**
   * Creates the server socket factory.<br>
   * Plain TCP transports use an unencrypted factory. SSL-based transports
   * override this method to create an SSL-aware factory.
   *
   * @return the server socket factory
   */
  protected ServerSocketFactory createServerSocketFactory() {
    return new TcpServerSocketFactory();
  }

  @Override
  protected Pool<Connection> createClientConnectionPool() {
    return new TcpConnectionPool(this, getClientDictionary());
  }

  /**
   * Creates the server socket.
   *
   * @return the server socket
   * @throws IOException if creating the socket fails
   * @throws TripRuntimeException if the URI provides no host or an invalid port
   */
  protected ServerSocket createServerSocket() throws IOException {
    int backlog = getUriPars().getOrDefault("backlog", 50);
    URI uri = getUri();
    String host = uri.getHost();
    int port = uri.getPort();
    if (port < 0 || port > 0xffff) {
      throw new TripRuntimeException("invalid port number " + port + " in URI " + uri);
    }
    if ("0".equals(host)) { // use 0.0.0.0 to bind on all IPV4 interfaces, 0 binds also IPV6
      return getServerSocketFactory().createServerSocket(port, backlog);
    }
    else if (host != null) {
      return getServerSocketFactory().createServerSocket(port, backlog, InetAddress.getByName(host));
    }
    else {
      throw new TripRuntimeException("missing host in URI " + uri);
    }
  }

}
