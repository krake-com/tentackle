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

package org.tentackle.dbms.trip;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.io.UriHelper;
import org.tentackle.log.Logger;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

/**
 * A generic TRIP server.<br>
 * Creates the server {@link org.tentackle.trip.Transport}(s) to handle incoming connections.
 * <p>
 * The backend properties from the {@link SessionInfo} are parsed for the following keys:
 * <ul>
 * <li>
 *   {@code service=<uri>}:
 *   Defines the TRIP service if there is only a single endpoint the server listens to.
 *   The URI-scheme selects the {@link org.tentackle.trip.Transport} to use. The path element
 *   selects the connection class via its name registered at the {@link org.tentackle.trip.Registry}.
 *   Optional parameters are transport-specific.<br>
 *   Example: {@code trips://localhost:18000/myapp?idle=10&backlog=100}<br>
 *   (see {@link org.tentackle.trip.transport.SslTransport}).
 *   <p>
 *   If there is more than one transport, use {@code service_<somename>=<uri>} to specify
 *   them separately. For example, {@code service_plain=<uri>}, {@code service_ssl=<uri>}
 *   or {@code service_quic=<uri>}
 *   <p>
 *   The URI of a {@code service_<somename>} may be <em>derived</em> from the default {@code service} URI
 *   by referencing its components with a simple expression language (see {@link UriHelper#expand}).
 *   This avoids repeating (or externally configuring) values such as the host or port when they only
 *   differ slightly between transports.
 *   <p>
 *   Example (given {@code service=trips://localhost:18000/myapp?idle=10}):
 *   <pre>
 *     service_compressed=tripcs://$host:${port+1}/$path?$query
 *   </pre>
 *   expands to {@code tripcs://localhost:18001/myapp?idle=10}. Only the {@code service_<somename>} entries
 *   are expanded; the default {@code service} URI is always used verbatim as the template.
 * </li>
 * <li>
 * {@code connectionclass=<FQCN>}: the connection class is usually provided programmatically but
 *  can be configured as a property as well.
 * </li>
 * <li>
 * {@code timeoutinterval=<timeout-polling-interval-in-milliseconds>}:
 *  The polling interval for dead sessions in milliseconds. Defaults to 1000 ms.
 *  0 turns off the cleanup thread completely (risky!).
 * </li>
 * <li>
 * {@code timeout=session-timeout}:
 *  The default session timeout (in polling-intervals) for dead client connections.
 *  Defaults to 30, i.e., 30 seconds.
 * </li>
 * <li>
 * {@code preauth=enforce|audit|off}:
 *  What a client may send before it has logged in (see {@link PreAuthenticationPolicy}).
 *  {@code enforce} (default) refuses everything a login doesn't need, {@code audit} only logs it,
 *  and {@code off} accepts everything from everyone, as in former releases.
 * </li>
 * <li>
 * {@code maxlogins=<number>}:
 *  The maximum number of logins processed concurrently (default 64, 0 = unlimited).
 *  Each login holds a session while the credentials are verified.
 *  A login exceeding the limit waits up to 10 seconds and is then refused as "server busy".
 * </li>
 * </ul>
 *
 * @author harald
 */
public class TripServer {

  /**
   * The property key for the connection class to export.
   */
  public static final String CONNECTION_CLASS = "connectionclass";

  /**
   * The property key for the service name, if there is only a single endpoint the server listens to.
   */
  public static final String SINGLE_SERVICE = "service";

  /**
   * The property key prefix for the service name.
   */
  public static final String MULTI_SERVICE = "service_";

  /**
   * The property key for the session timeout count.
   */
  public static final String TIMEOUT = "timeout";

  /**
   * The property key for the session timeout interval units in milliseconds.
   */
  public static final String TIMEOUT_INTERVAL = "timeoutinterval";

  /**
   * The property key for the maximum number of concurrent logins.
   */
  public static final String MAX_CONCURRENT_LOGINS = "maxlogins";

  /**
   * The property key for the pre-authentication policy mode.
   */
  public static final String PRE_AUTHENTICATION = "preauth";

  /**
   * Default timeout check interval in milliseconds (1s).
   */
  private static final long DEFAULT_CHECK_INTERVAL = 1000;

  /**
   * Default timeout in timeout check intervals (30s).
   */
  private static final int DEFAULT_SESSION_TIMEOUT = 30;


  private static final Logger LOGGER = Logger.get(TripServer.class);

