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

package org.tentackle.app;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.ModuleInfo;
import org.tentackle.common.ModuleSorter;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.OperationInvocationHandler;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoCacheOperation;
import org.tentackle.pdo.PdoInvocationHandler;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;
import org.tentackle.security.SecurityFactory;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * Base class for all kinds of Tentackle applications.
 *
 * @author harald
 */
public abstract class AbstractApplication implements Application {

  /**
   * Property to disable the modification tracker.
   */
  public static final String DISABLE_MODIFICATION_TRACKER = "notracker";

  /**
   * Property to disable the tentackle security manager.
   */
  public static final String DISABLE_SECURITY_MANAGER = "nosecurity";

  /**
   * Property to enable statistics.
   */
  public static final String ENABLE_STATISTICS = "statistics";

  /**
   * Property to set the default scripting language.
   */
  public static final String SCRIPTING = "scripting";

  /**
   * Property to set the default locale.
   */
  public static final String LOCALE = "locale";


  private static final Logger LOGGER = Logger.get(AbstractApplication.class);



  private final String name;                  // the application's name
  private final String version;               // the application's version
  private final long creationTime;            // creation time in epochal milliseconds

  private EncryptedProperties props;          // the application's properties
  private CommandLine cmdLine;                // command line
  private DomainContext context;              // the server's connection context
  private SessionInfo sessionInfo;            // server's session info
  private boolean stopping;                   // true if application is stopping


