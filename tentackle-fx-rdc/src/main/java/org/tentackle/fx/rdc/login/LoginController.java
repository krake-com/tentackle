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

package org.tentackle.fx.rdc.login;

import org.tentackle.fx.FxController;
import org.tentackle.session.SessionInfo;

/**
 * Interface for the login view controller.<br>
 * Allows applications to replace the login view, see {@code LoginApplication#loadLoginController()}.
 */
public interface LoginController extends FxController {

  /**
   * Sets the initial focus.
   */
  void requestInitialFocus();

  /**
   * Sets the session info.
   *
   * @param sessionInfo the session info
   */
  void setSessionInfo(SessionInfo sessionInfo);

  /**
   * Logs in automatically.<br>
   * The user cannot edit the username and password.
   */
  void autoLogin();

  /**
   * Sets the progress.
   *
   * @param progress &le; 0 login not begun yet, &ge; 1 for completed
   */
  void setProgress(double progress);

  /**
   * Sets the message field.
   *
   * @param message the message
   */
  void setMessage(String message);

  /**
   * Performs the login.
   */
  void login();

}
