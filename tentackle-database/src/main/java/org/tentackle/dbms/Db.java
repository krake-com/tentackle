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



package org.tentackle.dbms;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URI;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.tentackle.common.Constants;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.FileHelper;
import org.tentackle.daemon.Scavenger;
import org.tentackle.dbms.rmi.DbRemoteDelegate;
import org.tentackle.dbms.rmi.RemoteDbConnection;
import org.tentackle.dbms.rmi.RemoteDbSession;
import org.tentackle.dbms.rmi.RemoteDelegate;
import org.tentackle.io.ReconnectionPolicy;
import org.tentackle.io.Reconnector;
import org.tentackle.io.SocketFactoryFactory;
import org.tentackle.io.SocketFactoryType;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.reflect.ReflectionHelper;
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
import org.tentackle.session.TransactionEnvelope;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendInfo;


/**
 * A logical database connection.
 * <p>
 * Each thread must use its own Db. If threads must share the same db, access to the Db
 * must be synchronized at the application-level!
 * <p>
 * Db connections are an abstraction for a connection between a client
 * and a server, whereas "server" is not necessarily a database server,
 * because Db connections can be either local or remote.<br>
 * A local Db talks to a database backend via a {@link ConnectionManager}
 * which is responsible for the physical JDBC-connection. However, there
 * is no 1:1 relationship between a Db and a physical connection. This is up
 * to the connection manager.
 * Local Db connections are used in client applications running in 2-tier mode
 * or in application servers.<br>
 * Remote Db connections are connected to a Tentackle application server
 * via RMI. Client applications running in 3-tier mode (more precise n-tier, with n &ge; 3)
 * use remote connections.<br>
 * With the abstraction of a logical Db connection, Tentackle applications
 * are able to run both in 2- or n-tier mode without a single modification
 * to the source code. It's just a small change in a config file.
 * <p>
 * The configuration is achieved by a property file, which is located
 * by the {@link SessionInfo} passed to the Db.
 * <p>
 * Local connections can be either a DataSource bound via JNDI or a direct
 * JDBC connection. JNDI is used in application servers, for example running
 * a JRuby on Rails application from within Glassfish (see tentackle-web).<br>
 * JDBC connections are for standalone 2-tier applications or Tentackle application servers.
 * <br>
 * For local connections via JDBC the file contains at least the following properties:
 *
 * <pre>
 * driver=jdbc-driver
 * url=jdbc-url
 *
 * Example:
 * driver=org.postgresql.Driver
 * url=jdbc:postgresql://gonzo.krake.local/erp
 * </pre>
 *
 * Local connections via JNDI need only the url. The url starts with
 * "<tt>jndi:</tt>" and is followed by the JNDI-name. The optional database backend type is
 * only necessary if it cannot be determined from the connection's metadata.
 *
 * <pre>
 * url=jndi:name[:database-type]
 *
 * Example:
 * url=jndi:jdbc/erpPool
 * </pre>
 *
 * For remote connections only one line is required at minimum:
 *
 * <pre>
 * url=rmi://hostname[:port]/service
 *
 * Example:
 * url=rmi://gonzo.krake.local:28004/ErpServer
 * </pre>
 *
 * The port is optional and defaults to 1099 (the default RMI registry).
 * <p>
 * Optionally, the following properties can be defined:
 * <p>
 * For local connections via JDBC:
 * <ul>
 *  <li>
 *  A predefined user and password
 *  <pre>
 *  user=fixed-user
 *  password=fixed-password
 *  </pre>
 *  </li>
 *  <li>
 *  By default the credentials used to connect to the backend are the same as the ones in the session info.
 *  However, if a technical user must be used, the configuration for the backend may be defined in another
 *  property file.
 *  <pre>
 *  backendinfo=property-file
 *  </pre>
 *  </li>
 * </ul>
 * <p>
 * For local connections via JDBC or JNDI:
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
 * For remote connections, the properties (session info) will be sent to the server.
 * This can be used to set some application specific options or to tune the connection.
 *
 * @author harald
 */
public class Db implements Session, Cloneable {



  /**
   * property key for IdSource.
   */
  public static final String PROPERTY_IDSOURCE = "idsource";

  /**
   * The property key for the socket factory.
   */
  public static final String SOCKET_FACTORY = "socketfactory";

  /**
   * The property key for the SSL cipher suites.
   */
  public static final String CIPHER_SUITES = "ciphersuites";

  /**
   * The property key for the SSL protocols.
   */
  public static final String PROTOCOLS = "protocols";


  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(Db.class);



  /**
   * Global close handlers.
   */
  private static final Set<SessionCloseHandler> GLOBAL_CLOSE_HANDLERS =
                       Collections.newSetFromMap(new ConcurrentHashMap<>());

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
   * We keep an internal set of all open Db-sessions via WeakReferences, so the
   * Db-session still will be finalized when the Db isn't used anymore.
   * The set of Db-sessions is used to enhance the diagnostic utilities.
   */
  private static final Set<WeakReference<Db>> DB_SET =
          Collections.newSetFromMap(new ConcurrentHashMap<>());


  /**
   * Registers this Db.
   */
  private void registerDb() {
    DB_SET.add(new WeakReference<>(this));
  }


  /**
   * Unregisters this Db.
   * <p>
   * Also cleans up the set by removing unreferenced or closed db sessions.
   */
  private void unregisterDb() {
    for (Iterator<WeakReference<Db>> iter = DB_SET.iterator(); iter.hasNext(); ) {
      WeakReference<Db> ref = iter.next();
      Db refDb = ref.get();
      // if closed or unreferenced or the db to remove
      if (refDb == null || !refDb.isOpen() || refDb == this) {   // == is okay here
        iter.remove();
      }
    }
  }


