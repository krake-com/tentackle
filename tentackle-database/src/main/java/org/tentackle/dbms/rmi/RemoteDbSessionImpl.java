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

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.io.ServerSocketConfigurator;
import org.tentackle.io.ServerSocketConfiguratorHolder;
import org.tentackle.io.SocketConfigurator;
import org.tentackle.io.SocketConfiguratorHolder;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.MethodStatistics;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * User session within the application server.
 *
 * @author  harald
 */
public abstract class RemoteDbSessionImpl implements RemoteDbSession, Exportable {

  private static final Logger LOGGER = Logger.get(RemoteDbSessionImpl.class);


  /**
   * We keep an internal set of all sessions via WeakReferences, so the
   * sessions still will be finalized when the session isn't used anymore.
   * The set of sessions is used to determine stale sessions, i.e. ones
   * with timed out sessions. These are typically caused by clients
   * not properly closing the rmi-session.
   */
  private static final Set<WeakReference<RemoteDbSessionImpl>> SESSIONS = ConcurrentHashMap.newKeySet();


  /**
   * The session cleanup thread.
   */
  private static RemoteDbSessionCleanupThread cleanupThread;


  /**
   * Starts the optional cleanup thread that will
   * monitor the sessions for database activity.
   *
   * @param checkInterval is the interval in ms
   */
  public static void startCleanupThread(long checkInterval) {
    cleanupThread = new RemoteDbSessionCleanupThread(checkInterval, SESSIONS, 3);
    cleanupThread.start();
  }


  /**
   * Stops the cleanup thread.
   */
  public static synchronized void stopCleanupThread() {
    if (cleanupThread != null && cleanupThread.isAlive()) {
      cleanupThread.requestTermination();
      cleanupThread = null;
    }
  }


  // each session gets a unique number (only for identification in log files)
  private static final AtomicLong LAST_SESSION_NUMBER = new AtomicLong();


  /**
   * Returns a list of all open sessions.
   *
   * @return the sessions, never null
   */
  public static Collection<RemoteDbSessionImpl> getOpenSessions() {
    Collection<RemoteDbSessionImpl> openSessions = new ArrayList<>();
    for (WeakReference<RemoteDbSessionImpl> ref : SESSIONS) {
      RemoteDbSessionImpl session = ref.get();
      if (session != null && session.isOpen()) {
        openSessions.add(session);
      }
    }
    return openSessions;
  }


  /**
   * Checks if the user is already logged in.
   *
   * @param sessionInfo the user's session info
   * @return the session info if already logged in, null if not logged in
   */
  public static SessionInfo isUserLoggedIn(SessionInfo sessionInfo) {
    for (RemoteDbSessionImpl session: getOpenSessions()) {
      SessionInfo sessionSessionInfo = session.getClientSessionInfo();
      if (sessionInfo != sessionSessionInfo && sessionSessionInfo.equals(sessionInfo)) {
        return sessionSessionInfo;
      }
    }
    return null;
  }


  /**
   * relation between a class and their delegates.
   */
  private static class DelegateClasses {

    private final Class<?> clazz;                                   // client side class
    private final Class<?> effectiveClass;                          // the effective class, null if same as clazz
    private final Class<RemoteDelegate> delegateClass;              // its delegate interface
    private final Class<RemoteDelegateImpl<?>> delegateImplClass;   // delegate implementation class

    private DelegateClasses(Class<?> effectiveClass, Class<?> clazz,
                            Class<RemoteDelegate> delegateClass, Class<RemoteDelegateImpl<?>> delegateImplClass) {
      this.effectiveClass = effectiveClass == clazz ? null : effectiveClass;
      this.clazz = clazz;
      this.delegateClass = delegateClass;
      this.delegateImplClass = delegateImplClass;
    }

