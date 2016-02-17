/**
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


package org.tentackle.persist.rmi;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.StringTokenizer;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ApplicationException;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.reflect.ReflectionHelper;


/**
 * A generic db-RMI-DbServer.<br>
 *
 * The db properties file is parsed for the following keywords:
 * <ul>
 * <li>
 * <tt>service=service-name</tt>:
 *  defaults to the basename of the DbServer-class instance, i.e. -> rmi://localhost:1099/&lt;Basename&gt;
 * </li>
 *
 * <li>
 * <tt>createregistry</tt>:
 *  creates a local registry (on the port according to service, defaulting to 1099)
 * </li>
 *
 * <li>
 * <tt>connectionclass=connection-class</tt>:
 *  defaults to org.tentackle.db.rmi.RemoteConnectionImpl (which is only for testing. Real apps need another class)
 * </li>
 *
 *
 * <li>
 * <tt>timeoutinterval=timeout-polling-interval-in-milliseconds</tt>:
 *  The polling interval for dead sessions in milliseconds. Defaults to 1000ms.
 *  0 turns off the cleanup thread completely (risky!).
 * </li>
 *
 * <li>
 * <tt>timeout=session-timeout</tt>:
 *  The default session timeout (in polling-intervals) for dead client connections (see Db -> keepAlive).
 *  Defaults to 0, i.e. no timeout (sessions may request an individual timeout).
 * </li>
 *
 * <li>
 * <tt>port=port</tt>: for the connection object>
 *  default is 0, i.e. system default (or from fixed ports)
 * </li>
 *
 * <li>
 * Fixed ports:<br>
 * <tt>ports=28000</tt>: plain=28000, compressed=28001, ssl=28002, compressed+ssl=28003<br>
 *  is the same as:<br>
 * <tt>ports=28000,28001,28002,28003</tt><br>
 * Default is: <tt>ports=serviceport+0,serviceport+1,serviceport+2,serviceport+3</tt> if the service port is not
 * the default registry port, else <tt>ports=0,0,0,0</tt>.<br>
 * Use -1 to disable service at this port and 0 to use a system default port, i.e.
 * "ports=-1,-1,28002,28003" means: ssl only, with or without compression.
 * </li>
 *
 * <li>
 * <tt>socketfactory=[system|default|plain|ssl|compressed]</tt>: the socket factory type:
 *  <ul>
 *    <li><tt>system</tt>: use system default factories (this is the default)</li>
 *    <li><tt>default</tt>: same as system</li>
 *    <li><tt>plain</tt>: plain sockets (see {@link ClientSocketFactory}, {@link ServerSocketFactory}</li>
 *    <li><tt>ssl</tt>: use SSL (see {@link SslClientSocketFactory}, {@link SslServerSocketFactory}</li>
 *    <li><tt>compressed</tt>: use compression (see {@link CompressedClientSocketFactory}, {@link CompressedServerSocketFactory}</li>
 *  </ul>
 *  If both <tt>ssl</tt> and <tt>compressed</tt> is given, the factories used are {@link CompressedSslClientSocketFactory} and
 *  {@link CompressedSslServerSocketFactory}.
 * </li>
 * <p>
 * For SSL only:
 * <li>
 * <tt>ciphersuites=...</tt>: comma separated list of enabled cipher suites
 * </li>
 *
 * <li>
 * <tt>protocols=...</tt>: comma separated list of enabled protocols
 * </li>
 *
 * <li>
 * <tt>clientauth</tt>: set if server requires client authentication
 * </li>
 *
 *
 * </ul>
 *
 * @author harald
 */
public class DbServer {

  /**
   * The property key for the connection class to export.
   */
  public static final String CONNECTION_CLASS = "connectionclass";

  /**
   * The property key for the RMI service name.
   */
  public static final String RMI_SERVICE = "service";

  /**
   * The property key whether to create a registry or use an external one.
   */
  public static final String CREATE_REGISTRY = "createregistry";

  /**
   * The property key for the session timeout count.
   */
  public static final String TIMEOUT = "timeout";

  /**
   * The property key for the session timeout interval units in milliseconds.
   */
  public static final String TIMEOUT_INTERVAL = "timeoutinterval";

