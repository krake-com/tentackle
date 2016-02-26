/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.persist.rmi;

import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;


interface SocketFactoryFactory$Singleton {
  SocketFactoryFactory INSTANCE = ServiceFactory.createService(SocketFactoryFactory.class, SocketFactoryFactory.class);
}


/**
 * The factory for RMI socket factories.
 * <p>
 * Notice: for SSL and SSL_COMPRESSED, the system properties javax.net.ssl.keyStore, javax.net.ssl.keyStorePassword,
 * javax.net.ssl.trustStore and javax.net.ssl.trustStorePassword should be provided.
 *
 * @author harald
 */
@Service(SocketFactoryFactory.class)    // defaults to self
public class SocketFactoryFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static SocketFactoryFactory getInstance() {
    return SocketFactoryFactory$Singleton.INSTANCE;
  }


  /**
   * the enabled cipher suites.<br>
   * null if system default.
   */
  public static String[] enabledCipherSuites;

  /**
   * the enabled protocols.<br>
   * null if system default.
   */
  public static String[] enabledProtocols;

  /**
   * true if server request SSL-client-authentication.
   */
  public static boolean needClientAuth;



  /**
   * Creates a client socket factory.
   *
   * @param defaultFactory the factory for {@link SocketFactoryType#DEFAULT}
   * @param factoryType the socket factory type
   * @return the factory, null if system default
   */
  public RMIClientSocketFactory createClientSocketFactory(RMIClientSocketFactory defaultFactory, SocketFactoryType factoryType) {
    RMIClientSocketFactory factory = null;

    switch (factoryType) {
      case DEFAULT:
        factory = defaultFactory;
        break;

      case PLAIN:
        factory = new ClientSocketFactory();
        break;

      case SSL:
        factory = new SslClientSocketFactory();
        break;

      case COMPRESSED:
        factory = new CompressedClientSocketFactory();
        break;

      case SSL_COMPRESSED:
        factory = new CompressedSslClientSocketFactory();
        break;

      // default: null = SYSTEM
    }

    return factory;
  }


  /**
   * Creates a server socket factory.
   *
   * @param defaultFactory the factory for {@link SocketFactoryType#DEFAULT}
   * @param factoryType the socket factory type
   * @return the factory, null if system default
   */
  public RMIServerSocketFactory createServerSocketFactory(RMIServerSocketFactory defaultFactory, SocketFactoryType factoryType) {
    RMIServerSocketFactory factory = null;

    switch (factoryType) {
      case DEFAULT:
        factory = defaultFactory;
        break;

      case PLAIN:
        factory = new ServerSocketFactory();
        break;

      case SSL:
        factory = new SslServerSocketFactory(enabledCipherSuites, enabledProtocols, needClientAuth);
        break;

      case COMPRESSED:
        factory = new CompressedServerSocketFactory();
        break;

      case SSL_COMPRESSED:
        factory = new CompressedSslServerSocketFactory(enabledCipherSuites, enabledProtocols, needClientAuth);
        break;

      // default: null = SYSTEM
    }

    return factory;
  }

}