    private DelegateClasses(Class<?> effectiveClass, Class<?> clazz, RemoteDelegateLocator.Result delegates) {
      this(clazz,
           effectiveClass == null ? delegates.getEffectiveClass() : effectiveClass,
           delegates.getRemoteDelegate(), delegates.getRemoteDelegateImpl());
    }

    @Override
    public String toString() {
      return clazz.getName() + " -> " + delegateClass.getName() + " / " + delegateImplClass.getName();
    }
  }

  // maps classnames to delegate classes
  private static final ConcurrentHashMap<String,DelegateClasses> DELEGATE_CLASSES_MAP = new ConcurrentHashMap<>();


  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  /**
   * Holds the resources and state to be cleaned up.
   */
  private class Resources implements Runnable {

    private RemoteDbSessionImpl me;                   // != null if programmatic close
    private String name;                              // the session name
    private Db db;                                    // the local session
    private String mdcInfo;                           // the cached MDC info
    private final RemoteDbConnectionImpl con;         // the connection object
    private final Collection<WeakReference<RemoteDelegate>> exportedDelegates;  // delegates to un-export on close
    private final MethodStatistics methodStats;       // statistics for method invocations
                                                      // (managed by invocation handler, null if no stats)

    Resources(Db db, RemoteDbConnectionImpl con) {
      this.db = db;
      this.con = con;
      exportedDelegates = new ArrayList<>();
      methodStats = new MethodStatistics();
    }

    @Override
    public void run() {
      try {
        if (db != null) {
          if (me == null) {
            LOGGER.warning("cleaning up unreferenced remote session {0}", name);
          }
          cleanup(false);
          closeDb(false);
        }
        for (WeakReference<RemoteDelegate> ref : exportedDelegates) {
          RemoteDelegate delegate = ref.get();
          if (delegate != null) {
            con.unexportRemoteObject(delegate);
          }
        }
        exportedDelegates.clear();
        if (me != null) {
          unExportMe(me);
        }
      }
      catch (RemoteException | RuntimeException ex) {
        LOGGER.severe("closing session failed", ex);
      }
      finally {
        db = null;
        me = null;
      }
    }

    void cleanup(boolean crashed) {
      if (crashed) {
        LOGGER.warning("cleaning up crashed {0}{1}", name, DiagnosticUtilities.getInstance().createStackDump());
      }
      if (db != null) {
        db.setCrashed(crashed);
        if (!db.isRemote()) {
          db.rollbackImmediately(null);   // roll back if anything pending
        }
      }
      doLogStatistics(Level.INFO, true);
    }

    void closeDb(boolean cleanup) {
      if (db != null) {
        if (db.isPooled()) {
          if (!db.isRemote()) {
            db.rollbackImmediately(null); // roll back if anything pending
          }

          if (cleanup) {
            db.close(); // closed db will be removed from the pool in putDb
            LOGGER.warning("pooled {0} closed due to session cleanup", db);
          }
          else {
            // only return the db to the pool, don't close it
            LOGGER.info("returning {0} to pool", db);
          }

          if (db.isRemote()) {
            // remote sessions are closed and will be removed from the pool.
            // this closes the remote session in the remote server as well
            db.close();
          }
          else if (db.isOpen()) {
            // reset the session info for this Db for sure (the application may have changed it to clientInfo)
            db.setSessionInfo(serverInfo);
            db.setExportedSessionGroupId(0);      // remove from session group
          }

          db.getPool().putSession(db);    // this will work for MultiUserDbPool as well!
        }
        else {
          db.close();
        }
        db = null;    // closed -> to GC
      }
    }

    void unExportMe(RemoteDbSessionImpl remoteSession) throws RemoteException {
      LOGGER.info("end {0}", remoteSession);
      con.unexportRemoteObject(remoteSession);
    }

    void doLogStatistics(Level level, boolean clear) {
      methodStats.logStatistics(mdcInfo, level, "    >RMI-Stats: ", clear);
    }
  }


