/*
 * Tentackle - http://www.tentackle.org
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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

/**
 * Zip-compressed server socket factory.
 *
 * @author harald
 */
public class CompressedSslServerSocketFactory extends SslRMIServerSocketFactory implements ServerSocketConfiguratorHolder {

  private volatile ServerSocketConfigurator socketConfigurator;   // the optional socket configurator


  /**
   * Creates a new instance of CompressedServerSocketFactory.
   *
   * @param enabledCipherSuites the enabled cipher suites, null for default
   * @param enabledProtocols the enabled protocols, null for default
   * @param needClientAuth true if server request SSL-client-authentication
   */
  public CompressedSslServerSocketFactory(String[] enabledCipherSuites,
                                          String[] enabledProtocols,
                                          boolean needClientAuth) {

    super(enabledCipherSuites, enabledProtocols, needClientAuth);
  }


  /**
   * Creates a CompressedServerSocketFactory with default cipher suites,
   * protocols and without client authentication.
   */
  public CompressedSslServerSocketFactory() {
    this(null, null, false);
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    return createConfiguredServerSocket(port);
  }


  /**
   * Gets the ssl client socket factory (without compression).
   *
   * @return the ssl client socket factory
   */
  protected SSLSocketFactory getSslSocketFactory() {
    return (SSLSocketFactory) SSLSocketFactory.getDefault();
  }


  @Override
  public ServerSocket createUnconfiguredServerSocket(int port) throws IOException {

    final SSLSocketFactory sslSocketFactory = getSslSocketFactory();

    return new ServerSocket(port) {

      @Override
      public Socket accept() throws IOException {
        Socket socket = super.accept();
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                socket, socket.getInetAddress().getHostName(), socket.getPort(), true);
        sslSocket.setUseClientMode(false);
        if (getEnabledCipherSuites() != null) {
          sslSocket.setEnabledCipherSuites(getEnabledCipherSuites());
        }
        if (getEnabledProtocols() != null) {
          sslSocket.setEnabledProtocols(getEnabledProtocols());
        }
        sslSocket.setNeedClientAuth(getNeedClientAuth());
        if (sslSocketFactory instanceof SocketConfiguratorHolder) {
          SocketConfigurator clientSocketConfigurator =
                  ((SocketConfiguratorHolder) sslSocketFactory).getSocketConfigurator();
          if (clientSocketConfigurator != null) {
            clientSocketConfigurator.configure(sslSocket);
          }
        }
        // wrap the ssl-socket by a compressed socket!
        return new CompressedSocketWrapper(sslSocket);
      }
    };
  }


  @Override
  public int hashCode() {
    int hash = 5;
    hash = 11 * hash + super.hashCode();
    hash = 11 * hash + Objects.hashCode(socketConfigurator);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj)) {
      return false;
    }
    final CompressedSslServerSocketFactory other = (CompressedSslServerSocketFactory) obj;
    return ServerSocketConfigurator.equals(this.socketConfigurator, other.socketConfigurator);
  }

  @Override
  public ServerSocketConfigurator getSocketConfigurator() {
    return socketConfigurator;
  }

  @Override
  public void setSocketConfigurator(ServerSocketConfigurator socketConfigurator) {
    this.socketConfigurator = socketConfigurator;
  }

}
