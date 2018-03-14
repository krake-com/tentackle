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

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.security.SecurityFactory;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionProvider;


/**
 * Base class for all kinds of Tentackle applications.
 *
 * @author harald
 */
public abstract class AbstractApplication implements SessionProvider, DomainContextProvider {

  private static final Logger LOGGER = Logger.get(AbstractApplication.class);

  private static AbstractApplication running = null;   // != null if an application is running in this JVM (classloader)

  /**
   * Gets the (singleton) application instance currently running.
   * <p>
   * Notice: the method is not synchronized because no serious application
   * will invoke getAbstractApplication() before getting itself up and running.
   * So, we leave off these singleton maniac codings here.
   *
   * @return the application, null if no application started
   */
  public static AbstractApplication getRunningApplication() {
    return running;
  }




  private String name;                        // the application's name
  private Properties props;                   // the application's properties
  private CommandLine cmdLine;                // command line
  private DomainContext context;              // the server's connection context
  private SessionInfo sessionInfo;            // server's user info
  private boolean runsInContainer;            // true if application is running within a container (tomcat, EE)
  private boolean stopping;                   // true if application is stopping
  private final long creationTime;            // creation time in epochal milliseconds


  /**
   * Super constructor for all derived classes.<br>
   * Detects whether application is running within a container or deployed by JNLP (webstart).
   *
   * @param name the application name
   */
  public AbstractApplication(String name) {
    setName(name);
    detectContainer();
    creationTime = System.currentTimeMillis();
  }


  /**
   * Gets the application name.
   *
   * @return the application name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the application name.
   *
   * @param name the name
   */
  protected void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the creation time in epochal milliseconds.
   *
   * @return the creation time of this application
   */
  public long getCreationTime() {
    return creationTime;
  }

  /**
   * Gets the application's name.
   * @return the name
   */
  @Override
  public String toString() {
    return getName();
  }


  /**
   * Gets the command line.
   *
   * @return the commandline, null if not started
   */
  public CommandLine getCommandLine() {
    return cmdLine;
  }


  /**
   * Logs a stackdump.
   * <p>
   * The logging level used is WARNING.<br>
   * Can be used for example from a groovy console at runtime.
   */
  public void logStackdump() {
    LOGGER.warning(DiagnosticUtilities.getInstance().createStackDump());
  }


  /**
   * Returns whether the application is a server.
   *
   * @return true if server, false if a client or nothing of both
   */
  public abstract boolean isServer();


  /**
   * Registers the application.<br>
   * Makes sure that only one application is running at a time.
   * Should be invoked in the start-method.
   * <p>
   * Throws TentackleRuntimeException if an application is already running.
   */
  protected void register() {
    synchronized(AbstractApplication.class) {   // not getClass() because this applies to all kinds of applications!
      if (running != null) {
        throw new TentackleRuntimeException(MessageFormat.format(AppCoreBundle.getString("APPLICATION {0} ALREADY RUNNING"), running));
      }
      running = this;
    }
  }

