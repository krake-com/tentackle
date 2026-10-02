/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.app.AbstractClientApplication;
import org.tentackle.common.Constants;
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.reflect.ClassMappedListener;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.PasswordCredentials;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCache;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Properties;

/**
 * Tentackle JavaFX desktop application.<br>
 * Launches the {@link FxApplication} (by default the {@link LoginApplication}), performs the login
 * in a background service while showing the progress in the login view, and finally opens the main
 * window with the main controller.
 * <p>
 * The modification tracker runs with its own session, a clone of the login session grouped with it.
 *
 * @author harald
 * @param <C> the main controller type
 */
public abstract class DesktopApplication<C extends FxController>
       extends AbstractClientApplication
       implements ClassMappedListener {

  /** minimum milliseconds a status message should be visible to the user. */
  private static final long MESSAGE_MIN_MILLIS = 100;


  private static final Logger LOGGER = Logger.get(DesktopApplication.class);

  private FxApplication fxApplication;        // the FX application instance
  private C mainController;                   // the main controller
  private LoginFailedHandler lfh;             // the login failed handler
  private Stage mainStage;                    // the main stage
  private String lastStatusMessage;           // last status message
  private long lastStatusMillis;              // last epochal millis the status message has changed
  private boolean autoLogin;                  // user is logged in w/o prompting for username/password


  /**
   * Creates an FX desktop application.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for the default version
   */
  public DesktopApplication(String name, String version) {
    super(name, version);
  }

  @Override
  public boolean isInteractive() {
    return true;
  }

  /**
   * Gets the main controller to be displayed initially.<br>
   * Maintains the main scene of the application.
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
   * Returns the autologin flag.
   *
   * @return true if the user is logged in w/o being asked for username/password, default is false
   * @see Constants#BACKEND_AUTOLOGIN
   */
  public boolean isAutoLogin() {
    return autoLogin;
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

  @Override
  public SessionInfo createSessionInfo(String username, char[] password, String sessionPropertiesBaseName) {
    SessionInfo sessionInfo = super.createSessionInfo(username, password, sessionPropertiesBaseName);
    autoLogin = sessionInfo.getProperties().containsKey(Constants.BACKEND_AUTOLOGIN);
    return sessionInfo;
  }

  /**
   * Performs the login and final startup of the application.
   * <p>
   * The login runs in a background service. After a successful login, the application is configured,
   * the main controller is loaded, and the main window is displayed. The login view is hidden
   * when the main window is shown. Failed logins are handled by the {@link LoginFailedHandler}.
   * <p>
   * Notice that this method is invoked from within the FX thread.
   *
   * @param view the view to hide if login succeeded and the application window is visible
   * @param sessionInfo the session info
   */
  public void login(Parent view, SessionInfo sessionInfo) {

    // run the startup from a service-thread to allow updating the FX view
    Service<Void> loginSvc = new Service<>() {

      @Override
      protected Task<Void> createTask() {
        return new Task<>() {

          @Override
          protected Void call() {
            try {
              // with single sign-on the username is unknown until the server verified the credentials
              if (sessionInfo.getCredentials() instanceof PasswordCredentials &&
                  StringHelper.isAllWhitespace(sessionInfo.getUserName())) {
                showApplicationStatus(AppFxRdcBundle.getString("PLEASE ENTER THE USERNAME"), 0.0);
                return null;
              }
              showApplicationStatus(AppFxRdcBundle.getString("CONNECTING TO SERVER..."), 0.05);
              // connect to server/backend
              Session session;

              try {
                session = createSession(sessionInfo);
                // success!
                session.makeCurrent();        // thread from pool: TL session will be cleared below
                setSessionInfo(sessionInfo);
                DomainContext context = createDomainContext(session);
                if (context == null)  {
                  // next round
                  session.getSessionInfo().clearPassword();
                  session.close();
                  session.clearCurrent();
                  return null;
                }
                setDomainContext(context);
                updateSessionInfoAfterLogin();
                Platform.runLater(session::makeCurrent);   // make it known to the FX thread as soon as possible
              }
              catch (Exception ex) {
                if (lfh == null) {
                  lfh = createLoginFailedHandler(view, sessionInfo);
                }
                Runnable runnable = lfh.handle(ex);
                if (runnable != null) {
                  runnable.run();
                }
                Session.setCurrentSession(null);
                return null;
              }

              showApplicationStatus(AppFxRdcBundle.getString("CONFIGURE APPLICATION..."), 0.1);
              try {
                configure();
              }
              catch (RuntimeException ex) {
                Session.setCurrentSession(null);
                throw new TentackleRuntimeException("configure application failed", ex);
              }

              showApplicationStatus(AppFxRdcBundle.getString("LOADING GUI..."), 0.5);
              final C controller = Fx.load(getMainControllerClass());

              showApplicationStatus(AppFxRdcBundle.getString("LAUNCH MAIN WINDOW..."), 0.99);
              showApplicationStatus("", 1.0);   // time to read the last message
              Platform.runLater(() -> {
                try {
                  session.makeCurrent();
                  setMainController(controller);
                  Stage stage = Fx.createStage(Modality.NONE, null);
                  configureMainStage(stage);
                  Scene scene = Fx.createScene(controller.getView());
                  stage.setScene(scene);
                  stage.addEventHandler(WindowEvent.WINDOW_SHOWN, _ -> {
                    // all is fine, close the login scene, the application keeps running since the main window is shown
                    try {
                      finishStartup();
                      view.getScene().getWindow().hide();
                    }
                    catch (RuntimeException ex) {
                      throw new TentackleRuntimeException("finish startup failed", ex);
                    }
                  });
                  Fx.show(stage);
                }
                catch (RuntimeException rex) {
                  String msg = MessageFormat.format(AppFxRdcBundle.getString("LAUNCHING {0} FAILED"),
                                                    getMainControllerClass().getSimpleName());
                  LOGGER.severe(msg, rex);
                  Fx.error(view, msg, rex, () -> view.getScene().getWindow().hide());
                }
              });
            }
            catch (Throwable t) {
              Session.setCurrentSession(null);
              // if anything else fails: log that
              LOGGER.severe("login task failed", t);
              Platform.runLater(() -> Fx.error(view, t.getLocalizedMessage(), t, () -> view.getScene().getWindow().hide()));
            }
            Session.setCurrentSession(null);
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
   * The FX application usually provides a login view and is responsible
   * for spawning the main view after successful login.
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
   * Gets the FX application class.<br>
   * Override to launch an application other than the {@link LoginApplication}.
   *
   * @return the FX application class
   */
  public Class<? extends FxApplication> getApplicationClass() {
    return LoginApplication.class;
  }


  /**
   * Displays a message during login.
   *
   * @param msg the status message
   * @param progress the progress, 0 to disable, negative if infinite, 1.0 if done
   * @param minMillis minimum milliseconds the previous message must have been visible
   */
  public void showApplicationStatus(String msg, double progress, long minMillis) {
    if (fxApplication != null) {
      if (lastStatusMillis == 0) {
        lastStatusMillis = System.currentTimeMillis();
      }

      // make sure the last message was displayed at least minMillis
      if (!Objects.equals(msg, lastStatusMessage)) {
        long now = System.currentTimeMillis();
        long remainingMillis = lastStatusMillis + minMillis - now;
        if (minMillis > 0 && !StringHelper.isAllWhitespace(lastStatusMessage) && remainingMillis > 0) {
          try {
            // show last message at least minMillis
            Thread.sleep(remainingMillis);
          }
          catch (InterruptedException ix) {
            throw new InterruptedRuntimeException(ix);
          }
          lastStatusMillis = now + remainingMillis;
        }
        else {
          lastStatusMillis = now;
        }
        lastStatusMessage = msg;
      }

      Platform.runLater(() -> fxApplication.showApplicationStatus(msg, progress));
    }
    else {
      LOGGER.info(msg);
    }
  }


  /**
   * Displays a message during login.<br>
   * Makes sure that the message is shown at least 100ms.
   *
   * @param msg the status message
   * @param progress the progress, 0 to disable, negative if infinite, 1.0 if done
   */
  public void showApplicationStatus(String msg, double progress) {
    showApplicationStatus(msg, progress, MESSAGE_MIN_MILLIS);
  }


  /**
   * Registers a handler for uncaught exceptions.<br>
   * Applies to all threads and the FX thread.
   */
  public void registerUncaughtExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler(getUncaughtExceptionHandler());             // all threads
    Thread.currentThread().setUncaughtExceptionHandler(getUncaughtExceptionHandler());    // overwrite existing handler for fx thread
  }


  /**
   * Gets the exception handler.<br>
   * The default handler logs the exception and shows it in an error dialog.
   *
   * @return the handler
   */
  public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
    return (_, e) -> {
      LOGGER.severe("unhandled exception detected", e);
      Platform.runLater(() -> {
        try {
          Fx.error(mainStage, e.getLocalizedMessage(), e);
        }
        catch (Throwable x) {
          stop(99, x);    // there is something severely wrong -> terminate application immediately
        }
      });
    };
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create a DomainContext with a thread-local session.
   * <p>
   * In desktop client apps there are 2 threads using their own session:
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
  public void applyProperties(Properties properties) {
    super.applyProperties(properties);
    ThemeUtilities.getInstance().applyThemePreferences(getThemePreferences());
  }

  /**
   * Gets the configured dark mode and theme preferences.<br>
   * They can be configured via command line or user preferences.
   * If the dark mode is not configured, it is determined automatically according
   * to the desktop settings.
   *
   * @return the theme preferences, with {@code darkMode()} null if automatic, TRUE if fixed dark mode,
   * FALSE if fixed light mode
   */
  public ThemeUtilities.ThemePreferences getThemePreferences() {
    String darkModeStr = getPropertyIgnoreCase(ThemeUtilities.DARK_MODE);
    Boolean darkMode = darkModeStr == null ? null : Boolean.valueOf(darkModeStr);
    String themeName = getPropertyIgnoreCase(ThemeUtilities.THEME_NAME);
    return ThemeUtilities.getInstance().loadPreferences(getClass(), darkMode, themeName);
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
    Session trackerSession = getSession().clone("MT");          // already open
    trackerSession.groupWith(getSession().getSessionId());      // build group
    ModificationTracker.getInstance().setSession(trackerSession);
  }

  @Override
  protected void finishStartup() {
    super.finishStartup();
    registerColorSchemeListener();
    showApplicationStatus(AppFxRdcBundle.getString("SUCCESSFULLY LOGGED IN"), 1.0);
  }

  /**
   * Registers a listener to update the dark mode of all windows
   * if configuration is automatic.
   */
  protected void registerColorSchemeListener() {
    Platform.getPreferences().colorSchemeProperty().subscribe(colorScheme -> {
      if (getThemePreferences().darkMode() == null) {
        ThemeUtilities.getInstance().setDarkMode(colorScheme == ColorScheme.DARK);
      }
    });
  }

  @Override
  protected void initializeScripting() {
    super.initializeScripting();
    PdoFactory.getInstance().getPersistenceMapper().addListener(this);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Whenever a PDO class is used the first time, all field validators are prepared in the background,
   * which pre-compiles the validator scripts, if any.
   * This usually improves UX snappiness.
   * <p>
   * The default implementation adds a {@link org.tentackle.task.Task} to the {@link ModificationTracker}.
   */
  @Override
  public void classMapped(String name, Class<?> clazz) {
    ModificationTracker.submit(() -> {
      Class<?> pdoClass = PdoFactory.getInstance().create(name).getEffectiveClass();
      LOGGER.fine("validating validators of {0}", pdoClass);
      for (Validator validator : ValidatorCache.getInstance().getFieldValidators(pdoClass)) {
        validator.validate();
      }
    });
  }


  @Override
  protected void cleanup() {
    super.cleanup();
    logStatistics();
    Fx.terminate();
  }

}
