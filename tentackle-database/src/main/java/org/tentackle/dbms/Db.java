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

package org.tentackle.dbms;

import org.tentackle.common.Constants;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.FileHelper;
import org.tentackle.daemon.Scavenger;
import org.tentackle.dbms.rmi.DbRemoteDelegate;
import org.tentackle.dbms.rmi.RemoteDbConnection;
import org.tentackle.dbms.rmi.RemoteDbSession;
import org.tentackle.dbms.rmi.RemoteDelegate;
import org.tentackle.io.RMISocketFactoryFactory;
import org.tentackle.io.RMISocketFactoryType;
import org.tentackle.io.ReconnectionPolicy;
import org.tentackle.io.Reconnector;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
import org.tentackle.misc.Provider;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.DefaultSessionTaskDispatcher;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.RemoteSession;
import org.tentackle.session.SavepointHandle;
import org.tentackle.session.Session;
import org.tentackle.session.SessionCloseHandler;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionTaskDispatcher;
import org.tentackle.session.SessionUtilities;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.ScriptRunnerResult;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A persistence session.
 * <p>
 * Each thread must use its own session. If threads must share the same session, access to the session
 * must be synchronized at the application-level!
 * <p>
 * Sessions are an abstraction for a connection between a client
 * and a server, whereas "server" is not necessarily a database server,
 * because sessions can be either local or remote.<br>
 * A local session talks to a database backend via a {@link ConnectionManager}
 * which is responsible for the physical JDBC-connection. However, there
 * is no 1:1 relationship between a session and a physical connection. This is up
 * to the connection manager.
 * Local sessions are used in client applications running in 2-tier mode
 * or in application servers.<br>
 * Remote sessions are connected to a Tentackle application server
 * via RMI. Client applications running in 3-tier mode (more precise n-tier, with n &ge; 3)
 * use remote connections.<br>
 * With the abstraction of a session, Tentackle applications
 * are able to run both in 2- or n-tier mode without a single modification
 * to the source code. It's just a small change in a config file.
 * <p>
 * The configuration is achieved by a property file, which is located
 * by the {@link SessionInfo} passed to the session.
 * <p>
 * Local sessions can be either a DataSource bound via JNDI or a direct
 * JDBC connection. JNDI is used in application servers, for example running
 * a JRuby on Rails application from within Glassfish (see tentackle-web).<br>
 * JDBC connections are for standalone 2-tier applications or Tentackle application servers.
 * <br>
 * For local sessions via JDBC the file contains at least the following properties:
 *
 * <pre>
 * url=jdbc-url[|backend-type]
 *
 * Example:
 * url=jdbc:postgresql://gonzo.krake.local/erp
 * </pre>
 *
 * The backend type is optional and overrides the default resolution via URL.
 * <p>
 * <br>
 * Local sessions via JNDI need only the url. The url starts with
 * "<code>jndi:</code>" and is followed by the JNDI-name. The optional database backend type is
 * only necessary if it cannot be determined from the connection's metadata.
 *
 * <pre>
 * url=jndi:name[|backend-type]
 *
 * Example:
 * url=jndi:jdbc/erpPool
 * </pre>
 *
 * For remote sessions only one line is required at minimum:
 *
 * <pre>
 * url=rmi://hostname[:port]/service
 *
 * Example:
 * url=rmi://gonzo.krake.local:28004/ErpServer
 * </pre>
 *
 * The port is optional and defaults to 1099 (the default RMI registry).<br>
 * The initial socket factory type must correspond to the server's configuration (see {@link org.tentackle.dbms.rmi.RmiServer}).
 * If this is not the system's default (plain, unencrypted, uncompressed), it must be set via {@code socketfactory}.
 * Optionally, {@code ciphersuites} and {@code protocols} can be set (again, see {@link org.tentackle.dbms.rmi.RmiServer}).
 * <p>
 * Optionally, the following properties can be defined:
 * <p>
 * For local connections via JDBC:
 * <ul>
 *  <li>
 *  dynamically loaded JDBC driver
 *  <pre>driver=org.postgresql.Driver:jar:file:/usr/share/java/postgresql.jar!/</pre>
 *  </li>
 *  <li>
 *  A predefined user and password
 *  <pre>
 *  user=fixed-user
 *  password=fixed-password
 *  </pre>
 *  If the password begins with a {@code ~} <em>and</em> the application provides a {@link org.tentackle.common.Cryptor},
 *  the string following the {@code ~} is considered to be encrypted. If an unencrypted password must begin with a {@code ~},
 *  use {@code ~~} instead.
 *  </li>
 *  <li>
 *  By default the credentials used to connect to the backend are the same as the ones in the session info.
 *  However, if a technical user must be used, the configuration for the backend may be defined in another
 *  property file.
 *  <pre>
 *  backendinfo=property-file
 *  </pre>
 *  </li>
 *  <li>
 *  Automatic JDBC batching can be enabled via:
 *  <pre>
 *  batchsize=size
 *  </pre>
 *  if the size is &gt; 1. The persistence layer collects matching statements and executes them within batches.
 *  See {@link DbBatch}.
 *  </li>
 * </ul>
 * <p>
 * For local sessions via JDBC or JNDI:
 * <ul>
 *  <li>
 *  Each object persistable to the database (database object for short)
 *  gets a unique object ID which is generated
 *  by an {@link IdSource}. The default source is {@link ObjectId}.
 *  <pre>
 *  idsource=id-source-descriptor
 *  </pre>
 *  See {@link IdSourceConfigurator} for details.
 *  </li>
 * </ul>
 * <p>
 * For remote sessions, the properties (session info) will be sent to the server.
 * This can be used to set some application specific options or to tune the session.
 *
 * @author harald
 */
public class Db implements Session, Cloneable {

  /**
   * property key for IdSource.
   */
  public static final String IDSOURCE = "idsource";

  /**
   * Property key for the socket factory.
   */
  public static final String SOCKET_FACTORY = "socketfactory";

  /**
   * Property key for the SSL cipher suites.
   */
  public static final String CIPHER_SUITES = "ciphersuites";

  /**
   * Property key for the SSL protocols.
   */
  public static final String PROTOCOLS = "protocols";

  /**
   * Property key to enable batching for this session.
   */
  public static final String BATCHSIZE = "batchsize";


  private static final Logger LOGGER = Logger.get(Db.class);


  /**
   * Global close handlers.
   */
  private static final Set<SessionCloseHandler> GLOBAL_CLOSE_HANDLERS = ConcurrentHashMap.newKeySet();

