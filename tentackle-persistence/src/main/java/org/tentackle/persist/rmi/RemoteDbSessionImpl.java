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

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.tentackle.app.AbstractApplication;
import org.tentackle.io.ServerSocketConfigurator;
import org.tentackle.io.ServerSocketConfiguratorHolder;
import org.tentackle.io.SocketConfigurator;
import org.tentackle.io.SocketConfiguratorHolder;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.MethodStatistics;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.misc.Duration;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.SessionManagerProvider;
import org.tentackle.pdo.SessionPool;
import org.tentackle.pdo.SessionPoolProvider;
import org.tentackle.persist.ConnectionManager;
import org.tentackle.persist.Db;
import org.tentackle.reflect.ReflectionHelper;



/**
 * User session within the application server.
 *
 * @author  harald
 */
abstract public class RemoteDbSessionImpl implements RemoteDbSession, Exportable {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDbSessionImpl.class);



  /**
   * We keep an internal set of all sessions via WeakReferences, so the
   * sessions still will be finalized when the session isn't used anymore.
   * The set of sessions is used to determine stale sessions, i.e. ones
   * with timed out db-connections. These are typically caused by clients
   * not properly closing the rmi-session.
   */
  private static final Set<WeakReference<RemoteDbSessionImpl>> SESSIONS =
          Collections.newSetFromMap(new ConcurrentHashMap<>());


  /**
   * The session cleanup thread.
   */
  private static RemoteDbSessionCleanupThread cleanupThread;



  /**
   * Starts the optional cleanup thread that will
   * monitor the sessions for db-activity.
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
   * @param userInfo the user's info
   * @return the userinfo if already logged in, null if not logged in
   */
  public static SessionInfo isUserLoggedIn(SessionInfo userInfo) {
    for (RemoteDbSessionImpl session: getOpenSessions()) {
      SessionInfo sessionSessionInfo = session.getClientSessionInfo();
      if (userInfo != sessionSessionInfo && sessionSessionInfo.equals(userInfo)) {
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



  // ------------------- end static section ------------------------


  private final RemoteDbConnectionImpl con;           // the connection object
  private final SessionInfo clientInfo;               // saved client info
  private final SessionInfo serverInfo;               // the server info for creating a new Db connection
  private final long sessionNumber;                   // unique session number

  private String clientHost;                          // the client host string
  private int timeout;                                // timeout in polling intervals of the cleanupthread
  private int port;                                   // port for all delegates
  private RMIClientSocketFactory csf;                 // client socket factory for all delegates
  private RMIServerSocketFactory ssf;                 // server socket factory for all delegates

  private int timeoutCount;                           // consecutive timeouts
  private Db db;                                      // the local Db-connection
  private long closedSince;                           // session closed since, 0 if still open
  private final MethodStatistics methodStats;         // statistics for method invocations
                                                      // (managed by invocation handler, null if no stats)
  private final Collection<WeakReference<RemoteDelegate>> exportedDelegates;  // delegates to unexport on close

  /**
   * Creates a session on a given connection.
   *
   * @param con the connection
   * @param clientInfo the SessionInfo from the client
   * @param serverInfo the SessionInfo to establish the connection to the database server
   *
   * @throws RemoteException if the session could not initiated.
   */
  public RemoteDbSessionImpl(RemoteDbConnectionImpl con, SessionInfo clientInfo, SessionInfo serverInfo) throws RemoteException {

    this.con = con;
    this.clientInfo = clientInfo;
    this.serverInfo = serverInfo;

    methodStats = new MethodStatistics();
    sessionNumber = LAST_SESSION_NUMBER.incrementAndGet();
    exportedDelegates = new ArrayList<>();

    try {
      clientHost = UnicastRemoteObject.getClientHost();
      db = openDb();
      clientInfo.setSince(System.currentTimeMillis());
      verifySessionInfo(clientInfo);
      // switch to client info for logging
      db.setSessionInfo(clientInfo);
    }
    catch (Exception ex)  {
      if (db != null) {
        cleanup(false);
        closeDb(false);
      }
      if (ex instanceof LoginFailedException) {
        throw (LoginFailedException) ex;
      }
      throw new RemoteException("could not setup remote session", ex);
    }

    // add session to the set
    SESSIONS.add(new WeakReference<>(this));

    // config from connection
    port = con.getPort(db, clientInfo, serverInfo);
    csf = con.getClientSocketFactory(db, clientInfo, serverInfo);
    ssf = con.getServerSocketFactory(db, clientInfo, serverInfo);
    timeout = con.getSessionTimeout(db, clientInfo, serverInfo);
  }


  @Override
  public void exportMe() throws RemoteException {
    port = con.exportRemoteObject(this, port, csf, ssf);
    LOGGER.info("begin {0}", this);
  }

  @Override
  public void unexportMe() throws RemoteException {
    con.unexportRemoteObject(this);
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
    con.exportRemoteObject(delegate, port, csf, ssf);
    exportedDelegates.add(new WeakReference<>(delegate));
  }

  /**
   * Unexports given remote delegate.
   * <p>
   * Notice: when the session is closed, all still exported objects will be unexported.
   * This method is only necessary if a delegate must be explicitly unexported.
   *
   * @param delegate the delegate
   * @throws RemoteException if object not exported
   */
  public void unexportRemoteDelegate(RemoteDelegate delegate) throws RemoteException {
    con.unexportRemoteObject(delegate);
    for (Iterator<WeakReference<RemoteDelegate>> iter = exportedDelegates.iterator(); iter.hasNext(); ) {
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
   *
   * @param sessionInfo the session info
   * @throws LoginFailedException if login is not allowed for whatever reason
   */
  abstract public void verifySessionInfo(SessionInfo sessionInfo) throws LoginFailedException;


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
   * Gets the session db connection
   * @return the db connection
   */
  public Db getSession() {
    return db;
  }


  /**
   * Gets the server connection.
   *
   * @return the connection
   */
  public RemoteDbConnectionImpl getConnection() {
    return con;
  }


  /**
   * Gets the client user info.
   *
   * @return the client user info
   */
  @Override
  public SessionInfo getClientSessionInfo() {
    return clientInfo;
  }


  /**
   * Gets the server user info.
   *
   * @return the server user info
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
   * @param duration execution duration
   */
  public void countMethodInvocation(Method method, Class<?> servicedClass, Duration duration) {
    methodStats.countMethodInvocation(method, servicedClass, duration);
  }


  /**
   * Opens a new Db.<br>
   * The default implementation opens a new Db.
   * Can be overridden if, for example, pools are used instead.
   * @return the db connection
   * @throws LoginFailedException if opening the db failed
   */
  protected Db openDb() throws LoginFailedException {

    SessionPool dbPool = null;
    ConnectionManager conMgr = null;

    if (AbstractApplication.getRunningApplication() instanceof SessionPoolProvider) {
      dbPool = ((SessionPoolProvider) AbstractApplication.getRunningApplication()).getSessionPool();
    }
    else if (AbstractApplication.getRunningApplication() instanceof SessionManagerProvider) {
      conMgr = (ConnectionManager) ((SessionManagerProvider) AbstractApplication.getRunningApplication()).getSessionManager();
    }
    else  {
      throw new PersistenceException("no running application with Db persistence at all");
    }

    if (dbPool != null) {
      // if pooled
      try {
        Db pooledDb = (Db) dbPool.getSession();
        LOGGER.info("using {0}", pooledDb);
        return pooledDb;
      }
      catch (Exception ex) {
        throw new LoginFailedException("open Db failed", ex);
      }
    }
    else  {
      Db newDb = new Db(conMgr, serverInfo);
      newDb.open();
      return newDb;
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
    if (crashed) {
      LOGGER.warning("cleaning up crashed {0}{1}", this, DiagnosticUtilities.getInstance().createStackDump());
    }
    if (db != null) {
      db.setCrashed(crashed);
      db.rollbackImmediately(null);   // roll back if anything pending
    }
    doLogStatistics(Level.INFO, true);
  }


  /**
   * Logs the statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  protected void doLogStatistics(Level level, boolean clear) {
    methodStats.logStatistics(level, "    >RMI-Stats: ", clear);
  }


  /**
   * Closes the database connection (and thus rolls back any pending transaction).
   * If the db is pooled, it will be returned to the pool instead of being closed.
   * @param cleanup true if db must be physically closed even if pooled, due to cleanup
   */
  protected void closeDb(boolean cleanup) {
    if (db != null) {
      if (db.isPooled()) {
        db.rollbackImmediately(null); // roll back if anything pending
        if (cleanup) {
          db.close(); // closed db will be removed from the pool in putDb
          LOGGER.warning("pooled {0} closed due to session cleanup", db);
        }
        else  {
          // only return the db to the pool, don't close it
          LOGGER.info("returning {0} to pool", db);
        }
        // reset the user info for this db for sure (the application may have changed it to clientInfo)
        db.setSessionInfo(serverInfo);
        db.getPool().putSession(db);
      }
      else  {
        db.close();
      }
      db = null;    // closed -> to GC
      closedSince = System.currentTimeMillis();
    }
  }


  /**
   * Forces a cleanup if all cleanup and closing failed.
   * <p>
   * Simply marks the session closed and moves all references to GC.
   */
  protected void forceCleanup() {
    closedSince = System.currentTimeMillis();
    Db zombieDb = db;
    if (zombieDb != null) {
      db = null;  // block any further RMI requests (isOpen() == false)
      try {
        zombieDb.close();   // should work
        if (zombieDb.isPooled()) {
          zombieDb.getPool().putSession(zombieDb);
        }
      }
      catch (Exception ex) {
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
    buf.append(ReflectionHelper.getClassBaseName(getClass())).append('#').append(sessionNumber).append(": csf=");

    if (csf == null) {
      buf.append("<system>");
    }
    else {
      buf.append(ReflectionHelper.getClassBaseName(csf.getClass()));
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
      buf.append(ReflectionHelper.getClassBaseName(ssf.getClass()));
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
       .append(", user=").append(clientInfo)
       .append(", host=").append(clientHost);

    return buf.toString();
  }


  /**
   * Determines whether the session is open.
   *
   * @return true if session is open
   */
  public boolean isOpen() {
    return db != null;
  }


  /**
   * Returns whether this is grouped session.<br>
   * By default, user sessions are grouped, i.e. one for the modification thread
   * and one for the AWT/FX thread.
   *
   * @return true if user session
   */
  public boolean isGrouped() {
    return db.getSessionGroupId() != 0;
  }


  /**
   * Cleanup in case someone forgot to logoff().
   *
   * @throws java.lang.Throwable
   */
  @Override
  protected void finalize() throws Throwable {
    try {
      if (isOpen()) {
        LOGGER.warning("closing unreferenced open session: " + this);
      }
      close();    // cleanup in case client forgot
    }
    catch (Exception ex)  {
      try {
        LOGGER.severe("closing unreferenced session '" + this + "' failed in finalizer", ex);
      }
      catch (Exception ex2) {
        // don't stop finalization if just the logging failed
      }
    }
    finally {
      super.finalize();
    }
  }


  // ----------------- implements RemoteDbSession ------------------------


  @Override
  public String getServerName() throws RemoteException {
    try {
      return AbstractApplication.getRunningApplication().getName();
    }
    catch (Exception ex) {
      throw new RemoteException("getting application name failed", ex);
    }
  }


  @Override
  public void close() throws RemoteException {
    try {
      if (db != null) {
        LOGGER.info("end {0}", this);
        for (WeakReference<RemoteDelegate> ref : exportedDelegates) {
          RemoteDelegate delegate = ref.get();
          if (delegate != null) {
            con.unexportRemoteObject(delegate);
          }
        }
        exportedDelegates.clear();
        unexportMe();
        cleanup(false);
        closeDb(false);
      }
    }
    catch (Exception ex)  {
      throw new RemoteException("closing db failed", ex);
    }
  }


  @Override
  public void log(Level level, String message) throws RemoteException {
    try {
      LOGGER.log(level, message, null);
    }
    catch (Exception ex)  {
      throw new RemoteException("log() failed", ex);
    }
  }


  @Override
  public void logStatistics(Level level, boolean clear) throws RemoteException {
    try {
      doLogStatistics(level, clear);
    }
    catch (Exception ex) {
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
            Class<?> nextServicedClass = PdoUtilities.getInstance().determineServicedClass(currentClazz);
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
            if (currentClazz == Object.class) {
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
    catch (RemoteException remex) {
      throw remex;
    }
    catch (Exception ex)  {
      throw new RemoteException("coudn't create delegate for " + classname, ex);
    }
  }


  @Override
  public DbRemoteDelegate getDbRemoteDelegate() throws RemoteException  {
    try {
      DbRemoteDelegate delegate = createRemoteDelegate(DbRemoteDelegate.class, DbRemoteDelegateImpl.class, Db.class, null);
      exportRemoteDelegate(delegate);
      return delegate;
    }
    catch (Exception ex)  {
      throw new RemoteException("coudn't create delegate for " + db, ex);
    }
  }




  /**
   * Creates the classname of the remote delegate interface from the
   * serviced classname.<br>
   * The default implementation returns:
   * <tt>packagename + ".rmi." + classbasename + "RemoteDelegate"</tt>
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
   * @return the created
   * @throws InstantiationException
   * @throws NoSuchMethodException
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   * @throws InvocationTargetException
   */
  @SuppressWarnings("unchecked")
  public <T extends RemoteDelegate, I extends RemoteDelegateImpl<?>> T  createRemoteDelegateInstance(

          Class<T> delegateClass,
          Class<I> delegateImplClass,
          Class<?> clazz,
          Class<?> effectiveClass,
          Object... configArgs)

            throws InstantiationException, NoSuchMethodException, IllegalAccessException,
                   IllegalArgumentException, InvocationTargetException {

    // get the constructor with args (RemoteDbSessionImpl session, Class clazz)
    Constructor<I> constructor =
            effectiveClass == null ? delegateImplClass.getConstructor(RemoteDbSessionImpl.class, Class.class) :
                                     delegateImplClass.getConstructor(RemoteDbSessionImpl.class, Class.class, Class.class);
    // create instance of delegate for the session db
    T delegate = (T) (effectiveClass == null ? constructor.newInstance(this, clazz) :
                                               constructor.newInstance(this, effectiveClass, clazz));

    ((RemoteDelegateImpl) delegate).initialize();

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
   * @throws InstantiationException
   * @throws NoSuchMethodException
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   * @throws InvocationTargetException
   */
  @SuppressWarnings("unchecked")
  public <T extends RemoteDelegate, I extends RemoteDelegateImpl<?>> T createRemoteDelegate(

          Class<T> delegateClass,
          Class<I> delegateImplClass,
          Class<?> clazz,
          Class<?> effectiveClass,
          Object... configArgs)

            throws InstantiationException, NoSuchMethodException, IllegalAccessException,
                   IllegalArgumentException, InvocationTargetException {

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
  public boolean hasTimedOut() {
    if (db.isAlive()) {
      timeoutCount = 0;
    }
    else  {
      timeoutCount++;
    }
    return timeoutCount > timeout;
  }


  /**
   * Sets this session as being polled for timeout.
   */
  public void polled() {
    db.setAlive(false);
  }

}
