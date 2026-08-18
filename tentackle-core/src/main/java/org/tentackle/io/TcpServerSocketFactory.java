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

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * A TCP server socket factory with configurable SSL/TLS support.<br>
 * <p>
 * In <em>plain</em> mode (default constructor) it creates ordinary TCP server sockets.
 * In <em>SSL</em> mode (constructed with {@link SslParameters}) it creates {@link SSLServerSocket}s
 * from an {@link javax.net.ssl.SSLContext} that presents the certificate selected by its alias and
 * honors the configured SSL options (enabled protocols, cipher suites, client-certificate policy).
 * <p>
 * This factory is the SSL-alias-aware replacement for the default
 * {@link javax.net.ssl.SSLServerSocketFactory}, which always presents the first certificate
 * found in the keystore.
 *
 * @see SslParameters
 * @see TcpSocketFactory
 */
public class TcpServerSocketFactory extends ServerSocketFactory {

  private final SslParameters sslParameters;   // null in plain mode
  private final ServerSocketFactory delegate;  // the underlying factory the sockets are created by

  /**
   * Creates a plain (non-SSL) TCP server socket factory.
   */
  public TcpServerSocketFactory() {
    this.sslParameters = null;
    this.delegate = ServerSocketFactory.getDefault();
  }

  /**
   * Creates an SSL/TLS TCP server socket factory.
   *
   * @param sslParameters the resolved SSL parameters, never null
   */
  public TcpServerSocketFactory(SslParameters sslParameters) {
    this.sslParameters = sslParameters;
    this.delegate = sslParameters.getSSLContext().getServerSocketFactory();
  }

  @Override
  public ServerSocket createServerSocket() throws IOException {
    return configure(delegate.createServerSocket());
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    return configure(delegate.createServerSocket(port));
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog) throws IOException {
    return configure(delegate.createServerSocket(port, backlog));
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
    return configure(delegate.createServerSocket(port, backlog, ifAddress));
  }

  /**
   * Applies the SSL options to a freshly created server socket.
   *
   * @param serverSocket the created server socket
   * @return the configured server socket
   */
  private ServerSocket configure(ServerSocket serverSocket) {
    if (sslParameters != null) {
      sslParameters.configureServer((SSLServerSocket) serverSocket);
    }
    return serverSocket;
  }

}
