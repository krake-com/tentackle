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
 */
@TransportService(TcpTransport.PROTOCOL)
public class TcpTransport extends AbstractTransport {

  /**
   * The protocol name for TCP.
   */
  public static final String PROTOCOL = "trip";


  private final SocketFactory socketFactory = SocketFactory.getDefault();
  private final ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
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
  protected SocketFactory getSocketFactory() {
    return socketFactory;
  }

  /**
   * Gets the server socket factory.
   *
   * @return the socket factory
   */
  protected ServerSocketFactory getServerSocketFactory() {
    return serverSocketFactory;
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
   */
  protected ServerSocket createServerSocket() throws IOException {
    int backlog = getUriPars().getOrDefault("backlog", 50);
    String host = getUri().getHost();
    return host == null || host.isBlank() ?
             getServerSocketFactory().createServerSocket(getUri().getPort(), backlog) :
             getServerSocketFactory().createServerSocket(getUri().getPort(), backlog, InetAddress.getByName(host));
  }

}
