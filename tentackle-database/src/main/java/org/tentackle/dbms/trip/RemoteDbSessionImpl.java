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

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbUtilities;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.MethodStatistics;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.MasterSerialEvent;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;
import org.tentackle.session.auth.Authentication;
import org.tentackle.session.auth.AuthenticationManager;
import org.tentackle.session.auth.AuthenticationProvider;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteRoot;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.TransportClientInfo;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User session within the application server.
 *
 * @author  harald
 */
public abstract class RemoteDbSessionImpl implements RemoteDbSession, RemoteRoot {

  private static final Logger LOGGER = Logger.get(RemoteDbSessionImpl.class);


  /**
   * We keep an internal set of all sessions via WeakReferences, so the
   * sessions still will be finalized when the session isn't used anymore.
   * The set of sessions is used to determine stale sessions, i.e., ones
   * with timed-out sessions. These are typically caused by clients
   * not properly closing the TRIP-session.
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
    private final Class<RemoteDbDelegate> delegateClass;              // its delegate interface
    private final Class<RemoteDbDelegateImpl<?>> delegateImplClass;   // delegate implementation class

    private DelegateClasses(Class<?> effectiveClass, Class<?> clazz,
                            Class<RemoteDbDelegate> delegateClass, Class<RemoteDbDelegateImpl<?>> delegateImplClass) {
      this.effectiveClass = effectiveClass == clazz ? null : effectiveClass;
      this.clazz = clazz;
      this.delegateClass = delegateClass;
      this.delegateImplClass = delegateImplClass;
    }

    private DelegateClasses(Class<?> effectiveClass, Class<?> clazz, RemoteDbDelegateLocator.Result delegates) {
      this(clazz,
           effectiveClass == null ? delegates.effectiveClass() : effectiveClass,
           delegates.remoteDelegate(), delegates.remoteDelegateImpl());
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
  private static class Resources implements Runnable {

    private final RemoteDbConnectionImpl con;   // the connection object
    private final SessionInfo serverInfo;       // the server info (to restore after close)
    private final MethodStatistics methodStats; // method statistics (managed by invocation handler, null if no stats)
    private RemoteDbSessionImpl me;             // != null if programmatic close
    private String name;                        // the session name
    private Db db;                              // the local session
    private String mdcInfo;                     // the cached MDC info

    Resources(Db db, RemoteDbConnectionImpl con, SessionInfo serverInfo) {
      this.db = db;
      this.con = con;
      this.serverInfo = serverInfo;
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
        if (me != null) {
          UUID uuid = con.getTransport().getRegistry().lookup(me);
          if (uuid != null) {
            con.getTransport().getRegistry().unregister(uuid);
          }
          // else: already unregistered -> not an error
        }
      }
      catch (RuntimeException ex) {
        LOGGER.severe("closing session failed", ex);
      }
      finally {
        db = null;
        me = null;
      }
    }

    void cleanup(boolean crashed) {
      if (crashed) {
        DiagnosticUtilities.getInstance().logStackDump(Level.WARNING, "cleaning up crashed " + name);
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
            // reset the session info for this Db for sure (the application usually changed it to clientInfo)
            db.setSessionInfo(serverInfo);
            db.setExportedSessionGroupId(0);      // remove from the session group
          }

          db.getPool().put(db);    // this will work for MultiUserDbPool as well!
        }
        else {
          db.close();
        }
        db = null;    // closed -> to GC
      }
    }

    void doLogStatistics(Level level, boolean clear) {
      methodStats.logStatistics(mdcInfo, level, "    >TRP-Stats: ", clear);
    }
  }


  private final Resources resources;                          // resources to be cleaned up
  private final Cleanable cleanable;                          // to clean up the connection
  private final SessionInfo clientInfo;                       // saved client info
  private final SessionInfo serverInfo;                       // the server info for creating a new session
  private final long sessionNumber;                           // unique session number
  private final String clientHost;                            // the client host string
  private final Dictionary dictionary;                        // to prevent GC as long as this session is open
  private final Map<String, Logger> loggers;                  // logger-name :: logger
  private int timeout;                                        // timeout in polling intervals of the RemoteDbSessionCleanupThread
  private int timeoutCount;                                   // consecutive timeouts
  private long closedSince;                                   // session closed since, 0 if still open
  private Registry registry;                                  // the registry where the delegate is registered
  private UUID remoteId;                                      // the remote delegate ID of this session
  private RemoteDbSessionInvocationFilter invocationFilter;   // optional invocation filter
  private Authentication authentication;                      // the proven identity, null if not authenticated yet
  private final AtomicBoolean modificationTrackerSession;     // true if this the remote ModificationTracker's session
  private final Queue<MasterSerialEvent> masterSerialEvents;  // master serial event queue


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

    // use copies of the session infos to avoid side effects due to further modifications
    this.clientInfo = clientInfo.clone();
    this.clientInfo.setSessionName(clientInfo.getSessionName());  // sessionName was cleared in clone()
    if (!clientInfo.isCloned()) {
      this.clientInfo.clearCloned();    // un-clone, if the original session wasn't
    }

    this.serverInfo = serverInfo.clone();
    this.serverInfo.setSessionName(serverInfo.getSessionName());
    if (!serverInfo.isCloned()) {
      this.serverInfo.clearCloned();
    }

    sessionNumber = LAST_SESSION_NUMBER.incrementAndGet();

    Db db = null;
    try {
      TransportClientInfo transportInfo = TransportClientInfo.get();
      if (transportInfo != null) {
        // use only the host address since the port-number is the one at this moment
        // and is not fixed for further invocations for this session
        clientHost = transportInfo.getHost();
        // Since the dictionary is shared among all TRIP connections of the same client,
        // it must not be GC'd as long as a single session for this client is still open.
        // TCP connections remain open until explicitly closed.
        // QUIC streams, however, are only virtual connections, and they can be closed
        // and opened by the protocol at any time. So it could well be that there
        // is no stream at all temporarily.
        dictionary = transportInfo.dictionary();
      }
      else {
        clientHost = "?";
        dictionary = null;
      }
      db = openDb();
      resources = new Resources(db, con, this.serverInfo);
      synchronized (SESSIONS) {   // prevents race condition between verifySessionInfo and SESSIONS.add
        this.clientInfo.setSince(System.currentTimeMillis());
        db.makeCurrent();   // authentication providers may need the persistence layer to look up the user
        verifySessionInfo(this.clientInfo);
        db.setSessionInfo(this.clientInfo);  // switch to client info for the lifetime of this remote session
        SESSIONS.add(new WeakReference<>(this));
      }
      if (dictionary == null) {
        LOGGER.warning("no dictionary assigned to {0}", this);
      }
    }
    catch (RuntimeException ex) {
      if (db != null) {
        try {
          cleanup(false);
          closeDb(false);
        }
        catch (RuntimeException rx) {
          // maybe some overridden stuff failed in application
          LOGGER.warning("could not close session after failed login -> ignored", rx);
        }
      }
      if (ex instanceof LoginFailedException) {
        throw ex;
      }
      throw new PersistenceException("could not setup remote session", ex);
    }

    timeout = determineTimeout(db, clientInfo, serverInfo);
    loggers = new ConcurrentHashMap<>();

    modificationTrackerSession = new AtomicBoolean();
    masterSerialEvents = new ConcurrentLinkedQueue<>();

    resources.name = toString();    // chicken egg...
    cleanable = CLEANER.register(this, resources);
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + '#' + sessionNumber +
           ", timeout=" + timeout +
           ", client=" + clientInfo +
           ", host=" + clientHost;
  }

  /**
   * @inheritDoc
   * <p>
   * RemoteDbSessions, which are created by a {@link RemoteDbConnection} instance,
   * are the root of all remote delegates related to this session. When the session
   * is closed, the session should be unregistered, which in turn unregisters
   * all related delegates recursively.
   *
   * @param registry the registry
   * @param id the delegate ID
   */
  @Override
  public void setRegistryCoordinates(Registry registry, UUID id) {
    this.registry = registry;
    this.remoteId = id;
  }

