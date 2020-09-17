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


package org.tentackle.dbms.rmi;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.dbms.Db;
import org.tentackle.io.RMISocketFactoryFactory;
import org.tentackle.io.RMISocketFactoryType;
import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.SessionInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * A generic RMI server.<br>
 *
 * The backend properties file is parsed for the following keywords:
 * <ul>
 * <li>
 * <code>service=service-URL</code>:
 *  defaults to the basename of the RMI-server class instance at the default registry port on localhost,
 *  i.e. {@code rmi://localhost:1099/RmiServer}.
 * </li>
 *
 * <li>
 * <code>createregistry[=default|plain|ssl|compressed]</code>:
 *  creates a local registry (on the port according to the service URL, default is 1099).
 *  By default, the created registry uses the system-default socket factories.
 *  However, it may be forced to use another one, for example ssl.
 *  If set, the connection and session object will use the same factories as the registry.
 *  All other delegates will be created using the factories given by socketfactory=.. or the systen default.
 * </li>
 *
 * <li>
 * <code>connectionclass=connection-class</code>: the connection class is usually provided programmatically, but
 *  can be configured as a property as well. Defaults to org.tentackle.persist.rmi.RemoteDbConnectionImpl.
 * </li>
 *
 * <li>
 * <code>timeoutinterval=timeout-polling-interval-in-milliseconds</code>:
 *  The polling interval for dead sessions in milliseconds. Defaults to 1000ms.
 *  0 turns off the cleanup thread completely (risky!).
 * </li>
 *
 * <li>
 * <code>timeout=session-timeout</code>:
 *  The default session timeout (in polling-intervals) for dead client connections (see Db -&gt; keepAlive).
 *  Defaults to 0, i.e. no timeout (sessions may request an individual timeout).
 * </li>
 *
 * <li>
 * <code>port=port</code>: for the connection object.
 *  Default is from service URL.
 * </li>
 *
 * <li>
 * Configure factories at fixed ports:<br>
 * <code>ports=28000</code>: plain=28000, compressed=28001, ssl=28002, compressed+ssl=28003<br>
 *  is the same as:<br>
 * <code>ports=28000,28001,28002,28003</code><br>
 * Default is: <code>ports=serviceport+0,serviceport+1,serviceport+2,serviceport+3</code> if the service port is not
 * the default registry port, else <code>ports=0,0,0,0</code>.<br>
 * Use -1 to disable service at this port and 0 to use a system default port, i.e.
 * "ports=-1,-1,28002,28003" means: ssl only, with or without compression.
 * </li>
 *
 * <li>
 * <code>socketfactory=[system|default|plain|ssl|compressed]</code>: the socket factory type:
 *  <ul>
 *    <li><code>system</code>: use system default factories (this is the default)</li>
 *    <li><code>default</code>: same as system</li>
 *    <li><code>plain</code>: plain sockets (see {@link org.tentackle.io.ClientSocketFactory},
 *              {@link org.tentackle.io.ServerSocketFactory}</li>
 *    <li><code>ssl</code>: use SSL (see {@link org.tentackle.io.SslClientSocketFactory},
 *              {@link org.tentackle.io.SslServerSocketFactory}</li>
 *    <li><code>compressed</code>: use compression (see {@link org.tentackle.io.CompressedClientSocketFactory},
 *              {@link org.tentackle.io.CompressedServerSocketFactory}</li>
 *  </ul>
 *
 *  If both <code>ssl</code> and <code>compressed</code> is given, the factories used are
 *              {@link org.tentackle.io.CompressedSslClientSocketFactory} and
 *              {@link org.tentackle.io.CompressedSslServerSocketFactory}.
 *
 *  Notice that {@code createregistry=...} can be used to define a different socket factory exclusively for the login phase,
 *  and that the server may select a different factory type programmatically after the login phase (for each delegate).
 *  If {@code ports=...} is missing, the login port defaults to the one defined by the service url and an optionally
 *  different factory for the remaining delegates gets a port number increased by 1.
 * </li>
 *
 * <li>
 * For SSL only:
 * <ul>
 * <li>
 * <code>ciphersuites=...</code>: comma separated list of enabled cipher suites
 * </li>
 *
 * <li>
 * <code>protocols=...</code>: comma separated list of enabled protocols
 * </li>
 *
 * <li>
 * <code>clientauth</code>: set if server requires client authentication
 * </li>
 * </ul>
 * <li>
 *
 * </ul>
 *
 * Examples:
 * <p>
 * Creates a registry at 33000, encrypts the login phase, the rest is transferred unencrypted, 30s session timeout.
 * <pre>
 * service=rmi://localhost:33000/MyServer
 * createregistry=ssl
 * timeout=30
 * </pre>
 *
 * Creates a registry at 33000, encrypts and compresses all traffic, 30s session timeout
 * <pre>
 * service=rmi://localhost/MyServer
 * createregistry
 * socketfactory=ssl compressed
 * timeout=30
 * </pre>
 *
 * Creates a registry at 33000, encrypts the login phase, the rest is transferred compressed at 33001, 30s session timeout.
 * <pre>
 * service=rmi://localhost/MyServer
 * createregistry=ssl
 * socketfactory=compressed
 * timeout=30
 * </pre>
 *
 * Creates a registry at 33000, encrypts the login phase, the rest is transferred unencrypted, 30s session timeout,
 * Plain traffic at 33002, ssl at 33000 (must match the URL), no compression available.
 * <pre>
 * service=rmi://localhost:33000/MyServer
 * createregistry=ssl
 * ports=33002,-1,33000,-1
 * timeout=30
 * </pre>
 *
 * @author harald
 */
public class RmiServer {

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
   * The property key for the RMI ports.
   */
  public static final String PORTS = "ports";

  /**
   * The property key for the single RMI port.
   */
  public static final String PORT = "port";

  /**
   * The property key for the SSL client authentication.
   */
  public static final String CLIENT_AUTH = "clientauth";


  /**
   * Default timeout check interval in milliseconds (1s).
   */
  private static final long DEFAULT_CHECK_INTERVAL = 1000;

  /**
   * Default timeout in timeout check intervals (30s).
   */
  private static final int DEFAULT_SESSION_TIMEOUT = 30;


  private static final Logger LOGGER = Logger.get(RmiServer.class);
  private static final String SYSTEM_DEFAULT = "<system default>";

  private final SessionInfo sessionInfo;                  // server session info
  private String service;                                 // name of the RMI service
  private boolean createRegistry;                         // true to create a local registry
  private Class<? extends RemoteDbConnectionImpl> connectionClass;  // class for connection object
  private RemoteDbConnectionImpl connectionObject;        // the connection object (and to keep the object referenced!)
  private int sessionTimeout;                             // default session timeout in seconds
  private long sessionTimeoutCheckInterval;               // check interval for session timeout in milliseconds, 0 = none
  private int port;                                       // port for the delegates
  private int loginPort;                                  // port for the registry and login phase
  private Registry registry;                              // local registry, if createRegistry = true
  private RMIClientSocketFactory loginCsf;                // client socket factory for the registry and login phase
  private RMIServerSocketFactory loginSsf;                // server socket factory for the registry and login phase
  private RMIClientSocketFactory csf;                     // client socket factory for the delegates
  private RMIServerSocketFactory ssf;                     // server socket factory for the delegates


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
   */
  @SuppressWarnings("unchecked")
  public RmiServer(SessionInfo sessionInfo, Class<? extends RemoteDbConnectionImpl> connectionClass) {

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
        throw new TentackleRuntimeException("connection class '" + val + "' not found");
      }
    }

    val = props.getProperty(RMI_SERVICE);
    service = Objects.requireNonNullElseGet(val, () -> "rmi://localhost:" + Registry.REGISTRY_PORT + "/" +
                                                       ReflectionHelper.getClassBaseName(this.getClass()));

    // set the default ports, if not the REGISTRY_PORT.
    try {
      URI uri = new URI(service);
      port = uri.getPort();
    }
    catch (URISyntaxException ex) {
      throw new TentackleRuntimeException("malformed service URL '" + service + "'", ex);
    }

    RMISocketFactoryType loginFactoryType = null;
    val = props.getProperty(CREATE_REGISTRY);
    if (val != null) {
      createRegistry = true;
      if (!val.isEmpty()) {
        loginFactoryType = RMISocketFactoryType.parse(val);
        loginCsf = RMISocketFactoryFactory.getInstance().createClientSocketFactory(null, loginFactoryType);
        loginSsf = RMISocketFactoryFactory.getInstance().createServerSocketFactory(null, loginFactoryType);
      }
    }

    val = props.getProperty(TIMEOUT);
    if (val != null) {
      sessionTimeout = Integer.parseInt(val);
    }
    else {
      sessionTimeout = DEFAULT_SESSION_TIMEOUT;
    }

    val = props.getProperty(TIMEOUT_INTERVAL);
    if (val != null) {
      sessionTimeoutCheckInterval = Long.parseLong(val);
    }
    else  {
      sessionTimeoutCheckInterval = DEFAULT_CHECK_INTERVAL;
    }

    // check for default ports
    val = props.getProperty(PORTS);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      int pos = 0;
      while (stok.hasMoreTokens()) {
        int p = Integer.parseInt(stok.nextToken());
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
            throw new TentackleRuntimeException("malformed 'ports = " + val + "'");
        }
        pos++;
      }
      if (pos == 0) {
        throw new TentackleRuntimeException("missing port numbers in 'ports = " + val + "'");
      }
      else if (pos == 1) {
        // short form
        compressedPort    = plainPort + 1;
        sslPort           = plainPort + 2;
        compressedSslPort = plainPort + 3;
      }
      else if (pos < 4) {
        throw new TentackleRuntimeException("either one or all four ports must be given in 'ports = " + val + "'");
      }
      // check port range
      checkPort(plainPort);
      checkPort(compressedPort);
      checkPort(sslPort);
      checkPort(compressedSslPort);
    }

    // more server side ssl properties
    val = props.getProperty(Db.CIPHER_SUITES);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      RMISocketFactoryFactory.getInstance().setEnabledCipherSuites(new String[stok.countTokens()]);
      int i = 0;
      while (stok.hasMoreTokens()) {
        RMISocketFactoryFactory.getInstance().getEnabledCipherSuites()[i++] = stok.nextToken();
      }
    }

    val = props.getProperty(Db.PROTOCOLS);
    if (val != null) {
      StringTokenizer stok = new StringTokenizer(val, " \t,;");
      RMISocketFactoryFactory.getInstance().setEnabledProtocols(new String[stok.countTokens()]);
      int i = 0;
      while (stok.hasMoreTokens()) {
        RMISocketFactoryFactory.getInstance().getEnabledProtocols()[i++] = stok.nextToken();
      }
    }

    val = props.getProperty(CLIENT_AUTH);
    if (val != null)  {
      RMISocketFactoryFactory.getInstance().setClientAuthenticationRequired(true);
    }


    // switch socket factories
    RMISocketFactoryType factoryType = RMISocketFactoryType.parse(sessionInfo.getProperties().getProperty(Db.SOCKET_FACTORY));
    csf = RMISocketFactoryFactory.getInstance().createClientSocketFactory(null, factoryType);
    ssf = RMISocketFactoryFactory.getInstance().createServerSocketFactory(null, factoryType);

    val = props.getProperty(PORT);
    // notice: ssl and/or compressed requires another port than the original serverport
    if (val != null) {
      port = Integer.valueOf(val);
      checkPort(port);
    }

    // verify port agains fixed ports for sure
    port = getPort(port, factoryType);

    if (loginFactoryType == null || loginFactoryType == factoryType) {
      loginCsf = csf;
      loginSsf = ssf;
      loginPort = port;
    }
    else {
      loginPort = getPort(0, loginFactoryType);
      if (loginPort == 0 && port != 0) {
        // no ports=... defined: re-arrange
        loginPort = port;
        port++;
      }
      else if (loginPort != 0 && loginPort == port) {
        throw new TentackleRuntimeException("ports=... misconfigured: login port is the same as the delegates port, but socket factories differ");
      }
    }
  }


  /**
   * Creates an instance of an RMI-db-server with default connection object.
   *
   * @param serverInfo the servers db-connection user info
   */
  @SuppressWarnings("unchecked")
  public RmiServer(SessionInfo serverInfo) {
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
   */
  public int getPort(int requestedPort, RMISocketFactoryType factoryType) {

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
      throw new TentackleRuntimeException("protocol for requested port " + requestedPort + " is fixed to " + p);
    }

    if (p < 0) {
      throw new TentackleRuntimeException("service at this port is disabled");
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
   * Gets the port the delegates.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets the port for the login phase.
   *
   * @return the port
   */
  public int getLoginPort() {
    return loginPort;
  }

  /**
   * Gets the server's csf for the delegates.
   *
   * @return the client socket factory
   */
  public RMIClientSocketFactory getClientSocketFactory() {
    return csf;
  }

  /**
   * Gets the server's ssf for the delegates.
   *
   * @return the server socket factory
   */
  public RMIServerSocketFactory getServerSocketFactory() {
    return ssf;
  }

  /**
   * Gets the server's csf for the login phase.
   *
   * @return the client socket factory
   */
  public RMIClientSocketFactory getLoginClientSocketFactory() {
    return loginCsf;
  }

  /**
   * Gets the server's ssf for the login phase.
   *
   * @return the server socket factory
   */
  public RMIServerSocketFactory getLoginServerSocketFactory() {
    return loginSsf;
  }


  /**
   * Gets the default session timeout.<br>
   * The default is 30s.
   *
   * @return the timeout in polling intervals
   * @see #getSessionTimeoutCheckInterval()
   */
  public int getSessionTimeout() {
    return sessionTimeout;
  }

  /**
   * Sets the session timeout.
   *
   * @param sessionTimeout the timeout in units of check intervals
   * @see #getSessionTimeoutCheckInterval()
   */
  public void setSessionTimeout(int sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
  }

  /**
   * Gets the timeout check interval in milliseconds.<br>
   * The default is 1000 (1s).
   *
   * @return the polling interval
   * @see #getSessionTimeout()
   */
  public long getSessionTimeoutCheckInterval() {
    return sessionTimeoutCheckInterval;
  }

  /**
   * Sets the timeout check interval in milliseconds.
   *
   * @param sessionTimeoutCheckInterval the polling interval
   */
  public void setSessionTimeoutCheckInterval(long sessionTimeoutCheckInterval) {
    this.sessionTimeoutCheckInterval = sessionTimeoutCheckInterval;
  }

  /**
   * Starts the RMI server.
   */
  public void start() {
    try {

      // create connection object
      Constructor<? extends RemoteDbConnectionImpl> constructor = connectionClass.getConstructor(
          RmiServer.class, Integer.TYPE, RMIClientSocketFactory.class, RMIServerSocketFactory.class);
      connectionObject = constructor.newInstance(this, loginPort, loginCsf, loginSsf);

      final int registryPort;
      final String serviceName;

      if (createRegistry) {
        URI uri = new URI(service);
        String uriPath = uri.getPath();
        serviceName = uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
        int uriPort = uri.getPort();
        registryPort = uriPort <= 0 ? Registry.REGISTRY_PORT : uriPort;
        registry = LocateRegistry.createRegistry(registryPort, loginCsf, loginSsf);
        registry.bind(serviceName, connectionObject);
      }
      else  {
        // rebind if already bound
        Naming.rebind(service, connectionObject);
        registryPort = 0;
        serviceName = null;
      }

      // start cleanup thread
      if (sessionTimeoutCheckInterval > 0) {
        RemoteDbSessionImpl.startCleanupThread(sessionTimeoutCheckInterval);
      }

      LOGGER.info(() -> {
        StringBuilder buf = new StringBuilder();
        buf.append("\nTentackle RMI-server ").append(getClass().getName()).append(" started");
        buf.append("\nservice = ").append(service);
        if (createRegistry) {
          buf.append(", registry created at port ").append(registryPort);
        }
        else {
          buf.append(", using existing registry");
        }
        buf.append(", session timeout = ").append(sessionTimeout).append("*").append(sessionTimeoutCheckInterval).append("ms");
        if (loginCsf != csf) {
          buf.append("\nlogin client socket factory = ");
          if (loginCsf == null) {
            buf.append(SYSTEM_DEFAULT);
          }
          else {
            buf.append(loginCsf.getClass().getName());
          }
          buf.append(", server socket factory = ");
          if (loginSsf == null) {
            buf.append(SYSTEM_DEFAULT);
          }
          else {
            buf.append(loginSsf.getClass().getName());
          }
          buf.append(", port = ");
          if (loginPort == 0) {
            buf.append(SYSTEM_DEFAULT);
          }
          else {
            buf.append(loginPort);
          }
          buf.append("\ndefault ");
        }
        else {
          buf.append('\n');
        }
        buf.append("client socket factory = ");
        if (csf == null) {
          buf.append(SYSTEM_DEFAULT);
        }
        else {
          buf.append(csf.getClass().getName());
        }
        buf.append(", server socket factory = ");
        if (ssf == null) {
          buf.append(SYSTEM_DEFAULT);
        }
        else {
          buf.append(ssf.getClass().getName());
        }
        buf.append(", port = ");
        if (port == 0) {
          buf.append(SYSTEM_DEFAULT);
        }
        else {
          buf.append(port);
        }
        return buf.toString();
      });
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           SecurityException | InvocationTargetException | MalformedURLException | URISyntaxException |
           AlreadyBoundException | RemoteException e) {
      throw new TentackleRuntimeException("server startup failed", e);
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
   */
  public void stop() {
    try {
      if (connectionObject != null) {
        connectionObject.unexportRemoteObject(connectionObject);
        connectionObject = null;
      }
      if (registry != null) {
        // unbind all services registered for local registry
        for (String name: registry.list()) {
          LOGGER.info("unbinding {0}", name);
          registry.unbind(name);
        }
        UnicastRemoteObject.unexportObject(registry, true);
        registry = null;
      }
      else {
        LOGGER.info("unbinding {0}", service);
        Naming.unbind(service);
      }
    }
    catch (MalformedURLException | NotBoundException | RemoteException e) {
      throw new TentackleRuntimeException("server shutdown failed", e);
    }
  }


  // check port range
  private void checkPort(int port) {
    if (port < -1 || (port > 0 && port < 1024)) {
      throw new TentackleRuntimeException("illegal port number " + port + ". Possible values: -1, 0, >= 1024");
    }
  }

}
