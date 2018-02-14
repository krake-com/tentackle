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


package org.tentackle.persist.app;

import java.rmi.RemoteException;
import java.util.Properties;
import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.CommandLine;
import org.tentackle.common.StringHelper;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultDbPool;
import org.tentackle.dbms.MpxConnectionManager;
import org.tentackle.dbms.rmi.DbServer;
import org.tentackle.dbms.rmi.RemoteDbConnectionImpl;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;



/**
 * Application Server.
 *
 * @author harald
 */
public abstract class ServerApplication extends AbstractApplication
       implements SessionPoolProvider, ConnectionManagerProvider {



  /**
   * Gets the running server application.<br>
   *
   * @return the application
   */
  public static ServerApplication getServerApplication() {
    return (ServerApplication) getRunningApplication();
  }


  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ServerApplication.class);


  private final Class<? extends RemoteDbConnectionImpl> connectionClass;   // the connection class

  private CommandLine cmdLine;                        // command line
  private DbServer dbServer;                          // the RMI server instance
  private ConnectionManager connectionManager;        // the connection manager
  private Db serverDb;                                // the primary server db
  private SessionPool sessionPool;                    // the session pool
  private boolean stopping;                           // true if server is stopping


  /**
   * Creates an instance of an application server.
   *
   * @param name the application name
   * @param connectionClass the class of the connection object to instantiate, null = default or from serverInfo's properties file
   */
  public ServerApplication(String name, Class<? extends RemoteDbConnectionImpl> connectionClass) {
    super(name);
    this.connectionClass = connectionClass;
  }


  /**
   * Gets the command line.
   *
   * @return the command line
   */
  public synchronized CommandLine getCommandLine() {
    return cmdLine;
  }


  /**
   * Gets the RMI server.
   *
   * @return the RMI dbserver
   */
  public DbServer getDbServer() {
    return dbServer;
  }


  @Override
  public boolean isServer() {
    return true;
  }


  /**
   * Starts the application server.
   *
   * @param args the arguments (usually from commandline)
   */
  public void start(String[] args) {

    cmdLine = new CommandLine(args);
    setProperties(cmdLine.getOptionsAsProperties());

    try {

      LOGGER.fine("register application server");
      // make sure that only one application is running at a time
      register();

      LOGGER.fine("initialize application server");
      // doInitialize environment
      doInitialize();

      LOGGER.fine("login to backend");
      // login to the database server
      doLogin();

      LOGGER.fine("configure application server");
      // configure the server
      doConfigureApplication();

      LOGGER.fine("finish startup");
      // finish startup and start the RMI service
      doFinishStartup();

      LOGGER.fine("start services");
      // start the RMI-server
      doStartDbServer();
    }
    catch (ApplicationException | RuntimeException e) {
      // doStop with error
      doStop(3, e);
    }
  }


  /**
   * Starts the application server without further arguments.
   */
  public void start() {
    start(null);
  }


  /**
   * Gracefully terminates the application server.
   */
  public void stop() {
    try {
      doStop(0);
    }
    catch (Exception e) {
      LOGGER.logStacktrace(e);
    }
    finally {
      try {
        unregister();
      }
      catch (ApplicationException ex) {
        LOGGER.logStacktrace(ex);
      }
    }
  }



  @Override
  protected void configurePreferences() {
    super.configurePreferences();
    PersistedPreferencesFactory.getInstance().setSystemOnly(true);
  }


  /**
   * Creates the DbServer instance (but does not start it).<br>
   * The default implementation creates a {@link DbServer}.
   *
   * @param connectionClass the class of the connection object to instantiate,
   *                        null = default or from serverInfo's properties file
   * @return the created DbServer
   * @throws ApplicationException if creating the DbServer failed
   */
  protected DbServer createDbServer(Class<? extends RemoteDbConnectionImpl> connectionClass) throws ApplicationException  {
    return new DbServer(getSessionInfo(), connectionClass);
  }



  /**
   * Creates the connection manager for the client sessions.
   * The default creates an MpxConnectionManager.
   *
   * @return the connection manager
   */
  public ConnectionManager createConnectionManager() {
    return new MpxConnectionManager(serverDb.getBackendInfo(), serverDb.getSessionId() + 1);
  }


  /**
   * Gets the connection manager.<br>
   *
   * @return the connection manager.
   */
  @Override
  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }


  /**
   * Creates the logical SessionPool.
   * The default implementation creates a DefaultDbPool.
   *
   * @return the database pool, null if don't use a pool
   */
  public SessionPool createSessionPool() {

    return new DefaultDbPool(getConnectionManager(), getSessionInfo()) {

      @Override
      public Db getSession() {
        Db db = super.getSession();
        // get a fresh copy of a user info.
        // wipe out old userinfo, i.e. force application to set the correct info and security manager
        SessionInfo sessionInfo = getSessionInfo().clone();
        db.setSessionInfo(sessionInfo);
        sessionInfo.setUserId(0);
        sessionInfo.setUserClassId(0);
        sessionInfo.setUserName(null);
        return db;
      }
    };
  }


  @Override
  public SessionPool getSessionPool() {
    return sessionPool;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overwridden to protect the sessioninfo once it is set.
   */
  @Override
  public void setSessionInfo(SessionInfo sessionInfo) {
    if (sessionInfo == null) {
      throw new NullPointerException("userinfo must not be null");
    }
    if (getSessionInfo() != null) {
      throw new PersistenceException("userinfo already set, cannot be changed in a running server");
    }
    sessionInfo.setImmutable(true);
    super.setSessionInfo(sessionInfo);
  }


  /**
   * Configures the session info.
   *
   * @param sessionInfo the session info
   */
  protected void configureSessionInfo(SessionInfo sessionInfo) {
    // load properties
    Properties sessionProps = null;
    try {
      // try from filesystem first
      sessionProps = sessionInfo.getProperties();
    }
    catch (PersistenceException e1) {
      // neither properties file nor in classpath: set props
      sessionInfo.setProperties(getProperties());
    }

    if (sessionProps != null) {
      // merge (local properties override those from file or classpath)
      for (String key: getProperties().stringPropertyNames()) {
        sessionProps.setProperty(key, getProperties().getProperty(key));
      }
      sessionInfo.setProperties(sessionProps);
    }

    if (sessionInfo.getApplicationName() == null) {
      sessionInfo.setApplicationName(ReflectionHelper.getClassBaseName(getClass()));
    }

    sessionInfo.applyProperties();
  }


  /**
   * Connects the server to the database backend.
   *
   * @throws ApplicationException if login failed
   */
  protected void doLogin() throws ApplicationException {

    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    String sessionPropsName = getProperty(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    configureSessionInfo(sessionInfo);

    if (serverDb != null) {
      throw new ApplicationException("only one server application instance allowed");
    }
    serverDb = (Db) createSession(sessionInfo);

    /**
     * If the db-properties file contained the login data (which is very often the case)
     * copy that login data to the userinfo.
     */
    username = serverDb.getBackendInfo().getUser();
    if (username != null) {
      sessionInfo.setUserName(username);
    }
    char[] passwd = serverDb.getBackendInfo().getPassword();
    if (passwd != null && passwd.length > 0 && passwd[0] != 0) {
      // if not cleared
      sessionInfo.setPassword(passwd);
    }

    // open the database connection
    serverDb.open();

    serverDb.makeCurrent();

    setSessionInfo(sessionInfo);    // this will also make the session info immutable

    // create the default context
    DomainContext context = createDomainContext(serverDb);
    if (context == null) {
      throw new ApplicationException("creating the database context failed");
    }

    setDomainContext(context);
  }



  /**
   * Finishes the startup.<br>
   * The default implementation starts the modification thread, unless
   * {@code "--nomodthread"} given, creates the connection manager, the Db pool,
   * and the DbServer instance.
   *
   * @throws ApplicationException if finish failed
   */
  @Override
  protected void doFinishStartup() throws ApplicationException {

    super.doFinishStartup();

    // add a shutdown handler in case the modthread terminates unexpectedly
    ModificationTracker.getInstance().addShutdownRunnable(() -> doStop(5));

    connectionManager = createConnectionManager();
    sessionPool = createSessionPool();
    dbServer = createDbServer(connectionClass);
  }


  /**
   * Starts the RMI-server.
   * The default implementation just does {@code dbServer.start()}.
   * @throws ApplicationException if starting the dbServer failed
   */
  protected void doStartDbServer() throws ApplicationException {
    dbServer.start();
  }


  /**
   * Terminates the application server.
   *
   * @param exitValue the doStop value for System.exit()
   * @param ex an exception causing the termination, null if none
   */
  protected void doStop(int exitValue, Exception ex) {

    synchronized(this) {
      if (stopping) {
        return;
      }
      stopping = true;
    }

    LOGGER.info("terminating server {0} with exit value {1} ...", getName(), exitValue);

    if (ex != null) {
      LoggerOutputStream.logException(ex, LOGGER);
    }

    try {

      /**
       * kill all sessions and GC resources.
       */
      for (RemoteDbSessionImpl session: RemoteDbSessionImpl.getOpenSessions()) {
        try {
          session.close();
        }
        catch (RuntimeException rex) {
          LOGGER.warning("closing pending session " + session + " failed:", rex);
        }
      }

      // terminate all helper threads
      Pdo.terminateHelperThreads();

      RemoteDbSessionImpl.stopCleanupThread();

      if (sessionPool != null) {
        sessionPool.shutdown();
        sessionPool = null;
      }

      if (serverDb != null) {
        serverDb.close();
        serverDb = null;
      }

      if (connectionManager != null) {
        connectionManager.shutdown();
        connectionManager = null;
      }

      if (dbServer != null) {
        dbServer.stop();
        dbServer = null;
      }

      if (isRunningInContainer()) {
        // deregister all JDBC drivers loaded by the server app's classloader
        deregisterJdbcDrivers(Thread.currentThread().getContextClassLoader());
      }
    }
    catch (RemoteException | ApplicationException | RuntimeException anyEx) {
      LOGGER.severe("server application stopped ungracefully", anyEx);
    }

    if (!isRunningInContainer()) {
      System.exit(exitValue);
    }
  }


  /**
   * Terminates the application server.
   *
   * @param exitValue the doStop value for System.exit()
   */
  protected void doStop(int exitValue) {
    doStop(exitValue, null);
  }

}