  /**
   * Registers a global close handler.
   *
   * @param closeHandler the handler
   * @return true if added, false if already registered
   */
  public static boolean registerGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return GLOBAL_CLOSE_HANDLERS.add(closeHandler);
  }

  /**
   * Unregisters a global close handler.
   *
   * @param closeHandler the handler
   * @return true if removed, false if not registered
   */
  public static boolean unregisterGlobalCloseHandler(SessionCloseHandler closeHandler) {
    return GLOBAL_CLOSE_HANDLERS.remove(closeHandler);
  }


  /**
   * We keep an internal set of all open sessions via WeakReferences, so the
   * session still will be finalized when the session isn't used anymore.
   * The set of sessions is used to enhance the diagnostic utilities.
   */
  private static final Set<WeakReference<Db>> SESSIONS = ConcurrentHashMap.newKeySet();


  /**
   * Registers this session.
   */
  private void registerSession() {
    SESSIONS.add(new WeakReference<>(this));
  }


  /**
   * Unregisters this session.
   * <p>
   * Also cleans up the set by removing unreferenced or closed sessions.
   */
  private void unregisterSession() {
    resources.unregisterSession(this);
  }


  /**
   * Gets a list of all currently open sessions.
   *
   * @return the list of open sessions
   */
  public static Collection<Db> getAllOpenSessions() {
    List<Db> dbList = new ArrayList<>();
    for (Iterator<WeakReference<Db>> iter = SESSIONS.iterator(); iter.hasNext(); ) {
      WeakReference<Db> ref = iter.next();
      Db refDb = ref.get();
      if (refDb != null && refDb.isOpen()) {
        dbList.add(refDb);
      }
      else  {
        iter.remove();
      }
    }
    return dbList;
  }

  /**
   * Gets an open session by its ID.
   *
   * @param sessionId the session ID
   * @param url the session URL
   * @return the session, null if no such open session
   * @see Session#getSessionId()
   */
  public static Db getOpenSession(int sessionId, String url) {
    for (Iterator<WeakReference<Db>> iter = SESSIONS.iterator(); iter.hasNext(); ) {
      WeakReference<Db> ref = iter.next();
      Db refDb = ref.get();
      if (refDb != null && refDb.isOpen()) {
        if (refDb.getSessionId() == sessionId && Objects.equals(refDb.getUrl(), url)) {
          return refDb;
        }
      }
      else  {
        iter.remove();
      }
    }
    return null;
  }


  private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();  // global session instance counter

  // for RMI remote connections
  private static Class<?>[]         remoteClasses;        // the classes the delegates provide service for
  private static int                nextDelegateId;       // next handle per class

  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  // crashed remote sessions are closed from a separate thread to avoid blocking (TCP stack, whatever)
  private static final AtomicInteger REMOTE_CLOSING_COUNTER = new AtomicInteger();
  private static final ExecutorService REMOTE_CLOSING_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
    Thread t = new Thread(runnable, "Remote Session Closer(" + REMOTE_CLOSING_COUNTER.incrementAndGet() + ")");
    t.setDaemon(true); // don't inhibit termination of JVM!
    return t;
  });

  /**
   * Holds the resources to be cleaned up.
   */
  private static class Resources implements Runnable {
    private String name;                                    // the name of the session
    private final ConnectionManager conMgr;                 // connection manager for local connections
    private DbRemoteDelegate rdel;                          // remote database delegate
    private RemoteDbConnection rcon;                        // != null if remote connection established to RMI-server
    private RemoteDbSession rses;                           // remote database session if logged in to RMI-server
    private int sessionId;                                  // the session ID
    private volatile ManagedConnection con;                 // connection if attached, null = detached
    private boolean crashed;                                // true = session is closed because it is treated as crashed
    private final Set<SessionCloseHandler> closeHandlers;   // close handlers
    private Db me;                                          // != null if programmatic cleanup (close)

    Resources(ConnectionManager conMgr, int sessionId, DbRemoteDelegate rdel, RemoteDbConnection rcon, RemoteDbSession rses) {
      this.conMgr = conMgr;
      this.sessionId = sessionId;
      this.rdel = rdel;
      this.rcon = rcon;
      this.rses = rses;
      closeHandlers = new HashSet<>();
    }

    @Override
    public void run() {
      try {
        if (isOpen()) {
          try {
            if (me != null) {
              for (SessionCloseHandler closeHandler : closeHandlers) {
                try {
                  closeHandler.beforeClose(me);
                }
                catch (RuntimeException ex) {
                  LOGGER.warning("SessionCloseHandler.beforeClose failed for " + me, ex);
                }
              }
              for (SessionCloseHandler closeHandler : GLOBAL_CLOSE_HANDLERS) {
                try {
                  closeHandler.beforeClose(me);
                }
                catch (RuntimeException ex) {
                  LOGGER.warning("global SessionCloseHandler.beforeClose failed for " + me, ex);
                }
              }
            }
            // else: cleanup unreferenced session. If unref, there cannot be anyone be interested in
            // this event, because nobody has a reference to it.

            if (rcon != null && rses != null) {
              if (crashed) {
                // don't rses.close() if crashed, because connection may block!
                // do it from a background thread instead.
                // remote side must time out anyway. (or has already timed out)
                // this is especially useful if this is a server running against another server.
                REMOTE_CLOSING_EXECUTOR.submit(() -> remoteClose(rses));
              }
              else {
                rses.close();
              }
              if (me != null) {
                DbUtilities.getInstance().closeGroupsOfSession(me);
                LOGGER.info("remote session {0} closed", name);
              }
              else {
                LOGGER.warning("unreferenced remote session {0} cleaned up", name);
              }
            }
            else if (sessionId > 0) {
              ManagedConnection c = con;
              if (c != null) {    // if attached
                try {
                  c.closePreparedStatements(true);   // cleanup all pending statements
                }
                catch (RuntimeException ex) {
                  LOGGER.warning("closing prepared statements failed for " + name, ex);
                }
              }
              if (me != null) {
                DbUtilities.getInstance().closeGroupsOfSession(me);
                conMgr.logout(me);
                LOGGER.info("session {0} closed", name);
              }
              else {
                conMgr.cleanup(sessionId, c);
                LOGGER.warning("unreferenced session {0} cleaned up", name);
              }
            }

            if (me != null) {
              for (SessionCloseHandler closeHandler : closeHandlers) {
                try {
                  closeHandler.afterClose(me);
                }
                catch (RuntimeException ex) {
                  LOGGER.warning("SessionCloseHandler.afterClose failed for " + me, ex);
                }
              }
              for (SessionCloseHandler closeHandler : GLOBAL_CLOSE_HANDLERS) {
                try {
                  closeHandler.afterClose(me);
                }
                catch (RuntimeException ex) {
                  LOGGER.warning("global SessionCloseHandler.afterClose failed for " + me, ex);
                }
              }
            }
          }
          catch (PersistenceException pe) {
            throw pe;
          }
          catch (RemoteException | RuntimeException e) {
            LOGGER.severe("closing session " + name + " failed", e);
          }
          finally {
            // whatever happened: make it unusable
            clearMembers();   // clear members for re-open
            if (me != null) {
              unregisterSession(me);    // no harm if me==null, since session refs are weak
            }
          }
        }
      }
      finally {
        me = null;
      }
    }

    private void remoteClose(RemoteDbSession remoteDbSession) {
      LOGGER.warning("closing remote session " + remoteDbSession);
      try {
        remoteDbSession.close();
      }
      catch (RemoteException e) {
        LOGGER.warning("remote closing failed", e);
      }
    }

    boolean isOpen() {
      return sessionId > 0 || rdel != null;
    }

    void clearMembers() {
      rdel = null;
      rses = null;
      rcon = null;
      sessionId = 0;
    }

    void unregisterSession(Db session) {
      for (Iterator<WeakReference<Db>> iter = SESSIONS.iterator(); iter.hasNext();) {
        WeakReference<Db> ref = iter.next();
        Db refDb = ref.get();
        // if closed or unreferenced or the session to remove
        if (refDb == null || !refDb.isOpen() || refDb == session) {   // == is okay here
          iter.remove();
        }
      }
    }
  }


  private final BackendInfo backendInfo;                      // the backend information
  private Resources resources;                                // resources to be cleaned up (not final due to reopen)
  private Cleanable cleanable;                                // to clean up the connection (not final due to reopen)
  private int instanceNumber;                                 // each session gets a unique instance number
  private String idConfig;                                    // configuration of IdSource
  private int sessionGroupId;                                 // ID of the session group, 0 = none
  private int exportedSessionGroupId;                         // session group exported to the remote client, 0 = none
  private SessionPool dbPool;                                 // != null if this Db is managed by a SessionPool
  private int poolId;                                         // the poolid if dbPool != null
  private SessionInfo sessionInfo;                            // session information
  private Db clonedFromDb;                                    // the original session if this is a cloned one
  private volatile boolean autoCommit;                        // true if autocommit on, else false
  private boolean countModificationAllowed = true;            // allow modification count
  private boolean logModificationAllowed = true;              // allow modification log
  private boolean readOnly;                                   // true if database is readonly
  private IdSource defaultIdSource;                           // default ID-Source (if not overridden in derivates)
  private int fetchSize;                                      // default fetchsize, 0 = drivers default
  private int maxRows;                                        // maximum rows per resultset, 0 = no limit
  private boolean logModificationTxEnabled;                   // true if log transaction begin/commit in modification log
  private long logModificationTxId;                           // transaction id. !=0 if 'commit' is pending in modification log
  private boolean logModificationDeferred;                    // true if log to memory rather than dbms
  private List<ModificationLog> modificationLogList;          // in-memory modification-log for current transaction
  private DbTransaction transaction;                          // the running transaction
  private Deque<Consumer<Session>> postCommits;               // post commits
  private boolean postCommitsRunning;                         // flag to inhibit registering post commits while running them
  private int immediatelyRolledBack;                          // txLevel > 0 if a transaction has been rolled back by rollbackImmediately (only for local Db)
  private volatile boolean alive;                             // true = connection is still in use, false = connection has timed out
  private long keepAliveInterval;                             // the auto keep alive interval in ms, 0 = no keep alive
  private volatile Thread ownerThread;                        // the exclusive owner thread
  private WeakReference<SessionTaskDispatcher> dispatcherRef; // task dispatcher for asynchronous tasks
  private Map<String, Serializable> applicationProperties;    // application-specific properties
  private ReconnectionPolicy<Db> reconnectionPolicy;          // reconnection policy, null if none (default)
  private int batchSize;                                      // >1 if JDBC batching should be used for certain prepared statements

  // for RMI remote connections
  private RMIClientSocketFactory csf;                         // client socket factory, null if system default
  private RemoteDelegate[] delegates;                         // the delegates per session




  /**
   * Creates an instance of a logical session.<br>
   * If the login fails due to wrong passwords
   * or denied access by the application server,
   * the method {@link #handleConnectException} is invoked.
   *
   * @param conMgr the connection manager if local connection (ignored if remote)
   * @param sessionInfo session information
   */
  public Db(ConnectionManager conMgr, SessionInfo sessionInfo) {

    instanceNumber = INSTANCE_COUNTER.incrementAndGet();

    setSessionInfo(sessionInfo);

    // establish connection to the database server
    try {

      // load configuration settings
      Properties sessionProperties = sessionInfo.getProperties();
      Properties backendProperties = sessionProperties;   // backend properties defaults to session properties
      String technicalBackendInfoName = sessionInfo.getProperties().getProperty(Constants.BACKEND_INFO);
      if (technicalBackendInfoName != null) {
        if (technicalBackendInfoName.startsWith(Constants.BACKEND_INFO_USER) ||
            technicalBackendInfoName.startsWith(Constants.BACKEND_INFO_SYSTEM)) {
          int ndx = technicalBackendInfoName.indexOf(':');
          if (ndx >= 0 && ndx < technicalBackendInfoName.length() - 1) {
            String configName = technicalBackendInfoName.substring(ndx + 1);
            boolean systemPrefs = technicalBackendInfoName.startsWith(Constants.BACKEND_INFO_SYSTEM);
            if (sessionInfo.getApplicationName() != null) {
              BackendConfiguration backendConfiguration = BackendConfiguration.getBackendConfigurations(
                  sessionInfo.getApplicationName(), systemPrefs).get(configName);
              if (backendConfiguration != null) {
                backendProperties = new Properties();
                DbUtilities.getInstance().applyBackendConfiguration(backendConfiguration, backendProperties);
              }
              else {
                LOGGER.warning("no such backend configuration: @{0}:{1} for {2} -> fallback to session properties",
                               systemPrefs ? "system" : "user", configName, sessionInfo.getApplicationName());
              }
            }
            else {
              LOGGER.warning("cannot load backend configuration @{0}:{1} due to missing application name -> fallback to session properties",
                             systemPrefs ? "system" : "user", configName);
            }
          }
          // else: user didn't select a backendConfiguration -> continue with default settings from properties
        }
        else{
          // programmatically predefined (usually technical) backend info from another property file
          try {
            backendProperties = FileHelper.loadProperties(technicalBackendInfoName);
          }
          catch (IOException e1) {
            throw new PersistenceException("technical backend properties '" + technicalBackendInfoName + "' could not be loaded", e1);
          }
        }
      }

      try {
        backendInfo = new BackendInfo(backendProperties);
      }
      catch (BackendException bex) {
        throw new PersistenceException(this, "invalid configuration in " + sessionInfo.getPropertiesName(), bex);
      }

      if (backendInfo.isRemote()) {
        conMgr = null;                         // no connection manager for remote connections
        RMISocketFactoryType factoryType = RMISocketFactoryType.parse(backendProperties.getProperty(SOCKET_FACTORY));
        csf = RMISocketFactoryFactory.getInstance().createClientSocketFactory(null, factoryType);
      }
      else  {
        if (conMgr == null) {
          throw new PersistenceException("connection manager required for local connections");
        }
        String val = backendProperties.getProperty(BATCHSIZE);
        if (val != null) {
          batchSize = Integer.parseInt(val);
        }
      }

      String val = sessionProperties.getProperty(IDSOURCE);
      if (val != null) {
        idConfig = val;
      }

      open(conMgr);
    }
    catch (PersistenceException rex) {
      throw rex;
    }
    catch (RuntimeException ex) {
      throw new PersistenceException(this, "database configuration failed", ex);
    }
  }


  /**
   * Opens the session.
   *
   * @param conMgr the connection manager, null if remote
   */
  private void open(ConnectionManager conMgr) {
    try {
      int sessionId;                            // the local or remote session ID
      DbRemoteDelegate rdel = null;             // remote database delegate
      RemoteDbConnection rcon = null;           // != null if remote connection established to RMI-server
      RemoteDbSession rses = null;              // remote database session if logged in to RMI-server

      if (backendInfo.isRemote()) {

        // get connection to RMI-server
        if (csf != null) {
          String rmiUrl = backendInfo.getUrl();
          URI uri = new URI(rmiUrl);
          String host = uri.getHost();
          int port = uri.getPort();
          String path = uri.getPath();
          if (path.startsWith("/")) {
            path = path.substring(1);
          }
          LOGGER.info("connecting to {0}:{1}/{2} using {3}", host, port, path, csf);
          Registry registry = LocateRegistry.getRegistry(host, port, csf);
          try {
            rcon = (RemoteDbConnection) registry.lookup(path);
          }
          catch (NotBoundException nx) {
            StringBuilder buf = new StringBuilder();
            buf.append("bound services =");
            String[] services = registry.list();
            if (services != null) {
              for (String service : services) {
                buf.append(" '").append(service).append("'");
              }
            }
            LOGGER.warning(buf.toString());
            throw nx;
          }
        }
        else {
          rcon = (RemoteDbConnection) Naming.lookup(backendInfo.getUrl());
        }

        /*
         * Check that server matches expected version. The server checks the client's version in rcon.login() below. So,
         * we have a double check.
         */
        sessionInfo.checkServerVersion(rcon.getServerVersion());

        // get session
        rses = rcon.login(sessionInfo);    // throws exception if login denied

        // get delegate
        rdel = rses.getDbRemoteDelegate();

        // get the remote connection id
        sessionId = rdel.getSessionId();
      }
      else {
        // direct connection to database
        sessionId = conMgr.login(this);
      }

      // fresh connections are always in autoCommit mode
      autoCommit = true;
      transaction = null;

      setupIdSource();
      if (!sessionInfo.isImmutable()) {
        sessionInfo.setSince(System.currentTimeMillis());
      }

      alive = true;   // all sessions start alive!

      resources = new Resources(conMgr, sessionId, rdel, rcon, rses);
      resources.name = toString();
      cleanable = CLEANER.register(this, resources);

      registerSession();

      if (!isCloned()) {
        LOGGER.info("session {0} opened", this);
      }
    }
    catch (MalformedURLException | URISyntaxException | NotBoundException | RemoteException | VersionIncompatibleException ex) {
      throw handleConnectException(ex);
    }
  }


  /**
   * Gets the unique instance number of this session.
   *
   * @return the instance number
   */
  @Override
  public final int getInstanceNumber() {
    return instanceNumber;
  }


  /**
   * Gets the session name.<br>
   * Consists of the instance number, the connection id and the connection group id.
   *
   * @return the session name
   */
  @Override
  public String getName() {
    StringBuilder buf = new StringBuilder();
    buf.append("Db");
    buf.append(instanceNumber);
    if (resources != null) {
      buf.append('c').append(resources.sessionId);
    }
    if (sessionGroupId > 0) {
      buf.append('g');
      buf.append(sessionGroupId);
    }
    return  buf.toString();
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getName());
    // according to management order: pool | connection manager | managed connection | physical connection
    if (dbPool != null) {
      buf.append('|').append(dbPool.getName());
    }
    ManagedConnection mc = null;
    if (resources != null) {
      mc = resources.con;
      if (resources.conMgr != null) {
        buf.append('|').append(resources.conMgr.getName());
      }
    }
    if (backendInfo != null) {
      buf.append('|').append(backendInfo);
    }
    if (mc != null) {
      buf.append('|').append(mc.getName());
    }
    if (isTxRunning() && transaction != null) {   // during open autoCommit is still false
      buf.append('|').append(transaction);
    }
    return buf.toString();
  }



  /**
   * Compares two session instances.<br>
   * We simply use the unique instanceNumber.<br>
   * Because the instanceNumber is unique, we don't need to override equals/hash.
   * In fact, we must not override equals because it may be used in WeakHashMaps, where
   * the object reference counts, for example.
   *
   * @param session the session to compare this session with
   * @return  a negative integer, zero, or a positive integer as this object
   *          is less than, equal to, or greater than the specified object.
   */
  @Override
  public int compareTo(Session session) {
    return instanceNumber - ((Db) session).instanceNumber;
  }


  /**
   * Gets the connection manager for this session.
   *
   * @return the connection manager
   */
  public ConnectionManager getConnectionManager() {
    return resources.conMgr;
  }


  /**
   * Sets the pool manager.<br>
   * The method is invoked from a SessionPool when the session is created.
   *
   * @param sessionPool the session pool, null = not pooled
   */
  public void setPool(SessionPool sessionPool) {
    this.dbPool = sessionPool;
  }

  /**
   * Gets the pool manager.
   *
   * @return the session pool, null = not pooled
   */
  @Override
  public SessionPool getPool() {
    return dbPool;
  }


  /**
   * Checks whether this session is pooled.
   *
   * @return true if pooled, false if not pooled
   */
  @Override
  public boolean isPooled() {
    return dbPool != null;
  }


  /**
   * Sets the pool id.<br>
   * The method is invoked from a SessionPool when the session is used in a pool.
   *
   * @param poolId the ID given by the pool (&gt; 0), 0 = not used (free session), -1 if removed from pool
   */
  public void setPoolId(int poolId) {
    this.poolId = poolId;
  }

  /**
   * Gets the poolid.
   *
   * @return the pool id
   */
  public int getPoolId() {
    return poolId;
  }


  /**
   * Returns whether session is readonly.
   *
   * @return true if readonly.
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Sets the session to readonly.
   * <p>
   * If a database is readonly, no updates, deletes or inserts
   * are possible. Any attempt to do so results in a persistence exception.
   * <p>
   * Notice that this flag is valid for the session, not for the connection, as the physical connection may change over time!
   *
   * @param readOnly true if readonly
   */
  public void setReadOnly(boolean readOnly) {
    assertOpen();
    if (isRemote()) {
      try {
        resources.rdel.setReadOnly(readOnly);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.readOnly = readOnly;
  }


  /**
   * Gets the batch size if batching is enabled.
   *
   * @return the batch size, &le; 1 if disabled
   * @see DbBatch
   */
  public int getBatchSize() {
    return batchSize;
  }

  /**
   * Sets the batch size for JDBC auto batching.
   *
   * @param batchSize the batch size, &le; 1 to disable
   * @see DbBatch
   */
  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }


  /**
   * Gets the remote connection object.
   *
   * @return the connection object, null if local
   */
  public RemoteDbConnection getRemoteConnection() {
    return resources.rcon;
  }


  /**
   * Gets the remote session object.
   * @return the session, null if local
   */
  public RemoteDbSession getRemoteDbSession() {
    return resources.rses;
  }


  /**
   * Runs an SQL script.
   *
   * @param txName the transaction name
   * @param script the SQL script
   * @return the results
   */
  public List<ScriptRunnerResult> runScript(String txName, String script) {
    return transaction(txName, () -> {
      // connection is safely attached only within a transaction
      return getBackend().createScriptRunner(connection().getConnection()).run(script);
    });
  }


  /**
   * Asserts session is not used by another thread than the ownerthread.
   */
  public void assertOwnerThread() {
    Thread ownrThread = getOwnerThread();
    if (ownrThread != null) {
      Thread currentThread = Thread.currentThread();
      if (ownrThread != currentThread && !Scavenger.isScavenger(currentThread)) {
        throw new PersistenceException(this,
                                       "illegal attempt to use session owned by " + ownrThread +
                                       " by " + currentThread.getName());
      }
    }
  }


  /**
   * Asserts that a pooled session is requested from the pool (in use).
   */
  public void assertRequestedIfPooled() {
    if (isPooled() && getPoolId() == 0) {
      throw new PersistenceException(this, "illegal attempt to use a pooled session which was already returned to the pool");
    }
  }


  /**
   * Asserts that the database is open.
   */
  public void assertOpen() {
    try {
      if (!isOpen()) {
        if (getPool() != null) {
          throw new SessionClosedException(this, "database session already closed by pool " + getPool() +
                  "! application still holding a reference to this session after returning it to the pool!");
        }
        throw new SessionClosedException(this, "database session is closed");
      }
    }
    catch (RuntimeException rex) {
      handleExceptionForScavenger(rex);
    }
  }


  /**
   * Asserts that a transaction with the given voucher is running.
   */
  public void assertTxRunning() {
    try {
      if (transaction == null) {
        throw new PersistenceException(this, "no transaction running");
      }
    }
    catch (RuntimeException rex) {
      handleExceptionForScavenger(rex);
    }
  }


  /**
   * Asserts that no transaction is running.
   */
  public void assertNoTxRunning() {
    try {
      if (transaction != null) {
        throw new PersistenceException(this, transaction + " still pending");
      }
    }
    catch (RuntimeException rex) {
      handleExceptionForScavenger(rex);
    }
  }


  /**
   * asserts that this is not a remote session
   */
  public void assertNotRemote() {
    if (isRemote()) {
      throw new PersistenceException(this, "operation not allowed for remote sessions");
    }
  }

  /**
   * asserts that this is a remote session
   */
  public void assertRemote() {
    if (!isRemote()) {
      throw new PersistenceException(this, "operation not allowed for local sessions");
    }
  }


  /**
   * Logs exception if scavenger thread, else throws it.
   *
   * @param rex the runtime exception
   */
  private void handleExceptionForScavenger(RuntimeException rex) {
    Thread currentThread = Thread.currentThread();
    if (Scavenger.isScavenger(currentThread)) {
      LOGGER.warning("exception ignored by scavenger " + currentThread, rex);
    }
    else  {
      throw rex;
    }
  }


  /**
   * Checks whether the session is still in use.
   * Whenever a {@link StatementWrapper} or {@link PreparedStatementWrapper} is used
   * (i.e. executeQuery or executeUpdate), the session is set to be alive.
   * Some other thread may clear this flag regularly and check whether it has
   * been set in the meantime.
   *
   * @return true if connection still in use, false if not used since last setAlive(false).
   */
  @Override
  public boolean isAlive() {
    assertOpen();
    return alive;
  }

  /**
   * Sets the session's alive state.
   *
   * @param alive is true to signal it's alive, false to clear
   */
  @Override
  public void setAlive(boolean alive) {
    assertOpen();
    this.alive = alive;
    if (alive && isRemote()) {    // only for alive=true -> to remote side (remote times out on its own if false)
      try {
        resources.rdel.setAlive(true);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
  }

  @Override
  public long getKeepAliveInterval() {
    return keepAliveInterval;
  }

  @Override
  public void setKeepAliveInterval(long keepAliveInterval) {
    if (this.keepAliveInterval != keepAliveInterval) {
      this.keepAliveInterval = keepAliveInterval;
      SessionUtilities.getInstance().keepAliveIntervalChanged(this);
    }
  }


  /**
   * Sets the ID-Source configuration.
   *
   * @param idConfig the configuration
   */
  public void setIdConfiguration(String idConfig) {
    this.idConfig = idConfig;
  }

  /**
   * Gets the ID-Source configuration.
   *
   * @return the configuration
   */
  public String getIdSourceConfiguration() {
    return idConfig;
  }

  /**
   * Gets the dispatcher for this session.<br>
   * The dispatcher can be used to submit asynchronous tasks in a serial manner.
   * The returned dispatcher is configured to shut down if idle for a given timeout.
   *
   * @return the dispatcher
   */
  @Override
  public synchronized SessionTaskDispatcher getDispatcher() {
    SessionTaskDispatcher dispatcher = null;
    if (dispatcherRef != null) {
      dispatcher = dispatcherRef.get();
      if (dispatcher == null) {
        dispatcherRef = null;   // GC'd
      }
    }
    if (dispatcher == null || !dispatcher.isAlive()) {
      dispatcher = new DefaultSessionTaskDispatcher("Task Dispatcher for " + getUrl());
      dispatcher.setShutdownIdleTimeout(300000);   // shutdown if idle for more than 5 minutes
      dispatcherRef = new WeakReference<>(dispatcher);
      dispatcher.start();
    }
    return dispatcher;
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T extends Serializable> T setProperty(String key, T value) {
    if (isRemote()) {
      try {
        return resources.rdel.setProperty(key, value);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    // else: local session
    synchronized (this) {
      return (T) getApplicationProperties().put(key, value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Serializable> T getProperty(String key) {
    if (isRemote()) {
      try {
        return resources.rdel.getProperty(key);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    // else: local session
    synchronized (this) {
      return (T) getApplicationProperties().get(key);
    }
  }

  /**
   * Lazy getter for application properties.
   *
   * @return the properties, never null
   */
  private Map<String,Serializable> getApplicationProperties() {
    if (applicationProperties == null) {
      applicationProperties = new HashMap<>();
    }
    return applicationProperties;
  }


  /**
   * Configures the ID source if not remote.
   */
  private void setupIdSource() {
    if (!isRemote()) {
      if (idConfig != null) {
        setDefaultIdSource(IdSourceConfigurator.getInstance().configure(this, idConfig));
      }
      else  {
        IdSource idSource = IdSourceConfigurator.getInstance().configure(this, null);
        setDefaultIdSource(idSource);
      }
    }
  }


  /**
   * Handles a connect-exception (in open or clone).
   * The method returns a PersistenceException.
   */
  private PersistenceException handleConnectException(Exception e) {

    if (e instanceof RemoteException)  {
      // translate RemoteException to real exception
      e = PersistenceException.createFromRemoteException(this, (RemoteException) e);
    }

    if (e instanceof LoginFailedException) {
      return (LoginFailedException) e;
    }

    if (e instanceof PersistenceException) {
      return (PersistenceException) e;
    }

    return new PersistenceException(this, "connection error", e);
  }


  @Override
  public void reOpen() {
    if (isOpen()) {
      try {
        close();
      }
      catch (PersistenceException ex) {
        // nothing to log
      }
    }
    clearMembers();
    unregisterSession();
    open(resources.conMgr);
    if (sessionGroupId > 0) {
      groupWith(sessionGroupId);
    }
  }


  /**
   * Enables the automatic reconnection capability.<br>
   * If the backend is shutdown or not available for some other reason,
   * the application will retry to connect periodically.
   *
   * @param blocking true if reconnection blocks the current thread
   * @param millis the milliseconds between connection retries
   */
  public void enableReconnection(boolean blocking, long millis) {
    reconnectionPolicy = DbUtilities.getInstance().createReconnectionPolicy(this, blocking, millis);
  }

  /**
   * Disables the automatic reconnection capability.
   */
  public void disableReconnection() {
    reconnectionPolicy = null;
  }

  /**
   * Reconnects the session if a reconnection policy is defined.
   *
   * @return true if reconnected, false if reconnection disabled or non-blocking in background
   */
  public boolean optionallyReconnect() {
    if (reconnectionPolicy != null) {
      Reconnector.getInstance().submit(reconnectionPolicy);
      return reconnectionPolicy.isBlocking();
    }
    return false;
  }


  @Override
  public boolean registerCloseHandler(SessionCloseHandler closeHandler) {
    return resources.closeHandlers.add(closeHandler);
  }

  @Override
  public boolean unregisterCloseHandler(SessionCloseHandler closeHandler) {
    return resources.closeHandlers.remove(closeHandler);
  }


  @Override
  public void close() {
    if (isOpen()) {
      LOGGER.fine("closing session {0}", this);
      resources.me = this;    // programmatic close
      cleanable.clean();
    }
  }


  /**
   * Sets the crash flag.<br>
   * The session may be marked as crashed to reduce logging of succeeding errors.
   *
   * @param crashed the crash flag
   */
  public void setCrashed(boolean crashed) {
    resources.crashed = crashed;
  }


  /**
   * Gets the crash flag.<br>
   * May be invoked from any thread.
   *
   * @return true if Db is marked as crashed
   */
  public boolean isCrashed() {
    return resources.crashed;
  }


  /**
   * Gets the connection state.
   *
   * @return true if session is open, else false
   */
  @Override
  public boolean isOpen() {
    return resources.isOpen();
  }


  /**
   * Transactions get a unique transaction number by
   * counting the transactions per Db instance.
   *
   * @return the current transaction counter, 0 if no transaction in progress
   */
  public long getTxNumber()  {
    return transaction == null ? 0 : transaction.getTxNumber();
  }


  /**
   * Gets the optional transaction name.
   * Useful to distinguish transactions in logModification or alike.
   * The tx-name is cleared after commit or rollback.
   *
   * @return the transaction name, null if no transaction in progress
   */
  @Override
  public String getTxName() {
    return transaction == null ? null : transaction.getTxName();
  }


  /**
   * Gets the transaction nesting level.
   *
   * @return the nesting level, 0 if no transaction in progress
   */
  public int getTxLevel() {
    return transaction == null ? 0 : transaction.getTxLevel();
  }

  /**
   * Marks the txLevel invalid.
   * <p>
   * Will suppress any checks and warnings.
   */
  public void invalidateTxLevel() {
    if (transaction != null) {
      transaction.invalidateTxLevel();
    }
  }

  /**
   * Returns whether the txLevel is valid.
   *
   * @return true if valid
   */
  public boolean isTxLevelValid() {
    return transaction != null && transaction.isTxLevelValid();
  }

  /**
   * Gets the pending txLevel after an immediate rollback.
   *
   * @return &gt; 0 if there was an immediate rollback
   */
  public int getImmediateRollbackTxLevel() {
    return immediatelyRolledBack;
  }


  @Override
  public <T, E extends Exception> T transaction(String txName, Provider<T, E> txe) throws E {
    if (txName == null) {
      Holder<String> sh = new Holder<>();   // we cannot modify txName from within the lambda below
      StackWalker.getInstance(Set.of(), 3).forEach(s -> {
        if (sh.get() == null && !Db.class.getName().equals(s.getClassName())) {
          sh.accept(ReflectionHelper.getClassBaseName(s.getClassName()) + "#" + s.getMethodName());
        }
      });
      if (sh.get() == null) {
        // call tree too long? -> just use the classname (better than nothing...)
        txName = txe.getClass().getSimpleName();
        int ndx = txName.indexOf('$');    // cut useless info $$Lambda$0/123456789
        if (ndx > 0) {
          txName = txName.substring(0, ndx);
        }
      }
      else {
        txName = sh.get();
      }
    }

    long txVoucher = begin(txName);

    try {
      T rv = txe.get();
      commit(txVoucher);
      return rv;
    }
    catch (Throwable ex) {
      try {
        if (txVoucher != 0 && ExceptionHelper.extractException(PersistenceException.class, true, ex) != null) {
          // log only if cause is a PersistenceException
          rollback(txVoucher);
        }
        else {
          rollbackSilently(txVoucher);
        }
      }
      catch (RuntimeException rex) {
        LOGGER.severe("rollback failed", rex);
      }
      throw ex;
    }
  }

  @Override
  public <T, E extends Exception> T transaction(Provider<T, E> txe) throws E {
    return transaction(null, txe);
  }

  /**
   * Creates a transaction.
   *
   * @param txName the transaction name, null if &lt;unnamed&gt;
   * @param fromRemote true if initiated from remote client
   * @return the transaction
   */
  protected DbTransaction createTransaction(String txName, boolean fromRemote) {
    return new DbTransaction(this, txName, fromRemote);
  }


  @Override
  public long begin(String txName, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability) {
    return begin(txName, false, transactionIsolation, transactionWritability);
  }

  @Override
  public long begin(String txName)  {
    return begin(txName, false, null, null);
  }

  @Override
  public long begin() {
    return begin(null, false, null, null);
  }


  /**
   * Starts a transaction.<br>
   * Just increments the transaction level if a transaction is already running.
   *
   * @param txName is the optional transaction name, null if none
   * @param fromRemote true if invocation from remote client via {@link DbRemoteDelegate#begin}
   * @param transactionIsolation the transaction isolation level, null or {@link TransactionIsolation#DEFAULT} if default
   * @param transactionWritability the transaction writabilty, null or {@link TransactionWritability#DEFAULT} if default
   *
   * @return the transaction voucher (!= 0) if a new transaction was begun, else 0
   */
  private long begin(String txName, boolean fromRemote, TransactionIsolation transactionIsolation, TransactionWritability transactionWritability)  {

    assertOpen();
    assertOwnerThread();

    long txVoucher = 0;

    if (isRemote()) {
      if (autoCommit) {
        try {
          txVoucher = resources.rdel.begin(txName, transactionIsolation, transactionWritability);
          if (txVoucher != 0) {
            autoCommit = false;           // we are now within a tx
          }
          else {
            throw new PersistenceException(this, "server transaction already running");
          }
        }
        catch (RemoteException e)  {
          throw PersistenceException.createFromRemoteException(this, e);
        }
      }
      // else: only the outermost transaction is initiated by the remote client
      // there is no transaction nesting level at the client side!
      // However, if remote methods are invoked within a tx, those methods running
      // at the server may increase the nesting level > 1.
    }
    else {

      alive = true;

      immediatelyRolledBack = 0;  // the first begin clears all pending immediate rollbacks() ...

      if (setAutoCommit(c -> {
          // new transaction
          assertNoTxRunning();
          if (transactionIsolation != null && transactionIsolation != TransactionIsolation.DEFAULT) {
            c.setTransactionIsolation(transactionIsolation.getLevel());
          }
          if (transactionWritability != null && transactionWritability != TransactionWritability.DEFAULT) {
            c.setReadOnly(transactionWritability.getWritable());
          }
        })) {

        modificationLogList = null;
        transaction = createTransaction(txName, fromRemote);
        txVoucher = transaction.getTxVoucher();
        LOGGER.fine("{0}: begin transaction, voucher {1}", this, txVoucher);
      }
      else  {
        // already running transaction
        assertTxRunning();
        if (transactionIsolation != null && transactionIsolation != TransactionIsolation.DEFAULT &&
            connection().getTransactionIsolation() != transactionIsolation.getLevel()) {
          throw new PersistenceException(this, "cannot change a running transaction's isolation from " +
                                               TransactionIsolation.valueOf(connection().getTransactionIsolation()) +
                                               " to " + transactionIsolation);
        }
        if (transactionWritability != null && transactionWritability != TransactionWritability.DEFAULT &&
            transactionWritability.getWritable() != connection().isReadOnly()) {
          throw new PersistenceException(this, connection().isReadOnly() ?
                                               "cannot change a running read-only transaction to read-write" :
                                               "cannot change a running read-write transaction to read-only");
        }
        transaction.incrementTxLevel(txName);    // just increment the nesting level
        LOGGER.fine("{0}: nested begin", this);
      }
    }

    return txVoucher;
  }


  /**
   * Creates an unnamed savepoint in the current transaction.
   *
   * @return the savepoint handle unique within current transaction
   */
  @Override
  public SavepointHandle setSavepoint() {
    if (isRemote()) {
      try {
        return resources.rdel.setSavepoint();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      executeBatch();
      ManagedConnection mc = connection();
      Savepoint savepoint = mc.setSavepoint();
      try {
        SavepointHandle handle = new SavepointHandle(savepoint.getSavepointId());
        transaction.addSavepoint(handle, savepoint);
        return handle;
      }
      catch (SQLException sqx) {
        throw mc.createFromSqlException("setting unnamed savepoint failed", sqx);
      }
    }
  }

  /**
   * Creates a savepoint with the given name in the current transaction.
   *
   * @param name the savepoint name
   *
   * @return the savepoint handle unique within current transaction
   */
  @Override
  public SavepointHandle setSavepoint(String name) {
    if (isRemote()) {
      try {
        return resources.rdel.setSavepoint(name);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      executeBatch();
      ManagedConnection mc = connection();
      Savepoint savepoint = mc.setSavepoint(name);
      try {
        SavepointHandle handle = new SavepointHandle(savepoint.getSavepointName());
        transaction.addSavepoint(handle, savepoint);
        return handle;
      }
      catch (SQLException sqx) {
        throw mc.createFromSqlException("setting named savepoint '" + name + "' failed", sqx);
      }
    }
  }

  /**
   * Undoes all changes made after the given <code>Savepoint</code> object was set.
   *
   * @param handle the savepoint handle
   */
  @Override
  public void rollback(SavepointHandle handle) {
    if (isRemote()) {
      try {
        resources.rdel.rollback(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = transaction.removeSavepoint(handle);
      if (savepoint == null) {
        throw new PersistenceException(this, "no such savepoint to rollback: " + handle);
      }
      connection().rollback(savepoint);
    }
  }

  /**
   * Removes the specified <code>Savepoint</code> and subsequent <code>Savepoint</code> objects from the current
   * transaction.
   *
   * @param handle the savepoint handle
   */
  @Override
  public void releaseSavepoint(SavepointHandle handle) {
    if (isRemote()) {
      try {
        resources.rdel.releaseSavepoint(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = transaction.removeSavepoint(handle);
      if (savepoint == null) {
        throw new PersistenceException(this, "no such savepoint to release: " + handle);
      }
      connection().releaseSavepoint(savepoint);
    }
  }

  /**
   * Returns whether a transaction with savepoints is currently running.
   *
   * @return true if savepoints present
   */
  public boolean isTxWithSavepoints() {
    return transaction != null && transaction.isWithSavepoints();
  }

  /**
   * Checks whether current transaction was initiated by a remote client.
   *
   * @return true if tx is a remote client transaction
   */
  public boolean isClientTx() {
    assertTxRunning();
    return transaction.getTxVoucher() < 0;
  }



  /**
   * Creates a {@link  DbModificationType#BEGIN} modification log, if necessary.
   */
  public void logBeginTx() {
    if (logModificationTxEnabled && logModificationTxId == 0 && isTxRunning()) {
      ModificationLog log = ModificationLogFactory.getInstance().createModificationLog(this, DbModificationType.BEGIN);
      log.reserveId();
      log.setTxId(log.getId());
      log.saveObject();
      logModificationTxId = log.getTxId();
    }
  }


  /**
   * Creates a commit modification log if necessary.
   */
  public void logCommitTx() {
    if (logModificationTxId != 0) {
      // only if BEGIN already created
      if (logModificationTxEnabled) {
        ModificationLogFactory.getInstance().createModificationLog(this, DbModificationType.COMMIT).saveObject();
      }
      logModificationTxId = 0;
    }
  }


  /**
   * Registers a {@link PersistenceVisitor} to be invoked just before
   * performing a persistence operation.<br>
   * Notice that the visitor must be registered within the transaction, i.e. <em>after</em> {@link #begin()}.
   * The visitors are automatically unregistered at the end of a transaction
   * (commit or rollback).
   *
   * @param visitor the visitor to register
   * @return a handle uniquely referring to the visitor
   * @see #isPersistenceOperationAllowed(AbstractDbObject, ModificationType)
   */
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) {
    if (isRemote()) {
      try {
        return resources.rdel.registerPersistenceVisitor(visitor);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      assertTxRunning();
      return transaction.registerPersistenceVisitor(visitor);
    }
  }


  /**
   * Unregisters a {@link PersistenceVisitor}.
   *
   * @param handle the visitor's handle to unregister
   * @return the visitor if removed, null if not registered
   */
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) {
    if (isRemote()) {
      try {
        return resources.rdel.unregisterPersistenceVisitor(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.unregisterPersistenceVisitor(handle) : null;
    }
  }


  /**
   * Gets the currently registered persistence visitors.
   *
   * @return the visitors, null or empty if none
   */
  public Collection<PersistenceVisitor> getPersistenceVisitors() {
    if (isRemote()) {
      try {
        return resources.rdel.getPersistenceVisitors();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.getPersistenceVisitors() : null;
    }
  }


  /**
   * Checks whether a persistence operation is allowed.<br>
   * This is determined by consulting the {@link PersistenceVisitor}s.<br>
   *
   * @param object the persistence object
   * @param modType the modification type
   * @return true if allowed
   * @see #registerPersistenceVisitor(PersistenceVisitor)
   */
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, ModificationType modType) {
    return transaction == null || transaction.isPersistenceOperationAllowed(object, modType);
  }


  @Override
  public void postCommit(Consumer<Session> postCommit) {
    if (isTxRunning()) {
      if (postCommitsRunning) {
        throw new PersistenceException(this, "post-commits cannot be registered from within another post-commit");
      }
      if (postCommits == null) {
        postCommits = new ArrayDeque<>();
      }
      postCommits.push(postCommit);
    }
    else {
      throw new PersistenceException(this, "post-commits can only be registered from within a running transaction");
    }
  }


  /**
   * Runs the post commits in reverse order of registration.<br>
   * Post commits are registered for the session, not the transaction,
   * i.e. they are not transferred via RMI to the middle tier like {@link CommitTxRunnable}s or {@link RollbackTxRunnable}s.
   */
  protected void runPostCommits() {
    try {
      postCommitsRunning = true;
      if (postCommits != null) {
        Iterator<Consumer<Session>> iterator = postCommits.iterator();
        while (iterator.hasNext()) {
          iterator.next().accept(this);
        }
        postCommits = null;
      }
    }
    finally {
      postCommitsRunning = false;
    }
  }


  /**
   * Registers a {@link CommitTxRunnable} to be invoked just before
   * committing a transaction.
   *
   * @param commitRunnable the runnable to register
   * @return the handle
   */
  public DbTransactionHandle registerCommitTxRunnable(CommitTxRunnable commitRunnable) {
    if (isRemote()) {
      try {
        return resources.rdel.registerCommitTxRunnable(commitRunnable);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      assertTxRunning();
      return transaction.registerCommitTxRunnable(commitRunnable);
    }
  }

  /**
   * Unregisters a {@link CommitTxRunnable}.
   *
   * @param handle the runnable's handle to unregister
   * @return the runnable, null if not registered
   */
  public CommitTxRunnable unregisterCommitTxRunnable(DbTransactionHandle handle) {
    if (isRemote()) {
      try {
        return resources.rdel.unregisterCommitTxRunnable(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.unregisterCommitTxRunnable(handle) : null;
    }
  }


  /**
   * Gets the currently registered commit runnables.
   *
   * @return the runnables, null or empty if none
   */
  public Collection<CommitTxRunnable> getCommitTxRunnables() {
    if (isRemote()) {
      try {
        return resources.rdel.getCommitTxRunnables();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.getCommitTxRunnables() : null;
    }
  }


  /**
   * Registers a {@link RollbackTxRunnable} to be invoked just before
   * rolling back a transaction.
   *
   * @param rollbackRunnable the runnable to register
   * @return the handle
   */
  public DbTransactionHandle registerRollbackTxRunnable(RollbackTxRunnable rollbackRunnable) {
    if (isRemote()) {
      try {
        return resources.rdel.registerRollbackTxRunnable(rollbackRunnable);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      assertTxRunning();
      return transaction.registerRollbackTxRunnable(rollbackRunnable);
    }
  }

  /**
   * Unregisters a {@link RollbackTxRunnable}.
   *
   * @param handle the runnable's handle to unregister
   * @return the runnable, null if not registered
   */
  public RollbackTxRunnable unregisterRollbackTxRunnable(DbTransactionHandle handle) {
    if (isRemote()) {
      try {
        return resources.rdel.unregisterRollbackTxRunnable(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.unregisterRollbackTxRunnable(handle) : null;
    }
  }


  /**
   * Gets the currently registered rollback runnables.
   *
   * @return the runnables, null or empty if none
   */
  public Collection<RollbackTxRunnable> getRollbackTxRunnables() {
    if (isRemote()) {
      try {
        return resources.rdel.getRollbackTxRunnables();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return transaction != null ? transaction.getRollbackTxRunnables() : null;
    }
  }


  /**
   * Commits a transaction if the corresponding "begin()" has started it.
   * The corresponding "begin()" is determined by the txVoucher parameter.
   * If it fits to the issued voucher the tx will be committed.
   * If it is 0, nothing will happen.
   * In all other cases an exception will be thrown.
   *
   * @param txVoucher the transaction voucher, 0 to do nothing (nested tx)
   *
   * @return true if committed, false if nested tx
   */
  @Override
  public boolean commit(long txVoucher)  {

    assertOpen();
    assertOwnerThread();

    boolean committed = false;

    if (isRemote()) {
      if (txVoucher != 0) {   // ignore remote nesting levels
        try {
          if (autoCommit) {
            throw new PersistenceException(this, "no client transaction running");
          }
          committed = resources.rdel.commit(txVoucher);
          if (committed) {
            autoCommit = true;   // now outside tx again
            runPostCommits();
          }
          else {
            throw new PersistenceException(this, "no server transaction running");
          }
        }
        catch (RemoteException e)  {
          throw PersistenceException.createFromRemoteException(this, e);
        }
      }
    }

    else  {

      alive = true;

      assertTxRunning();

      if (txVoucher != 0) {

        if (!autoCommit) {
          // within a transaction: commit!
          LOGGER.fine("{0}: commit transaction, voucher {1}", this, txVoucher);
          transaction.assertValidTxVoucher(txVoucher);
          if (transaction.isTxLevelValid() && transaction.getTxLevel() != 1) {
            LOGGER.warning(this + ": txLevel=" + transaction.getTxLevel() + ", should be 1");
          }
          transaction.commit();
          logCommitTx();
          setAutoCommit(null);    // according to the specs: this will commit!
          logModificationDeferred = false;
          long txNo = getTxNumber();
          transaction = null;
          modificationLogList = null;
          committed = true;
          DbUtilities.getInstance().notifyCommit(this, txNo);
          runPostCommits();
        }
        else  {
          throw new PersistenceException(this, "transaction ended unexpectedly before commit, valid voucher " + txVoucher);
        }
      }
      else {
        // no voucher, no change (this is ok)
        LOGGER.fine("{0}: nested commit", this);
        transaction.decrementTxLevel();   // just decrement the tx level
      }
    }

    return committed;
  }



  @Override
  public boolean rollback(long txVoucher) {
    return rollback(txVoucher, true);
  }

  @Override
  public boolean rollbackSilently(long txVoucher) {
    return rollback(txVoucher, false);
  }


  /**
   * Rolls back a transaction if the corresponding "begin()" has started it.
   *
   * @param txVoucher the transaction voucher, 0 if ignore (nested tx)
   * @param withLog true if log via INFO
   * @return true if tx was really rolled back, false if not.
   */
  public boolean rollback(long txVoucher, boolean withLog)  {

    assertOpen();
    assertOwnerThread();

    boolean rolledBack = false;

    if (isRemote()) {
      if (txVoucher != 0) {   // ignore remote nesting levels
        try {
          if (autoCommit) {
            throw new PersistenceException(this, "no client transaction running");
          }
          rolledBack = resources.rdel.rollback(txVoucher, withLog);
          if (rolledBack) {
            autoCommit = true;   // now outside tx again
            postCommits = null;
          }
          else {
            throw new PersistenceException(this, "no server transaction running");
          }
        }
        catch (RemoteException e)  {
          throw PersistenceException.createFromRemoteException(this, e);
        }
      }
    }

    else {

      alive = true;

      if (transaction == null) {
        // allow pending rollbacks after rollbackImmediately
        if (immediatelyRolledBack > 0) {
          immediatelyRolledBack--;
          LOGGER.fine("pending immediate rollback counter decremented -> no physical rollback");
        }
        else {
          LOGGER.fine("{0}: no transaction running -> no physical rollback", this);
        }
        rolledBack = txVoucher != 0;
      }

      else {
        if (txVoucher != 0) {
          // matching begin() started a new tx
          if (!autoCommit) {
            // within a transaction
            LOGGER.fine("{0}: rollback transaction, voucher {1}", this, txVoucher);
            transaction.assertValidTxVoucher(txVoucher);
            if (transaction.isTxLevelValid() && transaction.getTxLevel() != 1) {
              LOGGER.warning(this + ": txLevel=" + transaction.getTxLevel() + ", should be 1");
            }
            transaction.rollback();
            ManagedConnection c = resources.con;
            if (c != null) {
              c.rollback(withLog);  // avoid a commit ...
              setAutoCommit(null);    // ... in setAutoCommit
            }
            else {
              LOGGER.warning("rollback too late: connection already detached");
              autoCommit = true;
            }
            logModificationDeferred = false;
            modificationLogList = null;
            logModificationTxId = 0;
            long txNo = getTxNumber();
            transaction = null;
            postCommits = null;
            rolledBack = true;
            DbUtilities.getInstance().notifyRollback(this, txNo);
          }
          else {
            throw new PersistenceException(this, "transaction ended unexpectedly before rollback (valid voucher)");
          }
        }
        else {
          // no voucher, no change (this is ok)
          LOGGER.fine("{0}: nested rollback", this);
          transaction.decrementTxLevel();
        }
      }
    }

    return rolledBack;
  }


  /**
   * Rolls back the current transaction, if pending.<br>
   * Used to clean up in case of an exception or alike.
   * <p>
   * Only applicable to local sessions.
   * <p>
   * Applications should use {@link #rollback(long)}.<br>
   * Invocations of this method will be logged as a warning.
   *
   * @param cause the cause of the rollback, may be null
   */
  public void rollbackImmediately(Throwable cause) {

    assertNotRemote();

    try {
      // cancel all pending statements (usually max. 1)
      ManagedConnection c = resources.con;
      if (c != null) {
        c.cancelRunningStatements();
      }

      if (transaction != null) {
        LOGGER.warning("*** immediate rollback for {0} ***", this);
        int currentTxLevel = transaction.getTxLevel();
        immediatelyRolledBack = 0;
        transaction.invalidateTxLevel();    // avoid misleading warnings
        // log statements only if the root-cause is a persistence exception
        boolean withLog = ExceptionHelper.extractException(PersistenceException.class, true, cause) != null;
        if (!rollback(transaction.getTxVoucher(), withLog)) {
          throw new PersistenceException(this, transaction + " not rolled back despite valid voucher");
        }
        transaction = null;
        postCommits = null;
        // remember the txLevel for pending rollback()s, if any will arrive from the application
        immediatelyRolledBack = currentTxLevel;
      }
      // else: not within a transaction: cleanup forced
      forceDetached();
    }
    catch (RuntimeException rex) {
      try {
        ManagedConnection c = resources.con;
        if (c != null && !c.isClosed()) {
          // check low level transaction state
          boolean connectionInTransaction;
          try {
            connectionInTransaction = c.getConnection().getAutoCommit();
          }
          catch (SQLException ex) {
            LOGGER.warning("cannot determine transaction state of " + c + ": assume transaction still running", ex);
            connectionInTransaction = true;
          }
          if (connectionInTransaction) {
            /*
             * If the physical connection is still running a transaction,
             * we need a low-level rollback and mark the connection dead
             * to avoid further use by the application.
             */
            LOGGER.warning(c + " still in transaction: performing low-level rollback");
            try {
              c.getConnection().rollback();
              c.getConnection().setAutoCommit(true);
              long txNo = getTxNumber();
              if (txNo != 0) {
                DbUtilities.getInstance().notifyRollback(this, txNo);
              }
            }
            catch (SQLException ex) {
              LOGGER.warning("low-level connection rollback failed for " + c, ex);
            }
            try {
              // this may yield some additional information
              c.logAndClearWarnings();
            }
            catch (PersistenceException ex) {
              LOGGER.warning("clear warnings failed for " + c, ex);
            }
          }
          c.setDead(true);    // don't use this connection anymore!
          LOGGER.warning("connection " + c + " marked dead");
        }
      }
      finally {
        handleExceptionForScavenger(rex);
      }
    }
  }

  /**
   * Sets the current connection.
   * This method is package scope and invoked whenever a connection
   * is attached or detached to/from a Db by the ConnectionManager.
   */
  void setConnection(ManagedConnection con) {
    resources.con = con;
  }


  /**
   * Gets the current connection.
   *
   * @return the connection, null = not attached
   */
  ManagedConnection getConnection() {
    return resources.con;
  }


  /**
   * Gets the currently attached connection.
   * <p>
   * Throws {@link PersistenceException} if no connection attached.
   * <p>
   * WARNING: this method is provided for internal use only and should not be used by applications!
   *
   * @return the attached connection, never null
   */
  public ManagedConnection connection() {
    ManagedConnection c = resources.con;
    if (c == null) {
      throw new PersistenceException(this, "not attached");
    }
    return c;
  }


  /**
   * Detach the session.<br>
   * Used in exception handling to clean up all pending statements and result sets.
   */
  void forceDetached() {
    resources.conMgr.forceDetach(this);
  }


  /**
   * Gets the session id.
   * This is a unique number assigned to this session by the ConnectionManager.
   *
   * @return the session id, 0 = session is new and not connected so far
   */
  @Override
  public int getSessionId() {
    return resources.sessionId;
  }

  /**
   * Clears the session ID.<br>
   * Used by connection managers only.
   * Package scope!
   */
  void clearSessionId() {
    resources.sessionId = 0;
  }


  /**
   * Sets the group number for this session.<br>
   *
   * This is an optional number describing groups of sessions,
   * which is particularly useful in RMI-servers: if one connection
   * fails, all others should be closed as well.
   * Groups are only meaningful for local sessions, i.e.
   * for remote sessions the group instance refers to that of the RMI-server.
   *
   * @param groupId is the group number, 0 = no group
   */
  public void setSessionGroupId(int groupId) {
    assertOpen();

    if (groupId != sessionGroupId) {
      if (groupId != 0) {
        if (sessionGroupId != 0) {
          throw new PersistenceException(this, "session already belongs to group " + sessionGroupId +
                                               ", cannot change to group " + groupId);
        }
        LOGGER.info("{0} joined group {1}", this, groupId);
      }
      else  {
        LOGGER.info("{0} removed from group {1}", this, sessionGroupId);
      }
      sessionGroupId = groupId;
    }
  }

  @Override
  public int getSessionGroupId() {
    return sessionGroupId;
  }


  /**
   * Gets the session group exported to remote clients.
   *
   * @return the exported group, 0 if not a client group
   */
  public int getExportedSessionGroupId() {
    return exportedSessionGroupId;
  }

  /**
   * Sets the session group exported to remote clients.
   *
   * @param groupId the exported group
   */
  public void setExportedSessionGroupId(int groupId) {
    assertOpen();
    if (groupId != exportedSessionGroupId) {
      if (groupId != 0) {
        if (exportedSessionGroupId != 0) {
          throw new PersistenceException(this, "session already belongs to exported group " + exportedSessionGroupId +
                                               ", cannot change to exported group " + groupId);
        }
        LOGGER.info("{0} joined exported group {1}", this, groupId);
      }
      else  {
        LOGGER.info("{0} removed from exported group {1}", this, exportedSessionGroupId);
      }
      exportedSessionGroupId = groupId;
    }
  }


  @Override
  public void groupWith(int sessionId) {
    assertOpen();

    if (isRemote()) {
      try {
        Db session2 = getOpenSession(sessionId, getUrl());
        if (session2 == null) {
          throw new PersistenceException(this, "no such local session with ID " + sessionId);
        }
        resources.rdel.groupWith(sessionId);
        setSessionGroupId(sessionId);
        session2.setSessionGroupId(sessionId);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      DbUtilities.getInstance().addToSessionGroup(this, sessionId, false);
    }
  }


  /**
   * Attaches the session.
   * <p>
   * Notice: package scope!
   */
  void attach() {
    assertOpen();
    assertOwnerThread();
    assertRequestedIfPooled();
    if (resources.conMgr == null) {
      throw new PersistenceException(this, "no connection manager");
    }
    resources.conMgr.attach(this);
  }

  /**
   * Detaches the session.
   * <p>
   * Notice: package scope!
   */
  void detach() {
    if (resources.sessionId > 0) {
      assertOwnerThread();
      resources.conMgr.detach(this);
    }
    // else: session is already closed (may happen during cleanup)
  }

  /**
   * Detaches the session after exception.
   */
  void detachSafely() {
    try {
      detach();
    }
    catch (RuntimeException rx) {
      LOGGER.warning("ignored exception: " + rx.getMessage());    // just log the reason w/o stacktrace
    }
  }


  /**
   * Sets the exclusive owner thread.
   * <p>
   * Allows detecting other threads accidentally using this session.<br>
   * Caution: don't forget to clear!
   *
   * @param ownerThread the owner thread, null to clear
   */
  @Override
  public void setOwnerThread(Thread ownerThread) {
    this.ownerThread = ownerThread;
  }


  /**
   * Gets the owner thread.
   *
   * @return the exclusive thread, null if none
   */
  @Override
  public Thread getOwnerThread() {
    return ownerThread;
  }



  /**
   * Creates a non-prepared statement.
   * <p>
   * Non-prepared statements attach the session as soon as they
   * are instantiated! The session is detached after executeUpdate or after
   * executeQuery when its result set is closed.
   *
   * @param resultSetType a result set type; one of
   *        <code>ResultSet.TYPE_FORWARD_ONLY</code>,
   *        <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
   *        <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
   * @param resultSetConcurrency a concurrency type; one of
   *        <code>ResultSet.CONCUR_READ_ONLY</code> or
   *        <code>ResultSet.CONCUR_UPDATABLE</code>
   * @return the statement wrapper
   */
  public StatementWrapper createStatement (int resultSetType, int resultSetConcurrency) {
    assertNotRemote();
    attach();
    try {
      StatementWrapper stmt = connection().createStatement(resultSetType, resultSetConcurrency);
      stmt.markReady();
      return stmt;
    }
    catch (RuntimeException rex) {
      detachSafely();
      throw rex;
    }
  }


  /**
   * Creates a non-prepared statement.
   *
   * @param resultSetType a result set type; one of
   *        <code>ResultSet.TYPE_FORWARD_ONLY</code>,
   *        <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
   *        <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
   * @return a new <code>Statement</code> object that will generate
   *         <code>ResultSet</code> objects with the given type and
   *         concurrency CONCUR_READ_ONLY
   */
  public StatementWrapper createStatement (int resultSetType) {
    return createStatement(resultSetType, ResultSet.CONCUR_READ_ONLY);
  }


  /**
   * Creates a non-prepared statement.
   *
   * @return a new <code>Statement</code> object that will generate
   *         <code>ResultSet</code> objects with type TYPE_FORWARD_ONLY and
   *         concurrency CONCUR_READ_ONLY
   */
  public StatementWrapper createStatement () {
    return createStatement(ResultSet.TYPE_FORWARD_ONLY);
  }


  /**
   * Gets the prepared statement.
   * <p>
   * Getting the prepared statement will attach the session to a connection.
   * The session will be detached after executeUpdate() or executeQuery when its
   * result set is closed.
   *
   * @param stmtKey the statement key
   * @param alwaysPrepare true if always do a physical prepare
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   * @param sqlSupplier the SQL supplier
   *
   * @return the prepared statement for this session
   */
  public PreparedStatementWrapper getPreparedStatement(StatementKey stmtKey, boolean alwaysPrepare, int resultSetType,
                                                       int resultSetConcurrency, Supplier<String> sqlSupplier) {
    assertNotRemote();
    attach();
    try {
      PreparedStatementWrapper stmt = connection().getPreparedStatement(
              stmtKey, alwaysPrepare, resultSetType, resultSetConcurrency, sqlSupplier);
      stmt.markReady(); // mark ready for being used once
      return stmt;
    }
    catch (RuntimeException rex) {
      detachSafely();
      throw rex;
    }
  }


  /**
   * Gets the prepared statement.<br>
   * Uses {@link ResultSet#TYPE_FORWARD_ONLY} and {@link ResultSet#CONCUR_READ_ONLY}.
   *
   * @param stmtKey the statement key
   * @param alwaysPrepare true if always do a physical prepare
   * @param sqlSupplier the SQL supplier
   *
   * @return the prepared statement for this session
   */
  public PreparedStatementWrapper getPreparedStatement(StatementKey stmtKey, boolean alwaysPrepare,
                                                       Supplier<String> sqlSupplier) {
    return getPreparedStatement(stmtKey, alwaysPrepare, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, sqlSupplier);
  }


  /**
   * Creates a one-shot prepared statement.
   * <p>
   * Getting the prepared statement will attach the session to a connection.
   * The session will be detached after executeUpdate() or executeQuery() when its
   * result set is closed.
   *
   * @param sql the SQL code
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   *
   * @return the prepared statement for this session
   */
  public PreparedStatementWrapper createPreparedStatement(String sql, int resultSetType, int resultSetConcurrency) {
    assertNotRemote();
    attach();
    try {
      PreparedStatementWrapper stmt = connection().createPreparedStatement(null, sql, resultSetType, resultSetConcurrency);
      stmt.markReady(); // mark ready for being used once
      return stmt;
    }
    catch (RuntimeException rex) {
      detachSafely();
      throw rex;
    }
  }

  /**
   * Creates a one-shot prepared statement.<br>
   * Uses {@link ResultSet#TYPE_FORWARD_ONLY} and {@link ResultSet#CONCUR_READ_ONLY}.
   *
   * @param sql the SQL code
   *
   * @return the prepared statement for this session
   */
  public PreparedStatementWrapper createPreparedStatement(String sql) {
    return Db.this.createPreparedStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
  }


  /**
   * Adds a statement with its related PDO to the current batch.<br>
   * Does nothing if there is no batched transaction running.<br>
   * <p>
   * IMPORTANT: only insert, update or delete statements that refer to a <em>single</em> row
   * are allowed for auto batching!
   *
   * @param modType the statement type
   * @param statement the statement to batch
   * @param object the object to insert, update or delete
   * @param rootClassId the root class ID
   */
  @SuppressWarnings("rawtypes")
  public void addToBatch(ModificationType modType, PreparedStatementWrapper statement, AbstractDbObject object, int rootClassId) {
    DbBatch batch = getBatch();
    if (batch != null) {
      batch.add(modType, statement, object, rootClassId);
    }
  }

  /**
   * Executes all pending batched statements.<br>
   * Does nothing if there is no batched transaction running.
   */
  public void executeBatch() {
    DbBatch batch = getBatch();
    if (batch != null) {
      batch.execute(false);
    }
  }

  /**
   * Gets the current batch.
   *
   * @return the batch, null if no batched transaction or no transaction at all
   */
  private DbBatch getBatch() {
    return transaction != null ? transaction.getBatch() : null;
  }


  /**
   * Sets autoCommit feature.
   * <p>
   * The method is provided for local sessions only.
   * Applications must use begin/commit instead.
   * Furthermore, the database will be attached and detached.
   * <p>
   * The method differs from the JDBC-method: a commit is *NOT* issued
   * if the autoCommit boolean value wouldn't change.
   * This allows nested setAutoCommit(false) in large transactions.
   *
   * @param txStarter connection consumer invoked immediately when a transaction is begun (sets autocommit to false), null for autocommit = true
   * @return the old(!) value of autoCommit
   */
  private boolean setAutoCommit (Consumer<ManagedConnection> txStarter) {
    boolean autoCommit = txStarter == null;
    if (this.autoCommit != autoCommit) {
      if (autoCommit) {
        if (getBackend().isExtraCommitRequired()) {
          // some dbms need a commit before setAutoCommit(true);
          connection().commit();
        }
      }
      else {
        // starting a tx
        attach();
        txStarter.accept(connection());
      }
      connection().setAutoCommit(autoCommit);
      if (autoCommit) {
        // ending a tx
        detach();
      }
      this.autoCommit = autoCommit;
      LOGGER.finer("physically setAutoCommit({0})", autoCommit);
      return !autoCommit;     // was toggled
    }
    else {
      return autoCommit;    // not changed
    }
  }


  /**
   * Gets the current transaction state.<br>
   * Technically, a transaction is running if the autocommit is turned off.
   *
   * @return true if a transaction is running
   */
  @Override
  public boolean isTxRunning() {
    return !autoCommit;
  }


  /**
   * Gets the current session info.
   *
   * @return the session info
   */
  @Override
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }


  /**
   * Sets the session info.<br>
   * This will *NOT* send the session to the remote server
   * if this is a remote session.
   *
   * @param sessionInfo the session info
   */
  public void setSessionInfo (SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }


  /**
   * Gets the backend info.
   *
   * @return the backend info
   */
  public BackendInfo getBackendInfo() {
    return backendInfo;
  }


  @Override
  public String getUrl() {
    return backendInfo.getUrl();
  }


  /**
   * clears the session's local data for close/clone
   */
  private void clearMembers() {
    if (isRemote()) {
      // cloning a remote session involves creating an entire new session via open()!
      // notice that a remote session is always open (otherwise it wouldn't be remote ;-))
      delegates = null;
      // returning to GC will also GC on server-side (if invoked from close())
    }
    else  {
      defaultIdSource = null;
      transaction = null;
      logModificationTxId = 0;
      logModificationDeferred = false;
      modificationLogList = null;
      immediatelyRolledBack = 0;
    }

    postCommits = null;
    postCommitsRunning = false;
    ownerThread = null;

    if (sessionInfo != null && !sessionInfo.isImmutable()) {
      sessionInfo.setSince(0);    // not logged in anymore
    }

    if (resources != null) {
      resources.clearMembers();
    }
  }

  @Override
  public Db clone() {
    return clone(null);   // super.clone() is invoked in clone(String) below
  }

  @Override
  public Db clone(String sessionName) {
    assertOpen();
    if (isPooled()) {
      throw new PersistenceException(this, "pooled sessions must not be cloned");
    }

    try {

      Db newDb = (Db) super.clone();

      if (sessionInfo != null) { // we need a new session info cause some things may be stored here!
        SessionInfo clonedInfo = sessionInfo.clone();
        if (sessionName != null) {
          clonedInfo.setSessionName(sessionName);
        }
        newDb.setSessionInfo(clonedInfo);
      }

      newDb.resources = null;
      newDb.clearMembers();
      newDb.sessionGroupId = 0;
      newDb.exportedSessionGroupId = 0;

      newDb.instanceNumber = INSTANCE_COUNTER.incrementAndGet();
      newDb.clonedFromDb = this;
      newDb.dispatcherRef = null;

      newDb.open(resources.conMgr);

      LOGGER.info("session {0} cloned from {1}", newDb, this);

      newDb.registerSession();
      return newDb;
    }
    catch (CloneNotSupportedException | RuntimeException e)  {
      throw handleConnectException(e);
    }
  }


  /**
   * Gets the original session if this session is cloned.
   *
   * @return the original session, null if this session is not cloned.
   */
  public Db getClonedFromDb() {
    return clonedFromDb;
  }


  /**
   * Clears the cloned state.
   * Useful if the information is no longer needed.
   */
  public void clearCloned() {
    this.clonedFromDb = null;
  }


  /**
   * Gets the cloned state.
   *
   * @return true if this session is cloned
   */
  public boolean isCloned() {
    return clonedFromDb != null;
  }


  /**
   * Gets the default fetchsize
   *
   * @return the default fetchSize.
   */
  public int getFetchSize() {
    return fetchSize;
  }

  /**
   * Sets the default fetchsize for all "wrapped" statements
   * (PreparedStatementWrapper and StatementWrapper)
   *
   * @param fetchSize the new default fetchSize
   *
   */
  public void setFetchSize(int fetchSize) {
    this.fetchSize = fetchSize;
  }


  /**
   * gets the maximum number of rows in resultsets.
   *
   * @return the max rows, 0 = no limit
   */
  public int getMaxRows() {
    return maxRows;
  }

  /**
   * sets the maximum number of rows in resultsets.
   * @param maxRows the max rows, 0 = no limit (default)
   *
   */
  public void setMaxRows(int maxRows) {
    this.maxRows = maxRows;
  }


  /**
   * Gets the type of the logical session.
   *
   * @return true if remote, false if local
   */
  @Override
  public boolean isRemote() {
    return backendInfo.isRemote();
  }

  @Override
  public RemoteSession getRemoteSession() {
    assertRemote();
    return RemoteSessionFactory.getInstance().create(resources.rses);
  }

  /**
   * Prepares a {@link RemoteDelegate}.
   * <p>
   * The delegates for the AbstractDbObject-classes "live" in the session, i.e. the remote session.
   * Thus, delegates are unique per class AND session!
   * In order for the AbstractDbObject-derived classes to quickly map to the corresponding delegate,
   * the delegates get a unique handle, i.e. an index to an array of delegates, which in
   * turn is unique among ALL sessions.
   *
   * @param clazz is the AbstractDbObject-class
   * @return the handle
   */
  public static synchronized int prepareRemoteDelegate (Class<?> clazz) {
    if (remoteClasses == null)  {
      remoteClasses = new Class<?>[16];    // start with a reasonable size
    }
    if (nextDelegateId >= remoteClasses.length) {
      Class<?>[] old = remoteClasses;
      remoteClasses = new Class<?>[old.length << 1];    // double size
      System.arraycopy(old, 0, remoteClasses, 0, old.length);
    }
    remoteClasses[nextDelegateId++] = clazz;
    return nextDelegateId;    // start at 1
  }


  /**
   * Gets the remote delegate by its id.
   *
   * @param delegateId is the handle for the delegate
   * @return the delegate for this session
   */
  public RemoteDelegate getRemoteDelegate (int delegateId)  {

    assertRemote();               // only allowed on remote connections!
    assertRequestedIfPooled();    // same as in attach(), but for remote session pools

    delegateId--; // starting from 0

    if (delegateId < 0 || delegateId >= remoteClasses.length) {
      throw new PersistenceException(this, "delegate handle out of range");
    }

    // enlarge if necessary
    if (delegates == null) {
      delegates = new RemoteDelegate[remoteClasses.length];
      Arrays.fill(delegates, null);
    }
    if (delegateId >= delegates.length) {
      RemoteDelegate[] old = delegates;
      delegates = new RemoteDelegate[remoteClasses.length];
      System.arraycopy(old, 0, delegates, 0, old.length);
      // set the rest to null
      for (int i=old.length; i < delegates.length; i++)  {
        delegates[i] = null;
      }
    }

    // check if delegate already fetched from RMI-server
    if (delegates[delegateId] == null) {
      // we need to prepare it
      assertOpen();
      try {
        RemoteDelegate delegate = resources.rses.getRemoteDelegate(remoteClasses[delegateId].getName());
        delegates[delegateId] = delegate;
        return delegate;
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    // already created
    return delegates[delegateId];
  }


  /**
   * Creates the remote delegate.
   *
   * @param className the classname
   * @return the delegate
   * @throws RemoteException if creating the delegate failed
   */
  public RemoteDelegate createRemoteDelegate(String className) throws RemoteException {
    assertOpen();
    return resources.rses.getRemoteDelegate(className);
  }


  /**
   * Checks whether objects are allowed to count modifications.
   * The default is true.
   *
   * @return true if objects are allowed to count modifications
   */
  public boolean isCountModificationAllowed() {
    return countModificationAllowed;
  }


  /**
   * Defines whether objects are allowed to count modifications.
   * Useful to turn off modification counting for a special (temporary) session doing
   * certain tasks that should not be counted.
   *
   * @param countModificationAllowed true if allowed, false if turned off
   *
   */
  public void setCountModificationAllowed(boolean countModificationAllowed) {
    assertOpen();
    if (isRemote()) {
      try {
        resources.rdel.setCountModificationAllowed(countModificationAllowed);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.countModificationAllowed = countModificationAllowed;
  }


  /**
   * Sets the modification allowed state.
   * Useful to turn off modlog for a special (temporary) session doing
   * certain tasks that should not be logged.
   * The state will be handed over to the remote session as well.
   *
   * @param logModificationAllowed true to allow, false to deny
   */
  public void setLogModificationAllowed(boolean logModificationAllowed) {
    assertOpen();
    if (isRemote()) {
      try {
        resources.rdel.setLogModificationAllowed(logModificationAllowed);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.logModificationAllowed = logModificationAllowed;
  }


  /**
   * Gets the state of logModificationAllowed.
   *
   * @return true if modification logging is allowed (default).
   */
  public boolean isLogModificationAllowed() {
    return logModificationAllowed;
  }




  /**
   * Sets the modification logging deferred state for the current transaction.<br>
   * In deferred mode the {@link ModificationLog}s are not written to the database.
   * Instead, they are kept in memory and can be processed later via
   * {@link #popModificationLogsOfTransaction()}.
   * The state will be handed over to the remote session as well.
   * <p>
   * Note: the deferred state will be reset to false after each begin/commit/rollback.
   * <p>
   * Important: if the deferred state is changed within a transaction and there are
   * already modifications made, i.e. modlogs were created, the deferred state will
   * *not* be changed! Furthermore, if the deferred state is changed to false.
   * the modlogs are removed from the database (but kept in memory).
   * As a consequence, the in-memory modlogs will always contain the whole transaction
   * or nothing. Only {@link #popModificationLogsOfTransaction()} removes the logs
   * collected so far or the end of the transaction.
   *
   * @param logModificationDeferred true to defer logs for the rest of current transaction, false to un-defer
   */
  public void setLogModificationDeferred(boolean logModificationDeferred) {
    assertOpen();
    if (isRemote()) {
      try {
        this.logModificationDeferred = resources.rdel.setLogModificationDeferred(logModificationDeferred);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      if (this.logModificationDeferred != logModificationDeferred &&
          isTxRunning() && modificationLogList != null && !modificationLogList.isEmpty()) {
        // deferred changed within transaction and modlogs already created
        if (logModificationDeferred) {
          // remove already persisted modlogs from database
          for (ModificationLog log: modificationLogList) {
            log.deleteObject();
            log.unmarkDeleted();
          }
        }
        else  {
          // don't turn off until end of transaction!
          return;
        }
      }
      this.logModificationDeferred = logModificationDeferred;
    }
  }


  /**
   * Gets the state for logModificationDeferred.
   *
   * @return true if modification logging is deferred. Default is not deferred.
   */
  public boolean isLogModificationDeferred() {
    return logModificationDeferred;
  }


  /**
   * Pushes a {@link ModificationLog} to the list of modlogs of the current transaction.
   * <p>
   * Notice: only allowed for local databases!
   *
   * @param log the modlog
   * @see #popModificationLogsOfTransaction()
   */
  public void pushModificationLogOfTransaction(ModificationLog log) {
    if (modificationLogList == null) {
      modificationLogList = new ArrayList<>();
    }
    modificationLogList.add(log);
  }


  /**
   * Returns the {@link ModificationLog}s of the current transaction.
   * Upon return the list of logs is cleared.
   * <p>
   * Works for local and remote databases.
   *
   * @return the logs, null = no logs
   * @see #pushModificationLogOfTransaction(ModificationLog)
   */
  public List<ModificationLog> popModificationLogsOfTransaction() {
    assertOpen();
    List<ModificationLog> list = modificationLogList;
    if (isRemote()) {
      try {
        list = resources.rdel.popModificationLogsOfTransaction();
        applyTo(list);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      modificationLogList = null;
    }
    return list;
  }


  /**
   * Gets the default {@link IdSource}.
   *
   * @return the idSource
   */
  public IdSource getDefaultIdSource() {
    return defaultIdSource;
  }


  /**
   * Set the default {@link IdSource}.<br>
   * This is the source that is used to generate unique object IDs
   * if classes did not configure their own source.
   *
   * @param idSource New value of property idSource.
   */
  public void setDefaultIdSource(IdSource idSource) {
    this.defaultIdSource = idSource;
  }



  /**
   * Returns the current transaction id from the last BEGIN modification log.
   * The tx-ID is only available if logModificationTx is true.
   *
   * @return the tx ID, 0 if no transaction is pending.
   */
  public long getLogModificationTxId() {
    assertOpen();
    if (isRemote()) {
      try {
        return resources.rdel.getLogModificationTxId();
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      return logModificationTxId;
    }
  }


  /**
   * Sets the transaction id.
   * Normally the tx-ID is derived from the id of the BEGIN-modlog,
   * so it's not necessary to invoke this method from an application.
   *
   * @param logModificationTxId the transaction ID
   */
  public void setLogModificationTxId(long logModificationTxId) {
    assertOpen();
    if (isRemote()) {
      try {
        resources.rdel.setLogModificationTxId(logModificationTxId);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {
      this.logModificationTxId = logModificationTxId;
    }
  }


  /**
   * Gets the value of logModificationTx.
   *
   * @return true if transaction begin/end is logged in the modification log, false if not (default)
   */
  public boolean isLogModificationTxEnabled() {
    return logModificationTxEnabled;
  }


  /**
   * Turn transaction-logging on or off. Default is off.<br>
   * With enabled logging the transactions are logged in the {@link ModificationLog}
   * as well.
   *
   * @param logModificationTxEnabled true to turn on transaction logging.
   */
  public void setLogModificationTxEnabled(boolean logModificationTxEnabled) {
    assertOpen();
    if (isRemote()) {
      try {
        resources.rdel.setLogModificationTxEnabled(logModificationTxEnabled);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.logModificationTxEnabled = logModificationTxEnabled;
  }


  /**
   * Gets the database backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backendInfo.getBackend();
  }

}
