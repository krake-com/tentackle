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

import org.tentackle.app.Application;
import org.tentackle.common.Constants;
import org.tentackle.common.Cryptor;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.Fx;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.rdc.login.Login;
import org.tentackle.fx.rdc.login.LoginController;
import org.tentackle.log.Logger;
import org.tentackle.misc.CommandLine;
import org.tentackle.session.SessionInfo;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.SplashScreen;
import java.util.Locale;

/**
 * The login FX application.
 * <p>
 * Displays the login view, centered on the splash screen if any, and applies the theme preferences.
 * The username and password may be preset via the command line, the backend properties, or the
 * process environment. With auto-login, the user is logged in immediately.
 *
 * @author harald
 */
public class LoginApplication extends FxApplication {

  private static final Logger LOGGER = Logger.get(LoginApplication.class);


  private LoginController controller;       // the login controller


  /**
   * Creates the login application.
   */
  public LoginApplication() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  public void startApplication(Stage stage) {
    try {
      ThemeUtilities.ThemePreferences themePreferences = ((DesktopApplication<?>) Application.getInstance()).getThemePreferences();
      if (themePreferences.darkMode() == null) {
        themePreferences = themePreferences.withDarkMode(Platform.getPreferences().getColorScheme() == ColorScheme.DARK);
      }
      ThemeUtilities.getInstance().applyThemePreferences(themePreferences);

      controller = loadLoginController();
      SessionInfo sessionInfo = createSessionInfo();
      controller.setSessionInfo(sessionInfo);
      Scene scene = Fx.createScene(controller.getView());
      stage.setScene(scene);
      stage.initStyle(StageStyle.TRANSPARENT);

      SplashScreen splashScreen = GraphicsEnvironment.isHeadless() ? null : SplashScreen.getSplashScreen();
      Point2D center = determineLoginWindowCenter(splashScreen);
      // this does the trick to avoid flickering on the screen due to the window first displayed and then moved
      stage.widthProperty().addListener((_, _, newWidth) ->
                                          stage.setX(center.getX() - newWidth.doubleValue() / 2.0));
      stage.heightProperty().addListener((_, _, newHeight) ->
                                           stage.setY(center.getY() - newHeight.doubleValue() / 2.0));

      stage.setOnShown(_ -> {
        if (splashScreen != null) {
          Platform.runLater(splashScreen::close);
        }
        if (((DesktopApplication<?>) Application.getInstance()).isAutoLogin()) {
          controller.autoLogin();   // log in w/o option to edit the user and/or password
        }
        else if (sessionInfo.getUserName() != null && sessionInfo.getPassword() != null) {
          controller.login();       // perform a login once, allow the user to edit if the login fails
        }
      });
      Fx.show(stage);
      stage.setResizable(false);
      controller.requestInitialFocus();
    }
    catch (RuntimeException rx) {
      throw new RdcRuntimeException("cannot start login application", rx);
    }
  }

  @Override
  public void showApplicationStatus(String msg, double progress) {
    if (controller.getView().isVisible()) {
      controller.setMessage(msg);
      controller.setProgress(progress);
    }
    else {
      LOGGER.info(msg);
    }
  }

  /**
   * Creates the session info to be passed to the login controller.<br>
   * The username and password are taken from the command line, the backend properties,
   * or the process environment, in that order. The username defaults to the system's user name.
   *
   * @return the session info
   */
  public SessionInfo createSessionInfo() {
    DesktopApplication<?> application = (DesktopApplication<?>) Application.getInstance();
    CommandLine commandLine = application.getCommandLine();
    String username = commandLine.getOptionValue(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(commandLine.getOptionValue(Constants.BACKEND_PASSWORD));
    String sessionPropsName = commandLine.getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = application.createSessionInfo(username, password, sessionPropsName);
    sessionInfo.setSessionName("FX");

    // provide user and password from backend properties, if given
    EncryptedProperties props = sessionInfo.getProperties();
    if (username == null) {   // if not overridden by the command line
      sessionInfo.setUserName(props.getProperty(Constants.BACKEND_USER, sessionInfo.getUserName()));
    }
    if (password == null) {   // if not overridden by the command line: get from properties, if set
      sessionInfo.setPassword(props.getPropertyAsChars(Constants.BACKEND_PASSWORD));
    }

    sessionInfo.applyProperties();

    if (sessionInfo.getUserName() == null) {
      updateSessionInfoFromEnvironment(sessionInfo);
    }
    if (sessionInfo.getUserName() == null) {
      sessionInfo.setUserName(System.getProperty("user.name"));
    }
    application.applyProperties(sessionInfo.getProperties());

    return sessionInfo;
  }


  /**
   * Determines the center of the login window.
   *
   * @param splashScreen the splash screen, null for none or headless mode
   * @return the center where to position the login window
   */
  protected Point2D determineLoginWindowCenter(SplashScreen splashScreen) {
    Screen screen = Screen.getPrimary();
    LOGGER.info("primary screen: {0}", screen);
    Rectangle2D screenSize = screen.getVisualBounds();
    double screenCenterX = screenSize.getWidth() / 2.0;
    double screenCenterY = screenSize.getHeight() / 2.0;
    // try to match the center of the splash screen if available and not in headless mode
    if (splashScreen != null) {
      Rectangle splashBounds = splashScreen.getBounds();
      // check the plausibility of the coordinates since there is a bug in AWT (at least on Linux)
      // that returns wrong bounds if fractional desktop scaling is enabled
      if (splashBounds.getX() <= screenCenterX && screenCenterX <= splashBounds.getMaxX() &&
          splashBounds.getY() <= screenCenterY && screenCenterY <= splashBounds.getMaxY()) {
        // the splash screen covers the center of the screen -> coordinates are correct
        return new Point2D(splashBounds.getX() + splashBounds.getWidth() / 2.0,
                           splashBounds.getY() + splashBounds.getHeight() / 2.0);
      }
      else {
        LOGGER.warning("possibly wrong splash screen coordinates: {0}, AWT screen bounds: {1}",
                       splashBounds,
                       GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds());
      }
    }
    return new Point2D(screenCenterX, screenCenterY);
  }

  /**
   * Updates the session with the username and password from the process environment.
   *
   * @param sessionInfo the session info to update
   */
  protected void updateSessionInfoFromEnvironment(SessionInfo sessionInfo) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      // Get user/passwd from the environment.
      // Must be encrypted to provide a minimum level of security.
      // Used by the auto-update feature when restarting the updated application
      // (see org.tentackle.fx.rdc.update.LoginFailedWithUpdateHandler.passSessionInfoViaEnvironment)
      String envPrefix = StringHelper.toAsciiLetterOrDigit(Application.getInstance().getName());
      try {
        String encUser = System.getenv((envPrefix + "_" + Constants.BACKEND_USER).toUpperCase(Locale.ROOT));
        if (encUser != null) {
          sessionInfo.setUserName(Cryptor.getInstance().decrypt64(encUser));
          String encPass = System.getenv((envPrefix + "_" + Constants.BACKEND_PASSWORD).toUpperCase(Locale.ROOT));
          if (encPass != null) {
            sessionInfo.setPassword(Cryptor.getInstance().decrypt64ToChars(encPass));
          }
        }
      }
      catch (RuntimeException rx) {
        LOGGER.warning("decryption of username/password from environment failed", rx);
      }
    }
  }

  /**
   * Loads the login controller.<br>
   * Applications may override this method to use specific FXML or CSS files.
   *
   * @return the login controller
   */
  protected LoginController loadLoginController() {
    return Fx.load(Login.class);
  }

}