  /**
   * Unregisters the application.<br>
   * Makes sure that only one application is running at a time.
   * Should be invoked in the stop-method.
   * <p>
   * Throws TentackleRuntimeException if no application is running.
   */
  protected void unregister() {
    synchronized(AbstractApplication.class) {   // not getClass() because this applies to all kinds of applications!
      if (running != null) {
        if (running != this) {
          throw new TentackleRuntimeException(MessageFormat.format(AppCoreBundle.getString("APPLICATION {0} IS NOT RUNNING"), this));
        }
        running = null;
      }
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


  /**
   * Indicates whether this application is running within a container.
   *
   * @return true if running in a container, false if not
   */
  public boolean isRunningInContainer() {
    return runsInContainer;
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
   * Sets the properties to configure the application.
   * <p>
   * Must be set before starting the application.
   *
   * @param props the properties to configure the application
   */
  protected void setProperties(Properties props) {
    this.props = props;
  }

  /**
   * Gets the current properties.
   *
   * @return the properties
   */
  public Properties getProperties() {
    return props;
  }

  /**
   * Gets a property.
   *
   * @param key the property's name
   * @return the value of the key, null if no such property, the empty string if no value for this property.
   */
  public String getProperty(String key) {
    return props == null ? null : props.getProperty(key);
  }


  /**
   * Gets the session.
   *
   * @return the session, null if not yet configured
   */
  @Override
  public Session getSession() {
    return context == null ? null : context.getSession();
  }


  /**
   * Sets the domain context.
   *
   * @param context the context
   */
  protected void setDomainContext(DomainContext context) {
    this.context = context;
  }


  /**
   * Gets the domain context.
   *
   * @return the domain context
   */
  @Override
  public DomainContext getDomainContext() {
    return context;
  }


  /**
   * Gets the session info.
   *
   * @return the session info
   */
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  /**
   * Sets the user info.
   *
   * @param sessionInfo the session info
   */
  protected void setSessionInfo(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }


  /**
   * Gets the AbstractPersistentObject corresponding to the object-ID of a user entity.
   * <p>
   * Should be overridden if application provides a user entity.
   * The default implementation returns null.
   *
   * @param <U> the pdo type
   * @param context the domain context
   * @param userId the user id
   * @return the user object, null if unknown
   */
  public abstract <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId);


  /**
   * Gets the AbstractPersistentObject corresponding to the userId in {@link SessionInfo}.
   * <p>
   * The default implementation invokes {@link #getUser(org.tentackle.pdo.DomainContext, long) }.
   *
   * @param <U> the pdo type
   * @param context the domain context
   * @return the logged in user object, null if unknown
   */
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context) {
    return getUser(context, sessionInfo == null ? 0 : sessionInfo.getUserId());
  }



  /**
   * Creates the sessionInfo.<br>
   * Presets attributes like locale, timezone, vm-, os- and host-info.
   *
   * @param username is the name of the user
   * @param password is the password, null if none
   * @param sessionPropertiesBaseName the resource bundle basename of the property file, null if default
   * @return the sessionInfo
   */
  public SessionInfo createSessionInfo(String username, char[] password, String sessionPropertiesBaseName) {
    SessionInfo info = Pdo.createSessionInfo(username, password, sessionPropertiesBaseName);

    // sets some infos about locale, vm, etc...
    info.setLocale(Locale.getDefault());
    info.setTimeZone(TimeZone.getDefault());

    Properties sysProps = System.getProperties();
    info.setVmInfo(sysProps.getProperty("java.version") + " (" +
                   sysProps.getProperty("java.vm.name") + ")");

    info.setOsInfo(sysProps.getProperty("os.name") +  " (" +
                   sysProps.getProperty("os.version") + " / " +
                   sysProps.getProperty("os.arch") + ")");

    String str = System.getenv("COMPUTERNAME"); // windoze
    if (str == null) {
      str = System.getenv("HOSTNAME");    // unix and derivates
    }
    if (str == null) {
      // last chance
      try {
        str = InetAddress.getLocalHost().getHostName();
      }
      catch (UnknownHostException ex) {
        str = "<unknown>";
      }
    }
    info.setHostInfo(str);

    return info;
  }


  /**
   * Creates a session.
   *
   * @param sessionInfo the session info
   * @return the open session
   */
  public Session createSession(SessionInfo sessionInfo) {
    return Pdo.createSession(sessionInfo);
  }


  /**
   * Creates the domain context.<br>
   * Override this method if the application uses a subclass of DomainContext.
   *
   * @param session the session, null if thread-local
   * @return the domain context
   */
  public DomainContext createDomainContext(Session session) {
    DomainContext ctx = Pdo.createDomainContext(session, true);
    session = ctx.getSession();   // replace by thread local if session was null
    if (session.isRemote()) {
      SessionInfo remoteInfo = session.getRemoteSession().getClientSessionInfo();
      // set the user's object and class id determined by the remote server
      SessionInfo localInfo = session.getSessionInfo();
      localInfo.setUserId(remoteInfo.getUserId());
      localInfo.setUserClassId(remoteInfo.getUserClassId());
    }
    return ctx;
  }


  /**
   * Configures the modification tracker singleton.<br>
   */
  protected void configureModificationTracker() {
    ModificationTracker tracker = ModificationTracker.getInstance();
    tracker.setSession(getSession());
  }


  /**
   * Configures the preferences.
   * <p>
   * If the property {@code "readonlyprefs"} is set, any write attempt to the preferences
   * will be silently ignored.<br>
   * The property {@code "noprefsync"} turns off preferences auto sync between jvms.<br>
   * The property {@code "systemprefs"} restricts to system preferences. Default is user
   * and system prefs.
   */
  protected void configurePreferences() {
    // install preferences handler to use the db as backing store
    PersistedPreferencesFactory.getInstance().setReadOnly(getProperty("readonlyprefs") != null);
    PersistedPreferencesFactory.getInstance().setSystemOnly(getProperty("systemprefs") != null);
    PersistedPreferencesFactory.getInstance().setAutoSync(getProperty("noprefsync") == null);
  }


  /**
   * Configures the security manager.
   */
  protected void configureSecurityManager() {
    SecurityFactory.getInstance().getSecurityManager().setEnabled(true);
  }


  /**
   * Initializes the application.
   * <p>
   * This is the first step when an application is launched.
   * <p>
   * The default implementation first parses the properties for system properties. System properties start with
   * {@literal "SYSTEM_"} followed by the original property name.<br>
   * Then the Java security manager is installed if the property {@literal "secman=...."} is given. Overrides should
   * invoke super.initialize() and perform any other necessary steps before construction of the GUI.
   * <p>
   * Throws TentackleRuntimeException if initialization failes.
   */
  protected void initialize() {
    // set system properties
    if (props != null) {
      for (String key: props.stringPropertyNames()) {
        if (key.startsWith("SYSTEM_")) {
          System.setProperty(key.substring(7), props.getProperty(key));
        }
      }
    }

    // install optional security manager
    String secman = getProperty("secman");   // NOI18N
    if (secman != null && !secman.isEmpty()) {
      try {
        System.setSecurityManager((java.lang.SecurityManager)(Class.forName(secman).getDeclaredConstructor().newInstance()));
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
             NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        throw new TentackleRuntimeException(AppCoreBundle.getString("CAN'T INSTALL JAVA RUNTIME SECURITY MANAGER"), ex);
      }
    }

    // switch locale
    String localeStr = getProperty("locale");
    if (localeStr != null && !localeStr.isEmpty()) {
      String language = null;
      String country = null;
      String variant = null;
      StringTokenizer stok = new StringTokenizer(localeStr, "_");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        if (language == null) {
          language = token;
        }
        else if (country == null) {
          country = token;
        }
        else if (variant == null) {
          variant = token;
        }
        else {
          break;
        }
      }

      Locale locale = null;
      if (variant != null) {
        locale = new Locale(language, country, variant);
      }
      else if (country != null) {
        locale = new Locale(language, country);
      }
      else if (language != null) {
        locale = new Locale(language);
      }
      if (locale != null) {
        Locale.setDefault(locale);
      }
    }
  }