  @Override
  public void unregisterFromRegistry() {
    if (registry != null && remoteId != null) {
      registry.unregister(remoteId);
    }
    registry = null;
    remoteId = null;
  }

  /**
   * Sets the invocation filter.
   *
   * @param invocationFilter the invocation filter, null for none (default)
   */
  public void setInvocationFilter(RemoteDbSessionInvocationFilter invocationFilter) {
    this.invocationFilter = invocationFilter;
  }

  /**
   * Gets the invocation filter.
   *
   * @return the invocation filter, null for none (default)
   */
  public RemoteDbSessionInvocationFilter getInvocationFilter() {
    return invocationFilter;
  }

  /**
   * Adds a master serial event.
   *
   * @param masterSerialEvent the event
   */
  public void addMasterSerialEvent(MasterSerialEvent masterSerialEvent) {
    if (isModificationTrackerSession()) {
      masterSerialEvents.add(masterSerialEvent);
    }
    else {
      throw new PersistenceException("not a modification tracker session");
    }
  }

  /**
   * Gets the next master serial event.<br>
   * The method is invoked by the {@link org.tentackle.session.ModificationTracker}.
   *
   * @return the next master serial event, null if none
   */
  public MasterSerialEvent pollMasterSerialEvent() {
    return masterSerialEvents.poll();
  }


