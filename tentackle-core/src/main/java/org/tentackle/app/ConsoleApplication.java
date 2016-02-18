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

import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.LoggerOutputStream;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.reflect.ReflectionHelper;



/**
 * Abstract class to handle the application's lifecycle for a console application (no gui).
 * Tentackle applications should extend this class and invoke {@link #start}.
 * To shutdown gracefully, application should invokd {@link #stop}.
 * <p>
 * The subclass just needs to provide a main-method, for example:
 * <pre>
  MyAbstractApplication app = new MyAbstractApplication();
  app.start(args);

  // do something...

  app.stop();
 </pre>
 *
 * @author harald
 */
public abstract class ConsoleApplication extends AbstractApplication {

  /**
   * Gets the running console application.<br>
   *
   * @return the application
   */
  public static ConsoleApplication getConsoleApplication() {
    return (ConsoleApplication) getRunningApplication();
  }

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleApplication.class);



  private CommandLine cmdLine;       // command line
  private String sessionPropsName;   // name of the db-properties file


  /**
   * Creates an application.
   *
   * @param name the application's name
   */
  public ConsoleApplication(String name) {
    super(name);
  }


  @Override
  public boolean isServer() {
    return false;
  }

  /**
   * Creates the modification thread ready for being started.<br>
   * The default implementation creates the modthread with a polling interval of 2 seconds
   * and which runs on the main session.
   * The main-thread in a console application is only used to start the application.
   * Anything else must be done either via Tasks via the PdoTracker or by spawning
   * other threads.
   */
  @Override
  protected void configurePdoTracker() {
    super.configurePdoTracker();
    PdoTracker.getInstance().setSession(getSession());
  }


  /**
   * Launches the application.
   *
   * @param args the arguments (usually from commandline)
   */
  public void start(String[] args) {

    cmdLine = new CommandLine(args);
    setProperties(cmdLine.getOptionsAsProperties());

    try {
      LOGGER.fine("register application");
      // make sure that only one application is running at a time
      register();

      LOGGER.fine("initialize application");
      // doInitialize environment
      doInitialize();

      LOGGER.fine("login to backend");
      // connect to database/application server
      if (doLogin() == null) {
        // no connection, immediately
        System.exit(1);
      }

      LOGGER.fine("configure application");
      // configure the application
      doConfigureApplication();

      LOGGER.fine("finish startup");
      // finish startup
      doFinishStartup();
    }
    catch (Exception e) {
      // print message to user, if GUI in window, else if headless to console
      LOGGER.logStacktrace(e);
      // doStop with error
      doStop(3, e);
    }
  }


  /**
   * Gracefully terminates the application.
   * Usually invoked from an exit-Button or when window is closed.
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


  /**
   * Gets the command line.
   *
   * @return the command line
   */
  public CommandLine getCommandLine() {
    return cmdLine;
  }



  /**
   * Connects to the database backend (or application server).
   *
   * @return the connected context db, null if login aborted or authentication failed
   * @throws ApplicationException if login failed
   */
  protected DomainContext doLogin() throws ApplicationException {

    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    sessionPropsName = cmdLine.getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    sessionInfo.applyProperties();

    if (sessionInfo.getApplicationName() == null) {
      sessionInfo.setApplicationName(ReflectionHelper.getClassBaseName(getClass()));
    }
    setSessionInfo(sessionInfo);
    Session session = createSession(sessionInfo);

    // open the database connection
    try {
      session.open();
    }
    catch (LoginFailedException ex) {
      throw new ApplicationException(AppCoreBundle.getString("LOGIN FAILED"), ex);
    }
    catch (PersistenceException pex) {
      throw pex;
    }

    session.makeCurrent();

    // createPdo the default context
    DomainContext context = createDomainContext(session);
    if (context == null)  {
      throw new ApplicationException(AppCoreBundle.getString("LOGIN REFUSED"));
    }
    setDomainContext(context);

    updateSessionInfoAfterLogin();

    return context;
  }



  /**
   * Finishes the startup.<br>
   * Invoked after all has been displayed.
   * The default implementation starts the modification thread, unless
   * {@code "--nomodthread"} given.
   *
   * @throws ApplicationException if failed
   */
  @Override
  protected void doFinishStartup() throws ApplicationException {

    super.doFinishStartup();

    // add a shutdown handler in case the modthread terminates unexpectedly
    PdoTracker.getInstance().addShutdownRunnable(() -> {
      LOGGER.severe("*** emergency shutdown ***");
      stop();
    });

  }


  /**
   * Terminates the application gracefully.
   * (this is the only do.. method that does not throw AbstractApplicationException)
   *
   * @param exitValue the doStop value for System.exit()
   * @param ex an exception causing the termination, null if none
   */
  protected void doStop(int exitValue, Exception ex) {

    if (exitValue != 0 || ex != null) {
      LOGGER.warning("application {0} abnormally terminated with exit code {1}", getName(), exitValue);
      if (ex != null) {
        LoggerOutputStream.logException(ex, LOGGER);
      }
    }
    else {
      LOGGER.info("application {0} terminated", getName());
    }

    try {

      // terminate all helper threads
      Pdo.terminateHelperThreads();

      // close db
      DomainContext context = getDomainContext();
      if (context != null) {
        Session session = context.getSession();
        if (session != null) {
          session.close();
        }
      }
    }
    catch (Exception anyEx) {
      LOGGER.severe("console application stopped ungracefully", anyEx);
    }

    // terminate runtime
    System.exit(exitValue);
  }


  /**
   * Terminates the application gracefully.
   *
   * @param exitValue the doStop value for System.exit()
   */
  protected void doStop(int exitValue) {
    doStop(exitValue, null);
  }

}