  private final Resources resources;                  // resources to be cleaned up
  private final Cleanable cleanable;                  // to clean up the connection
  private final SessionInfo clientInfo;               // saved client info
  private final SessionInfo serverInfo;               // the server info for creating a new session
  private final long sessionNumber;                   // unique session number
  private final String clientHost;                    // the client host string
  private int timeout;                                // timeout in polling intervals of the RemoteDbSessionCleanupThread
  private int port;                                   // port for all delegates
  private RMIClientSocketFactory csf;                 // client socket factory for all delegates
  private RMIServerSocketFactory ssf;                 // server socket factory for all delegates
  private int timeoutCount;                           // consecutive timeouts
  private long closedSince;                           // session closed since, 0 if still open


  /**
   * Creates a session on a given connection.
   *
   * @param con the connection
   * @param clientInfo the SessionInfo from the client
   * @param serverInfo the SessionInfo to establish the connection to the database server
   *
   * @throws PersistenceException if the session could not be initiated.
   */
  public RemoteDbSessionImpl(RemoteDbConnectionImpl con, SessionInfo clientInfo, SessionInfo serverInfo) {

    this.clientInfo = clientInfo.clone();
    if (!clientInfo.isCloned()) {
      this.clientInfo.clearCloned();
    }
    this.serverInfo = serverInfo.clone();
    if (!serverInfo.isCloned()) {
      this.serverInfo.clearCloned();
    }

    sessionNumber = LAST_SESSION_NUMBER.incrementAndGet();

    Db db = null;
    try {
      clientHost = UnicastRemoteObject.getClientHost();
      db = openDb();
      resources = new Resources(db, con);
      synchronized (SESSIONS) {   // prevents race condition between verifySessionInfo and SESSIONS.add
        this.clientInfo.setSince(System.currentTimeMillis());
        verifySessionInfo(this.clientInfo);
        db.setSessionInfo(this.clientInfo);  // switch to client info for the lifetime of this remote session
        SESSIONS.add(new WeakReference<>(this));
      }
    }
    catch (ServerNotActiveException | RuntimeException ex) {
      if (db != null) {
        cleanup(false);
        closeDb(false);
      }
      if (ex instanceof LoginFailedException) {
        throw (LoginFailedException) ex;
      }
      throw new PersistenceException("could not setup remote session", ex);
    }

    // config for delegates
    port = determinePort(db, clientInfo, serverInfo);
    csf = determineClientSocketFactory(db, clientInfo, serverInfo);
    ssf = determineServerSocketFactory(db, clientInfo, serverInfo);
    timeout = determineTimeout(db, clientInfo, serverInfo);

    resources.name = toString();    // chicken egg...
    cleanable = CLEANER.register(this, resources);
  }


  @Override
  public void exportMe() throws RemoteException {
    RmiServer rmiServer = resources.con.getRmiServer();
    resources.con.exportRemoteObject(this,
                                     rmiServer.getLoginPort(),
                                     rmiServer.getLoginClientSocketFactory(),
                                     rmiServer.getLoginServerSocketFactory());
    LOGGER.info("begin {0}", this);
    clientInfo.clearPassword();   // no more needed
  }

  @Override
  public void unExportMe() throws RemoteException {
    resources.unExportMe(this);
  }


  /**
   * Exports the given delegate.
   * <p>
   * Notice that the delegate must not extend {@link UnicastRemoteObject}!
   *
   * @param delegate the delegate
   * @throws RemoteException if export failed
   */
  public void exportRemoteDelegate(RemoteDelegate delegate) throws RemoteException {
    resources.con.exportRemoteObject(delegate, getPort(), getClientSocketFactory(), getServerSocketFactory());
    resources.exportedDelegates.add(new WeakReference<>(delegate));
  }

