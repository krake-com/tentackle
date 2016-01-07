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


package org.tentackle.app;

import java.util.Properties;
import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.SessionManager;
import org.tentackle.pdo.SessionManagerProvider;
import org.tentackle.pdo.SessionPool;
import org.tentackle.pdo.SessionPoolProvider;
import org.tentackle.persist.ConnectionManager;
import org.tentackle.persist.Db;
import org.tentackle.persist.DefaultDbPool;
import org.tentackle.persist.MpxConnectionManager;
import org.tentackle.persist.rmi.DbServer;
import org.tentackle.persist.rmi.RemoteDbConnectionImpl;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;



/**
 * Application Server.
 *
 * @author harald
 */
public abstract class ServerApplication extends AbstractApplication
       implements SessionPoolProvider, SessionManagerProvider {



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

  private CommandLine cmdLine;                 // command line
  private DbServer dbServer;                   // the RMI server instance
  private String sessionPropsName;             // db properties name
  private SessionManager sessionManager;       // the connection manager
  private Db serverDb;                         // the primary server db
  private SessionPool sessionPool;             // the session pool


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
  public CommandLine getCommandLine() {
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

    synchronized (this) {

      if (args != null) {
        cmdLine = new CommandLine(args);
        setProperties(cmdLine.getOptionsAsProperties());
      }

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
      catch (Exception e) {
        // doStop with error
        doStop(3, e);
      }
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
      unregister();   // not really necessary cause of System.exit in doStop...
      doStop(0);
    }
    catch (Exception e) {
      LOGGER.logStacktrace(e);
      // doStop with error
      doStop(4, e);
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
   * @param connectionClass the class of the connection object to instantiate, null = default or from serverInfo's properties file
   * @return the created DbServer
   * @throws ApplicationException
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
  public SessionManager createSessionManager() {
    return new MpxConnectionManager(serverDb);
  }


  /**
   * Gets the connection manager.<br>
   *
   * @return the connection manager.
   */
  @Override
  public SessionManager getSessionManager() {
    return sessionManager;
  }


  /**
   * Creates the logical SessionPool.
   * The default implementation creates a DefaultDbPool.
   *
   * @return the database pool, null if don't use a pool
   */
  public SessionPool createSessionPool() {

    return new DefaultDbPool((ConnectionManager) getSessionManager(), getSessionInfo()) {

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
   * Connects the server to the database backend.
   *
   * @throws org.tentackle.misc.ApplicationException
   */
  protected void doLogin() throws ApplicationException {

    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    sessionPropsName = getProperty(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);

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
   * @throws org.tentackle.misc.ApplicationException
   */
  @Override
  protected void doFinishStartup() throws ApplicationException {

    super.doFinishStartup();

    // add a shutdown handler in case the modthread terminates unexpectedly
    PdoTracker.getInstance().addShutdownRunnable(() -> {
      LOGGER.severe("*** emergency shutdown ***");
      stop();
    });

    sessionManager = createSessionManager();
    sessionPool = createSessionPool();
    dbServer = createDbServer(connectionClass);
  }


  /**
   * Starts the RMI-server.
   * The default implementation just does {@code dbServer.start()}.
   * @throws ApplicationException
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

    if (exitValue != 0 || ex != null) {
      LOGGER.warning("application server abnormally terminated with exit code " + exitValue);
      if (ex != null) {
        LoggerOutputStream.logException(ex, LOGGER);
      }
    }
    else {
      LOGGER.info("application server terminated");
    }

    try {

      // terminate all helper threads
      Pdo.terminateHelperThreads();

      if (sessionPool != null) {
        sessionPool.shutdown();
        sessionPool = null;
      }

      if (serverDb != null) {
        serverDb.close();
        serverDb = null;
      }

      if (sessionManager != null) {
        sessionManager.shutdown();
        sessionManager = null;
      }

      if (dbServer != null) {
        dbServer.stop();
        dbServer = null;
      }
    }
    catch (Exception anyEx) {
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