  /**
   * Do anything what's necessary after the connection has been established.<br>
   * The default creates the modification thread (but does not start it),
   * and configures the preferences and security manager.
   */
  protected void configure() {
    configureModificationTracker();
    configurePreferences();
    configureSecurityManager();
  }


  /**
   * Finishes the startup.<br>
   * The default implementation starts the modification thread, unless
   * the property {@literal "nomodthread"} is given.
   */
  protected void finishStartup() {

    // start the modification thread
    if (getProperty("nomodthread") == null) {
      ModificationTracker.getInstance().start();
      // add a shutdown handler in case the modthread terminates unexpectedly
      ModificationTracker.getInstance().addShutdownRunnable(() -> {
        if (!ModificationTracker.getInstance().isTerminationRequested()) {
          LOGGER.severe("*** emergency shutdown ***");
          AbstractApplication.this.stop(2, null);
        }
      });
    }
    else  {
      PdoCache.setAllEnabled(false);    // disable caching globally
    }
  }



  /**
   * Invokes all steps to startup the application.<br>
   * Invoked from {@link #start(java.lang.String[])}.
   */
  protected abstract void startup();


  /**
   * Starts the application server.
   *
   * @param args the arguments (usually from commandline)
   */
  public void start(String[] args) {
    cmdLine = new CommandLine(args);
    setProperties(cmdLine.getOptionsAsProperties());
    try {
      startup();
    }
    catch (RuntimeException e) {
      // stop with error
      stop(1, e);
    }
  }


  /**
   * Starts the application without further arguments.
   */
  public void start() {
    start(null);
  }


  /**
   * Terminates the application.
   *
   * @param exitValue the stop value for System.exit()
   * @param ex an exception causing the termination, null if none
   */
  public void stop(int exitValue, Exception ex) {

    synchronized(this) {
      if (stopping) {
        return;
      }
      stopping = true;
    }

    LOGGER.info("terminating {0} with exit value {1} ...", getName(), exitValue);
    if (ex != null) {
      LOGGER.logStacktrace(ex);
    }

    try {
      cleanup();

      if (isRunningInContainer()) {
        // deregister all JDBC drivers loaded by the server app's classloader
        deregisterJdbcDrivers(Thread.currentThread().getContextClassLoader());
      }
    }
    catch (Exception anyEx) {
      LOGGER.severe(getName() + " stopped ungracefully", anyEx);
    }

    if (!isRunningInContainer()) {
      System.exit(exitValue);
    }
  }


  /**
   * Gracefully terminates the application server.
   */
  public void stop() {
    try {
      stop(0, null);
    }
    catch (RuntimeException rx) {
      LOGGER.logStacktrace(rx);
    }
    finally {
      try {
        unregister();
      }
      catch (RuntimeException ex) {
        LOGGER.logStacktrace(ex);
      }
    }
  }


  /**
   * Cleans up resources.<br>
   * Invoked from {@link #stop(int, java.lang.Exception)
   */
  protected void cleanup() {
    Pdo.terminateHelperThreads();

    Session session = getSession();
    if (session != null) {
      session.close();
    }
  }

}
