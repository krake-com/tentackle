/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.app;

import java.text.MessageFormat;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxUtilities;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Java FX tentackle desktop application.
 *
 * @author harald
 * @param <C> the main controller type
 */
public abstract class DesktopApplication<C extends FxController> extends AbstractApplication {

  /**
   * Gets the running desktop application.<br>
   *
   * @return the application
   */
  public static DesktopApplication<?> getDesktopApplication() {
    return (DesktopApplication) getRunningApplication();
  }


  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DesktopApplication.class);

  private FxApplication fxApplication;        // the FX application instance
  private C mainController;                   // the main controller
  private CommandLine cmdLine;                // command line
  private LoginFailedHandler lfh;             // the login failed handler
  private Stage mainStage;                    // the main stage

  /**
   * Creates an FX desktop application.
   *
   * @param name the application name
   */
  public DesktopApplication(String name) {
    super(name);
  }

  /**
   * Gets the main-controller to be displayed initially.<br>
   * Maintains the main-scene of the application.
   *
   * @return the main controller class
   */
  public abstract Class<? extends C> getMainControllerClass();


  /**
   * Configures and sets the main stage.
   * <p>
   * If overridden, make sure to invoke super.configureMainStage!
   *
   * @param mainStage the main stage
   */
  public void configureMainStage(Stage mainStage) {
    this.mainStage = mainStage;
  }

  /**
   * Gets the main stage.
   *
   * @return the stage
   */
  public Stage getMainStage() {
    return mainStage;
  }


  /**
   * Sets the main controller instance.
   *
   * @param mainController the main controller
   */
  public void setMainController(C mainController) {
    this.mainController = mainController;
  }

  /**
   * Gets the main controller.
   *
   * @return the main controller
   */
  public C getMainController() {
    return mainController;
  }


  /**
   * Creates the login failed handler.
   *
   * @param view the view
   * @param sessionInfo the session info
   * @return the handler
   */
  public LoginFailedHandler createLoginFailedHandler(Parent view, SessionInfo sessionInfo) {
    return new LoginFailedHandler(this, view, sessionInfo);
  }


  /**
   * Performs the login and final startup of the application.
   * <p>
   * The method must hide the view when the main application windows is displayed
   * after successful login.
   * <p>
   * Notice that this method is invoked from within the FX thread.
   *
   * @param view the view to hide if login succeeded and application window visible
   * @param sessionInfo the session info
   */
  public void doLogin(Parent view, SessionInfo sessionInfo) {

    // run the startup from a service-thread to allow updating the FX view
    Service<Void> loginSvc = new Service<Void>() {

      @Override
      protected Task<Void> createTask() {
        return new Task<Void>() {

          @Override
          protected Void call() throws Exception {
            if (StringHelper.isAllWhitespace(sessionInfo.getUserName())) {
              showApplicationStatus(AppFxRdcBundle.getString("PLEASE ENTER THE USERNAME"), 0.0);
              return null;
            }
            showApplicationStatus(AppFxRdcBundle.getString("CONNECTING TO SERVER..."), 0.0);
            // connect to server/backend
            Session session = createSession(sessionInfo);
            try {
              session.open();
              // success!
              session.makeCurrent();
              setSessionInfo(sessionInfo);
              DomainContext context = createDomainContext(session);
              if (context == null)  {
                // next round
                session.getSessionInfo().clearPassword();
                session.close();
                return null;
              }
              setDomainContext(context);
              updateSessionInfoAfterLogin();
            }
            catch (LoginFailedException lfx) {
              if (lfh == null) {
                lfh = createLoginFailedHandler(view, sessionInfo);
              }
              lfh.handle(lfx);
              return null;
            }
            catch (RuntimeException rex) {
              LOGGER.severe("login failed", rex);
              Platform.runLater(() -> {
                Fx.error(AppFxRdcBundle.getString("LOGIN FAILED!"), rex);
                view.getScene().getWindow().hide();
              });
              return null;
            }

            showApplicationStatus(AppFxRdcBundle.getString("CONFIGURE APPLICATION..."), 0.1);
            try {
              doConfigureApplication();
            }
            catch (RuntimeException | ApplicationException ex) {
              LOGGER.severe("configure application failed", ex);
              return null;
            }

            showApplicationStatus(AppFxRdcBundle.getString("LOADING GUI..."), 0.5);
            final C mainController = Fx.load(getMainControllerClass());

            showApplicationStatus(AppFxRdcBundle.getString("LAUNCH MAIN WINDOW..."), 1.0);
            Platform.runLater(() -> {
              try {
                session.makeCurrent();
                setMainController(mainController);
                Stage stage = Fx.createStage(Modality.NONE);
                configureMainStage(stage);
                Scene scene = new Scene(mainController.getView());
                stage.setScene(scene);
                stage.addEventHandler(WindowEvent.WINDOW_SHOWN, (e) -> {
                  // all is fine, close the login scene, application keeps running since main window is shown
                  try {
                    doFinishStartup();
                    view.getScene().getWindow().hide();
                  }
                  catch (RuntimeException | ApplicationException ex) {
                    LOGGER.severe("finish startup failed", ex);
                  }
                });
                stage.show();
                // if all is shown: preload other controller singletons
                Platform.runLater(() -> FxFactory.getInstance().preloadControllers());
              }
              catch (RuntimeException rex) {
                String msg = MessageFormat.format(AppFxRdcBundle.getString("LAUNCHING {0} FAILED"),
                                                  ReflectionHelper.getClassBaseName(getMainControllerClass()));
                LOGGER.severe(msg, rex);
                Fx.error(msg, rex);
                view.getScene().getWindow().hide();
              }
            });

            return null;
          }
        };
      }
    };

    loginSvc.start();
  }



  /**
   * Gets the FX application instance.<br>
   * This is the instance of the class provided by {@link #getApplicationClass()}.<br>
   * The FX-application ususally provides a login-view and is responsible
   * to spawn the main view after successful login.
   *
   * @return the FX application
   */
  public FxApplication getFxApplication() {
    return fxApplication;
  }

  /**
   * Sets the FX application instance.
   *
   * @param fxApplication the FX application
   */
  public void setFxApplication(FxApplication fxApplication) {
    this.fxApplication = fxApplication;
  }


    /**
   * Gets the FX application class.
   *
   * @return the fx application class
   */
  public Class<? extends FxApplication> getApplicationClass() {
    return LoginApplication.class;
  }


  /**
   * Displays a message during login.
   *
   * @param msg the status message
   * @param progress the progress, 0 to disable, negative if infinite, 1.0 if done
   */
  public void showApplicationStatus(String msg, double progress) {
    if (fxApplication != null) {
      Platform.runLater(() -> fxApplication.showApplicationStatus(msg, progress));
      try {
        // wait some time to show...
        Thread.sleep(100);
      }
      catch (InterruptedException ix) {
        // ignore
      }
    }
    else {
      LOGGER.info(msg);
    }
  }


  /**
   * Registers a handler for uncaught exceptions.
   */
  public void registerUncaughtExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler(getUncaughtExceptionHandler());
  }


  /**
   * Gets the exception handler.
   *
   * @return the handler
   */
  public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
    return (t, e) -> {
      LOGGER.severe("unhandled exception detected", e);
      Platform.runLater(() -> Fx.error(AppFxRdcBundle.getString("UNEXPECTED EXCEPTION"), e));
    };
  }

  @Override
  public boolean isServer() {
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create a DomainContext with a thread-local session.
   * <p>
   * In deskop client apps there are 2 threads using their own session:
   * <ol>
   * <li>the FX thread</li>
   * <li>the PdoTracker thread</li>
   * </ol>
   * By using the thread-local session, PDOs can be used from both threads
   * without having to worry about the correct session.
   *
   * @return the domain context
   */
  @Override
  public DomainContext createDomainContext(Session session) {
    return super.createDomainContext(null);   // thread-local immutable context
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
      doInitialize();

      LOGGER.fine("initializing FX application");
      Application.launch(getApplicationClass(), args);

    }
    catch (ApplicationException | RuntimeException ex) {
      // print message to user, if GUI in window, else if headless to console
      try {
        Fx.error(MessageFormat.format(AppFxRdcBundle.getString("LAUNCHING {0} FAILED"), getName()), ex);
      }
      catch (RuntimeException rex) {
        // ignore if dialog cannot be displayed
      }
      // doStop with error
      doStop(3, ex);
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
    catch (RuntimeException | ApplicationException e) {
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
  public synchronized CommandLine getCommandLine() {
    return cmdLine;
  }


  /**
   * Installs the preferences backend.<br>
   * The option {@code "systemprefs"} forces usage of system preferences only.
   */
  @Override
  protected void configurePreferences() {
    showApplicationStatus(AppFxRdcBundle.getString("INSTALLING PREFERENCES..."), 0.3);
    super.configurePreferences();
  }

  @Override
  protected void configureSecurityManager() {
    showApplicationStatus(AppFxRdcBundle.getString("CONFIGURE SECURITY..."), 0.4);
    super.configureSecurityManager();
  }

  @Override
  protected void configurePdoTracker() {
    showApplicationStatus(AppFxRdcBundle.getString("CONFIGURE MONITORING..."), 0.2);
    super.configurePdoTracker();
    Session trackerSession = getSession().clone();          // already open
    trackerSession.groupWith(getSession().getSessionId());  // build group
    PdoTracker.getInstance().setSession(trackerSession);
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

    FxUtilities.getInstance().addTentackleStyleSheet();

    // add a shutdown handler in case the modthread terminates unexpectedly
    PdoTracker.getInstance().addShutdownRunnable(() -> {
      if (!PdoTracker.getInstance().isTerminationRequested()) {
        LOGGER.severe("*** emergency shutdown ***");
        stop();
      }
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
      LOGGER.log(Level.SEVERE,
                 "application " + getName() + " abnormally terminated with exit code " + exitValue,
                 ex);
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
    catch (RuntimeException rex) {
      LOGGER.severe("FX application stopped ungracefully", rex);
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