  private final SessionInfo sessionInfo;                              // server session info
  private final Map<String, URI> services;                            // the transports subname::transport
  private Class<? extends RemoteDbConnection> connectionClass;        // class for connection object
  private int sessionTimeout;                                         // default session timeout in seconds
  private long sessionTimeoutCheckInterval;                           // check interval for session timeout in milliseconds, 0 = none
  private PreAuthenticationPolicy.Mode preAuthenticationMode;         // null = no pre-authentication policy
  private int maxConcurrentLogins;                                    // maximum number of concurrent logins, 0 = unlimited



  /**
   * Creates an instance of a TRIP-server.
   *
   * @param sessionInfo the server's session info
   * @param connectionClass the class of the connection object to instantiate, null = default or from properties
   */
  @SuppressWarnings("unchecked")
  public TripServer(SessionInfo sessionInfo, Class<? extends RemoteDbConnection> connectionClass) {

    this.sessionInfo = sessionInfo;
    this.connectionClass = connectionClass;

    EncryptedProperties props = sessionInfo.getProperties();

    // load from properties, if specified
    String val = props.getPropertyIgnoreCase(CONNECTION_CLASS);
    if (val != null) {
      try {
        this.connectionClass = (Class<RemoteDbConnection>) Class.forName(val);
      }
      catch (ClassNotFoundException ex) {
        throw new TentackleRuntimeException("connection class '" + val + "' not found");
      }
    }

    val = props.getPropertyIgnoreCase(TIMEOUT);
    if (val != null) {
      sessionTimeout = Integer.parseInt(val);
    }
    else {
      sessionTimeout = DEFAULT_SESSION_TIMEOUT;
    }

    val = props.getPropertyIgnoreCase(TIMEOUT_INTERVAL);
    if (val != null) {
      sessionTimeoutCheckInterval = Long.parseLong(val);
    }
    else  {
      sessionTimeoutCheckInterval = DEFAULT_CHECK_INTERVAL;
    }

    val = props.getPropertyIgnoreCase(MAX_CONCURRENT_LOGINS);
    maxConcurrentLogins = val != null ? Integer.parseInt(val) : RemoteDbSessionImpl.DEFAULT_MAX_CONCURRENT_LOGINS;

    val = props.getPropertyIgnoreCase(PRE_AUTHENTICATION);
    if (val == null || val.equalsIgnoreCase("enforce")) {
      preAuthenticationMode = PreAuthenticationPolicy.Mode.ENFORCE;
    }
    else if (val.equalsIgnoreCase("audit")) {
      preAuthenticationMode = PreAuthenticationPolicy.Mode.AUDIT;
    }
    else if (val.equalsIgnoreCase("off")) {
      preAuthenticationMode = null;
      LOGGER.warning("pre-authentication policy turned off: unauthenticated clients are not restricted");
    }
    else {
      throw new TentackleRuntimeException("invalid value '" + val + "' for " + PRE_AUTHENTICATION +
                                          ", expected enforce, audit or off");
    }

    // first pass: collect the raw (unexpanded) service configurations
    Map<String, String> rawConfigs = new TreeMap<>();
    for (String key : props.stringPropertyNames()) {
      boolean multi = key.startsWith(MULTI_SERVICE);
      if (multi || key.equals(SINGLE_SERVICE)) {
        String config = props.getProperty(key);
        if (StringHelper.isAllWhitespace(config)) {
          LOGGER.warning("ignoring empty service URI for key '" + key + "'");
        }
        else {
          String subname = multi ? key.substring(MULTI_SERVICE.length()) : "";
          rawConfigs.put(subname, config);
        }
      }
    }

    // the default 'service' URI is the template all derived service URIs are expanded against
    URI baseUri = null;
    String baseConfig = rawConfigs.get("");
    if (baseConfig != null) {
      baseUri = createUri(baseConfig);
    }

    // second pass: expand and parse each service URI
    services = new TreeMap<>();
    for (Map.Entry<String, String> entry : rawConfigs.entrySet()) {
      String subname = entry.getKey();
      String config = subname.isEmpty() ? entry.getValue() : UriHelper.expand(entry.getValue(), baseUri);
      services.put(subname, subname.isEmpty() ? baseUri : createUri(config));
    }
  }

