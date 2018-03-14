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


package org.tentackle.web.app;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Properties;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.ConnectionManagerProvider;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DefaultDbPool;
import org.tentackle.dbms.MpxConnectionManager;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolProvider;

/**
 * Web AbstractApplication.
 * <p>
 Web applications usually run in a container such as glassfish or
 in case of JRuby/Rails in a pack of mongrels or a single webrick.
 Because we cannot make any assumptions about the threading model
 (single thread/single instance/single jvm as with JRuby/Webrick or
 full-blown multithreaded as in glassfish or half-way in between
 like JRuby with mongrel_jcluster) the WebAbstractApplication provides a
 logical Db-pool on top of multiplexed physical database connections.
 The initial size of the logical Db pool is 2 on 1 physical connection.
 This will not waste resources in single thread per JVM setups
 (like Rails/Webrick) but will dynamically grow otherwise.
 <p>
 * Web applications need some mapping between sessions and
 * {@link SessionInfo}-objects that in turn carry the user id
 * and a {@link SecurityManager}. For the time of a web-roundtrip
 * a logical Db is picked from the pool and associated with that
 * user info (i.e. "session"). Notice that there may be more than
 * one session per user (but each gets its own user info). That's
 * why user infos are mapped by a session key object and not by
 * the user id. Furthermore, depending on the container's session
 * model, it is not sure that the container's session carries
 * the user id at all.
 *
 * @author harald
 * @param <T> the web session key type
 */
