/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.io;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;


interface RMISocketFactoryFactoryHolder {
  RMISocketFactoryFactory INSTANCE = ServiceFactory.createService(RMISocketFactoryFactory.class, RMISocketFactoryFactory.class);
}


/**
 * The factory for RMI socket factories.
 * <p>
 * Notice: for SSL and SSL_COMPRESSED, the system properties javax.net.ssl.keyStore, javax.net.ssl.keyStorePassword,
 * javax.net.ssl.trustStore and javax.net.ssl.trustStorePassword should be provided.
 *
 * @author harald
 */
@Service(RMISocketFactoryFactory.class)    // defaults to self
public class RMISocketFactoryFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static RMISocketFactoryFactory getInstance() {
    return RMISocketFactoryFactoryHolder.INSTANCE;
  }


  private String[] enabledCipherSuites;
  private String[] enabledProtocols;
  private boolean clientAuthenticationRequired;


  /**
   * Creates the RMI socket factory.
   */
  public RMISocketFactoryFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the enabled cipher suites.
   *
   * @return null if system default
   */
  public String[] getEnabledCipherSuites() {
    return enabledCipherSuites;
  }

  /**
   * Sets the enabled cipher suites.
   *
   * @param enabledCipherSuites the cipher names, null if system default
   */
  public void setEnabledCipherSuites(String[] enabledCipherSuites) {
    this.enabledCipherSuites = enabledCipherSuites;
  }

  /**
   * Gets the enabled protocols.
   *
   * @return null if system default
   */
  public String[] getEnabledProtocols() {
    return enabledProtocols;
  }

  /**
   * Sets the enabled protocols.
   *
   * @param enabledProtocols the protocol names, null if system default
   */
  public void setEnabledProtocols(String[] enabledProtocols) {
    this.enabledProtocols = enabledProtocols;
  }

  /**
   * Returns whether server requests SSL-client-authentication.
   *
   * @return true if client certificates required
   */
  public boolean isClientAuthenticationRequired() {
    return clientAuthenticationRequired;
  }

  /**
   * Sets whether server requests SSL-client-authentication.
   *
   * @param clientAuthenticationRequired true if client certificates required
   */
  public void setClientAuthenticationRequired(boolean clientAuthenticationRequired) {
    this.clientAuthenticationRequired = clientAuthenticationRequired;
  }

  /**
   * Creates a client socket factory.
   *
   * @param defaultFactory the factory for {@link RMISocketFactoryType#DEFAULT}
   * @param factoryType the socket factory type
   * @return the factory, null if system default
   */
  public RMIClientSocketFactory createClientSocketFactory(RMIClientSocketFactory defaultFactory, RMISocketFactoryType factoryType) {
    return switch (factoryType) {
      case DEFAULT -> defaultFactory;
      case PLAIN -> new ClientSocketFactory();
      case SSL -> new SslClientSocketFactory();
      case COMPRESSED -> new CompressedClientSocketFactory();
      case SSL_COMPRESSED -> new CompressedSslClientSocketFactory();
      default -> null;   // null = SYSTEM
    };
  }

  /**
   * Creates a server socket factory.
   *
   * @param defaultFactory the factory for {@link RMISocketFactoryType#DEFAULT}
   * @param factoryType the socket factory type
   * @return the factory, null if system default
   */
  public RMIServerSocketFactory createServerSocketFactory(RMIServerSocketFactory defaultFactory, RMISocketFactoryType factoryType) {
    return switch (factoryType) {
      case DEFAULT -> defaultFactory;
      case PLAIN -> new ServerSocketFactory();
      case SSL -> new SslServerSocketFactory(getEnabledCipherSuites(), getEnabledProtocols(), isClientAuthenticationRequired());
      case COMPRESSED -> new CompressedServerSocketFactory();
      case SSL_COMPRESSED -> new CompressedSslServerSocketFactory(getEnabledCipherSuites(), getEnabledProtocols(), isClientAuthenticationRequired());
      default -> null;   // null = SYSTEM
    };
  }

}
