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

package org.tentackle.fx.rdc.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.tentackle.app.AbstractClientApplication;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxUtilities;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.text.MessageFormat;

/**
 * Java FX tentackle desktop application.
 *
 * @author harald
 * @param <C> the main controller type
 */
public abstract class DesktopApplication<C extends FxController> extends AbstractClientApplication {

  /**
   * Gets the running desktop application.<br>
   *
   * @return the application
   */
  public static DesktopApplication<?> getDesktopApplication() {
    return (DesktopApplication) getRunningApplication();
  }


  private static final Logger LOGGER = Logger.get(DesktopApplication.class);

  private FxApplication fxApplication;        // the FX application instance
  private C mainController;                   // the main controller
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
  public void login(Parent view, SessionInfo sessionInfo) {

    // run the startup from a service-thread to allow updating the FX view
    Service<Void> loginSvc = new Service<>() {

      @Override
      protected Task<Void> createTask() {
        return new Task<>() {

          @Override
          protected Void call() throws Exception {
            try {
              if (StringHelper.isAllWhitespace(sessionInfo.getUserName())) {
                showApplicationStatus(AppFxRdcBundle.getString("PLEASE ENTER THE USERNAME"), 0.0);
                return null;
              }
              showApplicationStatus(AppFxRdcBundle.getString("CONNECTING TO SERVER..."), 0.0);
              // connect to server/backend
              Session session;
              try {
                session = createSession(sessionInfo);
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
                configure();
              }
              catch (RuntimeException ex) {
                LOGGER.severe("configure application failed", ex);
                return null;
              }

              showApplicationStatus(AppFxRdcBundle.getString("LOADING GUI..."), 0.5);
              final C mainCtrlr = Fx.load(getMainControllerClass());

              showApplicationStatus(AppFxRdcBundle.getString("LAUNCH MAIN WINDOW..."), 1.0);
              Platform.runLater(() -> {
                try {
                  session.makeCurrent();
                  setMainController(mainCtrlr);
                  Stage stage = Fx.createStage(Modality.NONE);
                  configureMainStage(stage);
                  Scene scene = new Scene(mainCtrlr.getView());
                  stage.setScene(scene);
                  stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
                    // all is fine, close the login scene, application keeps running since main window is shown
                    try {
                      finishStartup();
                      view.getScene().getWindow().hide();
                    }
                    catch (RuntimeException ex) {
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
            }
            catch (RuntimeException rex) {
              // if anything else fails: log that
              LOGGER.severe("login task failed", rex);
              Platform.runLater(() -> {
                Fx.error(rex.getLocalizedMessage(), rex);
                Platform.exit();
              });
            }

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
        Thread.sleep(50);
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
      Platform.runLater(() -> {
        try {
          Fx.error(AppFxRdcBundle.getString("UNEXPECTED EXCEPTION"), e);
        }
        catch (Throwable x) {
          LOGGER.severe("cannot show error dialog", x);
          System.exit(99);    // there is something severe wrong -> terminate application immediately
        }
      });
    };
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create a DomainContext with a thread-local session.
   * <p>
   * In deskop client apps there are 2 threads using their own session:
   * <ol>
   * <li>the FX thread</li>
   * <li>the ModificationTracker thread</li>
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

  @Override
  protected void startup() {
    LOGGER.fine("register application");
    // make sure that only one application is running at a time
    register();

    LOGGER.fine("initialize application");
    initialize();

    LOGGER.fine("initializing FX application");
    Application.launch(getApplicationClass(), getCommandLine().getArgs());
  }

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
  protected void configureModificationTracker() {
    showApplicationStatus(AppFxRdcBundle.getString("CONFIGURE MONITORING..."), 0.2);
    super.configureModificationTracker();
    Session trackerSession = getSession().clone();          // already open
    trackerSession.groupWith(getSession().getSessionId());  // build group
    ModificationTracker.getInstance().setSession(trackerSession);
  }

  @Override
  protected void finishStartup() {
    super.finishStartup();
    // tentackle styles and PDO textfields
    FxUtilities.getInstance().addStyleSheets();
  }

}