  /**
   * Un-exports given remote delegate.
   * <p>
   * Notice: when the session is closed, all still exported objects will be un-exported.
   * This method is only necessary if a delegate must be explicitly un-exported.
   *
   * @param delegate the delegate
   * @throws RemoteException if object not exported
   */
  public void unExportRemoteDelegate(RemoteDelegate delegate) throws RemoteException {
    resources.con.unexportRemoteObject(delegate);
    for (Iterator<WeakReference<RemoteDelegate>> iter = resources.exportedDelegates.iterator(); iter.hasNext(); ) {
      WeakReference<RemoteDelegate> ref = iter.next();
      RemoteDelegate dg = ref.get();
      if (dg == null) {
        iter.remove();
      }
      else if (dg == delegate) {
        iter.remove();
        break;
      }
    }
  }


  /**
   * Verifies and updates the client's session info.<br>
   * Needs to be implemented by the application.<br>
   * Checks login credentials and sets the user id.
   * <p>
   * Throws LoginFailedException if login is not allowed for whatever reason.
   *
   * @param sessionInfo the session info
   */
  public abstract void verifySessionInfo(SessionInfo sessionInfo);


  /**
   * Gets the unique session number.
   *
   * @return the number of this session
   */
  public long getSessionNumber() {
    return sessionNumber;
  }


