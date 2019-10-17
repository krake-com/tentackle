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
import org.tentackle.common.StringHelper;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultDbPool;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.dbms.MpxConnectionManager;
import org.tentackle.dbms.StatementStatistics;
import org.tentackle.dbms.rmi.RemoteDelegateInvocationHandler;
import org.tentackle.log.Logger;
import org.tentackle.pdo.OperationInvocationHandler;
import org.tentackle.pdo.PdoInvocationHandler;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;

/**
 * Base class for server applications.
 *
 * @author harald
 */
public abstract class AbstractServerApplication extends AbstractApplication
                                                implements SessionPoolProvider, ConnectionManagerProvider {

  private static final Logger LOGGER = Logger.get(AbstractServerApplication.class);

  private SessionPool sessionPool;                    // the session pool
  private ConnectionManager connectionManager;        // the connection manager
  private boolean runsInContainer;                    // true if application is running within a container (tomcat, EE)


  /**
   * Creates a server.
   *
   * @param name the server's name
   * @param version the application version
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
  protected boolean isServerImpl() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overwridden to protect the sessioninfo once it is set.
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
  protected void finishStartup() {
    super.finishStartup();
    connectionManager = createConnectionManager();
    sessionPool = createSessionPool();
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
        // wipe out old userinfo, i.e. force application to set the correct info
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
    return new MpxConnectionManager(getSession().getBackendInfo(), getSession().getSessionId() + 1);
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
   * Connects the server to the database backend.
   */
  protected void login() {
    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    String sessionPropsName = getProperty(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    configureSessionInfo(sessionInfo);

    Db serverDb = (Db) createSession(sessionInfo);
    serverDb.makeCurrent();

    setSessionInfo(sessionInfo);    // this will also make the session info immutable
    setDomainContext(createDomainContext(serverDb));
  }


  @Override
  protected void cleanup() {
    super.cleanup();

    if (sessionPool != null) {
      sessionPool.shutdown();
      sessionPool = null;
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
   * Deregisters all JDBC-Drivers loaded by the app's classloader.<br>
   * Necessary in containers only (in case connected directly via JDBC and not via JNDI).
   * Avoids memleaks.
   *
   * @param cl the app's classloader
   */
  protected void deregisterJdbcDrivers(ClassLoader cl) {
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      if (driver.getClass().getClassLoader() == cl) {
        try {
          LOGGER.info("deregistering JDBC driver {0}", driver);
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
    PdoInvocationHandler.INVOKER.setCollectingStatistics(true);
    OperationInvocationHandler.INVOKER.setCollectingStatistics(true);
    RemoteDelegateInvocationHandler.setCollectingStatistics(true);
    ManagedConnection.setCollectingStatistics(true);
    ManagedConnection.setLogLevelForParallelOpenResultSets(Logger.Level.INFO);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.warning("*** server killed ***");
      PdoInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, false);
      OperationInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, false);
      StatementStatistics.log(LOGGER, Logger.Level.INFO, false);
    }));
  }
}