  /**
   * Verifies and updates the client's session info.<br>
   * Checks the login credentials and sets the user id.
   * <p>
   * The default implementation hands the credentials to the {@link AuthenticationManager}, which
   * drives the chain of configured {@link AuthenticationProvider}s and applies the application's
   * {@link org.tentackle.session.auth.UserResolver}, and then transfers the proven identity onto
   * the session info via {@link #applyAuthentication(SessionInfo, Authentication)}.
   * <p>
   * Applications override this method to add concerns that belong to the <em>session</em> rather
   * than to the verification of the credentials, such as refusing a second login of the same user
   * or writing an audit record. They should invoke {@code super.verifySessionInfo(sessionInfo)}
   * first, so the credentials are still verified by the provider chain.
   * <p>
   * Throws LoginFailedException if login is not allowed for whatever reason.
   *
   * @param sessionInfo the session info
   */
  public void verifySessionInfo(SessionInfo sessionInfo) {
    authentication = AuthenticationManager.getInstance().authenticate(sessionInfo.getCredentials());
    applyAuthentication(sessionInfo, authentication);
  }

  /**
   * Transfers a proven identity onto the client's session info.
   * <p>
   * The username is taken over as well, since with single sign-on the client does not
   * necessarily know it before the login.
   *
   * @param sessionInfo the session info
   * @param authentication the proven identity
   */
  protected void applyAuthentication(SessionInfo sessionInfo, Authentication authentication) {
    sessionInfo.setUserId(authentication.getUserId());
    sessionInfo.setUserClassId(authentication.getUserClassId());
    if (authentication.getName() != null) {
      sessionInfo.setUserName(authentication.getName());
    }
  }

  /**
   * Gets the proven identity of this session.
   * <p>
   * Provides the application with everything the authentication established beyond the user id,
   * such as the verified claims and the roles asserted by an identity provider.
   *
   * @return the authentication, null if the application authenticated without the provider chain
   */
  public Authentication getAuthentication() {
    return authentication;
  }

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
   * Returns whether this is the remote session of the modification tracker.
   *
   * @return true if tracker's session
   */
  public boolean isModificationTrackerSession() {
    return modificationTrackerSession.get();
  }