  /**
   * Gets the session timeout.
   *
   * @return the timeout in timeout intervals
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Sets the session timeout.
   *
   * @param timeout the timeout in timeout intervals
   */
  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }


  /**
   * Gets the session.
   *
   * @return the session
   */
  public Db getSession() {
    return resources.db;
  }


  /**
   * Gets the server connection.
   *
   * @return the connection
   */
  public RemoteDbConnectionImpl getConnection() {
    return resources.con;
  }


  /**
   * Gets the client session info.
   *
   * @return the client session info
   */
  @Override
  public SessionInfo getClientSessionInfo() {
    return clientInfo;
  }


  /**
   * Gets the server session info.
   *
   * @return the server session info
   */
  public SessionInfo getServerSessionInfo() {
    return serverInfo;
  }


  /**
   * Gets the epochal time when the session was closed.
   *
   * @return the time when closed, 0 if still open
   */
  public long getClosedSince() {
    return closedSince;
  }


  /**
   * Gets the port for all delegates.
   *
   * @return the port for all delegates
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets Gets the port for all delegates.
   *
   * @param port the port
   */
  public void setPort(int port) {
    this.port = port;
  }


  /**
   * Gets the default client socket factory for all delegates.
   *
   * @return the default csf for all delegates
   */
  public RMIClientSocketFactory getClientSocketFactory() {
    return csf;
  }

  /**
   * Sets the client socket factory for all delegates.
   *
   * @param csf the socket factory
   */
  public void setClientSocketFactory(RMIClientSocketFactory csf) {
    this.csf = csf;
  }


  /**
   * Gets the default server socket factory for all delegates.
   *
   * @return the default ssf for all delegates
   */
  public RMIServerSocketFactory getServerSocketFactory() {
    return ssf;
  }

  /**
   * Sets the server socket factory for all delegates.
   *
   * @param ssf the socket factory
   */
  public void setServerSocketFactory(RMIServerSocketFactory ssf) {
    this.ssf = ssf;
  }



  /**
   * Counts the invocation of a delegate method.
   *
   * @param method the method invoked
   * @param servicedClass the serviced class
   * @param timeKeeper execution duration
   */
  public void countMethodInvocation(Method method, Class<?> servicedClass, TimeKeeper timeKeeper) {
    resources.methodStats.countMethodInvocation(method, servicedClass, timeKeeper);
  }


  /**
   * Determines the tcp port for the delegates.<br>
   * The default implementation returns {@link RmiServer#getPort()}.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the session info from the client
   * @param serverInfo the session info to establish the connection to the database rmiServer
   * @return the tcp-port for this connection, 0 = system default
   */
  protected int determinePort(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return resources.con.getRmiServer().getPort();
  }

  /**
   * Determines the client socket factory for the delegates.<br>
   * The default implementation returns {@link RmiServer#getClientSocketFactory()}.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the session info from the client
   * @param serverInfo the session info to establish the connection to the database rmiServer
   * @return the client socket factory for this connection, null = system default
   */
  protected RMIClientSocketFactory determineClientSocketFactory(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return resources.con.getRmiServer().getClientSocketFactory();
  }

  /**
   * Determines the server socket factory for the delegates.<br>
   * The default implementation returns {@link RmiServer#getServerSocketFactory()}.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the session info from the client
   * @param serverInfo the session info to establish the connection to the database rmiServer
   * @return the rmiServer socket factory for this connection, null = system default
   */
  protected RMIServerSocketFactory determineServerSocketFactory(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return resources.con.getRmiServer().getServerSocketFactory();
  }

  /**
   * Determines the session timeout count.<br>
   * The default implementation returns {@link RmiServer#getSessionTimeout()}.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the session info from the client
   * @param serverInfo the session info to establish the connection to the database rmiServer
   * @return the timeout
   */
  protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return resources.con.getRmiServer().getSessionTimeout();
  }


  /**
   * Gets an open db session for this remote client session.
   *
   * @return the db session
   * @throws LoginFailedException if opening the session failed
   */
  protected Db openDb() {
    try {
      Db pooledDb = null;
      SessionPoolProvider sessionPoolProvider = DbUtilities.getInstance().getSessionPoolProvider();
      if (sessionPoolProvider != null) {
        SessionPool sessionPool = sessionPoolProvider.getSessionPool();
        if (sessionPool != null) {
            pooledDb = (Db) sessionPool.getSession();
        }
        else {
          MultiUserSessionPool remoteSessionPool = sessionPoolProvider.getRemoteSessionPool();
          if (remoteSessionPool != null) {
            SessionInfo sessionInfo = getClientSessionInfo();
            sessionInfo.setProperties(serverInfo.getProperties());
            pooledDb = (Db) remoteSessionPool.get(sessionInfo);
          }
        }
      }
      if (pooledDb == null) {
        pooledDb = (Db) SessionFactory.getInstance().create(serverInfo);
      }
      LOGGER.info("using {0}", pooledDb);
      return pooledDb;
    }
    catch (LoginFailedException lx) {
      throw lx;
    }
    catch (RuntimeException ex) {
      throw new LoginFailedException("obtaining session failed", ex);
    }
  }


  /**
   * Cleanup the session.
   * <p>
   * The method is invoked whenever the session is closed
   * due to an ordinary logout or client crash.
   * The default implementation rolls back any pending transaction.
   *
   * @param crashed true if client crashed, else regular logout
   */
  protected void cleanup(boolean crashed) {
    DbUtilities.getInstance().cleanupRemoteSession(this);
    if (resources != null) {
      resources.cleanup(crashed);
    }
  }


  /**
   * Logs the statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  protected void doLogStatistics(Level level, boolean clear) {
    resources.doLogStatistics(level, clear);
  }


  /**
   * Closes the database connection (and thus rolls back any pending transaction).
   * If the db is pooled, it will be returned to the pool instead of being closed.
   *
   * @param cleanup true if db must be physically closed even if pooled, due to clean up
   */
  protected void closeDb(boolean cleanup) {
    if (closedSince == 0) {
      if (resources != null) {
        resources.me = this;    // programmatic cleanup
        resources.closeDb(cleanup);
      }
      closedSince = System.currentTimeMillis();
    }
    if (cleanable != null) {
      cleanable.clean();
    }
  }


  /**
   * Forces a cleanup if all cleanup and closing failed.
   * <p>
   * Simply marks the session closed and moves all references to GC.
   */
  protected void forceCleanup() {
    closedSince = System.currentTimeMillis();
    Db zombieDb = resources.db;
    if (zombieDb != null) {
      resources.db = null;  // block any further RMI requests (isOpen() == false)
      try {
        zombieDb.close();   // should work
        if (zombieDb.isPooled()) {
          zombieDb.getPool().putSession(zombieDb);
        }
      }
      catch (RuntimeException ex) {
        LOGGER.warning("forced cleanup may be incomplete", ex);
      }
    }
  }


  /**
   * Gets the string representation of the client host connected to this session.
   *
   * @return the client host string
   */
  public String getClientHostString() {
    return clientHost;
  }


  /**
   * Returns an application-specific option string.<br>
   * For diagnostic purposes only.
   *
   * @return null if no options
   */
  public String getOptions() {
    return null;
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getClass().getSimpleName()).append('#').append(sessionNumber).append(": csf=");

    if (csf == null) {
      buf.append("<system>");
    }
    else {
      buf.append(csf.getClass().getSimpleName());
      if (csf instanceof SocketConfiguratorHolder) {
        SocketConfigurator csc = ((SocketConfiguratorHolder) csf).getSocketConfigurator();
        if (csc != null && csc.isValid()) {
          buf.append('[').append(csc).append(']');
        }
      }
    }

    buf.append(", ssf=");
    if (ssf == null) {
      buf.append("<system>");
    }
    else {
      buf.append(ssf.getClass().getSimpleName());
      if (ssf instanceof ServerSocketConfiguratorHolder) {
        ServerSocketConfigurator ssc = ((ServerSocketConfiguratorHolder) ssf).getSocketConfigurator();
        if (ssc != null && ssc.isValid()) {
          buf.append('[').append(ssc).append(']');
        }
      }
    }
    buf.append(", port=");
    if (port == 0) {
      buf.append("<auto>");
    }
    else  {
      buf.append(port);
    }
    buf.append(", timeout=").append(timeout)
       .append(", client=").append(clientInfo)
       .append(", host=").append(clientHost);

    return buf.toString();
  }


  /**
   * Gets a string useful for mapped diagnostic context info.
   *
   * @return the MDC info
   */
  public String getMdcInfo() {
    if (resources.mdcInfo == null) {
      StringBuilder buf = new StringBuilder();
      buf.append(sessionNumber).append(':');
      String userName = clientInfo.getUserName();
      if (userName != null && !userName.isEmpty()) {
        buf.append(userName);
      }
      String applicationName = clientInfo.getApplicationName();
      if (applicationName != null && !applicationName.isEmpty()) {
        buf.append('@').append(applicationName);
      }
      if (clientInfo.getApplicationId() != 0) {
        buf.append('#').append(clientInfo.getApplicationId());
      }
      if (clientInfo.isCloned()) {
        buf.append('*');
      }
      resources.mdcInfo = buf.toString();
    }
    return resources.mdcInfo;
  }


  /**
   * Determines whether the session is open.
   *
   * @return true if session is open
   */
  public boolean isOpen() {
    return resources.db != null;
  }



  // ----------------- implements RemoteDbSession ------------------------


  @Override
  public void close() throws RemoteException {
    cleanup(false);
    closeDb(false);
  }


  @Override
  public void log(Level level, String message) throws RemoteException {
    try {
      LOGGER.log(level, message, null);
    }
    catch (RuntimeException ex)  {
      throw new RemoteException("log() failed", ex);
    }
  }


  @Override
  public void logStatistics(Level level, boolean clear) throws RemoteException {
    try {
      doLogStatistics(level, clear);
    }
    catch (RuntimeException ex) {
      throw new RemoteException("logStatistics failed", ex);
    }
  }


  @SuppressWarnings("unchecked")
  @Override
  public <T extends RemoteDelegate> T getRemoteDelegate(String classname) throws RemoteException  {

    try {

      DelegateClasses delegateClasses = DELEGATE_CLASSES_MAP.get(classname);

      if (delegateClasses == null) {
        // try to find remote class.
        // Use superclass if no direct implementation found
        ClassNotFoundException nfe = null;    // first exception thrown
        Class<?> clazz = Class.forName(classname);
        Class<?> servicedClass = null;
        Class<?> currentClazz = clazz;
        while (currentClazz != null) {
          if (servicedClass == null) {
            Class<?> nextServicedClass = DbUtilities.getInstance().getServicedClass(currentClazz);
            if (nextServicedClass != null) {
              servicedClass = nextServicedClass;
            }
          }
          try {
            delegateClasses = new DelegateClasses(servicedClass, clazz,
                    RemoteDelegateLocator.getInstance().findRemoteDelegate(currentClazz));
            LOGGER.info("created remote delegate class mapping {0}", delegateClasses);
            DELEGATE_CLASSES_MAP.put(classname, delegateClasses);
            break;
          }
          catch (ClassNotFoundException e) {
            if (currentClazz == Object.class && nfe != null) {
              // abort with first exception thrown
              throw nfe;
            }
            if (nfe == null) {
              nfe = e;    // remember
            }
            // try superclass
            currentClazz = currentClazz.getSuperclass();
          }
        }
        if (delegateClasses == null) {
          throw new RemoteException("no delegate class found for " + clazz, nfe);
        }
      }

      T delegate = (T) createRemoteDelegate(delegateClasses.delegateClass, delegateClasses.delegateImplClass,
                                            delegateClasses.clazz, delegateClasses.effectiveClass);

      exportRemoteDelegate(delegate);

      LOGGER.fine("Delegate created for session={0}, class={1}, port={2}, csf={3}, ssf={4}",
                  this, delegateClasses.clazz, port,
                  csf == null ? "<default>" : csf.getClass().getName(),
                  ssf == null ? "<default>" : ssf.getClass().getName());

      return delegate;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException ex)  {
      throw new RemoteException("couldn't create delegate for " + classname, ex);
    }
  }


  @Override
  public DbRemoteDelegate getDbRemoteDelegate() throws RemoteException  {
    try {
      DbRemoteDelegate delegate = createRemoteDelegate(DbRemoteDelegate.class, DbRemoteDelegateImpl.class, Db.class, null);
      exportRemoteDelegate(delegate);
      return delegate;
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           InvocationTargetException | RemoteException ex)  {
      throw new RemoteException("couldn't create delegate for " + resources.db, ex);
    }
  }




  /**
   * Creates the classname of the remote delegate interface from the
   * serviced classname.<br>
   * The default implementation returns:
   * <code>packagename + ".rmi." + classbasename + "RemoteDelegate"</code>
   *
   * @param className the name of the class to look for a delegate
   * @return the classname of the remote delegate for given class
   */
  public String createRemoteDelegateClassName(String className) {
    int ndx = className.lastIndexOf('.');
    String pkgName = className.substring(0, ndx);
    String clsName = className.substring(ndx + 1);
    return pkgName + ".rmi." + clsName + "RemoteDelegate";
  }


  /**
   * Creates the classname of the remote delegate implementation from the
   * serviced classname.<br>
   * The default implementation returns
   * {@link #createRemoteDelegateClassName(java.lang.String) + "Impl"}.
   *
   * @param className the name of the class to look for a delegate
   * @return the classname of the remote delegate for given class
   */
  public String createRemoteDelegateImplClassName(String className) {
    return createRemoteDelegateClassName(className) + "Impl";
  }



  /**
   * Creates a remote delegate for the given class.
   * <p>
   * If the optional configuration arguments are given, the delegate must provide
   * a method with the following signature:
   * <pre>
   *  public void configureDelegate(Object... args) {
   *    // whatever args stands for
   *  }
   * </pre>
   * The optional configuration parameters allow passing additional objects
   * to the delegate without having to declare the setters in the remote interface.
   *
   * @param <T> the delegate class
   * @param <I> the delegate implementation class
   * @param delegateClass the interface class
   * @param delegateImplClass the implementing class
   * @param clazz the serviced class
   * @param effectiveClass the optional effective serviced class, null if clazz
   * @param configArgs optional arguments to configure the delegate
   * @return the created delegate
   * @throws InstantiationException if delegate could not be instantiated
   * @throws NoSuchMethodException if delegate does not provide the necessary constructor
   * @throws IllegalAccessException if access denied
   * @throws IllegalArgumentException if wrong arguments
   * @throws InvocationTargetException if invocation failed
   */
  @SuppressWarnings("unchecked")
  public <T extends RemoteDelegate, I extends RemoteDelegateImpl<?>> T  createRemoteDelegateInstance(

          Class<T> delegateClass,
          Class<I> delegateImplClass,
          Class<?> clazz,
          Class<?> effectiveClass,
          Object... configArgs)

            throws InstantiationException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

    // get the constructor with args (RemoteDbSessionImpl session, Class clazz)
    Constructor<I> constructor =
            effectiveClass == null ? delegateImplClass.getConstructor(RemoteDbSessionImpl.class, Class.class) :
                                     delegateImplClass.getConstructor(RemoteDbSessionImpl.class, Class.class, Class.class);
    // create instance of delegate for the session db
    T delegate = (T) (effectiveClass == null ? constructor.newInstance(this, clazz) :
                                               constructor.newInstance(this, effectiveClass, clazz));

    ((RemoteDelegateImpl<?>) delegate).initialize();

    // optionally configure the created delegate
    if (configArgs != null && configArgs.length > 0) {
      Method configureMethod = delegateImplClass.getMethod("configureDelegate", Object[].class);
      configureMethod.invoke(delegate, new Object[] { configArgs });
    }
    return delegate;
  }


  /**
   * Creates a remote delegate for the given class.<br>
   * Same as {@link #createRemoteDelegateInstance} but with dynamic proxy to allow intercepting.
   *
   * @param <T> the remote delegate class
   * @param <I> the delegate implementation class
   * @param delegateClass the interface class
   * @param delegateImplClass the implementing class
   * @param clazz the serviced class
   * @param effectiveClass the effectively serviced class, null if clazz
   * @param configArgs optional configuration arguments passed to {@link #createRemoteDelegateInstance}
   * @return the created
   * @throws InstantiationException if delegate could not be instantiated
   * @throws NoSuchMethodException if delegate does not provide the necessary constructor
   * @throws IllegalAccessException if access denied
   * @throws IllegalArgumentException if wrong arguments
   * @throws InvocationTargetException if invocation failed
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends RemoteDelegate, I extends RemoteDelegateImpl<?>> T createRemoteDelegate(

          Class<T> delegateClass,
          Class<I> delegateImplClass,
          Class<?> clazz,
          Class<?> effectiveClass,
          Object... configArgs)

            throws InstantiationException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

    return (T) Proxy.newProxyInstance(
            delegateImplClass.getClassLoader(),
            new Class<?>[] { delegateClass, Remote.class },
            new RemoteDelegateInvocationHandler((RemoteDelegateImpl)
                    createRemoteDelegateInstance(delegateClass, delegateImplClass, clazz, effectiveClass, configArgs)));
  }


  /**
   * Checks for timeout.
   * Will internally increment a counter until timeout has reached.
   *
   * @return true if timed out
   */
  protected boolean hasTimedOut() {
    Db ldb = resources.db;    // local copy in case of race cond avoids NPE
    if (ldb == null || ldb.isAlive()) {
      timeoutCount = 0;
    }
    else  {
      timeoutCount++;
    }
    return timeoutCount > timeout;
  }

  /**
   * Gets the current timeout counter.
   *
   * @return the timeout counter
   */
  protected int getTimeoutCount() {
    return timeoutCount;
  }

  /**
   * Sets this session as being polled for timeout.
   */
  protected void polled() {
    Db ldb = resources.db;    // local copy in case of race cond avoids NPE
    if (ldb != null) {
      ldb.setAlive(false);
    }
  }

}
