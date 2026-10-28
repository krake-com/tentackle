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

package org.tentackle.io;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * A TCP client socket factory with configurable SSL/TLS support.<br>
 * <p>
 * In <em>plain</em> mode (default constructor) it creates ordinary TCP sockets.
 * In <em>SSL</em> mode (constructed with {@link SslParameters}) it creates {@link SSLSocket}s
 * from an {@link javax.net.ssl.SSLContext} and honors the configured SSL options (enabled protocols,
 * cipher suites, key-exchange groups, endpoint identification). A client certificate is presented when the SSL parameters
 * provide a keystore; its alias can be selected explicitly.
 *
 * @see SslParameters
 * @see TcpServerSocketFactory
 */
public class TcpSocketFactory extends SocketFactory {

  private final SslParameters sslParameters;   // null in plain mode
  private final SocketFactory delegate;        // the underlying factory the sockets are created by

  /**
   * Creates a plain (non-SSL) TCP socket factory.
   */
  public TcpSocketFactory() {
    this.sslParameters = null;
    this.delegate = SocketFactory.getDefault();
  }

  /**
   * Creates an SSL/TLS TCP socket factory.
   *
   * @param sslParameters the resolved SSL parameters, never null
   */
  public TcpSocketFactory(SslParameters sslParameters) {
    this.sslParameters = sslParameters;
    this.delegate = sslParameters.getSSLContext().getSocketFactory();
  }

  @Override
  public Socket createSocket() throws IOException {
    return configure(delegate.createSocket());
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return configure(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
    return configure(delegate.createSocket(host, port, localHost, localPort));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return configure(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return configure(delegate.createSocket(address, port, localAddress, localPort));
  }

  /**
   * Applies the SSL options to a freshly created socket.
   *
   * @param socket the created socket
   * @return the configured socket
   */
  private Socket configure(Socket socket) {
    if (sslParameters != null) {
      sslParameters.configureClient((SSLSocket) socket);
    }
    return socket;
  }

}