  /**
   * Creates a service URI, translating parse errors to a {@link TentackleRuntimeException}.
   *
   * @param config the service configuration string
   * @return the URI
   */
  private static URI createUri(String config) {
    try {
      return URI.create(config);
    }
    catch (IllegalArgumentException ix) {
      throw new TentackleRuntimeException("malformed service URI '" + config + "'", ix);
    }
  }

  /**
   * Creates an instance of a TRIP-server with the default connection object.
   *
   * @param serverInfo the server's session info
   */
  public TripServer(SessionInfo serverInfo) {
    this(serverInfo, null);
  }


  /**
   * Gets the server's session info.
   *
   * @return the server's session info
   */
  public SessionInfo getSessionInfo() {
    return sessionInfo;
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
   * Gets the mode of the pre-authentication policy.
   *
   * @return the mode, null if the transports accept everything from unauthenticated clients
   */
  public PreAuthenticationPolicy.Mode getPreAuthenticationMode() {
    return preAuthenticationMode;
  }

  /**
   * Sets the mode of the pre-authentication policy.<br>
   * Must be invoked before {@link #start()}.
   *
   * @param preAuthenticationMode the mode, null to accept everything from unauthenticated clients
   */
  public void setPreAuthenticationMode(PreAuthenticationPolicy.Mode preAuthenticationMode) {
    this.preAuthenticationMode = preAuthenticationMode;
  }

  /**
   * Gets the maximum number of logins processed concurrently.
   *
   * @return the maximum number, 0 if unlimited
   */
  public int getMaxConcurrentLogins() {
    return maxConcurrentLogins;
  }

  /**
   * Sets the maximum number of logins processed concurrently.<br>
   * Must be invoked before {@link #start()}.
   *
   * @param maxConcurrentLogins the maximum number, 0 if unlimited
   */
  public void setMaxConcurrentLogins(int maxConcurrentLogins) {
    this.maxConcurrentLogins = maxConcurrentLogins;
  }

  /**
   * Creates the pre-authentication policy for a transport.<br>
   * Applications may override this method to add classes to the allowlist or to change the limits.
   *
   * @param transport the server transport
   * @param mode the mode
   * @return the policy, null if none
   */
  protected PreAuthenticationPolicy createPreAuthenticationPolicy(Transport transport, PreAuthenticationPolicy.Mode mode) {
    return new DefaultPreAuthenticationPolicy(mode);
  }

  /**
   * Starts the TRIP server.
   */
  public void start() {
    // start the transports in alphabetical order
    LOGGER.info("TRIP-server " + getClass().getName() + " starting...");
    RemoteDbSessionImpl.setMaxConcurrentLogins(maxConcurrentLogins);
    for (Map.Entry<String, URI> serviceEntry : services.entrySet()) {
      String name = serviceEntry.getKey();
      URI uri = serviceEntry.getValue();
      LOGGER.info("starting transport" + (name.isEmpty() ? ": " : (" '" + name + "': ")) + UriHelper.uriToSafeString(uri));
      try {
        Transport transport = TransportManager.getInstance().requestTransport(uri);
        if (preAuthenticationMode != null && transport.getPreAuthenticationPolicy() == null) {
          // before accepting: the policy applies to a connection from its very beginning
          transport.setPreAuthenticationPolicy(createPreAuthenticationPolicy(transport, preAuthenticationMode));
        }
        if (!transport.isAccepting()) {
          // new transport
          transport.accept();
        }
        Constructor<? extends RemoteDbConnection> constructor = connectionClass.getConstructor(TripServer.class, Transport.class);
        RemoteDbConnection connection = constructor.newInstance(this, transport);
        transport.getRegistry().register(connection, uri.getPath());
      }
      catch (NoSuchMethodException e) {
        throw new TripRuntimeException("no matching constructor: " + connectionClass.getName() + "(TripServer, Transport)", e);
      }
      catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
        throw new TripRuntimeException("cannot create connection object for " + connectionClass, e);
      }
      catch (TripRuntimeException tx) {
        throw new TripRuntimeException("cannot start transport " + uri, tx);
      }
    }

    // start cleanup thread
    if (sessionTimeoutCheckInterval > 0) {
      RemoteDbSessionImpl.startCleanupThread(sessionTimeoutCheckInterval);
    }
  }

  /**
   * Stops the server.
   * <p>
   * Unbinds the connection object.
   */
  public void stop() {
    LOGGER.info("Tentackle TRIP-server " + getClass().getName() + " stopping...");
    TransportManager.getInstance().closeAllTransports();
  }

}