  /**
   * The property key for the socket factory.
   */
  public static final String SOCKET_FACTORY = "socketfactory";

  /**
   * The property key for the RMI ports.
   */
  public static final String PORTS = "ports";

  /**
   * The property key for the single RMI port.
   */
  public static final String PORT = "port";

  /**
   * The property key for the SSL cipher suites.
   */
  public static final String CIPHER_SUITES = "ciphersuites";

  /**
   * The property key for the SSL protocols.
   */
  public static final String PROTOCOLS = "protocols";

  /**
   * The property key for the SSL client authentication.
   */
  public static final String CLIENT_AUTH = "clientauth";




  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DbServer.class);

  private final SessionInfo sessionInfo;                  // server session info
  private String service;                                 // name of the RMI service
  private boolean createRegistry;                         // true to create a local registry
  private Class<? extends RemoteDbConnectionImpl> connectionClass;  // class for connection object
  private RemoteDbConnectionImpl connectionObject;        // the connection object (and to keep the object referenced!)
  private int sessionTimeout;                             // default session timeout in seconds
  private long sessionTimeoutCheckInterval;               // check interval for session timeout in milliseconds, 0 = no polling at all
  private int port;                                       // port of connection object
  private RMIClientSocketFactory csf;                     // client socket factory for connection object
  private RMIServerSocketFactory ssf;                     // server socket factory for connection object
  private Registry registry;                              // local registry


  // fixed ports. 0 = no limitation
  private int plainPort;          // port for plain sockets, i.e. no ssl, no compression
  private int compressedPort;     // port for compressed sockets
  private int sslPort;            // port for ssl sockets
  private int compressedSslPort;  // port for compressed ssl sockets



  /**
   * Creates an instance of an RMI-db-server.
   *
   * @param sessionInfo the servers db-connection user info
   * @param connectionClass the class of the connection object to instantiate, null = default or from serverInfo's properties file
   *
   * @throws ApplicationException if some configuration error
   */
  @SuppressWarnings("unchecked")
  public DbServer(SessionInfo sessionInfo, Class<? extends RemoteDbConnectionImpl> connectionClass) throws ApplicationException {

    this.sessionInfo = sessionInfo;
    this.connectionClass = connectionClass == null ? RemoteDbConnectionImpl.class : connectionClass;

    Properties props = sessionInfo.getProperties();

    // check connection class
    String val = props.getProperty(CONNECTION_CLASS);
    if (val != null) {
      try {
        this.connectionClass = (Class<RemoteDbConnectionImpl>) Class.forName(val);
      }
      catch (ClassNotFoundException ex) {
        throw new ApplicationException("connection class '" + val + "' not found");
      }
    }

    val = props.getProperty(RMI_SERVICE);
    if (val != null) {
      service = val;
    }
    else  {
      service = "rmi://localhost:" + Registry.REGISTRY_PORT + "/" +
                ReflectionHelper.getClassBaseName(this.getClass());
    }

    // set the default ports, if not the REGISTRY_PORT.
    try {
      URI uri = new URI(service);
      int servicePort = uri.getPort();
      if (servicePort != Registry.REGISTRY_PORT)  {
        plainPort         = servicePort;
        compressedPort    = servicePort + 1;
        sslPort           = servicePort + 2;
        compressedSslPort = servicePort + 3;
      }
    }
    catch (URISyntaxException ex) {
      throw new ApplicationException("malformed service url", ex);
    }

    val = props.getProperty(CREATE_REGISTRY);
    if (val != null) {
      createRegistry = val.isEmpty() || Boolean.valueOf(val);
    }

    val = props.getProperty(TIMEOUT);
    if (val != null) {
      sessionTimeout = Integer.valueOf(val);
    }

    val = props.getProperty(TIMEOUT_INTERVAL);
    if (val != null) {
      sessionTimeoutCheckInterval = Long.valueOf(val);
    }
    else  {
      sessionTimeoutCheckInterval = 1000;
    }

    // check for default ports
    val = props.getProperty(PORTS);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      int pos = 0;
      while (stok.hasMoreTokens()) {
        int p = Integer.valueOf(stok.nextToken());
        switch (pos) {
          case 0:
            plainPort = p;
            break;
          case 1:
            compressedPort = p;
            break;
          case 2:
            sslPort = p;
            break;
          case 3:
            compressedSslPort = p;
            break;
          default:
            throw new ApplicationException("malformed 'ports = " + val + "'");
        }
        pos++;
      }
      if (pos == 0) {
        throw new ApplicationException("missing port numbers in 'ports = " + val + "'");
      }
      else if (pos == 1) {
        // short form
        compressedPort    = plainPort + 1;
        sslPort           = plainPort + 2;
        compressedSslPort = plainPort + 3;
      }
      else if (pos < 4) {
        throw new ApplicationException("either one or all four ports must be given in 'ports = " + val + "'");
      }
      // check port range
      checkPort(plainPort);
      checkPort(compressedPort);
      checkPort(sslPort);
      checkPort(compressedSslPort);
    }

    // more server side ssl properties
    val = props.getProperty(CIPHER_SUITES);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      SocketFactoryFactory.enabledCipherSuites = new String[stok.countTokens()];
      int i = 0;
      while (stok.hasMoreTokens()) {
        SocketFactoryFactory.enabledCipherSuites[i++] = stok.nextToken();
      }
    }

    val = props.getProperty(PROTOCOLS);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      SocketFactoryFactory.enabledProtocols = new String[stok.countTokens()];
      int i = 0;
      while (stok.hasMoreTokens()) {
        SocketFactoryFactory.enabledProtocols[i++] = stok.nextToken();
      }
    }

    val = props.getProperty(CLIENT_AUTH);
    if (val != null)  {
      SocketFactoryFactory.needClientAuth = true;
    }


    // switch socket factories
    SocketFactoryType factoryType = SocketFactoryType.parse(sessionInfo.getProperties().getProperty(SOCKET_FACTORY));
    csf = SocketFactoryFactory.getInstance().createClientSocketFactory(null, factoryType);
    ssf = SocketFactoryFactory.getInstance().createServerSocketFactory(null, factoryType);

    val = props.getProperty(PORT);
    // notice: ssl and/or compressed requires another port than the original serverport
    if (val != null) {
      port = Integer.valueOf(val);
      checkPort(port);
    }

    // verify port agains fixed ports for sure
    port = getPort(port, factoryType);
  }




  /**
   * Creates an instance of an RMI-db-server with default connection object
   * (or configured entirely by db properties file)
   *
   * @param serverInfo the servers db-connection user info
   *
   * @throws ApplicationException if some configuration error
   */
  @SuppressWarnings("unchecked")
  public DbServer(SessionInfo serverInfo) throws ApplicationException {
    this(serverInfo, null);
  }



  /**
   * Gets the server's user info.
   *
   * @return the server info
   */
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }



  /**
   * Gets the rmi port for a new remote object.
   *
   * @param requestedPort the requested port by the delegate, 0 = use system default
   * @param factoryType the socket factory type
   * @return the granted port, 0 = use system default
   *
   * @throws ApplicationException if requested port could not be granted
   */
  public int getPort(int requestedPort, SocketFactoryType factoryType) throws ApplicationException {

    checkPort(requestedPort);

    int p = 0;    // granted port, 0 = all

    switch (factoryType) {
      case DEFAULT:
        p = port;
        break;

      case SYSTEM:
      case PLAIN:
        p = plainPort;
        break;

      case SSL:
        p = sslPort;
        break;

      case COMPRESSED:
        p = compressedPort;
        break;

      case SSL_COMPRESSED:
        p = compressedSslPort;
        break;
    }

    if (p == 0) {
      // no fixed port: requested one is ok
      p = requestedPort;
    }

    if (requestedPort != 0 && requestedPort != p) {
      throw new ApplicationException("protocol for requested port " + requestedPort + " is fixed to " + p);
    }

    if (p < 0) {
      throw new ApplicationException("service at this port is disabled");
    }

    return p;
  }



  /**
   * Get the fixed port for plain communication.
   *
   * @return the port number, 0 = not fixed, i.e. system default
   */
  public int getPlainPort() {
    return plainPort;
  }

  /**
   * Get the fixed port for compressed communication
   *
   * @return the port number, 0 = not fixed, i.e. system default
   */
  public int getCompressedPort() {
    return compressedPort;
  }

  /**
   * Get the fixed port for ssl communication
   *
   * @return the port number, 0 = not fixed, i.e. system default
   */
  public int getSslPort() {
    return sslPort;
  }

  /**
   * Get the fixed port for compressed+ssl communication
   *
   * @return the port number, 0 = not fixed, i.e. system default
   */
  public int getCompressedSslPort() {
    return compressedSslPort;
  }




  /**
   * Gets the port the server is listening on
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets the server's csf
   * @return the client socket factory
   */
  public RMIClientSocketFactory getClientSocketFactory() {
    return csf;
  }

  /**
   * Gets the server's ssf
   * @return the server socket factory
   */
  public RMIServerSocketFactory getServerSocketFactory() {
    return ssf;
  }


  /**
   * Gets the default session timeout.
   *
   * @return the timeout in polling intervals.
   * @see #getSessionTimeoutCheckInterval()
   */
  public int getSessionTimeout() {
    return sessionTimeout;
  }

  /**
   * Gets the timeout check interval in milliseconds.
   *
   * @return the polling interval
   * @see #getSessionTimeout()
   */
  public long getSessionTimeoutCheckInterval() {
    return sessionTimeoutCheckInterval;
  }


  /**
   * Starts the server.
   *
   * @throws ApplicationException if startup failed
   */
  public void start() throws ApplicationException {
    try {

      int registryPort = 0;
      if (createRegistry) {
        URI uri = new URI(service);
        registryPort = uri.getPort();
        if (registryPort <= 0)  {
          registryPort = Registry.REGISTRY_PORT;  // default port (1099)
        }
        if (csf != null && ssf != null) {
          registry = LocateRegistry.createRegistry(registryPort, csf, ssf);
        }
        else {
          registry = LocateRegistry.createRegistry(registryPort);
        }
      }

      // instantiate connection object
      Constructor<? extends RemoteDbConnectionImpl> constructor = this.connectionClass.getConstructor(
              DbServer.class, Integer.TYPE, RMIClientSocketFactory.class, RMIServerSocketFactory.class);
      connectionObject = constructor.newInstance(this, port, csf, ssf);

      // log
      LOGGER.info("\nTentackle RMI-server " + getClass().getName() +
                  "\nclient socket factory = " + (csf == null ? "<system default>" : csf.getClass().getName()) +
                  "\nserver socket factory = " + (ssf == null ? "<system default>" : ssf.getClass().getName()) +
                  "\nservice = " + service + (createRegistry ? (", registry created on port " + registryPort) : "") +
                  "\nlogin port = " + (port == 0 ? "<system default>" : port) +
                  ", session timeout = " + sessionTimeout + "*" + sessionTimeoutCheckInterval + "ms");

      // bind to service
      if (createRegistry) {
        // must be new
        Naming.bind(service, connectionObject);
      }
      else  {
        // rebind if already bound
        Naming.rebind(service, connectionObject);
      }

      // start cleanup thread
      if (sessionTimeoutCheckInterval > 0) {
        RemoteDbSessionImpl.startCleanupThread(sessionTimeoutCheckInterval);
      }
    }
    catch (Exception e) {
      throw new ApplicationException("server startup failed", e);
    }
  }


  /**
   * Gets the local registry.
   *
   * @return the registry, null if none created
   */
  public Registry getRegistry() {
    return registry;
  }


  /**
   * Stops the server.
   * <p>
   * Unbinds the connection object.
   * @throws ApplicationException
   */
  public void stop() throws ApplicationException {
    try {
      Naming.unbind(service);
      if (connectionObject != null) {
        connectionObject.unexportRemoteObject(connectionObject);
        connectionObject = null;
      }
      if (registry != null) {
        // if still anything left...
        for (String name: registry.list()) {
          LOGGER.info("unbinding {0}", name);
          registry.unbind(name);
        }
        UnicastRemoteObject.unexportObject(registry, true);
        registry = null;
      }
    }
    catch (Exception e) {
      throw new ApplicationException("server shutdown failed", e);
    }
  }



  // check port range
  private void checkPort(int port) throws ApplicationException {
    if (port < -1 || (port > 0 && port < 1024)) {
      throw new ApplicationException("illegal port number " + port + ". Possible values: -1, 0, >= 1024");
    }
  }


}
