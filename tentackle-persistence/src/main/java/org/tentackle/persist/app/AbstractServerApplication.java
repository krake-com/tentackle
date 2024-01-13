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

package org.tentackle.persist.app;

import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbPool;
import org.tentackle.dbms.DbTransactionFactory;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.dbms.MpxConnectionManager;
import org.tentackle.dbms.StatementStatistics;
import org.tentackle.dbms.rmi.RemoteDelegateInvocationHandler;
import org.tentackle.log.Logger;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolFactory;
import org.tentackle.session.SessionPoolProvider;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCache;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Base class for server applications.
 *
 * @author harald
 */
public abstract class AbstractServerApplication extends AbstractApplication
                                                implements SessionPoolProvider, ConnectionManagerProvider {

  private static final Logger LOGGER = Logger.get(AbstractServerApplication.class);

  private SessionPool sessionPool;                    // the session pool for local sessions
  private MultiUserSessionPool remoteSessionPool;     // the session pool for remote sessions
  private ConnectionManager connectionManager;        // the connection manager
  private boolean runsInContainer;                    // true if application is running within a container (tomcat, EE)


  /**
   * Creates a server.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for default version
   */
  public AbstractServerApplication(String name, String version) {
    super(name, version);
    detectContainer();
  }

  @Override
  public SessionPool getSessionPool() {
    return sessionPool;
  }

  @Override
  public MultiUserSessionPool getRemoteSessionPool() {
    return remoteSessionPool;
  }

  @Override
  public boolean isServer() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to protect the SessionInfo once it is set.
   */
  @Override
  protected void setSessionInfo(SessionInfo sessionInfo) {
    Objects.requireNonNull(sessionInfo, "sessionInfo");
    if (getSessionInfo() != null) {
      throw new PersistenceException("session info already set, cannot be changed in a running server");
    }
    sessionInfo.setImmutable(true);
    super.setSessionInfo(sessionInfo);
  }

  @Override
  protected void configurePreferences() {
    super.configurePreferences();
    PersistedPreferencesFactory.getInstance().setSystemOnly(true);
  }

  @Override
  protected void configureModificationTracker() {
    super.configureModificationTracker();
    ModificationTracker.getInstance().setSleepInterval(1000);
  }

  @Override
  protected void initializeScripting() {
    super.initializeScripting();
    validateValidators();
  }

  /**
   * Validates the validators.<br>
   * The default implementation loads all {@link Validator}s of all PDO classes and
   * invokes {@link Validator#validate()}. This will load all {@link org.tentackle.misc.CompoundValue}s
   * and compiles the scripts, if any.
   */
  protected void validateValidators() {
    LOGGER.info("validating validators");
    for (String pdoName: PdoFactory.getInstance().getPersistenceMapper().getMap().keySet()) {
      Class<?> pdoClass = PdoFactory.getInstance().create(pdoName).getEffectiveClass();
      for (Validator validator: ValidatorCache.getInstance().getFieldValidators(pdoClass)) {
        validator.validate();
      }
    }
  }

  @Override
  protected void finishStartup() {
    super.finishStartup();
    if (getSession().isRemote()) {
      remoteSessionPool = createRemoteSessionPool();
    }
    else {
      connectionManager = createConnectionManager();
      sessionPool = createSessionPool();
    }
  }


  /**
   * Creates the session pool to serve the client sessions.
   *
   * @return the session pool
   */
  public SessionPool createSessionPool() {
    // cannot be done via SessionPoolFactory because the pool uses another connection manager,
    // and ConnectionManager is low-level persistence layer, i.e. not available to the factory's module.
    return new DbPool(getConnectionManager(), getSessionInfo()) {

      @Override
      public Db getSession() {
        Db db = super.getSession();
        // get a fresh copy of a session info.
        // wipe out old session info, i.e. force application to set the correct info
        SessionInfo sessionInfo = getSessionInfo().clone();
        db.setSessionInfo(sessionInfo);
        sessionInfo.setUserId(0);
        sessionInfo.setUserClassId(0);
        sessionInfo.setUserName(null);
        sessionInfo.clearPassword();
        return db;
      }
    };
  }

  /**
   * Creates the remote session pool to serve the client sessions.
   *
   * @return the session pool
   */
  public MultiUserSessionPool createRemoteSessionPool() {
    // 100 remote sessions should be ok for most proxy rmi servers. Should be smaller than the local server's pool maxSize anyway.
    // Timeouts make no sense at all for pooling of remote client sessions ("get" when logged in and "close with put" on logout,
    // i.e. there are no idle sessions and the session must remain open until logout).
    if (getSession().getSessionGroupId() == 0) {
      // if not grouped so far, build a new session group within the remote server with a single session at start
      // so that all sessions of the session pool are grouped properly with the server's main session.
      // this will close all those sessions if the main session died due to a modtracker crash or whatever.
      getSession().groupWith(getSession().getSessionId());
    }
    return SessionPoolFactory.getInstance().create("remote-pool", getSession(), 0, 100, 0L, 0L);
  }

  @Override
  public Db getSession() {
    return (Db) super.getSession();
  }

  /**
   * Creates the connection manager for the client sessions.
   * The default creates an MpxConnectionManager.
   *
   * @return the connection manager
   */
  protected ConnectionManager createConnectionManager() {
    return new MpxConnectionManager(getSession().getBackendInfo(),
                                    getSession().getConnectionManager().getSessionIdOffset() +
                                    getSession().getConnectionManager().getMaxSessions());
  }


  @Override
  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }


  /**
   * Configures the session info.
   *
   * @param sessionInfo the session info
   */
  protected void configureSessionInfo(SessionInfo sessionInfo) {
    sessionInfo.setSessionName("SRV");    // server's main session (will be used by the modtracker)

    // load properties
    EncryptedProperties sessionProps = null;
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

    sessionInfo.applyProperties();
    applyProperties(sessionInfo.getProperties());
  }


  @Override
  protected void startup() {
    LOGGER.fine("register application server");
    // make sure that only one application is running at a time
    register();

    LOGGER.fine("initialize application server");
    // initialize environment
    initialize();

    LOGGER.fine("login to backend");
    // login to the database server
    login();

    LOGGER.fine("configure application server");
    // configure the server
    configure();

    LOGGER.fine("finish startup");
    // finish startup and start the RMI service
    finishStartup();
  }


  /**
   * Connects the server to the database backend or another remote server.
   */
  protected void login() {
    String username = getProperty(Constants.BACKEND_USER);
    char[] password = getPropertyAsChars(Constants.BACKEND_PASSWORD);
    String sessionPropsName = getProperty(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    configureSessionInfo(sessionInfo);

    Db serverDb = (Db) createSession(sessionInfo);
    serverDb.makeCurrent();

    setSessionInfo(sessionInfo);    // this will also make the session info immutable
    setDomainContext(createDomainContext(serverDb));

    DbTransactionFactory.getInstance().configure(sessionInfo.getProperties());
  }


  @Override
  protected void cleanup() {
    super.cleanup();

    if (sessionPool != null) {
      sessionPool.shutdown();
      sessionPool = null;
    }

    if (remoteSessionPool != null) {
      remoteSessionPool.shutdown();
      remoteSessionPool = null;
    }

    if (connectionManager != null) {
      connectionManager.shutdown();
      connectionManager = null;
    }

    if (isRunningInContainer()) {
      // deregister all JDBC drivers loaded by the server app's classloader
      deregisterJdbcDrivers(Thread.currentThread().getContextClassLoader());
    }
  }


  /**
   * Indicates whether this application is running within a container.
   *
   * @return true if running in a container, false if not
   */
  public boolean isRunningInContainer() {
    return runsInContainer;
  }

  @Override
  protected boolean isSystemExitNecessaryToStop() {
    return !isRunningInContainer();
  }


  /**
   * Detects whether this application is running within a container.<br>
   * The result can be retrieved by {@link #isRunningInContainer()}.
   */
  protected void detectContainer() {
    runsInContainer = false;
    try {
      new InitialContext().lookup("java:comp/env");
      // the lookup worked, we're in a web container
      runsInContainer = true;
      LOGGER.info("container detected");
    }
    catch (NamingException ex) {
      LOGGER.info("not running within a container");
    }
  }


  /**
   * De-registers all JDBC-Drivers loaded by the app's classloader.<br>
   * Necessary in containers only (in case connected directly via JDBC and not via JNDI).
   * Avoids memory leaks.
   *
   * @param cl the app's classloader
   */
  protected void deregisterJdbcDrivers(ClassLoader cl) {
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver.getClass().getClassLoader() == cl) {
        try {
          LOGGER.info("de-registering JDBC driver {0}", driver);
          DriverManager.deregisterDriver(driver);
        }
        catch (SQLException sx) {
          LOGGER.severe("failed to deregister JDBC driver {0}", driver, sx);
        }
      }
      else {
        LOGGER.fine("JDBC driver {0} skipped because it does not belong to this app's ClassLoader", driver);
      }
    }
  }

  @Override
  protected void activateStatistics() {
    super.activateStatistics();
    RemoteDelegateInvocationHandler.setCollectingStatistics(true);    // will be logged when session is closed
    ManagedConnection.setCollectingStatistics(true);
    ManagedConnection.setLogStatisticsMillis(Constants.SECOND_MS * 30);
    ManagedConnection.setLogLevelForParallelOpenResultSets(Logger.Level.INFO);
    DbTransactionFactory.getInstance().setCollectingStatistics(true);
  }

  /**
   * Logs and clears the statistics.
   */
  public void logStatistics() {
    super.logStatistics();
    StatementStatistics.log(Logger.Level.INFO, true);
    DbTransactionFactory.getInstance().logStatistics(Logger.Level.INFO, true);
  }

}