  /**
   * Sets the modification tracker session flag.
   *
   * @param modificationTrackerSession true if tracker's session
   */
  public void setModificationTrackerSession(boolean modificationTrackerSession) {
    this.modificationTrackerSession.set(modificationTrackerSession);
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
   * @return true if the session is open
   */
  public boolean isOpen() {
    return resources.db != null;
  }


  @Override
  public void close() {
    cleanup(false);
    closeDb(false);
  }

  @Override
  public void log(String name, Level level, String message) {
    try {
      Logger logger = name == null ? LOGGER : loggers.computeIfAbsent(name, LoggerFactory::getLogger);
      logger.log(level, message);
    }
    catch (RuntimeException ex)  {
      throw new TripRuntimeException("log() failed", ex);
    }
  }

  @Override
  public void logStatistics(Level level, boolean clear) {
    try {
      doLogStatistics(level, clear);
    }
    catch (RuntimeException ex) {
      throw new TripRuntimeException("logStatistics failed", ex);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Remote> T getRemoteDelegate(String classname) {
    try {
      DelegateClasses delegateClasses = DELEGATE_CLASSES_MAP.get(classname);

      if (delegateClasses == null) {
        // try to find the remote class.
        // Use superclass if no direct implementation was found
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
                    RemoteDbDelegateLocator.getInstance().findRemoteDelegate(currentClazz));
            LOGGER.info("created remote delegate class mapping {0}", delegateClasses);
            DELEGATE_CLASSES_MAP.put(classname, delegateClasses);
            break;
          }
          catch (ClassNotFoundException e) {
            if (currentClazz == Object.class && nfe != null) {
              // abort with the first exception thrown
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
          throw new TripRuntimeException("no delegate class found for " + clazz, nfe);
        }
      }

      T delegate = (T) createRemoteDelegate(delegateClasses.delegateClass, delegateClasses.delegateImplClass,
                                            delegateClasses.clazz, delegateClasses.effectiveClass);

      LOGGER.fine("Delegate created for session={0}, class={1}",
                  this, delegateClasses.clazz);

      return delegate;
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException ex)  {
      throw new TripRuntimeException("couldn't create delegate for " + classname, ex);
    }
  }

  @Override
  public Remote[] getRemoteDelegates(String[] classnames) {
    Remote[] delegates = new Remote[classnames.length];
    for (int i = 0; i < classnames.length; i++) {
      try {
        delegates[i] = getRemoteDelegate(classnames[i]);
      }
      catch (RuntimeException rex) {
        // leave null: preloading must never break the session
        LOGGER.warning("cannot preload remote delegate for " + classnames[i], rex);
      }
    }
    LOGGER.fine("{0} delegates preloaded for session={1}", delegates.length, this);
    return delegates;
  }

  @Override
  public DbRemoteDelegate getRemoteDelegate() {
    try {
      return createRemoteDelegate(DbRemoteDelegate.class, DbRemoteDelegateImpl.class, Db.class, null);
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           InvocationTargetException ex)  {
      throw new TripRuntimeException("couldn't create delegate for " + resources.db, ex);
    }
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
   * @throws NoSuchMethodException if the delegate does not provide the necessary constructor
   * @throws IllegalAccessException if access denied
   * @throws IllegalArgumentException if wrong arguments
   * @throws InvocationTargetException if invocation failed
   */
  @SuppressWarnings("unchecked")
  public <T extends RemoteDbDelegate, I extends RemoteDbDelegateImpl<?>> T  createRemoteDelegateInstance(

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
    // create the instance of delegate for the session db
    T delegate = (T) (effectiveClass == null ? constructor.newInstance(this, clazz) :
                                               constructor.newInstance(this, effectiveClass, clazz));

    ((RemoteDbDelegateImpl<?>) delegate).initialize();

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
   * @throws NoSuchMethodException if the delegate does not provide the necessary constructor
   * @throws IllegalAccessException if access denied
   * @throws IllegalArgumentException if wrong arguments
   * @throws InvocationTargetException if invocation failed
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public <T extends RemoteDbDelegate, I extends RemoteDbDelegateImpl<?>> T createRemoteDelegate(

          Class<T> delegateClass,
          Class<I> delegateImplClass,
          Class<?> clazz,
          Class<?> effectiveClass,
          Object... configArgs)

            throws InstantiationException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

    return (T) Proxy.newProxyInstance(
            delegateImplClass.getClassLoader(),
            new Class<?>[] { delegateClass, Remote.class },
            new RemoteDbDelegateInvocationHandler((RemoteDbDelegateImpl)
                    createRemoteDelegateInstance(delegateClass, delegateImplClass, clazz, effectiveClass, configArgs)));
  }


  /**
   * Determines the session timeout count.<br>
   * The default implementation returns {@link TripServer#getSessionTimeout()}.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the session info from the client
   * @param serverInfo the session info to establish the connection to the database rmiServer
   * @return the timeout
   */
  protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return resources.con.getServer().getSessionTimeout();
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
          pooledDb = (Db) sessionPool.get();
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
   * Clean up the session.
   * <p>
   * The method is invoked whenever the session is closed
   * due to an ordinary logout or client crash.
   * The default implementation rolls back any pending transaction.
   *
   * @param crashed true if the client crashed, else regular logout
   */
  protected void cleanup(boolean crashed) {
    DbUtilities.getInstance().cleanupRemoteSession(this);
    unregisterFromRegistry();
    if (resources != null) {
      resources.cleanup(crashed);
    }
  }

  /**
   * Logs the TRIP-statistics.
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
   * @param cleanup true if db must be physically closed even if pooled, due to session cleanup
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
   * Forces a cleanup if the regular cleanup and closing failed.
   * <p>
   * Simply marks the session closed and moves all references to GC.
   */
  protected void forceCleanup() {
    closedSince = System.currentTimeMillis();
    Db zombieDb = resources.db;
    if (zombieDb != null) {
      resources.db = null;  // block any further TRIP requests (isOpen() == false)
      try {
        zombieDb.close();   // should work
        if (zombieDb.isPooled()) {
          zombieDb.getPool().put(zombieDb);
        }
      }
      catch (RuntimeException ex) {
        LOGGER.warning("forced cleanup may be incomplete", ex);
      }
    }
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
