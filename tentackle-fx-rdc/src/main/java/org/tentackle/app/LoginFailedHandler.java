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
import javafx.application.Platform;
import javafx.scene.Parent;
import org.tentackle.fx.Fx;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.VersionInfoIncompatibleException;

/**
 * To handle login failed exceptions.
 *
 * @author harald
 */
public class LoginFailedHandler {

  private final DesktopApplication<?> application;  // the desktop application
  private final Parent view;                        // the FX view
  private final SessionInfo sessionInfo;            // the session info

  private int loginCount;                           // number of login attempts

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
   */
  public void handle(LoginFailedException ex) {
    if (ex instanceof VersionInfoIncompatibleException) {
      VersionInfoIncompatibleException vx = (VersionInfoIncompatibleException) ex;
      Platform.runLater(() -> {
        Fx.info(MessageFormat.format(AppFxRdcBundle.getString(
                "client version {0} does not match server version {1}"),
                vx.getClientVersion(), vx.getServerVersion()));
        view.getScene().getWindow().hide();
      });
    }
    else {
      loginCount++;
      switch (loginCount) {
        case 1:
          application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED! (2 MORE RETRIES)"), 0.0);
          break;
        case 2:
          application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED! (LAST RETRY)"), 0.0);
          break;
        default:
          application.showApplicationStatus(AppFxRdcBundle.getString("LOGIN FAILED!"), 0.0);
          // this will close the login scene and terminate the application
          Platform.runLater(() -> {
            Fx.info(AppFxRdcBundle.getString("LOGIN REFUSED! PLEASE CHECK YOUR USERNAME AND PASSWORD"));
            view.getScene().getWindow().hide();
          });
      }
    }
  }

}