  /**
   * Super constructor for all derived classes.<br>
   * Detects whether application is running within a container or deployed by JNLP (webstart).
   *
   * @param name the application name, null for default name
   * @param version the application version, null for default version
   *
   * @see #filterName(String)
   * @see #filterVersion(String)
   */
  public AbstractApplication(String name, String version) {
    this.name = filterName(name);
    this.version = filterVersion(version);
    creationTime = System.currentTimeMillis();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> LOGGER.severe("uncaught exception", e));
  }


  /**
   * Gets the application name.
   *
   * @return the name, never null
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * Filters the application name.
   *
   * @param name the name given by the constructor
   * @return the filtered name, never null
   */
  protected String filterName(String name) {
    if (name == null) {
      Class<?> applicationClass = getClass();
      while (applicationClass != null && (name = applicationClass.getSimpleName()).isEmpty()) {
        // empty name means anonymous inner class (often used in unit tests, for example)
        applicationClass = applicationClass.getSuperclass();
      }
    }
    return name;
  }


  /**
   * Gets the application version.
   *
   * @return the version, never null
   */
  @Override
  public String getVersion() {
    return version;
  }

  /**
   * Filters the application version.
   *
   * @param version the version given by the constructor
   * @return the filtered version, never null
   */
  protected String filterVersion(String version) {
    return version == null ? "1.0.0-SNAPSHOT" : version;
  }


  /**
   * Gets the creation time in epochal milliseconds.
   *
   * @return the creation time of this application
   */
  @Override
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
  @Override
  public CommandLine getCommandLine() {
    return cmdLine;
  }

  /**
   * Logs a stackdump.
   * <p>
   * The logging level used is INFO.<br>
   * Can be used for example from a groovy console at runtime.
   */
  public void logStackdump() {
    DiagnosticUtilities.getInstance().logStackDump(Logger.Level.INFO, "");
  }


  /**
   * Returns whether the application is a server.
   *
   * @return true if server, false if a client or nothing of both
   */
  @Override
  public boolean isServer() {
    return false;
  }

  /**
   * Returns whether the running application is interactive.
   *
   * @return true if interaction with user, false if server, daemon or whatever
   */
  public boolean isInteractive() {
    return false;
  }


  /**
   * Sets the properties to configure the application.
   * <p>
   * Must be set before starting the application.
   *
   * @param props the properties to configure the application
   */
  protected void setProperties(EncryptedProperties props) {
    this.props = props;
  }

  /**
   * Gets the current properties.
   *
   * @return the properties
   */
  protected EncryptedProperties getProperties() {
    return props;
  }

  /**
   * Applies the given properties.<br>
   * If the given properties are different from the application properties, they will be copied to
   * the application properties.
   * <p>
   * The default implementation first parses the properties for system properties and replaces
   * any system properties according to {@link StringHelper#evaluate(String, Function)}.<br>
   * System properties start with {@code SYSTEM_} or {@code ^} followed by the property name.
   * <p>
   * Other well-known properties that can be set:
   * <p>
   * {@code scripting=...} sets the default scripting language
   * <p>
   * {@code nosecurity} disables the tentackle security manager
   * <p>
   * {@code locale=...} sets the default locale.
   *
   * @param properties the properties, null if none
   */
  @Override
  public void applyProperties(Properties properties) {
    if (properties != null) {
      // translate variables, set system properties and copy the rest
      Function<String, String> systemVariableProvider = System.getProperties()::getProperty;
      for (String propName : properties.stringPropertyNames()) {
        String propValue = properties.getProperty(propName);
        if (propValue != null) {  // null should not happen, but multi threading...
          String processedValue = StringHelper.evaluate(propValue, systemVariableProvider);
          if (!propValue.equals(processedValue)) {
            LOGGER.fine("property {0}: {1} evaluated to {2}", propName, propValue, processedValue);
            propValue = processedValue;
            if (properties == props) {
              props.setProperty(propName, processedValue);
            }
            // else override below if not already set via commandline
          }
          String sysKey = null;
          if (propName.startsWith("SYSTEM_")) {
            sysKey = propName.substring(7);
          }
          else if (propName.startsWith("^")) {
            sysKey = propName.substring(1);
          }
          if (sysKey != null) {
            System.setProperty(sysKey, propValue);
            if (LOGGER.isFineLoggable()) {
              LOGGER.fine("system property {0} set to {1}", sysKey, propValue);
            }
            else {
              LOGGER.info("system property {0} set", sysKey);  // don't log the value, could be a password...
            }
          }
          else if (props != null && props != properties && !props.containsKey(propName)) { // cmd props take precedence
            props.setProperty(propName, propValue);
            LOGGER.fine("property {0} set to {1}", propName, propValue);
          }
        }
      }

      String scripting = getPropertyIgnoreCase(SCRIPTING);
      if (scripting != null) {
        ScriptFactory.getInstance().setDefaultLanguage(scripting);
      }

      if (getPropertyIgnoreCase(DISABLE_SECURITY_MANAGER) != null) {
        SecurityFactory.getInstance().getSecurityManager().setEnabled(false);
      }

      // set default locale
      String localeStr = getPropertyIgnoreCase(LOCALE);
      if (StringHelper.isAllWhitespace(localeStr)) {
        try {
          // try to get from user preferences.
          // The application may provide a feature to set this via the UI, for example.
          localeStr = Preferences.userNodeForPackage(getClass()).get(LOCALE, "");
        }
        catch (RuntimeException rx) {
          LOGGER.warning("cannot retrieve locale from user preferences", rx);
        }
      }
      if (!StringHelper.isAllWhitespace(localeStr)) {
        Locale locale = LocaleProvider.getInstance().fromTag(localeStr);
        if (locale != null) {
          if (LocaleProvider.getInstance().isLocaleSupported(locale)) {
            Locale.setDefault(locale);
          }
          else {
            LOGGER.warning("locale {0} not supported", locale);
          }
        }
      }
      LOGGER.info("default locale is {0}", Locale.getDefault());

    }
  }

  @Override
  public String getProperty(String key) {
    return props == null ? null : props.getProperty(key);
  }

  @Override
  public String getPropertyIgnoreCase(String key) {
    return props == null ? null : props.getPropertyIgnoreCase(key);
  }

  @Override
  public char[] getPropertyAsChars(String key) {
    return props == null ? null : props.getPropertyAsChars(key);
  }

  @Override
  public char[] getPropertyAsCharsIgnoreCase(String key) {
    return props == null ? null : props.getPropertyAsChars(props.getKeyIgnoreCase(key));
  }

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
  @Override
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  /**
   * Sets the session info.
   *
   * @param sessionInfo the session info
   */
  protected void setSessionInfo(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }


  /**
   * Creates the sessionInfo.<br>
   * Presets the attributes like locale, timezone, vm-, os- and host-info.
   *
   * @param username is the name of the user
   * @param password is the password, null if none
   * @param sessionPropertiesBaseName the resource bundle basename of the property file, null if default
   * @return the sessionInfo
   */
  public SessionInfo createSessionInfo(String username, char[] password, String sessionPropertiesBaseName) {
    SessionInfo info = Pdo.createSessionInfo(username, password, sessionPropertiesBaseName);

    info.setClientVersion(getVersion());    // necessary for remote sessions, just info if local

    // sets some infos about locale, vm, etc...
    info.setLocale(Locale.getDefault());
    info.setTimeZone(TimeZone.getDefault());

    Properties sysProps = System.getProperties();
    info.setVmInfo(Runtime.version() + " (" +
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

    if (info.getApplicationName() == null) {
      info.setApplicationName(getName());
    }

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
    session = ctx.getSession();   // replace by thread-local if session was null
    if (session.isRemote()) {
      SessionInfo remoteInfo = session.getRemoteSession().getClientSessionInfo();
      // set the user's object and class id determined by the remote server
      SessionInfo localInfo = session.getSessionInfo();
      boolean immutable = localInfo.isImmutable();
      if (immutable) {
        localInfo.setImmutable(false);    // in case of a remote server application
      }
      localInfo.setUserId(remoteInfo.getUserId());
      localInfo.setUserClassId(remoteInfo.getUserClassId());
      if (remoteInfo.getUserName() != null) {
        // with single sign-on, the client does not know the username before the login
        localInfo.setUserName(remoteInfo.getUserName());
      }
      localInfo.setImmutable(immutable);  // restore immutable flag if changed
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
    PersistedPreferencesFactory.getInstance().setReadOnly(getPropertyIgnoreCase("readonlyprefs") != null);
    PersistedPreferencesFactory.getInstance().setSystemOnly(getPropertyIgnoreCase("systemprefs") != null);
    PersistedPreferencesFactory.getInstance().setAutoSync(getPropertyIgnoreCase("noprefsync") == null);
  }


  /**
   * Configures the security manager.
   */
  protected void configureSecurityManager() {
    SecurityFactory.getInstance().getSecurityManager().setEnabled(true);
  }


  /**
   * Initializes the application.<br>
   * This is the first step when an application is launched.
   */
  protected void initialize() {

    applyProperties(props);

    initializeScripting();

    // log module order
    List<ModuleInfo> infos = ModuleSorter.INSTANCE.getModuleInfos();
    if (infos.isEmpty()) {
      LOGGER.info("no module hooks found");
    }
    else {
      StringBuilder buf = new StringBuilder();
      buf.append(infos.size()).append(" module hooks found:");
      for (ModuleInfo info: infos) {
        buf.append('\n').append(info);
      }
      LOGGER.info(buf.toString());
    }
  }


  /**
   * Initializes the scripting.
   */
  protected void initializeScripting() {
    // set the default scripting language, if not already set and exactly one provided
    if (ScriptFactory.getInstance().getDefaultLanguage() == null) {
      Set<ScriptingLanguage> languages = ScriptFactory.getInstance().getLanguages();
      if (languages.size() == 1) {
        ScriptFactory.getInstance().setDefaultLanguage(languages.iterator().next());
      }
    }
  }


  /**
   * Do anything what's necessary after the connection has been established.<br>
   * The default creates the modification tracker (but does not start it),
   * and configures the preferences and security manager.
   */
  protected void configure() {
    configureModificationTracker();
    configurePreferences();
    configureSecurityManager();
  }


  /**
   * Finishes the startup.<br>
   * The default implementation starts the modification tracker, unless the property {@literal "notracker"} is given.<br>
   * The property {@literal "statistics"} activates the statistics.
   */
  protected void finishStartup() {

    if (getProperties().containsKey(ENABLE_STATISTICS)) {
      activateStatistics();
    }

    // start the modification tracker
    if (!getProperties().containsKey(DISABLE_MODIFICATION_TRACKER)) {
      // Runs in a parallel thread on a cloned session for remote applications or
      // for local interactive applications (desktop).
      // In all other cases, it runs on the main thread.
      CountDownLatch latch = preloadCaches();

      ModificationTracker.getInstance().start();
      // add a shutdown handler in case the modification tracker terminates unexpectedly
      ModificationTracker.getInstance().addShutdownRunnable(() -> {
        if (ModificationTracker.getInstance().isTerminationRequested()) {
          LOGGER.info("termination requested");
          AbstractApplication.this.stop(0, null);
        }
        else {
          LOGGER.severe("*** emergency shutdown ***");
          AbstractApplication.this.stop(2, null);
        }
      });

      if (isServer() && latch != null) {
        // don't bring up the remote services before the preload has finished,
        // since the clients preload as well and expect the preloading caches
        // already initialized. Otherwise, the first client has to wait too long.
        try {
          LOGGER.info("waiting for cache preload to finish ...");
          latch.await();
          LOGGER.info("cache preload finished: continuing with server startup");
        }
        catch (InterruptedException e) {
          throw new InterruptedRuntimeException("cache preload interrupted", e);
        }
      }
    }
    else  {
      PdoCache.setAllEnabled(false);    // disable caching globally
    }
  }

  /**
   * Preloads the caches.<br>
   * Invoked from {@link #finishStartup()} after initializing the modification tracker.
   *
   * @return a countdown latch that can be used to wait for the preload to finish, null if no parallel download
   */
  protected CountDownLatch preloadCaches() {
    // don't use op() since the context's session is not set and the session is not thread-local (yet)
    return Pdo.createOperation(PdoCacheOperation.class, getDomainContext().clone(getSession())).preloadPdoCaches();
  }

  /**
   * Activate statistics.<br>
   * Recommended during development.
   */
  protected void activateStatistics() {
    PdoInvocationHandler.INVOKER.setCollectingStatistics(true);
    OperationInvocationHandler.INVOKER.setCollectingStatistics(true);
  }

  /**
   * Logs and clears the statistics.
   */
  public void logStatistics() {
    PdoInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, true);
    OperationInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, true);
  }


  /**
   * Invokes all steps to start up the application.<br>
   * Invoked from {@link #start(java.lang.String[])}.
   */
  protected abstract void startup();


  @Override
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


  @Override
  public void stop(int exitValue, Throwable exitThrowable) {
    synchronized (this) {
      if (stopping) {
        return;
      }
      stopping = true;
    }

    try {
      LOGGER.info("terminating {0} with exit value {1} ...", getName(), exitValue);
      if (exitThrowable != null) {
        LOGGER.logStacktrace(exitThrowable);
      }

      try {
        cleanup();
      }
      catch (Exception anyEx) {
        LOGGER.severe(getName() + " stopped ungracefully", anyEx);
      }
    }
    finally {
      try {
        unregister();
      }
      catch (RuntimeException ex) {
        LOGGER.logStacktrace(ex);
      }
      if (isSystemExitNecessaryToStop()) {
        System.exit(exitValue);
      }
    }
  }


  /**
   * Returns whether System.exit() must be invoked to stop the application.
   *
   * @return true if JVM must be terminated
   */
  protected boolean isSystemExitNecessaryToStop() {
    return true;
  }

  /**
   * Cleans up resources.<br>
   * Invoked from {@link #stop(int, java.lang.Throwable)}
   */
  protected void cleanup() {
    Pdo.terminateHelperThreads();

    Session session = getSession();
    if (session != null) {
      session.close();
    }
  }

}