public abstract class WebApplication<T> extends AbstractApplication
       implements SessionPoolProvider, ConnectionManagerProvider {


  private static final Logger LOGGER = Logger.get(WebApplication.class);

  /**
   * Gets the current web application instance.<p>
   *
   * This is just a convenience method to {@link AbstractApplication#getRunningApplication()}.
   *
   * @param <T> the web session key type
   * @return the application, null if not started yet
   */
  @SuppressWarnings("unchecked")
  public static <T> WebApplication<T> getWebApplication() {
    return (WebApplication<T>) getRunningApplication();
  }


  /**
   * Gets the current web application instance or
   * starts a new instance if not already running.
   * <p>
   * Web containers should use this method to make sure
   * that only one instance is started per JVM/classloader-context.
   * <p>
   * If the application runs in a container (tomcat, glassfish, etc...) additional
   * properties may be appended/overwritten to/in the given <tt>props</tt>-argument.
   * In <tt>web.xml</tt> add the following lines:
   * <pre>
       &lt;env-entry&gt;
          &lt;env-entry-name&gt;tentackle.properties&lt;/env-entry-name&gt;
          &lt;env-entry-value&gt;../myapplication.properties&lt;/env-entry-value&gt;
          &lt;env-entry-type&gt;java.lang.String&lt;/env-entry-type&gt;
        &lt;/env-entry&gt;
   * </pre>
   *
   * This will load the properties-file <tt>myapplication.properties</tt> from
   * the <tt>WEB-INF</tt>-directory (without <tt>"../"</tt> from <tt>WEB-INF/classes</tt>)
   * and modify the properties given by <tt>props</tt>.
   * <p>
   * Throws TentackleRuntimeException creating application instance failed.
   *
   * @param <T> the web session key type
   * @param clazz the application class
   * @param props the properties to configure the application
   * @return the application
   */
  public static <T> WebApplication<T> getInstance(Class<? extends WebApplication<T>> clazz, Properties props) {

    synchronized(AbstractApplication.class) {

      WebApplication<T> app = getWebApplication();
      if (app == null) {
        try {
          app = clazz.getDeclaredConstructor().newInstance();
          if (app.isRunningInContainer()) {
            // if in container: add extra properties
            javax.naming.Context env = (javax.naming.Context) new InitialContext().lookup("java:comp/env"); // must work!
            try {
              // find <env-entry> for tentackle.properties in web.xml
              String filename = (String) env.lookup("tentackle.properties");
              try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {
                Properties extraProps = new Properties();
                extraProps.load(is);
                // set the properties
                for (String key: extraProps.stringPropertyNames()) {
                  props.setProperty(key, extraProps.getProperty(key));
                }
              }
            }
            catch (NamingException | IOException ex) {
              // no extra properties
            }
          }

          // start the app
          app.start(props);
        }
        catch (IllegalAccessException | InstantiationException | NoSuchMethodException |
               InvocationTargetException | NamingException | RuntimeException ex) {
          if (app != null) {
            app.doStop(4);
          }
          throw new TentackleRuntimeException("creating application instance failed: " + ex.getMessage(), ex);
        }
      }
      return app;
    }
  }





  private WebSessionKeyCache<T> sessionCache;   // the web session cache
  private ConnectionManager connectionManager;  // the connection manager
  private Db serverDb;                          // the primary server db
  private SessionPool sessionPool;              // the session pool



  /**
   * Creates an instance of a web application.
   * @param name the application name
   */
  public WebApplication(String name) {
    super(name);
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
   * Returns whether the application is a server.
   *
   * @return true
   */
  @Override
  public boolean isServer() {
    return true;
  }


  @Override
  protected void configurePreferences() {
    super.configurePreferences();
    PersistedPreferencesFactory.getInstance().setSystemOnly(true);
  }


  /**
   * Adds a mapping between a web session key and a user session info.<br>
   * This is usually done in the login controller.
   * If a session with that key already exists, the user info
   * will be replaced.
   *
   * @param sessionKey the (unique) web session key
   * @param userInfo the user info
   */
  public void addWebSession(T sessionKey, SessionInfo userInfo) {
    sessionCache.addSession(sessionKey, userInfo);
  }


  /**
   * Removes a mapping between a web session key and a user session info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) web session key
   */
  public void removeWebSession(T sessionKey) {
    sessionCache.removeSession(sessionKey);
  }


  /**
   * Gets the web session keys for a user session info.<br>
   *
   * @param sessionInfo the user session info
   * @return the web session keys, never null
   */
  public Collection<T> getSessionKeys(SessionInfo sessionInfo) {
    return sessionCache.getSessionKeys(sessionInfo);
  }


  /**
   * Gets a persistence session from the session pool by a web session key.<br>
   *
   * @param sessionKey the web session key
   * @return the attached session or null if no such session
   */
  public Session getSession(T sessionKey) {
    SessionInfo sessionInfo = sessionCache.getSession(sessionKey);
    if (sessionInfo != null) {
      // not cleared so far: we can use it
      Db db = (Db) sessionPool.getSession();
      db.setSessionInfo(sessionInfo); // attach userinfo
      return db;
    }
    else  {
      return null;
    }
  }


  /**
   * Release a persistence session.<br>
   * Should be invoked after sending/rendering the response to the web browser.
   *
   * @param session the persistence session to release
   */
  public void putSession(Session session) {
    /**
     * dereference user info (possible target for the GC).
     * If (due to tight memory or timeout) the userinfo gets garbage
     * collected the user must login again.
     */
    ((Db) session).setSessionInfo(null);
    sessionPool.putSession(session);
  }




  /**
   * Starts the application.
   *
   * @param props the properties to configure the application
   * @see AbstractApplication#setProperties(java.util.Properties)
   */
  public void start(Properties props) {
    synchronized (this) {
      sessionCache = new WebSessionKeyCache<>();

      setProperties(props);

      LOGGER.fine("register application");
      // make sure that only one application is running at a time
      register();

      LOGGER.fine("initialize application");
      // doInitialize environment
      doInitialize();

      LOGGER.fine("login to backend");
      // login to the database server
      doLogin();

      LOGGER.fine("configure application");
      // configure the server
      doConfigureApplication();

      LOGGER.fine("finish startup");
      // finish startup and start the RMI service
      doFinishStartup();
    }
  }


  /**
   * Gracefully terminates the web application server.
   */
  public void stop() {
    unregister();
    doStop(0);
  }


  /**
   * Creates the connection cache for the client sessions.
   * The default creates an MpxConnectionManager with initially 1 open
   * connection, max 100.
   *
   * @return the connection cache
   */
  public ConnectionManager createConnectionManager() {
    return new MpxConnectionManager("webmpx-mgr", serverDb.getBackendInfo(), 100, serverDb.getSessionId() + 1,
                                    1, 1, 1, 100, 720, 2160);
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
   * Creates the logical SessionPool.<br>
   * The default implementation creates a DefaultDbPool with a
   * 2 pre-opened Db, increment by 1, don't drop below 2.
   * The maximum number of Db instances is derived from the
   * connection cache. Because of the nature of web applications,
   * it doesn't make sense to allow more Db instances than connections
   * (web sessions are short lived as opposed to desktop sessions).
   *
   * @return the database pool, null if don't use a pool
   */
  public SessionPool createSessionPool() {
    return new DefaultDbPool("web-pool", connectionManager, getSessionInfo(), 2, 1, 2,
                             connectionManager.getMaxSessions(), 60, 24*60);
  }

  @Override
  public SessionPool getSessionPool() {
    return sessionPool;
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
   * The database properties may be either given by the "db" property
   * or if this is missing the application's properties will be used.
   * <p>
   * Throws TentackleRuntimeException if a server instance is already running.
   */
  protected void doLogin() {

    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    String sessionPropertiesName = getProperty(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropertiesName);
    configureSessionInfo(sessionInfo);
    setSessionInfo(sessionInfo);

    if (serverDb != null) {
        throw new TentackleRuntimeException("only one server application instance allowed");
    }
    serverDb = (Db) createSession(sessionInfo);

    // createPdo the default context
    DomainContext context = createDomainContext(serverDb);
    setDomainContext(context);
  }


  @Override
  protected void doFinishStartup() {

    super.doFinishStartup();

    // add a shutdown handler in case the modthread terminates unexpectedly
    ModificationTracker.getInstance().addShutdownRunnable(() -> {
      LOGGER.severe("*** emergency shutdown ***");
      doStop(5);
    });

    connectionManager = createConnectionManager();
    sessionPool = createSessionPool();

    // start the session cache
    sessionCache.startup(300000);    // 5 minutes
  }


  /**
   * Terminates the application server.
   *
   * @param exitValue the doStop value for System.exit()
   * @param ex an exception causing the termination, null if none
   */
  protected void doStop(int exitValue, Exception ex) {

    if (exitValue != 0 || ex != null) {
      LOGGER.warning("web server abnormally terminated with exit code " + exitValue);
      if (ex != null) {
        LoggerOutputStream.logException(ex, LOGGER);
      }
    }
    else {
      LOGGER.info("web server terminated");
    }

    try {

      // terminate all helper threads
      Pdo.terminateHelperThreads();

      if (sessionCache != null) {
        sessionCache.terminate();
        sessionCache = null;
      }

      if (sessionPool != null) {
        sessionPool.shutdown();
        sessionPool = null;
      }

      if (serverDb != null) {
        serverDb.close();
        serverDb = null;
      }

      connectionManager.shutdown();
      connectionManager = null;

      if (isRunningInContainer()) {
        // deregister all JDBC drivers loaded by the server app's classloader
        deregisterJdbcDrivers(Thread.currentThread().getContextClassLoader());
      }
    }
    catch (RuntimeException anyEx) {
      LOGGER.severe("web server stopped ungracefully", anyEx);
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
