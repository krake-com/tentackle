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

import org.tentackle.common.ModuleInfo;
import org.tentackle.common.ModuleSorter;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.DiagnosticUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.OperationInvocationHandler;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoInvocationHandler;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;
import org.tentackle.security.SecurityFactory;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionProvider;

import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.function.Function;


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




  private final String name;                  // the application's name
  private final String version;               // the application's version
  private final long creationTime;            // creation time in epochal milliseconds

  private Properties props;                   // the application's properties
  private CommandLine cmdLine;                // command line
  private DomainContext context;              // the server's connection context
  private SessionInfo sessionInfo;            // server's user info
  private boolean stopping;                   // true if application is stopping


  /**
   * Super constructor for all derived classes.<br>
   * Detects whether application is running within a container or deployed by JNLP (webstart).
   *
   * @param name the application name
   * @param version the application version
   */
  public AbstractApplication(String name, String version) {
    this.name = name;
    this.version = version;
    creationTime = System.currentTimeMillis();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> LOGGER.severe("uncaught exception", e));
  }


  /**
   * Gets the application name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the application version.
   *
   * @return the version
   */
  public String getVersion() {
    return version;
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
   * Returns whether the running application is a server.
   *
   * @return true if server, false if not a server or no application running at all
   */
  public static boolean isServer() {
    AbstractApplication application = getRunningApplication();
    return application != null && application.isServerImpl();
  }

  /**
   * Returns whether the application is a server.
   *
   * @return true if server, false if a client or nothing of both
   */
  protected boolean isServerImpl() {
    return false;
  }

  /**
   * Returns whether the running application is interactive.
   *
   * @return true if interaction with user, false if server, daemon or whatever
   */
  public static boolean isInteractive() {
    AbstractApplication application = getRunningApplication();
    return application != null && application.isInteractiveImpl();
  }

  /**
   * Returns whether the application is interactive.
   *
   * @return true if interaction with user, false if server, daemon or whatever
   */
  protected boolean isInteractiveImpl() {
    return false;
  }

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
   * {@code secman=...} installs the Java security manager given by its FQCN.
   * <p>
   * {@code locale=...} sets the default locale.
   *
   * @param properties the properties, null if none
   */
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

      String scripting = getProperty("scripting");
      if (scripting != null) {
        ScriptFactory.getInstance().setDefaultLanguage(scripting);
      }

      // install optional security manager
      String secman = getProperty("secman");   // NOI18N
      if (secman != null && !secman.isEmpty()) {
        try {
          System.setSecurityManager((java.lang.SecurityManager) (Class.forName(secman).getDeclaredConstructor().newInstance()));
          LOGGER.info("security manager {0} installed", secman);
        }
        catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
                   NoSuchMethodException | SecurityException | InvocationTargetException ex) {
          throw new TentackleRuntimeException(AppCoreBundle.getString("CAN'T INSTALL JAVA RUNTIME SECURITY MANAGER"), ex);
        }
      }

      // set default locale
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
          LOGGER.info("default locale set to {0}", locale);
        }
      }
    }
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

    if (getProperties().containsKey("statistics")) {
      activateStatistics();
    }
  }

  /**
   * Activate statistics.<br>
   * Recommended during development.
   */
  protected void activateStatistics() {
    PdoInvocationHandler.INVOKER.setCollectingStatistics(true);
    OperationInvocationHandler.INVOKER.setCollectingStatistics(true);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      PdoInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, true);
      OperationInvocationHandler.INVOKER.logStatistics(Logger.Level.INFO, true);
    }));
  }


  /**
   * Invokes all steps to startup the application.<br>
   * Invoked from {@link #start(java.lang.String[])}.
   */
  protected abstract void startup();


  /**
   * Starts the application.
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
   * @param t an exception causing the termination, null if none
   */
  public void stop(int exitValue, Throwable t) {

    synchronized(this) {
      if (stopping) {
        return;
      }
      stopping = true;
    }

    LOGGER.info("terminating {0} with exit value {1} ...", getName(), exitValue);
    if (t != null) {
      LOGGER.logStacktrace(t);
    }

    try {
      cleanup();
    }
    catch (Exception anyEx) {
      LOGGER.severe(getName() + " stopped ungracefully", anyEx);
    }

    if (isSystemExitNecessaryToStop()) {
      System.exit(exitValue);
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
