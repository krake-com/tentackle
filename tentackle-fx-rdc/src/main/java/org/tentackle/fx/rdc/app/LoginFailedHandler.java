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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.fx.Fx;
import org.tentackle.log.Logger;
import org.tentackle.session.AlreadyLoggedInException;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.CredentialsExpiredException;
import org.tentackle.session.auth.CredentialsProvider;
import org.tentackle.session.auth.PasswordCredentials;

import javafx.application.Platform;
import javafx.scene.Parent;
import java.text.MessageFormat;

/**
 * To handle login failed exceptions.
 *
 * @author harald
 */
public class LoginFailedHandler {

  private static final Logger LOGGER = Logger.get(LoginFailedHandler.class);

  private final DesktopApplication<?> application;  // the desktop application
  private final Parent view;                        // the FX view
  private final SessionInfo sessionInfo;            // the session info

  private int loginCount;                           // number of login attempts
  private boolean renewed;                          // true if the credentials have been renewed already

  /**
   * Creates a login failed handler.
   *
   * @param application the application
   * @param view the view
   * @param sessionInfo the session info
   */
  public LoginFailedHandler(DesktopApplication<?> application, Parent view, SessionInfo sessionInfo) {
    this.application = application;
    this.view = view;
    this.sessionInfo = sessionInfo;
  }

  /**
   * Gets the application.
   *
   * @return the application
   */
  public DesktopApplication<?> getApplication() {
    return application;
  }

  /**
   * Gets the view.
   *
   * @return the view
   */
  public Parent getView() {
    return view;
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
   * Handles the login failed exception.
   *
   * @param ex the login failed exception
   * @return null if retry login, else a runnable to be executed within the login task
   */
  public Runnable handle(Exception ex) {
    LOGGER.severe(ex.getMessage(), ex);

    if (ex instanceof CredentialsExpiredException) {
      Runnable retry = renewCredentials();
      if (retry != null) {
        return retry;   // renewed: this doesn't count as a login attempt
      }
      // could not renew: fall through and treat it like any other refused login
    }

    if (ex instanceof AlreadyLoggedInException) {
      application.showApplicationStatus(ex.getLocalizedMessage(), 0.0);
    }
    else if (ex instanceof VersionIncompatibleException vx) {
      Platform.runLater(() -> Fx.info(view, MessageFormat.format(AppFxRdcBundle.getString("client version {0} does not match server version {1}"),
                                                                 vx.getClientVersion(), vx.getServerVersion()), () -> view.getScene().getWindow().hide()));
    }
    else if (ex instanceof LoginFailedException){
      if (application.isAutoLogin()) {
        loginCount = 3;
      }
      else {
        loginCount++;
      }
      switch (loginCount) {
        case 1 -> application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED! (2 MORE RETRIES)"), 0.0);
        case 2 -> application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED! (LAST RETRY)"), 0.0);
        default -> {
          application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED!"), 0.0);
          // there is no username and password to check unless the credentials are password-based
          String message = sessionInfo.getCredentials() instanceof PasswordCredentials
                           ? AppFxRdcBundle.getString("LOGIN REFUSED! PLEASE CHECK YOUR USERNAME AND PASSWORD")
                           : AppFxRdcBundle.getString("LOGIN REFUSED!");
          // this will close the login scene and terminate the application
          Platform.runLater(() -> Fx.info(view, message, () -> view.getScene().getWindow().hide()));
        }
      }
    }
    else {
      LOGGER.severe("login failed", ex);
      Platform.runLater(() -> {
        if (application.getFxApplication() != null) {
          application.getFxApplication().setStopReason(98, ex);
        }
        Fx.error(view,
                 AppFxRdcBundle.getString("LOGIN FAILED!") +
                 "\n" + ExceptionHelper.concatenateMessages(ex), ex,
                 () -> view.getScene().getWindow().hide());
        // the application terminates when the error window is closed
      });
    }

    return null;
  }


  /**
   * Renews expired credentials and retries the login.
   * <p>
   * Asks the configured {@link CredentialsProvider} to refresh the credentials, which for a
   * token-based method means using the refresh token instead of bothering the user again.
   * Only tried once per handler, so that a server insisting on "expired" cannot spin the client.
   *
   * @return the retry, null if the credentials could not be renewed
   */
  protected Runnable renewCredentials() {
    if (renewed) {
      return null;
    }
    renewed = true;
    Credentials credentials;
    try {
      credentials = CredentialsProvider.getInstance(sessionInfo).refresh(sessionInfo.getCredentials());
    }
    catch (RuntimeException rx) {
      LOGGER.warning("could not renew the credentials", rx);
      return null;
    }
    if (credentials == null) {
      return null;
    }
    sessionInfo.setCredentials(credentials);
    application.showApplicationStatus(AppFxRdcBundle.getString("RENEWING CREDENTIALS..."), 0.05);
    return () -> Platform.runLater(() -> application.login(view, sessionInfo));
  }

}