  /**
   * Gets a list of all currently open Db sessions.
   *
   * @return the list of open Db sessions
   */
  public static Collection<Db> getAllOpenDb() {
    List<Db> dbList = new ArrayList<>();
    for (Iterator<WeakReference<Db>> iter = DB_SET.iterator(); iter.hasNext(); ) {
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


  private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(); // global instance counter

  // for RMI remote connections (all Db)
  private static Class<?>[]         remoteClasses;        // the classes Objects for the delegates provide service for
  private static int                nextDelegateId;       // next handle per class

  private int instanceNumber;                             // each Db gets a unique instance number
  private BackendInfo backendInfo;                        // the backend information
  private String idConfig;                                // configuration of IdSource
  private final ConnectionManager conMgr;                 // connection manager for local connections
  private int conId;                                      // local connection ID
  private int groupConId;                                 // ID of the connection group, 0 = none
  private ManagedConnection con;                          // connection if attached, null = detached
  private SessionPool dbPool;                             // != null if this Db is managed by a SessionPool
  private int poolId;                                     // the poolid if dbPool != null
  private SessionInfo sessionInfo;                        // user information
  private Db clonedFromDb;                                // the original db if this is a cloned one
  private volatile boolean autoCommit;                    // true if autocommit on, else false
  private boolean countModificationAllowed = true;        // allow modification count
  private boolean logModificationAllowed = true;          // allow modification log
  private final Set<SessionCloseHandler> closeHandlers = new HashSet<>();  // close handlers
  private boolean readOnly;                               // true if database is readonly
  private IdSource defaultIdSource;                       // default ID-Source for db-connection (if not overridden in derivates)
  private int fetchSize;                                  // default fetchsize, 0 = drivers default
  private int maxRows;                                    // maximum rows per resultset, 0 = no limit
  private boolean logModificationTxEnabled;               // true if log transaction begin/commit in modification log
  private long logModificationTxId;                       // transaction id. !=0 if 'commit' is pending in modification log
  private boolean logModificationDeferred;                // true if log to memory rather than dbms
  private List<ModificationLog> modificationLogList;      // in-memory modification-log for current transaction
  private DbTransaction transaction;                      // the running transaction
  private int immediatelyRolledBack;                      // txLevel > 0 if a transaction has been rolled back by rollbackImmediately (only for local Db)
  private int immediatelyCommitted;                       // txLevel > 0 if a transaction has been committed by commitImmediately (only for local Db)
  private volatile boolean alive;                         // true = connection is still in use, false = connection has timed out
  private long keepAliveInterval;                         // the auto keep alive interval in ms, 0 = no keep alive
  private boolean crashed;                                // true = db is closed because it is treated as crashed
  private volatile Thread ownerThread;                    // the exclusive owner thread
  private WeakReference<SessionTaskDispatcher> dispatcherRef;  // task dispatcher for asynchroneous tasks
  private Map<String,Serializable> applicationProperties; // application-specific properties
  private ReconnectionPolicy<Db> reconnectionPolicy;      // reconnection policy, null if none (default)

  // for RMI remote connections
  private RMIClientSocketFactory csf;                     // client socket factory, null if system default
  private RemoteDbConnection rcon;                        // != null if remote connection established to RMI-server
  private RemoteDbSession rses;                           // remote database session if logged in to RMI-server
  private DbRemoteDelegate rdel;                          // remote database delegate
  private RemoteDelegate[] delegates;                     // the delegates per session




  /**
   * Creates an instance of a logical db connection.
   *
   * @param conMgr the connection manager to use for this db, unused for local connections
   * @param sessionInfo user information
   */
  public Db(ConnectionManager conMgr, SessionInfo sessionInfo) {

    instanceNumber = INSTANCE_COUNTER.incrementAndGet();

    setSessionInfo(sessionInfo);

    // establish connection to the database server
    try {

      // load configuration settings
      Properties sessionProperties = sessionInfo.getProperties();
      Properties backendProperties = sessionProperties;   // backend properties defaults to session properties
      String technicalBackendInfoName = sessionInfo.getProperties().getProperty(Constants.BACKEND_TECHNICAL_INFO);
      if (technicalBackendInfoName != null) {
        // programmatically predefined (usually technical) backend info
        try {
          backendProperties = FileHelper.loadProperties(technicalBackendInfoName);
        }
        catch (FileNotFoundException e1) {
          throw new PersistenceException("technical backend properties '" + technicalBackendInfoName + "' not found");
        }
      }

      try {
        backendInfo = new BackendInfo(backendProperties);
      }
      catch (BackendException bex) {
        throw new PersistenceException(this, "invalid configuration in " + sessionInfo.getPropertiesName(), bex);
      }

      if (backendInfo.isRemote()) {
        this.conMgr = null;                         // no connection manager for remote connections
        SocketFactoryType factoryType = SocketFactoryType.parse(backendProperties.getProperty(SOCKET_FACTORY));
        csf = SocketFactoryFactory.getInstance().createClientSocketFactory(null, factoryType);
        LOGGER.info("using client socket factory {0}", csf);
      }
      else  {
        if (conMgr == null) {
          throw new PersistenceException("connection manager required for local connections");
        }
        this.conMgr = conMgr;
      }

      String val = sessionProperties.getProperty(PROPERTY_IDSOURCE);
      if (val != null) {
        idConfig = val;
      }
    }
    catch (PersistenceException rex) {
      throw rex;
    }
    catch (Exception ex) {
      throw new PersistenceException(this, "database configuration failed", ex);
    }
  }


  /**
   * Gets the unique instance number of this Db.
   *
   * @return the instance number
   */
  @Override
  public final int getInstanceNumber() {
    return instanceNumber;
  }


  /**
   * Gets the db name.<br>
   * Consists of the instance number, the connection id and the connection group id.
   *
   * @return the db name
   */
  @Override
  public String getName() {
    StringBuilder buf = new StringBuilder();
    buf.append("Db");
    buf.append(instanceNumber);
    buf.append('c');
    buf.append(conId);
    if (groupConId > 0) {
      buf.append('g');
      buf.append(groupConId);
    }
    return  buf.toString();
  }


  @Override
  public String toString()  {
    StringBuilder buf = new StringBuilder(getName());
    if (conMgr != null || dbPool != null) {
      buf.append('{');
      if (conMgr != null) {
        buf.append(conMgr.getName());
      }
      if (conMgr != null && dbPool != null) {
        buf.append('/');
      }
      if (dbPool != null) {
        buf.append(dbPool.getName());
      }
      buf.append('}');
    }
    buf.append(':');
    buf.append(backendInfo);
    if (con != null) {
      buf.append('[');
      buf.append(con.getName());
      buf.append(']');
    }
    return buf.toString();
  }



  /**
   * Compares two db instances.<br>
   * We simply use the unique instanceNumber.<br>
   * Because the instanceNumber is unique, we don't need to override equals/hash.
   * In fact, Db must not override equals because it may be used in WeakHashMaps, where
   * the object reference counts, for example.
   *
   * @param session the session to compare this db with
   * @return  a negative integer, zero, or a positive integer as this object
   *          is less than, equal to, or greater than the specified object.
   */
  @Override
  public int compareTo(Session session) {
    return instanceNumber - ((Db) session).instanceNumber;
  }


  /**
   * Gets the connection manager for this Db.
   *
   * @return the connection manager
   */
  @Override
  public ConnectionManager getSessionManager() {
    return conMgr;
  }


  /**
   * Sets the pool manager.
   * The method is invoked from a SessionPool when the Db is created.
   *
   * @param sessionPool the db pool, null = not pooled
   */
  public void setPool(SessionPool sessionPool) {
    this.dbPool = sessionPool;
  }

  /**
   * Gets the pool manager.
   *
   * @return the db pool, null = not pooled
   */
  @Override
  public SessionPool getPool() {
    return dbPool;
  }


  /**
   * Checks whether this Db is pooled.
   *
   * @return true if pooled, false if not pooled
   */
  @Override
  public boolean isPooled() {
    return dbPool != null;
  }


  /**
   * Sets the pool id.
   * The method is invoked from a SessionPool when the Db is used in a pool.
   *
   * @param poolId the ID given by the pool (&gt; 0), 0 = not used (free Db), -1 if removed from pool
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
   * Returns whether Db is readonly.
   *
   * @return true if readonly.
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Sets the db to readonly.
   * <p>
   * If a database is readonly, no updates, deletes or inserts
   * are possible. Any attempt to do so results in a persistence exception.
   *
   * @param readOnly true if readonly
   */
  public void setReadOnly(boolean readOnly) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setReadOnly(readOnly);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.readOnly = readOnly;
  }


  /**
   * Gets the remote connection object.
   *
   * @return the connection object, null if local
   */
  public RemoteDbConnection getRemoteConnection() {
    return rcon;
  }


  /**
   * Gets the remote session object.
   * @return the session, null if local
   */
  public RemoteDbSession getRemoteDbSession() {
    return rses;
  }


  /**
   * Asserts db is not used by another thread than the ownerthread.
   */
  public void assertOwnerThread() {
    Thread ownrThread = getOwnerThread();
    if (ownrThread != null) {
      Thread currentThread = Thread.currentThread();
      if (ownrThread != currentThread && !Scavenger.isScavenger(currentThread)) {
        throw new PersistenceException(this,
                                       "illegal attempt to use Db owned by " + ownrThread +
                                       " by " + currentThread.getName());
      }
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
                  "! application still holding a reference to this Db after returning it to the pool!");
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
   * asserts that this is not a remote connection
   */
  public void assertNotRemote() {
    if (isRemote()) {
      throw new PersistenceException(this, "operation not allowed for remote db connections");
    }
  }

  /**
   * asserts that this is a remote connection
   */
  public void assertRemote() {
    if (!isRemote()) {
      throw new PersistenceException(this, "operation not allowed for local db connections");
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
   * Checks whether the db connection is still in use.
   * Whenever a {@link StatementWrapper} or {@link PreparedStatementWrapper} is used
   * (i.e executeQuery or executeUpdate), the db connection is set to be alive.
   * Some other thread may clear this flag regulary and check whether it has
   * been set in the meantime.
   *
   * @return true if connection still in use, false if not used since last setAlive(false).
   */
  @Override
  public boolean isAlive() {
    assertOpen();
    if (isRemote()) {
      try {
        return rdel.isAlive();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    return alive;
  }

  /**
   * Sets the db connection's alive state.
   *
   * @param alive is true to signal it's alive, false to clear
   */
  @Override
  public void setAlive(boolean alive) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setAlive(alive);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      this.alive = alive;
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
   * The dispatcher can be used to submit asynchroneous tasks in a serial manner.
   * The returned dispatcher is configured to shutdown if idle for a given timeout.
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


  /**
   * Checks for a dead communications link.<br>
   * If the link is down, for example the database server closed it,
   * the connection is marked dead.
   *
   * @param ex the sql exception
   * @return true if connection is dead
   * @see ManagedConnection#setDead(boolean)
   */
  public boolean checkForDeadLink(SQLException ex) {
    if (getBackend().isCommunicationLinkException(ex)) {
      // some severe comlink error, probably closed by server
      ManagedConnection mc = getConnection();
      if (mc != null) {
        // if connection still attached: mark it dead!
        mc.setDead(true);
        LOGGER.severe("managed connection " + mc + " marked dead", ex);
        return true;
      }
    }
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Serializable> T setProperty(String key, T value) {
    if (isRemote()) {
      try {
        return rdel.setProperty(key, value);
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
        return rdel.getProperty(key);
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
   * Handles a connect exception (in open or clone).
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




  /**
   * Open a database connection.<br>
   * If the login failes due to wrong passwords
   * or denied access by the application server,
   * the method {@link #handleConnectException} is invoked.
   */
  @Override
  public void open() {

    if (isOpen()) {
      throw new PersistenceException(this, "session is already open");
    }

    if (getPoolId() == -1) {
      throw new PersistenceException(this, "db was closed and removed from pool " + getPool() +", cannot be re-opened");
    }

    try {

      if (isRemote()) {

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


        /**
         * Check that server matches expected version.
         * The server checks the client's version in rcon.login() below.
         * So, we have a double check.
         */
        sessionInfo.checkServerVersionInfo(rcon.getServerVersionInfo());

        // get session
        rses = rcon.login(sessionInfo);    // throws exception if login denied

        // get delegate
        rdel = rses.getDbRemoteDelegate();

        // get the remote connection id
        conId = rdel.getSessionId();
      }
      else  {
        // direct connection to database
        conId = conMgr.login(this);
      }

      // fresh connections are always in autoCommit mode
      autoCommit = true;
      transaction = null;

      setupIdSource();
      if (!sessionInfo.isImmutable()) {
        sessionInfo.setSince(System.currentTimeMillis());
      }

      alive = true;   // all db start alive!

      if (!isCloned()) {
        LOGGER.info("connection {0} established", this);
      }

      registerDb();
    }

    // warning causes
    catch (Exception e)  {
      throw handleConnectException(e);
    }
  }


  /**
   * Re-opens the session.
   */
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
    unregisterDb();
    open();
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


  /**
   * Clears all passwords (stored in char[]-arrays) so
   * that they are no more visible in memory.
   */
  public void clearPassword() {
    sessionInfo.clearPassword();
    backendInfo.clearPassword();
  }


  /**
   * Registers a close handler.
   *
   * @param closeHandler the handler
   * @return true if added, false if already registered
   */
  public boolean registerCloseHandler(SessionCloseHandler closeHandler) {
    return closeHandlers.add(closeHandler);
  }


  /**
   * Unregisters a close handler.
   *
   * @param closeHandler the handler
   * @return true if removed, false if not registered
   */
  public boolean unregisterCloseHandler(SessionCloseHandler closeHandler) {
    return closeHandlers.remove(closeHandler);
  }


  @Override
  public void close() {
    if (isOpen()) {
      if (Scavenger.isScavenger(Thread.currentThread())) {
        crashed = true;
      }
      if (crashed) {
        // non-blocking close to cleanup crashed session
        closeImpl();
      }
      else {
        // regular close
        synchronized (this) {
          closeImpl();
        }
      }
    }
  }

  private void closeImpl() {
    try {
      for (SessionCloseHandler closeHandler : closeHandlers) {
        try {
          closeHandler.beforeClose(this);
        }
        catch (Exception ex) {
          LOGGER.warning("closehandler.beforeClose failed for " + this, ex);
        }
      }
      for (SessionCloseHandler closeHandler : GLOBAL_CLOSE_HANDLERS) {
        try {
          closeHandler.beforeClose(this);
        }
        catch (Exception ex) {
          LOGGER.warning("global closehandler.beforeClose failed for " + this, ex);
        }
      }

      if (isRemote()) {
        if (rcon != null && rses != null) {
          // don't rcon.logout if crashed, because connection may block.
          // remote side must timeout anyway. (or has already timed out)
          if (!crashed) {
            rcon.logout(rses);
          }
          LOGGER.info("remote db {0} closed", this);
        }
      }
      else {
        if (conId > 0) {
          if (con != null) {
            try {
              con.closePreparedStatements(true);   // cleanup all pending statements
            }
            catch (Exception ex) {
              LOGGER.warning("closing prepared statements failed for " + this, ex);
            }
          }
          conMgr.logout(this);
          LOGGER.info("db {0} closed", this);
        }
      }

      for (SessionCloseHandler closeHandler : closeHandlers) {
        try {
          closeHandler.afterClose(this);
        }
        catch (Exception ex) {
          LOGGER.warning("closehandler.afterClose failed for " + this, ex);
        }
      }
      for (SessionCloseHandler closeHandler : GLOBAL_CLOSE_HANDLERS) {
        try {
          closeHandler.afterClose(this);
        }
        catch (Exception ex) {
          LOGGER.warning("global closehandler.afterClose failed for " + this, ex);
        }
      }
    }
    catch (Exception e) {
      throw new PersistenceException(this, "closing db failed", e);
    }
    finally {
      // whatever happened: make it unusable
      clearMembers();   // clear members for re-open
      unregisterDb();
    }
  }


  /**
   * Cleanup if unreferenced connection is still open.
   * <p>
   * {@inheritDoc}
   */
  @Override
  protected void finalize() throws Throwable {
    try {
      if (isOpen()) {
        LOGGER.warning("closing unreferenced open db: " + this);
        closeImpl();
      }
    }
    catch (Exception ex) {
       try {
        LOGGER.severe("closing unreferenced db '" + this + "' failed in finalizer");
      }
      catch (Exception ex2) {
        // don't stop finalization if just the logging failed
      }
    }
    finally {
      super.finalize();
    }
  }



  /**
   * Sets the crash flag.<br>
   * The db may be marked as crashed to reduce logging of succeeding errors.
   *
   * @param crashed the crash flag
   */
  public void setCrashed(boolean crashed) {
    this.crashed = crashed;
  }


  /**
   * Gets the crash flag.<br>
   * May be invoked from any thread.
   *
   * @return true if Db is marked as crashed
   */
  public boolean isCrashed() {
    return crashed;
  }


  /**
   * Gets the connection state.
   *
   * @return true if db is open, else false
   */
  @Override
  public boolean isOpen() {
    if (isRemote()) {
      return rdel != null;
    }
    else  {
      return conId > 0;
    }
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

  /**
   * Gets the pending txLevel after an immediate commit.
   *
   * @return &gt; 0 if there was an immediate commit
   */
  public int getImmediateCommitTxLevel() {
    return immediatelyCommitted;
  }


  /**
   * Sets the optional transaction object.<br>
   * By default, whenever a transaction is initiated by a persistence operation of
   * a {@link AbstractDbObject}, that object becomes the "parent" of the
   * transaction.<br>
   * The {@code txObject} is mainly used for logging and enhanced auditing (partial history) during transactions.<br>
   * The {@code txObject} is cleared at the end of the transaction.
   *
   * @param txObject the transaction object, null to clear
   */
  public void setTxObject(AbstractDbObject<?> txObject) {
    assertTxRunning();
    transaction.setTxObject(txObject);
  }


  /**
   * Gets the optional transaction object.
   *
   * @return the transaction object, null if none
   */
  public AbstractDbObject<?> getTxObject() {
    assertTxRunning();
    return transaction.getTxObject();
  }


  @Override
  public <T> T transaction(TransactionEnvelope<T> txe, String txName) {
    if (txName == null) {
      Method method = txe.getClass().getEnclosingMethod();
      if (method != null) {
        // inner class
        txName = ReflectionHelper.getClassBaseName(method.getDeclaringClass()) + "#" + method.getName();
      }
      else {
        // lambdas don't provide an enclosing method, because they are not inner classes.
        // can't figure out method name -> just use the classname (better than nothing...)
        txName = ReflectionHelper.getClassBaseName(txe.getClass());
        int ndx = txName.indexOf('$');    // cut useless info $$Lambda$0/123456789
        if (ndx > 0) {
          txName = txName.substring(0, ndx);
        }
      }
    }
    long txVoucher = begin(txName);
    try {
      T rv = txe.run();
      commit(txVoucher);
      return rv;
    }
    catch (RuntimeException ex) {
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
  public <T> T transaction(TransactionEnvelope<T> txe) {
    return transaction(txe, null);
  }



  /**
   * Starts a transaction.<br>
   * Does nothing if a transaction is already running!
   *
   * @param txName is the optional transaction name, null if none
   *
   * @return the transaction voucher (!= 0) if a new transaction was begun, else 0
   */
  @Override
  public long begin(String txName)  {
    return begin(txName, false);
  }

  @Override
  public long begin() {
    return begin(null);
  }


  /**
   * Starts a transaction.<br>
   * Just increments the transaction level if a transaction is already running.
   *
   * @param txName is the optional transaction name, null if none
   * @param fromRemote true if invocation from remote client via {@link DbRemoteDelegate#begin}
   *
   * @return the transaction voucher (!= 0) if a new transaction was begun, else 0
   */
  private long begin(String txName, boolean fromRemote)  {

    assertOpen();
    assertOwnerThread();

    long txVoucher = 0;

    if (isRemote()) {
      if (autoCommit) {
        try {
          txVoucher = rdel.begin(txName);
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
      // as a result: the nesting level at the server side is max. 1
    }
    else {

      alive = true;

      immediatelyRolledBack = 0;  // the first begin clears all pending immediate rollbacks() ...
      immediatelyCommitted  = 0;  // ... and commits()

      if (setAutoCommit(false)) {
        // new transaction
        assertNoTxRunning();
        modificationLogList = null;
        transaction = new DbTransaction(this, txName, fromRemote);
        txVoucher = transaction.getTxVoucher();
      }
      else  {
        // already running transaction
        assertTxRunning();
        transaction.incrementTxLevel();    // just increment the nesting level
      }
    }

    LOGGER.fine("{0} {1}", transaction, txVoucher != 0 ? " begun" : " already running");

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
        return rdel.setSavepoint();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = con.setSavepoint();
      try {
        SavepointHandle handle = new SavepointHandle(savepoint.getSavepointId());
        transaction.addSavepoint(handle, savepoint);
        return handle;
      }
      catch (SQLException sqx) {
        throw new PersistenceException(this, "setting unnamed savepoint failed", sqx);
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
        return rdel.setSavepoint(name);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = con.setSavepoint(name);
      try {
        SavepointHandle handle = new SavepointHandle(savepoint.getSavepointName());
        transaction.addSavepoint(handle, savepoint);
        return handle;
      }
      catch (SQLException sqx) {
        throw new PersistenceException(this, "setting named savepoint '" + name + "' failed", sqx);
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
        rdel.rollback(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = transaction.getSavepoint(handle);
      if (savepoint == null) {
        throw new PersistenceException(this, "no such savepoint to rollback: " + handle);
      }
      con.rollback(savepoint);
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
        rdel.rollback(handle);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else {
      assertTxRunning();
      Savepoint savepoint = transaction.getSavepoint(handle);
      if (savepoint == null) {
        throw new PersistenceException(this, "no such savepoint to release: " + handle);
      }
      con.releaseSavepoint(savepoint);
    }
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
   * Creates a begin modification log if necessary.
   */
  public void logBeginTx() {
    if (logModificationTxEnabled && logModificationTxId == 0 && isTxRunning()) {
      ModificationLog log = ModificationLogFactory.getInstance().createModificationLog(this, ModificationLog.BEGIN);
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
        ModificationLogFactory.getInstance().createModificationLog(this, ModificationLog.COMMIT).saveObject();
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
   * @see #isPersistenceOperationAllowed(AbstractDbObject, char)
   */
  public DbTransactionHandle registerPersistenceVisitor(PersistenceVisitor visitor) {
    if (isRemote()) {
      try {
        return rdel.registerPersistenceVisitor(visitor);
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
   * Unegisters a {@link PersistenceVisitor}.
   *
   * @param handle the visitor's handle to unregister
   * @return the visitor if removed, null if not registered
   */
  public PersistenceVisitor unregisterPersistenceVisitor(DbTransactionHandle handle) {
    if (isRemote()) {
      try {
        return rdel.unregisterPersistenceVisitor(handle);
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
        return rdel.getPersistenceVisitors();
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
  public boolean isPersistenceOperationAllowed(AbstractDbObject<?> object, char modType) {
    return transaction == null || transaction.isPersistenceOperationAllowed(object, modType);
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
        return rdel.registerCommitTxRunnable(commitRunnable);
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
        return rdel.unregisterCommitTxRunnable(handle);
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
        return rdel.getCommitTxRunnables();
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
        return rdel.registerRollbackTxRunnable(rollbackRunnable);
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
        return rdel.unregisterRollbackTxRunnable(handle);
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
        return rdel.getRollbackTxRunnables();
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
   * Commits a transaction if the corresponding begin() has started it.
   * The corresponding begin() is determined by the txVoucher parameter.
   * If it fits to the issued voucher the tx will be committed.
   * If it is 0, nothing will happen.
   * In all other cases an exception will be thrown.
   *
   * @param txVoucher the transaction voucher, 0 if do nothing (nested tx)
   *
   * @return true if committed, false if nested tx
   */
  @Override
  public boolean commit(long txVoucher)  {

    assertOpen();
    assertOwnerThread();

    boolean committed = false;

    if (isRemote()) {
      if (txVoucher != 0) {
        try {
          if (autoCommit) {
            throw new PersistenceException(this, "no client transaction running");
          }
          committed = rdel.commit(txVoucher);
          if (committed) {
            autoCommit = true;   // now outside tx again
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

      // allow pending rollbacks after commitImmediately
      if (immediatelyCommitted > 0) {
        assertNoTxRunning();
        immediatelyCommitted--;
        if (immediatelyCommitted < 0) {
          LOGGER.warning(this + ": too many commits after commitImmediately, ignored");
        }
        return true;
      }

      assertTxRunning();

      if (txVoucher != 0) {

        if (!autoCommit) {
          // within a transaction: commit!
          logCommitTx();
          if (transaction.isTxLevelValid() && transaction.getTxLevel() != 1) {
            LOGGER.warning(this + ": txLevel=" + transaction.getTxLevel() + ", should be 1");
          }
          transaction.invokeCommitTxRunnables();
          setAutoCommit(true);    // according to the specs: this will commit!
          logModificationDeferred = false;
          transaction = null;
          modificationLogList = null;
          committed = true;
        }
        else  {
          throw new PersistenceException(this, "transaction ended unexpectedly before commit (valid voucher)");
        }
      }
      else {
        // no voucher, no change (this is ok)
        transaction.decrementTxLevel();   // just decrement the tx level
      }
    }

    LOGGER.fine("{0} {1}", transaction, committed ? " committed" : " nesting level decremented");

    return committed;
  }



  /**
   * Commits the current transaction, if pending.
   * <p>
   * Only applicable to local sessions.
   * <p>
   * Applications should use {@link #commit(long)}.
   * Invocations of this method will be logged as a warning.
   *
   * @return true if committed
   */
  public boolean commitImmediately() {

    assertNotRemote();

    if (transaction != null) {
      int currentTxLevel = transaction.getTxLevel();
      immediatelyCommitted = 0;
      immediatelyRolledBack = 0;
      transaction.invalidateTxLevel();    // avoid misleading warnings
      if (!commit(transaction.getTxVoucher())) {
        throw new PersistenceException(this, transaction + " not committed despite valid voucher");
      }
      transaction = null;
      LOGGER.warning("*** immediate commit for " + this + " ***");
      if (!isRemote()) {
        // keep the txLevel for pending commit()s, if any will arrive from the application
        immediatelyCommitted = currentTxLevel;
      }
      return true;
    }
    else  {
      return false;
    }
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
   * Rolls back a transaction if the corresponding begin() has started it.
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
      if (txVoucher != 0) {
        try {
          if (autoCommit) {
            throw new PersistenceException(this, "no client transaction running");
          }
          rolledBack = rdel.rollback(txVoucher, withLog);
          if (rolledBack) {
            autoCommit = true;   // now outside tx again
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

      // allow pending rollbacks after rollbackImmediately
      if (immediatelyRolledBack > 0) {
        assertNoTxRunning();
        immediatelyRolledBack--;
        if (immediatelyRolledBack < 0) {
          LOGGER.warning(this + ": too many rollbacks after rollbackImmediately, ignored");
        }
        LOGGER.fine("pending immediate rollback counter decremented -> no physical rollback");
        return true;
      }

      if (transaction != null) {

        if (txVoucher != 0) {

          // begin() started a new tx
          if (!autoCommit) {
            // within a transaction
            if (transaction.isTxLevelValid() && transaction.getTxLevel() != 1) {
              LOGGER.warning(this + ": txLevel=" + transaction.getTxLevel() + ", should be 1");
            }
            transaction.invokeRollbackTxRunnables();
            if (con != null) {
              con.rollback(withLog);  // avoid a commit ...
              setAutoCommit(true);    // ... in setAutoCommit
            }
            else {
              LOGGER.warning("rollback too late: connection already detached");
              autoCommit = true;
            }
            LOGGER.fine("{0} rolled back", transaction);
            logModificationDeferred = false;
            modificationLogList = null;
            logModificationTxId = 0;
            transaction = null;
            rolledBack = true;
          }
          else {
            throw new PersistenceException(this, "transaction ended unexpectedly before rollback (valid voucher)");
          }
        }
        else {
          // no voucher, no change (this is ok)
          transaction.decrementTxLevel();
          LOGGER.fine("{0} nesting level decremented -> no physical rollback", transaction);
        }
      }
      else {
        LOGGER.fine("no transaction running -> no physical rollback");
      }
    }

    return rolledBack;
  }


  /**
   * Rolls back the current transaction, if pending.<br>
   * Used to cleanup in case of an exception or alike.
   * <p>
   * Only applicable to local sessions.
   * <p>
   * Applications should use {@link #rollback(long)}.<br>
   * Invocations of this method will be logged as a warning.
   *
   * @param cause the cause of the rollback, may be null
   * @return true if rolled back
   */
  public boolean rollbackImmediately(Throwable cause) {

    assertNotRemote();

    try {
      // cancel all pending statements (usually max. 1)
      if (con != null) {
        con.cancelRunningStatements();
      }

      if (transaction != null) {
        LOGGER.warning("*** immediate rollback for {0} ***", this);
        int currentTxLevel = transaction.getTxLevel();
        immediatelyCommitted = 0;
        immediatelyRolledBack = 0;
        transaction.invalidateTxLevel();    // avoid misleading warnings
        // log statements only if the root-cause is a persistence exception
        boolean withLog = ExceptionHelper.extractException(PersistenceException.class, true, cause) != null;
        if (!rollback(transaction.getTxVoucher(), withLog)) {
          throw new PersistenceException(this, transaction + " not rolled back despite valid voucher");
        }
        transaction = null;
        // remember the txLevel for pending rollback()s, if any will arive from the application
        immediatelyRolledBack = currentTxLevel;
        forceDetached();
        return true;
      }
      else  {
        // not within a transaction: cleanup forced
        forceDetached();
        return false;
      }
    }
    catch (RuntimeException rex) {
      if (con != null && !con.isClosed()) {
        // check low level transaction state
        boolean connectionInTransaction;
        try {
          connectionInTransaction = con.getConnection().getAutoCommit();
        }
        catch (SQLException ex) {
          LOGGER.warning("cannot determine transaction state of " + con + ": assume transaction still running", ex);
          connectionInTransaction = true;
        }
        if (connectionInTransaction) {
          /**
           * If the physical connection is still running a transaction,
           * we need a low-level rollback and mark the connection dead
           * to avoid further use by the application.
           */
          LOGGER.warning(con + " still in transaction: performing low-level rollback");
          try {
            con.getConnection().rollback();
            con.getConnection().setAutoCommit(true);
          }
          catch (SQLException ex) {
            LOGGER.warning("low-level connection rollback failed for " + con, ex);
          }
          try {
            // this may yield some additional information
            con.logAndClearWarnings();
          }
          catch (PersistenceException ex) {
            LOGGER.warning("clear warnings failed for " + con, ex);
          }
        }
        con.setDead(true);    // don't use this connection anymore!
        LOGGER.warning("connection " + con + " marked dead");
      }
      handleExceptionForScavenger(rex);
      return transaction != null;
    }
  }

  /**
   * Sets the current connection.
   * This method is package scope and invoked whenever a connection
   * is attached or detached to/from a Db by the ConnectionManager.
   */
  synchronized void setConnection(ManagedConnection con) {
    this.con = con;
  }


  /**
   * Gets the current connection.
   *
   * @return the connection, null = not attached
   */
  synchronized ManagedConnection getConnection() {
    return con;
  }

  /**
   * Gets the current connection.<br>
   * Same as {@link #getConnection() } but unsynchronized.
   *
   * @return the connection, null = not attached
   */
  public ManagedConnection getConnectionUnsafe() {
    return con;
  }


  /**
   * Detach the db.<br>
   * Used in execption handling to cleanup all pending statements and result sets.
   */
  public void forceDetached() {
    if (con != null) {
      conMgr.forceDetach(this);
    }
  }


  /**
   * Gets the connection id.
   * This is a unique number assigned to this Db by the ConnectionManager.
   *
   * @return the connection id, 0 = Db is new and not connected so far
   */
  @Override
  public int getSessionId() {
    return conId;
  }


  /**
   * Sets the group number for this db.<br>
   *
   * This is an optional number describing groups of db-connections,
   * which is particulary useful in rmi-servers: if one connection
   * fails, all others should be closed as well.
   * Groups are only meaningful for local db-connections, i.e.
   * for remote dbs the group instance refers to that of the rmi-server.
   *
   * @param number is the group number, 0 = no group
   */
  public void setSessionGroupId(int number) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setSessionGroupId(number);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    if (number != groupConId) {
      if (number != 0) {
        LOGGER.info("{0} joined group {1}", this, number);
      }
      else  {
        LOGGER.info("{0} removed from group {1}", this, groupConId);
      }
    }
    groupConId = number;
  }


  @Override
  public int getSessionGroupId() {
    assertOpen();
    if (isRemote()) {
      try {
        return rdel.getSessionGroupId();
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    return groupConId;
  }


  @Override
  public int groupWith(int sessionId) {
    if (sessionId <= 0) {
      throw new PersistenceException(this, "invalid session id " + sessionId);
    }
    assertOpen();
    if (isRemote()) {
      try {
        groupConId = rdel.groupWith(sessionId);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    else  {

      if (groupConId > 0 && sessionId != groupConId) {
        throw new PersistenceException(this, "session to join group " + sessionId + " already belongs to group " + groupConId);
      }
      setSessionGroupId(sessionId);
      for (WeakReference<Db> dbRef: DB_SET) {
        Db db = dbRef.get();
        if (db != null && db.conId == sessionId) {
          if (db.groupConId > 0 && sessionId != db.groupConId) {
            throw new PersistenceException(db, "session already belongs to group " + db.groupConId);
          }
          db.setSessionGroupId(sessionId);
          break;
        }
      }
    }
    return groupConId;
  }


  /**
   * Attaches the connection.
   * <p>
   * Notice: package scope!
   */
  synchronized void attach() {
    assertOpen();
    assertOwnerThread();
    if (conMgr == null) {
      throw new PersistenceException(this, "no connection manager");
    }
    if (isPooled() && getPoolId() == 0) {
      // no SessionPool.getDb() on this db: possible closed RemoteSession?
      throw new PersistenceException(this, "illegal attempt to attach a pooled Db which is not in use");
    }
    conMgr.attach(this);
  }

  /**
   * Detaches the connection.
   * <p>
   * Notice: package scope!
   */
  synchronized void detach() {
    if (conId > 0) {
      assertOwnerThread();
      conMgr.detach(this);
    }
    // else: Db is already closed (may happen during cleanup)
  }




  /**
   * Sets the exclusive owner thread.
   * <p>
   * Allows to detect other threads accidently using this db.<br>
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
   * Non-prepared statements attach the db as soon as they
   * are instatiated! The db is detached after executeUpdate or after
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
  public synchronized StatementWrapper createStatement (int resultSetType, int resultSetConcurrency) {
    assertNotRemote();
    attach();
    try {
      StatementWrapper stmt = con.createStatement(resultSetType, resultSetConcurrency);
      stmt.markReady();
      return stmt;
    }
    catch (RuntimeException rex) {
      detach();
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
   * Getting the prepared statement will attach the db to a connection.
   * The db will be detached after executeUpdate() or executeQuery when its
   * result set is closed.
   *
   * @param stmtKey the statement key
   * @param alwaysPrepare true if always do a physical prepare
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   * @param sqlSupplier the SQL supplier
   *
   * @return the prepared statement for this db
   */
  public synchronized PreparedStatementWrapper getPreparedStatement(
          StatementKey stmtKey, boolean alwaysPrepare, int resultSetType, int resultSetConcurrency, Supplier<String> sqlSupplier) {
    assertNotRemote();
    attach();
    try {
      PreparedStatementWrapper stmt = con.getPreparedStatement(stmtKey, alwaysPrepare, resultSetType, resultSetConcurrency, sqlSupplier);
      stmt.markReady(); // mark ready for being used once
      return stmt;
    }
    catch (RuntimeException rex) {
      detach();
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
   * @return the prepared statement for this db
   */
  public PreparedStatementWrapper getPreparedStatement(StatementKey stmtKey, boolean alwaysPrepare,
                                                       Supplier<String> sqlSupplier) {
    return getPreparedStatement(stmtKey, alwaysPrepare, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, sqlSupplier);
  }


  /**
   * Creates a one-shot prepared statement.
   * <p>
   * Getting the prepared statement will attach the db to a connection.
   * The db will be detached after executeUpdate() or executeQuery() when its
   * result set is closed.
   *
   * @param sql the SQL code
   * @param resultSetType one of ResultSet.TYPE_...
   * @param resultSetConcurrency one of ResultSet.CONCUR_...
   *
   * @return the prepared statement for this db
   */
  public synchronized PreparedStatementWrapper createPreparedStatement(String sql, int resultSetType, int resultSetConcurrency) {
    assertNotRemote();
    attach();
    try {
      PreparedStatementWrapper stmt = con.createPreparedStatement(null, sql, resultSetType, resultSetConcurrency);
      stmt.markReady(); // mark ready for being used once
      return stmt;
    }
    catch (RuntimeException rex) {
      detach();
      throw rex;
    }
  }

  /**
   * Creates a one-shot prepared statement.<br>
   * Uses {@link ResultSet#TYPE_FORWARD_ONLY} and {@link ResultSet#CONCUR_READ_ONLY}.
   *
   * @param sql the SQL code
   *
   * @return the prepared statement for this db
   */
  public PreparedStatementWrapper createPreparedStatement(String sql) {
    return Db.this.createPreparedStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
  }


  /**
   * Sets autoCommit feature.
   * <p>
   * The method is provided for local db connections only.
   * Applications must use begin/commit instead.
   * Furthermore, the database will be attached and detached.
   * <p>
   * The method differs from the JDBC-method: a commit is *NOT* issued
   * if the autoCommit boolean value wouldn't change.
   * This allows nested setAutoCommit(false) in large transactions.
   *
   * @param autoCommit the new commit value
   * @return the old(!) value of autoCommit
   */
  private boolean setAutoCommit (boolean autoCommit) {
    if (this.autoCommit != autoCommit) {
      if (autoCommit && getBackend().sqlRequiresExtraCommit()) {
        // some dbms need a commit before setAutoCommit(true);
        con.commit();
      }
      if (!autoCommit) {
        // starting a tx
        attach();
      }
      con.setAutoCommit(autoCommit);
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
   * Gets the current user info.
   *
   * @return the UserInfo
   */
  @Override
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }


  /**
   * Sets the userInfo.
   * This will *NOT* send the session to the remote server
   * if this is a remote db.
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
   * clears the db local data for close/clone
   */
  private void clearMembers() {
    if (isRemote()) {
      // cloning a remote connection involves creating an entire new connection via open()!
      // notice that a remote db is always open (otherwise it wouldn't be remote ;-))
      rdel               = null;
      rses               = null;
      rcon               = null;
      delegates          = null;
      // returning to GC will also GC on server-side (if invoked from close())
    }
    else  {
      defaultIdSource     = null;
      transaction         = null;
    }

    groupConId  = 0;
    conId       = 0;
    ownerThread = null;

    if (sessionInfo != null && !sessionInfo.isImmutable()) {
      sessionInfo.setSince(0);    // not logged in anymore
    }
  }



  /**
   * Clones a logical connection.
   * <p>
   * Connections may be cloned, which results in a new connection using
   * the cloned userinfo of the original connection.
   * If the old db is already open, the new db will be opened as well.
   * If the old db is closed, the cloned db will be closed.
   *
   * @return the cloned Db
   */
  @Override
  public Db clone() {

    try {

      Db newDb = (Db) super.clone();

      if (sessionInfo != null) { // we need a new UserInfo cause some things may be stored here!
        newDb.setSessionInfo(sessionInfo.clone());
      }

      newDb.clearMembers();

      newDb.instanceNumber = INSTANCE_COUNTER.incrementAndGet();
      newDb.clonedFromDb = this;

      if (isRemote()) {
        newDb.open();
      }
      else  {
        if (isOpen())  {
          if (newDb.sessionInfo != null) {
            newDb.getSessionInfo().setSince(System.currentTimeMillis());
          }
          newDb.conId = conMgr.login(newDb);
          // new connections always start with autocommit on!
          newDb.autoCommit = true;
          newDb.transaction = null;
          newDb.setupIdSource();
        }
      }

      LOGGER.info("connection {0} cloned from {1}, state={2}", newDb, this, newDb.isOpen() ? "open" : "closed");

      newDb.registerDb();
      return newDb;
    }
    catch (Exception e)  {
      throw handleConnectException(e);
    }
  }


  /**
   * Gets the original db if this db is cloned.
   *
   * @return the orginal db, null if this db is not cloned.
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
   * @return true if this db is cloned
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
   * Gets the type of the logical connection.
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
    return RemoteSessionFactory.getInstance().create(rses);
  }

  /**
   * Prepares a {@link RemoteDelegate}.
   * <p>
   * The delegates for the AbstractDbObject-classes "live" in the db, i.e. the remote session.
   * Thus, delegates are unique per class AND db!
   * In order for the AbstractDbObject-derived classes to quickly map to the corresponding delegate,
   * the delegates get a unique handle, i.e. an index to an array of delegates, which in
   * turn is unique among ALL sessions.
   *
   * @param clazz is the AbstractDbObject-class
   * @return the handle
   */
  public synchronized static int prepareRemoteDelegate (Class<?> clazz) {
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

    assertRemote();        // only allowed on remote connections!

    delegateId--; // starting from 0

    if (delegateId < 0 || delegateId >= remoteClasses.length) {
      throw new PersistenceException(this, "delegate handle out of range");
    }

    // enlarge if necessary
    if (delegates == null) {
      delegates = new RemoteDelegate[remoteClasses.length];
      for (int i=0; i < delegates.length; i++)  {
        delegates[i] = null;
      }
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
        RemoteDelegate delegate = rses.getRemoteDelegate(remoteClasses[delegateId].getName());
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
    return rses.getRemoteDelegate(className);
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
   * Useful to turn off modcount for a special (temporary) connection doing
   * certain tasks that should not be counted.
   *
   * @param countModificationAllowed true if allowed, false if turned off
   *
   */
  public void setCountModificationAllowed(boolean countModificationAllowed) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setCountModificationAllowed(countModificationAllowed);
      }
      catch (RemoteException e)  {
        throw PersistenceException.createFromRemoteException(this, e);
      }
    }
    this.countModificationAllowed = countModificationAllowed;
  }


  /**
   * Sets the modification allowed state.
   * Useful to turn off modlog for a special (temporary) connection doing
   * certain tasks that should not be logged.
   * The state will be handed over to the remote db-connection as well.
   *
   * @param logModificationAllowed true to allow, false to deny
   */
  public void setLogModificationAllowed(boolean logModificationAllowed) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setLogModificationAllowed(logModificationAllowed);
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
   * Instead they are kept in memory and can be processed later via
   * {@link #popModificationLogsOfTransaction()}.
   * The state will be handed over to the remote db-connection as well.
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
   * @param logModificationDeferred true to defer logs for the rest of current transaction, false to undefer
   */
  public void setLogModificationDeferred(boolean logModificationDeferred) {
    assertOpen();
    if (isRemote()) {
      try {
        this.logModificationDeferred = rdel.setLogModificationDeferred(logModificationDeferred);
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
        list = rdel.popModificationLogsOfTransaction();
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
        return rdel.getLogModificationTxId();
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
   * Normally the tx-ID is derived from the id of the BEGIN-modlog
   * so it's not necessary to invoke this method from an application.
   * (Poolkeeper's replication layer will do so!)
   *
   * @param logModificationTxId the transaction ID
   */
  public void setLogModificationTxId(long logModificationTxId) {
    assertOpen();
    if (isRemote()) {
      try {
        rdel.setLogModificationTxId(logModificationTxId);
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
        rdel.setLogModificationTxEnabled(logModificationTxEnabled);
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
